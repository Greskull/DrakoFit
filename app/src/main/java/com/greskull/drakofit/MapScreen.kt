package com.greskull.drakofit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import org.maplibre.android.maps.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.LineString
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight

enum class FinishReason {
    NORMAL,
    GPS_SUSPICIOUS
}

// 🔥 FIX (punto 7): filtro GPS basado en VELOCIDAD, no en distancia fija.
// Velocidad máxima creíble entre A y el tick siguiente (B).
private const val SPEED_THRESHOLD_MS = 15f          // 54 km/h
// Velocidad de CONFIRMACIÓN entre A y C. 🔥 FIX: antes era el doble (30f =
// 108 km/h) y eso dejaba el filtro prácticamente inútil: al ser una MEDIA
// A→C, un coche a velocidad constante solo se detectaba por encima de
// 108 km/h sostenidos, y entre 54 y 108 km/h no saltaba nunca (además
// sumaba esa distancia como pasos). La protección contra un único fix
// ruidoso ya la da el propio A→B→C (si B es un glitch, la media A→C sale
// baja y se acepta) y el filtro de precisión, no hace falta un umbral doble.
private const val SPEED_CONFIRM_THRESHOLD_MS = 15f  // 54 km/h
// Si el propio GPS reporta un círculo de error peor que esto, el fix no es fiable.
private const val MIN_ACCURACY_TO_TRUST = 25f
// Periodo de gracia tras el primer fix GPS válido: durante estos segundos
// no se evalúa el filtro de velocidad, para dar tiempo al chip GPS a
// estabilizarse (crítico en gama baja, como el Redmi).
private const val FILTER_GRACE_PERIOD_MS = 5000L // 5 segundos

// 🔥 FIX (filtro al reanudar): distancia máxima razonable entre el punto
// donde se pulsó "Pausar" y el primer fix tras pulsar "Reanudar". No es un
// filtro de velocidad (el tiempo de pausa no es significativo: puede ser
// un segundo o media hora), es un tope de distancia puro. 150m da margen
// para moverse un poco durante la pausa sin ser tan laxo como para dejar
// pasar un desplazamiento en coche/transporte.
private const val MAX_RESUME_JUMP_METERS = 150f


val mapRef = mutableStateOf<MapLibreMap?>(null)

