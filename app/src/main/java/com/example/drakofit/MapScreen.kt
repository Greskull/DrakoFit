package com.example.drakofit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import org.maplibre.android.annotations.Marker
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke


val mapRef = mutableStateOf<MapLibreMap?>(null)
private var dragonMarker: Marker? = null

@Composable
fun MapScreen(
    context: Context,
    engine: DragonEngine,
    onFinish: (Float) -> Unit
) {

    var distanceMeters by remember { mutableStateOf(0f) }
    var lastStepsComputed by remember { mutableStateOf(0) }

    var startTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var isPaused by remember { mutableStateOf(false) }

    var pausedAtMs by remember { mutableStateOf<Long?>(null) }
    var totalPausedMs by remember { mutableStateOf(0L) }

    var tick by remember { mutableStateOf(0) }

    var lastUserInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    var autoCenterEnabled by remember { mutableStateOf(true) }

    var dragonPosition by remember {
        mutableStateOf(Point.fromLngLat(0.0, 0.0))
    }

    var routeSourceRef by remember { mutableStateOf<GeoJsonSource?>(null) }

    val routePoints = remember { mutableStateListOf<Point>() }

    val fusedClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // ⏱ TIMER TICK (UI REFRESH)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    fun getElapsedTime(): Long {
        val now = System.currentTimeMillis()
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

    fun calculateDistance(points: List<Point>): Float {
        var total = 0f

        for (i in 1 until points.size) {
            val results = FloatArray(1)

            android.location.Location.distanceBetween(
                points[i - 1].latitude(),
                points[i - 1].longitude(),
                points[i].latitude(),
                points[i].longitude(),
                results
            )

            total += results[0]
        }

        return total
    }

    fun getPace(km: Float, ms: Long): String {
        if (km < 0.01f) return "--:--"

        val pace = (ms / 60000f) / km

        val min = pace.toInt()
        val sec = ((pace - min) * 60).toInt()

        return "%d:%02d".format(min, sec)
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

        val callback = object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                if (isPaused) return

                val loc = result.lastLocation ?: return

                val newPoint =
                    Point.fromLngLat(
                        loc.longitude,
                        loc.latitude
                    )

                val last = routePoints.lastOrNull()

                if (last != null &&
                    last.latitude() == newPoint.latitude() &&
                    last.longitude() == newPoint.longitude()
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
            fusedClient.requestLocationUpdates(
                request,
                callback,
                context.mainLooper
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 🗺️ MAP
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->

                val mapView = MapView(ctx)
                mapView.onCreate(null)

                mapView.getMapAsync { map ->

                    mapRef.value = map

                    map.setStyle(
                        "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
                    ) { style ->

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
                                getDragonSprite(engine.getState().level)
                            )

                        style.addImage("dragon-icon", dragonBitmap)

                        val dragonLayer = SymbolLayer(
                            "dragon-layer",
                            "dragon-source"
                        ).withProperties(
                            PropertyFactory.iconImage("dragon-icon"),
                            PropertyFactory.iconAllowOverlap(true),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconSize(0.35f)
                        )

                        style.addLayer(dragonLayer)

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

        // ⏱ TIMER (NOW REACTIVE)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
            contentAlignment = Alignment.TopCenter
        ) {

            Surface(
                color = Color(0xAA000000),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = formatTime(getElapsedTime()),
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // 📊 HUD
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            val km = calculateDistance(routePoints) / 1000f
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
                            totalPausedMs += (now - (pausedAtMs ?: now))
                            pausedAtMs = null
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
                        onFinish(distanceMeters / 1000f)
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