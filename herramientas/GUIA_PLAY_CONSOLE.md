# Fase 5 — Checklist de publicación en Google Play

Todo lo que hay que revisar antes de subir la versión con el tracking nuevo.

---

## 1. Permisos y declaraciones

### Servicio en primer plano de tipo ubicación

Ya está en el manifest:

```xml
<service
    android:name=".TrackingService"
    android:exported="false"
    android:foregroundServiceType="location" />
```

En Play Console, **App content → Foreground service permissions** hay que
declarar el caso de uso. El nuestro encaja en:

> **Location tracking / Fitness** — «registrar la ruta de una actividad
> deportiva del usuario, incluso con la pantalla apagada».

Prepara una descripción corta (ya está justificado en el manifest y en la UI,
pero Play lo pregunta aparte):

> DrakoFit registra rutas de actividad física al aire libre. El servicio en
> primer plano mantiene el GPS activo mientras el usuario camina o corre con
> la pantalla apagada, para no perder el recorrido. Se inicia solo cuando el
> usuario pulsa «Empezar actividad» y se detiene al finalizarla.

### Permisos que SÍ usa la app

| Permiso | Por qué | Notas para Play |
|---|---|---|
| `ACCESS_FINE_LOCATION` | GPS de la ruta | Uso principal |
| `ACCESS_COARSE_LOCATION` | Respaldo si el usuario da ubicación aproximada | |
| `FOREGROUND_SERVICE` | Servicio de tracking | |
| `FOREGROUND_SERVICE_LOCATION` | Obligatorio desde API 34 | |
| `POST_NOTIFICATIONS` | Notificación de progreso | |
| `WAKE_LOCK` | Evitar que el GPS se duerma con pantalla apagada | |
| `INTERNET` | Mapas y anuncios | |

### Permisos que NO usa (y conviene que siga así)

- **`ACCESS_BACKGROUND_LOCATION`**: no está declarado, y **es correcto**.
  El servicio arranca siempre desde primer plano (el botón «Empezar
  actividad»), y un servicio en primer plano de tipo `location` sigue
  recibiendo fixes en segundo plano sin este permiso. Añadirlo obligaría a
  una revisión adicional de Play y a grabar un vídeo justificativo.
  El caso que deja descubierto —servicio reiniciado por el sistema— ya está
  cubierto por la recuperación de la Fase 3: la sesión se restaura en pausa y
  el usuario decide.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`**: no se declara a propósito. La
  Fase 5 abre la lista general de optimización de batería, que es lo que Play
  permite sin justificación.

### Data safety

Declara en **App content → Data safety**:

- *Location*: recogida, **no compartida** (los mapas son Carto; las
  coordenadas no se envían a ningún servidor propio). Uso: funcionalidad de
  la app.
- *Health and fitness*: pasos, calorías, distancia. Uso: funcionalidad.
- Los datos se guardan **solo en el dispositivo** (SharedPreferences y
  ficheros internos). No hay cuenta ni servidor.

> Ojo con marcar «no se recogen»: si la app usa Google Mobile Ads, la
> política de Play exige declarar los datos que recoge el SDK de anuncios
> aparte. Revisa la sección del SDK de AdMob.

### Política de privacidad

Debe existir una URL pública y estar enlazada en la ficha. Tiene que
mencionar explícitamente:

- Que se accede a la ubicación **en segundo plano** (aunque sea mediante
  servicio en primer plano, es sensato explicarlo).
- Que las rutas se guardan en el dispositivo y **no se envían a ningún
  servidor**.
- Que los datos de actividad (pasos, distancia) son locales.
- Publicidad (AdMob) y qué recoge.

---

## 2. Antes de generar el bundle

- [ ] `versionCode` incrementado (hoy va por 10).
- [ ] `versionName` acorde.
- [ ] Probar en los tres móviles: instalación limpia, no solo actualización.
- [ ] Probar **actualización** desde la versión actual (es el caso real de
      tus usuarios: conservan sus 5 rutas antiguas, sin recorrido).
- [ ] `./gradlew :app:testDebugUnitTest` → 59 tests en verde.
- [ ] `./gradlew :app:assembleRelease` y probar el **APK de release**
      (no solo debug: R8 puede cambiar comportamiento).

---

## 3. Publicación escalonada

1. **20 %** durante 48-72 h.
2. Vigilar en **Android vitals** y en **Crashlytics / Play Console →
   Crashes**:
   - `ForegroundServiceDidNotStartInTimeException` — el servicio no llegó a
     promocionarse. Si aparece, revisar que `startForeground()` siga siendo
     la primera llamada de `onStartCommand`.
   - `ForegroundServiceStartNotAllowedException` — se intentó arrancar el
     servicio desde segundo plano. No debería pasar (solo se arranca desde
     el botón), pero si aparece, es que algo lo lanza desde un receiver.
   - `SecurityException` relacionada con `ACCESS_FINE_LOCATION`.
   - ANR en `TrackingService`.
3. Umbrales de decisión: **tasa de fallos anormal > 1,5 %** o **ANR > 0,5 %**
   → parar el despliegue y publicar el build anterior.
4. Si va limpio: 50 % → 100 %.

Comandos útiles para vigilar en local mientras tanto:

```bash
adb logcat -s DrakoFit AndroidRuntime
adb shell dumpsys activity services com.greskull.drakofit
```

---

## 4. Pruebas largas (obligatorias antes de subir)

Con `herramientas/pruebas_fase5.ps1`:

```
powershell -File herramientas\pruebas_fase5.ps1 -Accion info
powershell -File herramientas\pruebas_fase5.ps1 -Accion preparar
# abrir actividad, luego:
powershell -File herramientas\pruebas_fase5.ps1 -Accion iniciar
# 60 minutos con el móvil bloqueado
powershell -File herramientas\pruebas_fase5.ps1 -Accion finalizar
```

Mínimo: **60 minutos en los tres móviles**, uno de ellos el Redmi del caso
que originó el periodo de gracia.

Criterios de aceptación:

- La ruta conserva los minutos reales (± 1 min).
- Consumo por hora dentro de lo razonable (ver referencias del script).
- Sin huecos en el dibujo que no se correspondan con pérdidas reales de GPS.
- Si se fuerza Doze (`-Accion doze_on`), la sesión sigue.
- Tras `-Accion kill`, al abrir la app aparece el diálogo de recuperación.

---

## 5. Plantilla de notas de la versión

> **Novedades de esta versión**
>
> - El registro de rutas ahora sigue funcionando con la pantalla apagada y la
>   app en segundo plano, sin cortarse.
> - Historial con el recorrido dibujado de cada ruta y mapa interactivo.
> - Las rutas se recuperan si el móvil cierra la app a mitad de actividad.
> - Nuevo apartado «Permisos y batería» con los ajustes recomendados para tu
>   móvil.
> - Las rutas guardadas antes de esta versión se conservan, con sus datos,
>   pero sin recorrido dibujado.
