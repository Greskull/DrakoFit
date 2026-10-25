# Fase 2 — El servicio es dueño del GPS

**No se ha tocado ni una regla de negocio.** Ni un umbral, ni una fórmula, ni una
condición del filtro. Lo único que cambia es **quién es el dueño del GPS**: antes un
`LaunchedEffect` de `MapScreen`, ahora `TrackingService`.

Los 29 tests de la Fase 1 siguen en verde (`OK (29 tests)`), ejecutados en JVM real.

---

## 1. Archivos

```
NUEVO  app/src/main/java/com/greskull/drakofit/tracking/domain/
         Geo.kt              haversine propio
         FilterConfig.kt     tus umbrales (25 m / 54 km/h / 5 s / 150 m)
         TrackingFilter.kt   el filtro, idéntico a tu lógica, con tests
         SessionMath.kt      las 4 fórmulas de MapScreen, copiadas literales
       app/src/main/java/com/greskull/drakofit/tracking/system/
         LocationMapper.kt   Location -> Fix (reloj monotónico)
       app/src/main/java/com/greskull/drakofit/tracking/
         TrackingState.kt    StateFlow compartido (lo escribe el servicio)
         SessionFinalizer.kt cierre + guardado + XP (un solo sitio)

REESCRITO  app/src/main/java/TrackingService.kt        ahora dueño del GPS
MODIFICADO app/src/main/java/com/greskull/drakofit/DrakoFitApp.kt   engine único
MODIFICADO app/src/main/java/com/greskull/drakofit/MainActivity.kt  decide por sesión activa
MODIFICADO app/src/main/java/com/greskull/drakofit/MapScreen.kt     solo vista
MODIFICADO app/src/main/AndroidManifest.xml            + WAKE_LOCK
NUEVO      app/src/test/java/.../tracking/domain/      FakeGps + 29 tests

FASE2.patch            parche completo (2067 inserciones, 434 borrados)
TrackingService.kt     archivo completo
MapScreen.kt           archivo completo
MainActivity.kt        archivo completo
DrakoFitApp.kt         archivo completo
AndroidManifest.xml    archivo completo
tracking/              paquete completo (fuentes + tests)
```

Aplicar:

```bash
git checkout -b feature/gps-foreground-service
git apply FASE2.patch
./gradlew :app:testDebugUnitTest      # 29 tests
./gradlew :app:assembleDebug
```

> Verificado sintácticamente con `kotlinc` sobre el árbol completo: **0 errores de
> sintaxis**. No tengo Android SDK aquí, así que los ~1900 errores restantes son todos
> `unresolved reference` a `android.*`, `androidx.*`, MapLibre y GMS. Compílalo en
> Android Studio antes de instalar.

---

## 2. Qué hace ahora el servicio

| Antes (`MapScreen`) | Ahora (`TrackingService`) |
|---|---|
| `FusedLocationProviderClient` en un `LaunchedEffect` | en el servicio, con el **mismo** `LocationRequest` (HIGH_ACCURACY, 3500 ms) |
| filtros en el callback | `TrackingFilter` con `FilterConfig()` por defecto = tus valores |
| `rememberSaveable` de tracking | `TrackingState` (StateFlow), sobrevive a la Activity |
| sin WakeLock | **WakeLock parcial** (tope 6 h) |
| notificación genérica | **tiempo y distancia en vivo**, + acciones Pausar/Finalizar |
| `onTaskRemoved` → `stopSelf` a los 2 s (mataba la sesión) | **guarda y cierra** (tu decisión) |
| sospecha detectada por la pantalla | **la cierra el servicio**, la pantalla solo la muestra |

`startForeground()` se llama al principio de `onStartCommand`, con
`FOREGROUND_SERVICE_TYPE_LOCATION`: en Android 14, si tarda, el sistema mata el servicio.

El ticker de 1 s actualiza el cronómetro aunque el GPS tarde 3,5 s entre fixes (igual que
tu `tick++`) y la notificación se refresca como mucho cada 2 s (nunca un `notify()` por fix).

---

## 3. Cierre de sesión: qué pasa exactamente

Todo el cierre pasa por **un solo sitio**, `SessionFinalizer`, para que no puedan guardarse
dos rutas ni perderse una. Reproduce tu orden exacto:

```
si GPS sospechoso  ->  no guarda, no pasos, no XP, no recompensa
si no:
   engine.addSteps(steps)          // (km * 1300).toInt()
   engine.addDistance(km)
   RouteHistoryStorage.saveRoute(route)   // tu filtro: steps<500 || duration<250 s
   if (validRoute) engine.onValidActivityCompleted()
```

Las tres fórmulas viven en `SessionMath`, copiadas carácter a carácter de `MapScreen`
(65 kcal/km, 1300 pasos/km, `"--:--"` por debajo de 10 m, mismo redondeo del ritmo). Están
ahí porque ahora las usan dos consumidores —el HUD y la notificación— y si estuvieran
duplicadas darían números distintos.

**Cierre desde el gestor de apps** (`onTaskRemoved`), tal como lo pediste:

- Si la actividad pasa el filtro de rutas largas → se guarda, suma pasos, distancia, XP y racha.
- Si no lo pasa → se descarta y el proceso muere como si no hubiera existido.
- Si estaba marcada como sospechosa → no se guarda nada.

**GPS sospechoso con el móvil bloqueado**: el servicio cierra la sesión, se apaga y deja el
estado en `FINISHED + SUSPICIOUS_*`. Al abrir la app, `MainActivity` ve
`tracking.isFinished` y muestra `MapScreen` con la tarjeta de "Actividad GPS sospechosa"
durante 5 s, exactamente igual que si lo hubieras visto en directo.

---

## 4. Cambios fuera del tracking (y por qué son inevitables)

1. **`DragonEngine` pasa a la `Application`.** El servicio necesita el mismo motor para
   guardar cuando no hay Activity. Si siguiera creándose en `MainActivity`, habría dos
   instancias escribiendo el mismo SharedPreferences y se pisarían. Sigue siendo **una sola
   instancia por proceso**, con el mismo contexto. `MainActivity` no cambia nada más:
   `engine = (application as DrakoFitApp).engine`.
2. **`MainActivity` deja de usar `inActivity`** (que era un `rememberSaveable`). Ahora la
   pantalla se decide con `tracking.isActive || tracking.isFinished`. Esto es justo lo que
   pedías: reconexión por sesión activa, no por un booleano del Bundle.
3. **`MapScreen` pierde el GPS**: 488 líneas modificadas, de las cuales **~265 son lógica
   borrada** (filtros, reloj, distancia, `Location.distanceBetween`, `FusedLocation...`).
   Los botones ahora mandan órdenes (`TrackingService.pause/resume/finish`).
4. **Manifest**: `WAKE_LOCK`. Nada más. `foregroundServiceType="location"` ya lo tenías.

---

## 5. Pruebas en dispositivo (el orden importa)

1. **Vuelta rápida en primer plano** (5 min andando): distancia, pasos, kcal y ritmo deben
   coincidir con la versión actual en `main`.
2. **Minimizar y volver** a los 2 min: al volver, el cronómetro y la distancia deben ser
   los correctos, no reiniciados.
3. **Bloquear el móvil 30 min** con la app en segundo plano: al desbloquear, la ruta debe
   tener los 30 min. *Esta es la prueba clave de la fase.*
4. **Girar el móvil** y cambiar de app varias veces durante la sesión: nada se reinicia.
5. **Finalizar** → la ruta aparece en el historial, suma pasos/XP y la racha.
6. **Cerrar desde el gestor de apps** con una ruta larga en marcha → al abrir, la ruta está
   guardada y las métricas sumadas.
7. **Cerrar desde el gestor** con una ruta de 1 minuto → no debe aparecer nada.
8. **Coche:** 10 min conduciendo → se cierra como sospechosa (igual que hoy), y si lo haces
   con el móvil bloqueado, al abrir la app debe verse el aviso.
9. **Realme/ColorOS:** repetir 3 y 6 con la batería "sin restricciones" y, si puedes, con la
   restricción puesta, para ver el peor caso.

---

## 6. Límites conocidos de esta fase (van a la Fase 3)

- **Si el proceso muere de verdad** (no solo la Activity), el estado en memoria se pierde:
  la sesión se pierde con él. La persistencia incremental llega en la Fase 3.
- **`START_STICKY`**: si el sistema reinicia el servicio sin estado, se apaga solo en vez de
  fingir que sigue grabando. Es intencionado.
- **`ACCESS_BACKGROUND_LOCATION`** todavía no se pide. Con un FGS de tipo `location`
  arrancado en primer plano, Android sigue entregando fixes en segundo plano, así que la
  prueba 3 debería pasar. Pero es lo que hace que un servicio **reiniciado** por el sistema
  vuelva a recibir GPS; se decide en la Fase 3 con los datos de tus pruebas. Añadirlo luego
  implica también declararlo en Play Console.
- **`SharedPreferences.apply()`** es asíncrono. En el cierre desde el gestor de apps hay una
  ventana teórica de pérdida. Si en tus pruebas ves que alguna ruta no aparece, el cambio es
  una línea: `commit()` en `RouteHistoryStorage.saveRoute` y en `DragonStorage.save`.

---

## 7. Siguiente: Fase 3

Con esta base, la Fase 3 es aditiva, sin volver a tocar la pantalla:

- Volcado incremental de puntos a archivo (16 bytes/punto) + resumen cada 5-10 s, usando
  `TrackingFilter.snapshot()` que ya está implementado y testeado.
- Al volver: **preguntar** (tu decisión) — sesión restaurada en pausa, con "Continuar" o
  "Guardar y cerrar". Nunca reanudar sola, para no meter un salto falso en la distancia.
- Huecos de GPS: activar `enableGapSplitting = true` (ya está en `FilterConfig` y el filtro
  ya devuelve `newSegment`, solo falta que `MapScreen` dibuje una `Path` por tramo).
- Pruebas: `adb shell am kill com.greskull.drakofit` en segundo plano y
  `dumpsys deviceidle force-idle`.
