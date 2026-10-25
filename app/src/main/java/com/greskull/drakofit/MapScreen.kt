package com.greskull.drakofit

import android.content.Context
import androidx.compose.runtime.*
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
import org.maplibre.android.maps.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.collectAsState
import com.greskull.drakofit.tracking.TrackingState
import com.greskull.drakofit.tracking.domain.SessionMath

enum class FinishReason {
    NORMAL,
    GPS_SUSPICIOUS
}

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
    // 🧠 FASE 2: la pantalla ya no posee NADA del tracking. El GPS, el filtro
    // y las métricas viven en TrackingService; aquí solo se observa el estado
    // compartido. Si la Activity muere y vuelve, se reconecta a la sesión viva.
    val tracking by TrackingState.state.collectAsState()

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

    // FASE 2: el estado de pausa sale del servicio, ya no es un booleano local.
    val isPaused = tracking.isPaused


    var tick by remember { mutableStateOf(0) }

    var lastUserInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    var autoCenterEnabled by remember { mutableStateOf(true) }

    var dragonPosition by remember {
        mutableStateOf(Point.fromLngLat(0.0, 0.0))
    }

    var routeSourceRef by remember { mutableStateOf<GeoJsonSource?>(null) }

    // 🐉 FIX: referencia a la fuente del dragón, igual que routeSourceRef.
    // En la migración a la Fase 2 se perdió la línea que movía esta fuente, así
    // que el sprite se quedaba congelado en su posición inicial (0,0): al
    // animar la cámara al primer fix, el dragón se quedaba atrás y desaparecía.
    var dragonSourceRef by remember { mutableStateOf<GeoJsonSource?>(null) }

    // 🐉 FIX: factor de normalización del tamaño del PNG (1.0 = sin corregir).
    var dragonIconFactor by remember { mutableStateOf(1f) }

    // FASE 3: cuántos puntos del servicio llevamos ya dibujados.
    var drawnPointCount by remember { mutableStateOf(0) }

    // FASE 3: routePoints ya no hace falta. La fuente de verdad es
    // tracking.points (viene del servicio) y se dibuja agrupada por tramos.

    var mapViewRef by remember {
        mutableStateOf<MapView?>(null)
    }

    // ⏱ TIMER TICK (UI REFRESH)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    // 🧠 FASE 2: el cronómetro lo lleva el servicio, con reloj monotónico
    // (SystemClock.elapsedRealtime), así que un cambio de hora del móvil ya no
    // lo descuadra. Sigue congelándose en pausa, igual que antes.
    fun getElapsedTime(): Long = tracking.elapsedMs

    fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    // 🧠 FASE 2: estas tres fórmulas las usa también la notificación del
    // servicio, así que viven en SessionMath para que el HUD y la notificación
    // den SIEMPRE el mismo número. Los coeficientes son los de siempre:
    // 65 kcal/km y 1300 pasos/km.
    fun getCalories(km: Float) = SessionMath.caloriesFor(km)

    fun getSteps(km: Float): Int = SessionMath.stepsFor(km)

    fun getPace(km: Float, ms: Long): String = SessionMath.paceFor(km, ms)

    // =============================================================
    // FASE 2: la pantalla ya NO guarda la ruta.
    // Guardar y sumar pasos/XP/distancia lo hace SessionFinalizer desde
    // TrackingService, porque hay un segundo camino que puede cerrar la
    // sesión sin UI: el usuario cerrando la app desde el gestor de tareas.
    // Aquí solo se muestra el resultado y se avisa a MainActivity.
    // =============================================================
    LaunchedEffect(finishReason) {

        when (finishReason) {

            FinishReason.NORMAL -> {

                delay(5000)

                onFinish(FinishReason.NORMAL)
            }


            FinishReason.GPS_SUSPICIOUS -> {

                // 🚫 Actividad anulada por GPS sospechoso:
                // No guardar ruta
                // No sumar pasos
                // No dar XP
                // No generar recompensas
                // (Todo eso ya lo respeta SessionFinalizer.)

                delay(5000)

                onFinish(FinishReason.GPS_SUSPICIOUS)
            }


            null -> return@LaunchedEffect
        }
    }

    // 🧠 FASE 2: la sesión puede terminar sin que la pantalla esté delante
    // (GPS sospechoso con el móvil bloqueado, o finalizar desde la
    // notificación). Cuando eso pasa, al abrir la app se muestra el resultado
    // leyéndolo del estado del servicio.
    LaunchedEffect(tracking.status, tracking.stopReason) {

        if (tracking.isFinished && finishReason == null) {
            finishReason = if (tracking.isSuspicious) {
                FinishReason.GPS_SUSPICIOUS
            } else {
                FinishReason.NORMAL
            }
        }
    }

    // 🗺️ FASE 2: dibujo de la ruta a partir del estado del servicio.
    // Se van añadiendo solo los puntos nuevos, igual que antes se hacía en el
    // callback; si la pantalla se recrea, se redibuja la ruta completa.
    LaunchedEffect(tracking.revision) {

        if (tracking.points.isEmpty()) return@LaunchedEffect
        if (tracking.points.size <= drawnPointCount) return@LaunchedEffect

        drawnPointCount = tracking.points.size

        // =============================================================
        // 🛣️ FASE 3: la ruta se dibuja POR TRAMOS.
        // Un hueco de GPS (>60 s sin fix) abre un tramo nuevo en el filtro,
        // así que aquí se agrupa por `segmentIndex` y se dibuja un
        // MultiLineString: el hueco NO se une con una línea recta.
        // Sin huecos hay un solo tramo, así que el resultado es idéntico al
        // de antes.
        // =============================================================
        val lines = tracking.points
            .groupBy { it.segmentIndex }
            .toSortedMap()
            .values
            .mapNotNull { tramo ->
                if (tramo.size < 2) null // una LineString necesita 2 puntos
                else LineString.fromLngLats(
                    tramo.map { Point.fromLngLat(it.lon, it.lat) }
                )
            }

        if (lines.isNotEmpty()) {
            routeSourceRef?.setGeoJson(
                Feature.fromGeometry(MultiLineString.fromLineStrings(lines))
            )
        }

        // 🐉 dragón en el último punto
        val ultimo = tracking.points.last()
        dragonPosition = Point.fromLngLat(ultimo.lon, ultimo.lat)
        dragonSourceRef?.setGeoJson(Feature.fromGeometry(dragonPosition))

        mapRef.value?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                org.maplibre.android.geometry.LatLng(
                    dragonPosition.latitude(),
                    dragonPosition.longitude()
                ),
                17.5
            )
        )
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

    // =============================================================
    // 📍 FASE 2: aquí estaba el bloque GPS (FusedLocationProviderClient +
    // LocationCallback + filtro). Ya NO vive en la pantalla: se ha mudado a
    // TrackingService, que es quien sobrevive al bloqueo del móvil, a la
    // muerte de la Activity y a los asesinos de batería de ColorOS/MIUI.
    // La pantalla solo dibuja lo que publica TrackingState.
    // =============================================================

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

                        // 🐉 FIX: guardar la referencia para poder moverla después
                        dragonSourceRef = dragonSource

                        // 🐉 DRAGON SPRITE
                        val dragonBitmap =
                            BitmapFactory.decodeResource(
                                context.resources,
                                getDragonSprite(
                                    engine.getCurrentDragon(),
                                    engine.getState().level
                                )
                            )

                        // 🔥 FIX: tamaño constante en pantalla, mida lo que mida
                        // el PNG (los sprites van de 114x128 a 655x353).
                        dragonIconFactor = DragonSpriteSizing.factor(dragonBitmap)

                        style.addImage("dragon-icon", dragonBitmap)

                        val dragonLayer = SymbolLayer(
                            "dragon-layer",
                            "dragon-source"
                        ).withProperties(
                            PropertyFactory.iconImage("dragon-icon"),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconSize(
                                DragonSpriteSizing.iconSize(dragonIconFactor, mapDragonScale)
                            )
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

                // FASE 2: aquí ya no se quita el callback de ubicación: el GPS
                // es del servicio y NO debe pararse al cerrar la pantalla.
                mapViewRef?.onPause()
                mapViewRef?.onStop()
                mapViewRef?.onDestroy()

                mapRef.value = null
                mapViewRef = null
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

            val km = tracking.km

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
                        // 🧠 FASE 2: la pantalla solo manda la orden al
                        // servicio. El estado (y el chequeo de distancia al
                        // reanudar) los lleva el filtro, dentro del servicio.
                        if (!isPaused) {
                            TrackingService.pause(context)
                        } else {
                            TrackingService.resume(context)
                        }
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
                        // 🧠 FASE 2: finalizar, guardar la ruta y sumar
                        // pasos/XP/distancia los hace el servicio
                        // (SessionFinalizer), porque ese mismo cierre puede
                        // llegar desde el gestor de tareas sin que esta
                        // pantalla exista. El resumen se muestra al observar
                        // tracking.isFinished, más arriba.
                        TrackingService.finish(context)
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
                                                DragonSpriteSizing.iconSize(
                                                    dragonIconFactor,
                                                    mapDragonScale
                                                )
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
                                                DragonSpriteSizing.iconSize(
                                                    dragonIconFactor,
                                                    mapDragonScale
                                                )
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