@Composable
fun MapScreen(
    context: Context,
    engine: DragonEngine,
    onFinish: (FinishReason) -> Unit
) {

    var finishReason by remember { mutableStateOf<FinishReason?>(null) }
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    val rewardUnlocked = engine.wasRewardUnlocked()
    var lastValidPoint: Point? by remember { mutableStateOf(null) }
    // 🔥 FIX: timestamp del punto A, necesario para calcular velocidad real
    // (distancia / tiempo) en vez de una distancia fija por tick.
    var lastValidTimeMs: Long? by remember { mutableStateOf(null) }
    // Sigue tu lógica A→B→C:
    // B (primer salto) solo levanta sospecha, no cuenta sesión.
    // C se compara SIEMPRE contra A (el último punto válido), no contra B.
    var firstSuspiciousTick by remember { mutableStateOf(false) }
    var trackingStartTimeMs: Long? by remember { mutableStateOf(null) }

    val state by engine.state

    var mapDragonScale by remember(state.mapDragonScale) {
        mutableStateOf(state.mapDragonScale)
    }

    var showSettings by remember {
        mutableStateOf(false)
    }
    var dragonLayerRef by remember {
        mutableStateOf<SymbolLayer?>(null)
    }

    // 🔥 FIX (proceso muerto en segundo plano): startTime, isPaused,
    // pausedAtMs y totalPausedMs pasan a rememberSaveable. Si Android mata
    // el proceso mientras el móvil está bloqueado (típico en Realme/ColorOS
    // incluso con los permisos de batería bien puestos) pero conserva la
    // tarea, estos valores se restauran al volver — y como pasos, distancia,
    // kcal y ritmo se CALCULAN a partir de esto + routePoints (no son
    // contadores aparte), toda la sesión vuelve a ser correcta, no solo el
    // dibujo del mapa.
    var startTime by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    var isPaused by rememberSaveable { mutableStateOf(false) }

    // 🔥 FIX (filtro al reanudar): se activa al pulsar "Reanudar" y se
    // desactiva sola en cuanto llega el primer fix GPS tras la pausa
    // (pase o no el chequeo). Es "remember" normal, no rememberSaveable:
    // si el proceso muriese justo en este instante tan concreto, al volver
    // ya no estaría pausado desde la perspectiva del usuario, así que no
    // hace falta que sobreviva.
    var justResumed by remember { mutableStateOf(false) }

    // pausedAtMs usa -1L como "no pausado" en vez de null: rememberSaveable
    // no necesita un Saver aparte para tipos primitivos simples como Long,
    // pero sí lo complica innecesariamente con nulls.
    var pausedAtMs by rememberSaveable { mutableStateOf(-1L) }
    var totalPausedMs by rememberSaveable { mutableStateOf(0L) }

    // 🔥 FIX (sustituye al Saver de routePoints): un único Float, tamaño
    // fijo pase lo que pase de larga que sea la sesión — cero riesgo de
    // desbordar el Bundle. Se actualiza en cada punto GPS válido (ver el
    // callback de ubicación más abajo) y es la fuente real de la distancia:
    // pasos, kcal y ritmo se calculan a partir de ESTO, no de routePoints.
    var accumulatedDistanceMeters by rememberSaveable { mutableStateOf(0f) }

    var tick by remember { mutableStateOf(0) }

    var lastUserInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    var autoCenterEnabled by remember { mutableStateOf(true) }

    var dragonPosition by remember {
        mutableStateOf(Point.fromLngLat(0.0, 0.0))
    }

    var routeSourceRef by remember { mutableStateOf<GeoJsonSource?>(null) }

    // 🔥 FIX: routePoints vuelve a remember normal — solo sirve para
    // dibujar la línea en el mapa mientras la app está en primer plano.
    // La distancia real ya no depende de esta lista (ver
    // accumulatedDistanceMeters arriba), así que no hay ningún riesgo de
    // Bundle si crece mucho: si el proceso muere, el dibujo se reinicia
    // visualmente al volver, pero los datos (pasos/distancia/kcal/ritmo/
    // tiempo) siguen siendo correctos.
    val routePoints = remember { mutableStateListOf<Point>() }

    var mapViewRef by remember {
        mutableStateOf<MapView?>(null)
    }

    var locationCallback by remember {
        mutableStateOf<LocationCallback?>(null)
    }

    val fusedClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val routeHistoryStorage = remember {
        RouteHistoryStorage(context)
    }

    // ⏱ TIMER TICK (UI REFRESH)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    fun getElapsedTime(): Long {
        // 🔥 FIX: mientras está en pausa, "ahora" se congela en el instante
        // exacto en que se pulsó pausar (pausedAtMs), en vez de seguir
        // avanzando con el reloj real. Como el cronómetro (692) y el Ritmo
        // (778) llaman a esta misma función, ambos quedan congelados de
        // verdad — y de paso, si alguien finalizase la actividad estando
        // en pausa, el dato guardado también sería el correcto, congelado.
        val now = if (isPaused) pausedAtMs else System.currentTimeMillis()
        val base = now - startTime
        return base - totalPausedMs
    }

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    fun getCalories(km: Float) = km * 65f

    fun getSteps(km: Float): Int = (km * 1300).toInt()


    fun getPace(km: Float, ms: Long): String {
        if (km < 0.01f) return "--:--"

        val pace = (ms / 60000f) / km

        val min = pace.toInt()
        val sec = ((pace - min) * 60).toInt()

        return "%d:%02d".format(min, sec)
    }

    //Funcion para RouteHistoryEntry
    LaunchedEffect(finishReason) {

        when (finishReason) {

            FinishReason.NORMAL -> {

                val km = accumulatedDistanceMeters / 1000f

                val sessionSteps = getSteps(km)

                val route = RouteHistoryEntry(

                    distanceKm = km,

                    durationSeconds = (getElapsedTime() / 1000L),

                    steps = sessionSteps,

                    calories = getCalories(km).toInt(),

                    pace = getPace(km, getElapsedTime()),

                    xpEarned = sessionSteps / 100

                )

                // 🎁 Solo las actividades completamente válidas generan recompensas
                val validRoute = routeHistoryStorage.saveRoute(route)

                if (validRoute) {

                    engine.onValidActivityCompleted()

                }

                delay(5000)

                onFinish(FinishReason.NORMAL)
            }


            FinishReason.GPS_SUSPICIOUS -> {

                // 🚫 Actividad anulada por GPS sospechoso:
                // No guardar ruta
                // No sumar pasos
                // No dar XP
                // No generar recompensas

                delay(5000)

                onFinish(FinishReason.GPS_SUSPICIOUS)
            }


            null -> return@LaunchedEffect
        }
    }

    // 🧭 AUTO CENTER (PAUSE AWARE)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)

            if (isPaused) continue

            val map = mapRef.value ?: continue
            val now = System.currentTimeMillis()

            if (!autoCenterEnabled &&
                now - lastUserInteraction > 5000
            ) {
                val current = dragonPosition

                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        org.maplibre.android.geometry.LatLng(
                            current.latitude(),
                            current.longitude()
                        ),
                        17.5
                    )
                )

                autoCenterEnabled = true
            }
        }
    }

    // 📍 GPS
    LaunchedEffect(Unit) {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3500
        ).build()

        locationCallback = object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                if (isPaused) return

                val loc = result.lastLocation ?: return

                // 🔥 FIX: un fix de mala precisión (típico al arrancar en
                // gama baja, o en interiores) no se evalúa como movimiento.
                // Se descarta en silencio, sin contar como sospechoso.
                if (loc.hasAccuracy() && loc.accuracy > MIN_ACCURACY_TO_TRUST) {
                    android.util.Log.d(
                        "DRAKOFIT_GPS",
                        "Fix descartado por baja precisión: accuracy=${loc.accuracy}m (límite=${MIN_ACCURACY_TO_TRUST}m)"
                    )
                    return
                }

                // Al primer fix que pasa el filtro de accuracy, arrancamos el cronómetro
                // del periodo de gracia.
                if (trackingStartTimeMs == null) {
                    trackingStartTimeMs = loc.time
                }

                val withinGracePeriod =
                    (loc.time - (trackingStartTimeMs ?: loc.time)) < FILTER_GRACE_PERIOD_MS

                val newPoint =
                    Point.fromLngLat(
                        loc.longitude,
                        loc.latitude
                    )
                val lastValid = lastValidPoint
                val lastTime = lastValidTimeMs

                // 🔥 FIX (filtro al reanudar): chequeo de UN SOLO USO, solo
                // para el primer fix tras pulsar "Reanudar". Es deliberadamente
                // independiente del filtro de velocidad de más abajo: aquí
                // comparamos solo DISTANCIA (no velocidad), porque el tiempo
                // que ha estado en pausa no dice nada fiable — puede haber
                // sido 1 segundo o 40 minutos, así que dividir por ese tiempo
                // no tendría sentido. Se consume a sí mismo (justResumed pasa
                // a false) tanto si pasa como si no, así que el resto de
                // fixes de la sesión siguen su camino normal sin verse
                // afectados por este bloque.
                if (justResumed) {

                    justResumed = false

                    if (lastValid != null) {

                        val resumeResults = FloatArray(1)

                        android.location.Location.distanceBetween(
                            lastValid.latitude(),
                            lastValid.longitude(),
                            newPoint.latitude(),
                            newPoint.longitude(),
                            resumeResults
                        )

                        val resumeJumpMeters = resumeResults[0]

                        if (resumeJumpMeters > MAX_RESUME_JUMP_METERS) {

                            android.util.Log.d(
                                "DRAKOFIT_GPS",
                                "GPS_SUSPICIOUS al reanudar: salto de ${"%.1f".format(resumeJumpMeters)}m " +
                                    "desde el punto de pausa (límite=${MAX_RESUME_JUMP_METERS}m)"
                            )

                            finishReason = FinishReason.GPS_SUSPICIOUS
                            return
                        }
                    }
                }

                // 🔥 FIX: distancia/tiempo respecto de A se calculan SIEMPRE
                // que haya un punto anterior (incluso en periodo de gracia),
                // porque ahora es la fuente real de accumulatedDistanceMeters.
                // El filtro de velocidad (el 'if' de abajo) sigue evaluándose
                // solo fuera del periodo de gracia, igual que antes.
                var distanceFromA = 0f
                var deltaSeconds = 0f

                if (lastValid != null && lastTime != null) {

                    val results = FloatArray(1)

                    android.location.Location.distanceBetween(
                        lastValid.latitude(),
                        lastValid.longitude(),
                        newPoint.latitude(),
                        newPoint.longitude(),
                        results
                    )

                    // 🔥 FIX: distancia SIEMPRE respecto de A (lastValidPoint),
                    // nunca respecto del tick sospechoso anterior (B).
                    distanceFromA = results[0]
                    deltaSeconds = (loc.time - lastTime) / 1000f
                }

                if (!withinGracePeriod && lastValid != null && lastTime != null) {

                    // Velocidad real A→(B o C). Al usar tiempo real en vez de
                    // "5 segundos" fijos, no importa que el GPS llegue con
                    // intervalos irregulares (muy típico en gama baja).
                    val speedFromA =
                        if (deltaSeconds > 0f) distanceFromA / deltaSeconds else 0f

                    // Sin sospecha activa: umbral base (54 km/h).
                    // Con un B ya sospechoso: el umbral de confirmación en C
                    // sube al doble (108 km/h) para evitar falsos positivos.
                    val threshold =
                        if (firstSuspiciousTick)
                            SPEED_CONFIRM_THRESHOLD_MS
                        else
                            SPEED_THRESHOLD_MS

                    if (speedFromA > threshold) {

                        if (!firstSuspiciousTick) {
                            // B: primera sospecha → se ignora, A sigue siendo A
                            firstSuspiciousTick = true
                            android.util.Log.d(
                                "DRAKOFIT_GPS",
                                "SOSPECHA (tick B): velocidad=${"%.1f".format(speedFromA * 3.6f)} km/h " +
                                    "distancia=${"%.1f".format(distanceFromA)}m en ${"%.1f".format(deltaSeconds)}s " +
                                    "(umbral=${SPEED_THRESHOLD_MS * 3.6f} km/h) — se ignora, esperando confirmación"
                            )
                        } else {
                            // C: confirmado respecto de A → GPS falso o vehículo
                            android.util.Log.d(
                                "DRAKOFIT_GPS",
                                "CONFIRMADO (tick C) -> GPS_SUSPICIOUS: velocidad=${"%.1f".format(speedFromA * 3.6f)} km/h " +
                                    "distancia=${"%.1f".format(distanceFromA)}m en ${"%.1f".format(deltaSeconds)}s " +
                                    "(umbral confirmación=${SPEED_CONFIRM_THRESHOLD_MS * 3.6f} km/h)"
                            )
                            finishReason = FinishReason.GPS_SUSPICIOUS
                        }

                        return
                    }
                }

                // 👇 IMPORTANTE: este es OTRO "last", renombrado
                val lastRoutePoint = routePoints.lastOrNull()

                if (lastRoutePoint != null &&
                    lastRoutePoint.latitude() == newPoint.latitude() &&
                    lastRoutePoint.longitude() == newPoint.longitude()
                ) return

                dragonPosition = newPoint

                mapRef.value?.getStyle { style ->

                    val dragonSource =
                        style.getSourceAs<GeoJsonSource>(
                            "dragon-source"
                        )

                    dragonSource?.setGeoJson(
                        Feature.fromGeometry(newPoint)
                    )
                }

                // 🔥 FIX: punto válido → se limpia la sospecha y A pasa a ser
                // este punto. Si veníamos de un tick B ignorado, aquí se traza
                // la línea directa A→C, saltándose B automáticamente porque
                // B nunca llegó a añadirse a routePoints.
                firstSuspiciousTick = false
                // 🔥 FIX: la distancia real y persistente se acumula aquí,
                // no se recalcula de la lista completa en cada recomposición.
                if (lastValid != null) {
                    accumulatedDistanceMeters += distanceFromA
                }
                lastValidPoint = newPoint
                lastValidTimeMs = loc.time
                routePoints.add(newPoint)

                if (routePoints.size > 1) {

                    val line =
                        LineString.fromLngLats(routePoints)

                    routeSourceRef?.setGeoJson(
                        Feature.fromGeometry(line)
                    )
                }

                mapRef.value?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        org.maplibre.android.geometry.LatLng(
                            loc.latitude,
                            loc.longitude
                        ),
                        17.5
                    )
                )
            }
        }

        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationCallback?.let {
                fusedClient.requestLocationUpdates(
                    request,
                    it,
                    context.mainLooper
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 🗺️ MAP
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->

                val mapView = MapView(ctx)

                mapViewRef = mapView

                mapView.onCreate(null)
                mapView.onStart()
                mapView.onResume()

                mapView.getMapAsync { map ->

                    mapRef.value = map

                    val isDark = engine.state.value.isMapDarkMode

                    val styleUrl = if (isDark) {
                        "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
                    } else {
                        "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
                    }

                    map.setStyle(styleUrl) { style ->

                        // 🟠 ROUTE SOURCE
                        val routeSource = GeoJsonSource(
                            "route-source",
                            org.maplibre.geojson.FeatureCollection.fromFeatures(emptyArray())
                        )

                        style.addSource(routeSource)
                        routeSourceRef = routeSource

                        // 🐉 DRAGON SOURCE
                        val dragonSource = GeoJsonSource(
                            "dragon-source",
                            Feature.fromGeometry(Point.fromLngLat(0.0, 0.0))
                        )

                        style.addSource(dragonSource)

                        // 🐉 DRAGON SPRITE
                        val dragonBitmap =
                            BitmapFactory.decodeResource(
                                context.resources,
                                getDragonSprite(
                                    engine.getCurrentDragon(),
                                    engine.getState().level
                                )
                            )

                        style.addImage("dragon-icon", dragonBitmap)

                        val dragonLayer = SymbolLayer(
                            "dragon-layer",
                            "dragon-source"
                        ).withProperties(
                            PropertyFactory.iconImage("dragon-icon"),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconSize(0.30f * mapDragonScale)
                        )

                        style.addLayer(dragonLayer)

                        dragonLayerRef = dragonLayer

                        // =========================================================
                        // 🔥 ROUTE LAYERS (SCORCHED EFFECT)
                        // =========================================================

                        // 🔴 Outer glow (burned aura)
                        val glowLayer = LineLayer(
                            "route-glow-layer",
                            "route-source"
                        ).apply {
                            setProperties(
                                PropertyFactory.lineColor("#ff2a00"),
                                PropertyFactory.lineWidth(14f),
                                PropertyFactory.lineOpacity(0.25f),
                                PropertyFactory.lineBlur(1.2f)
                            )
                        }

                        // 🟠 Middle heat layer
                        val midLayer = LineLayer(
                            "route-mid-layer",
                            "route-source"
                        ).apply {
                            setProperties(
                                PropertyFactory.lineColor("#ff6a00"),
                                PropertyFactory.lineWidth(8f),
                                PropertyFactory.lineOpacity(0.6f)
                            )
                        }

                        // 🟡 Core hot line
                        val coreLayer = LineLayer(
                            "route-core-layer",
                            "route-source"
                        ).apply {
                            setProperties(
                                PropertyFactory.lineColor("#ffd36a"),
                                PropertyFactory.lineWidth(3.5f),
                                PropertyFactory.lineOpacity(1f)
                            )
                        }

                        // ✨ ORDER IS CRITICAL (bottom → top)
                        style.addLayer(glowLayer)
                        style.addLayer(midLayer)
                        style.addLayer(coreLayer)

                        // =========================================================
                        // ✨ FAKE SPARK EFFECT (NO IMPORTS)
                        // =========================================================

                        val sparkLayer = LineLayer(
                            "route-spark-layer",
                            "route-source"
                        ).apply {
                            setProperties(
                                PropertyFactory.lineColor("#ffcc88"),
                                PropertyFactory.lineWidth(2f),
                                PropertyFactory.lineOpacity(0.12f),
                                PropertyFactory.lineBlur(0.8f)
                            )
                        }

                        style.addLayer(sparkLayer)
                    }
                }
                mapView
            }
        )

        DisposableEffect(Unit) {

            onDispose {

                locationCallback?.let {
                    fusedClient.removeLocationUpdates(it)
                }

                mapViewRef?.onPause()
                mapViewRef?.onStop()
                mapViewRef?.onDestroy()

                mapRef.value = null
                mapViewRef = null
                locationCallback = null
            }
        }

        // ⚙️ BOTÓN AJUSTES

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            IconButton(

                onClick = {

                    showSettings = true

                },

                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = 12.dp,
                        end = 50.dp
                    )

            ) {

                Text(
                    text = "⚙️",
                    fontSize = 28.sp
                )

            }

        }

        // ⏱ TIMER (NOW REACTIVE)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Cronómetro (igual que antes)
                Surface(
                    color = Color(0xAA000000),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = formatTime(getElapsedTime()),
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = 10.dp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Banner independiente
                AdBanner(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 📊 HUD
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            val km = accumulatedDistanceMeters / 1000f
            val sessionSteps = getSteps(km)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xCC0B1B2B)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    // 🔵 PASOS
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("👣", color = Color(0xFF4DA3FF))
                        StatItem("Pasos", getSteps(km).toString())
                    }

                    DividerLine()

                    // 📍 DISTANCIA
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📍", color = Color(0xFF7ED957))
                        StatItem("Distancia", "%.2f km".format(km))
                    }

                    DividerLine()

                    // 🔥 CALORÍAS
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔥", color = Color(0xFFFF8A3D))
                        StatItem("Kcal", getCalories(km).toInt().toString())
                    }

                    DividerLine()

                    // ⏱ RITMO
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⏱", color = Color(0xFFFFC542))
                        StatItem("Ritmo", getPace(km, getElapsedTime()))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Button(
                    onClick = {
                        val now = System.currentTimeMillis()

                        if (!isPaused) {
                            pausedAtMs = now
                        } else {
                            totalPausedMs += (now - (if (pausedAtMs >= 0L) pausedAtMs else now))
                            pausedAtMs = -1L
                            // 🔥 FIX (filtro al reanudar): se arma el chequeo
                            // de distancia para el próximo fix GPS que llegue.
                            justResumed = true
                        }

                        isPaused = !isPaused
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xAA0B1B2B),
                        contentColor = Color(0xFFD4AF37)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFFD4AF37))
                ) {

                    Text(
                        if (isPaused) "▶ Reanudar" else "⏸ Pausar"
                    )
                }

                Button(
                    onClick = {
                        engine.addSteps(sessionSteps)
                        engine.addDistance(km)
                        finishReason = FinishReason.NORMAL
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xAA0B1B2B),
                        contentColor = Color(0xFFE53935)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFFE53935))
                ) {
                    Text("⏹ Finalizar")
                }
            }
        }

        if (finishReason != null) {

            LaunchedEffect(Unit) {
                alpha.snapTo(0f)
                scale.snapTo(0.6f)

                alpha.animateTo(1f, tween(300))
                scale.animateTo(1.15f, tween(250))
                scale.animateTo(1f, tween(150))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {

                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    val title = when (finishReason) {
                        FinishReason.NORMAL -> "¡Bien hecho! 🐉"
                        FinishReason.GPS_SUSPICIOUS -> "Actividad GPS sospechosa ⚠️"
                        null -> ""
                    }

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 28.sp,
                        modifier = Modifier.scale(scale.value)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Actividad finalizada",
                        color = Color.White.copy(alpha = alpha.value),
                        fontSize = 16.sp
                    )
                    if (rewardUnlocked) {

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "🎁 ¡Recompensa desbloqueada!",
                            color = Color(0xFFE53935).copy(alpha = alpha.value),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )

                    }
                }
            }
        }

        if (showSettings) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {

                Card(

                    shape = RoundedCornerShape(20.dp),

                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xAA1E2F45)
                    )

                ) {

                    Column(

                        modifier = Modifier.padding(24.dp),

                        horizontalAlignment = Alignment.CenterHorizontally

                    ) {

                        Text(
                            text = "Ajustes",
                            color = Color(0xFFFFB74D),
                            fontSize = 24.sp
                        )

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "🐉 Tamaño del dragón",
                            color = Color(0xFFFFB74D),
                            fontSize = 18.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        Row {

                            Button(

                                onClick = {

                                    if (mapDragonScale > 0.4f) {

                                        mapDragonScale -= 0.1f
                                        engine.setMapDragonScale(
                                            mapDragonScale
                                        )

                                        dragonLayerRef?.setProperties(
                                            PropertyFactory.iconSize(
                                                0.35f * mapDragonScale
                                            )
                                        )

                                    }

                                }

                            ) {

                                Text("➖")

                            }

                            Spacer(Modifier.width(16.dp))

                            Button(

                                onClick = {

                                    if (mapDragonScale < 1f) {

                                        mapDragonScale += 0.1f

                                        engine.setMapDragonScale(
                                            mapDragonScale
                                        )

                                        dragonLayerRef?.setProperties(
                                            PropertyFactory.iconSize(
                                                0.35f * mapDragonScale
                                            )
                                        )

                                    }

                                }

                            ) {

                                Text("➕")

                            }

                        }

                        Spacer(Modifier.height(24.dp))

                        Text(

                            text = "Escala: ${(mapDragonScale * 100).toInt()}%",

                            color = Color(0xFFFFD700)

                        )

                        Spacer(Modifier.height(24.dp))

                        Button(

                            onClick = {

                                showSettings = false

                            }

                        ) {

                            Text("Cerrar")

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}
@Composable
fun DividerLine() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(50.dp)
            .background(Color(0xFF2C3E50))
    )
}