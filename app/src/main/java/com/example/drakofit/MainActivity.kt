package com.example.drakofit

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.sources.GeoJsonSource
import android.util.Log
import android.graphics.BitmapFactory
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
private const val MIN_INTERVAL_MS = 800L


data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

class MainActivity : ComponentActivity() {

    private lateinit var engine: DragonEngine

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
    override fun onPause() {
        super.onPause()
        engine.checkDailyStatus()
    }

    override fun onStop() {
        super.onStop()
        engine.checkDailyStatus()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TEST_LOG", "APP ARRANCADA")

        org.maplibre.android.MapLibre.getInstance(this)

        engine = DragonEngine(this)

        // 🔥 IMPORTANTE: solo después de crear engine
        engine.checkDailyStatus()

        val fusedClient =
            com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(this)

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {

            fusedClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    android.util.Log.d(
                        "GPS_WARMUP",
                        "Last known fix: ${it.latitude}, ${it.longitude}"
                    )
                }
            }
        }
        setContent {

            var inActivity by remember { mutableStateOf(false) }

            var lastActivityText by remember { mutableStateOf("") }
            var lastXpText by remember { mutableStateOf("") }
            var activityHighlightColor by remember { mutableStateOf(Color.White) }

            if (inActivity) {

                MapScreen(
                    context = this,
                    engine = engine,
                    onFinish = { resultKm ->

                        engine.registerActivity()

                        inActivity = false
                    }
                )

            } else {

                HomeScreen(
                    engine = engine,
                    onStartActivity = {
                        requestLocationPermission {
                            inActivity = true
                        }
                    },
                    lastActivityText = lastActivityText,
                    lastXpText = lastXpText,
                    activityHighlightColor = activityHighlightColor
                )
            }
        }
    }

    private fun requestLocationPermission(
        onGranted: () -> Unit
    ) {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION

        if (
            ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        } else {
            locationPermissionLauncher.launch(permission)
        }
    }
}

@Composable
fun HomeScreen(
    engine: DragonEngine,
    onStartActivity: () -> Unit,
    lastActivityText: String,
    lastXpText: String,
    activityHighlightColor: Color
) {

    val state by engine.state
    var breathOffset by remember { mutableStateOf(0f) }
    var bounceOffset by remember { mutableStateOf(0f) }
    var isBouncing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {

            for (i in 0..10) {
                breathOffset = i / 200f
                delay(140)
            }

            for (i in 10 downTo 0) {
                breathOffset = i / 200f
                delay(140)
            }
        }
    }
    LaunchedEffect(state.happiness) {

        while (true) {

            val intervalSeconds =
                (37 - state.happiness * 2)
                    .coerceAtLeast(15)

            delay(intervalSeconds * 1000L)

            isBouncing = true

            // ⬆️ subida (salto)
            for (i in 0..10) {
                bounceOffset = i / 10f
                delay(25)
            }

            // ⬇️ bajada (caída)
            for (i in 10 downTo 0) {
                bounceOffset = i / 10f
                delay(35) // caída más suave = sensación de peso
            }

            bounceOffset = 0f
            isBouncing = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // 🖼️ FONDO
        Image(
            painter = painterResource(id = R.drawable.background_homescreen),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 🌑 OVERLAY para legibilidad (puedes ajustar intensidad)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x55000000))
        )

        // 📱 CONTENIDO UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                buildAnnotatedString {

                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFFE53935)
                        )
                    ) {
                        append("Drako")
                    }

                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFFFFD700)
                        )
                    ) {
                        append("Fit")
                    }
                },

                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,

                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xAA000000),
                        blurRadius = 12f
                    )
                )
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // 🏅 MEDALLA NIVEL
                Card(
                    modifier = Modifier.shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = Color(0x66FF6A00),
                        spotColor = Color(0xFFFF6A00)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xEE1E2F45)
                    ),
                    border = BorderStroke(
                        3.dp,
                        Color(0xFFFF6A00)
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 10.dp
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            "NIVEL",
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )

                        Text(
                            "${state.level}",
                            color = Color(0xFFFFD700),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // 🔥 BARRA EXP
                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        "${state.xp} / ${engine.getRequiredXpForNextLevel()} XP",
                        color = Color.White,
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.5.dp,
                                color = Color(0xFFFFD700),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {

                        LinearProgressIndicator(
                            progress = state.xp / engine.getRequiredXpForNextLevel().toFloat(),

                            modifier = Modifier.fillMaxSize(),

                            color = Color(0xFFFF9800),

                            trackColor = Color.Transparent
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "¡Listo para hacer deporte a tu lado!",
                color = Color(0xFFFFD700),
                fontSize = 16.sp
            )

            Spacer(Modifier.height(86.dp))

            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = getDragonSprite(state.level)),
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {

                            // 📉 siempre en el suelo
                            translationY = 60f

                            if (isBouncing) {
                                // 🦘 sombra se comprime al saltar
                                scaleX = 1f - (bounceOffset * 0.25f)
                                scaleY = 1f - (bounceOffset * 0.25f)
                                alpha = 0.15f
                            } else {
                                // 🌬️ respiración suave
                                val scale = 1f + breathOffset * 0.3f
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.2f
                            }
                        },
                    colorFilter = ColorFilter.tint(Color.Black)
                )

                // 🔥 GLOW (capa de atrás)
                Image(
                    painter = painterResource(id = getDragonSprite(state.level)),
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {

                            if (isBouncing) {
                                translationY = -bounceOffset * 30f
                                scaleX = 1.25f
                                scaleY = 1.25f
                                alpha = 0.5f
                            }

                        },
                    colorFilter = ColorFilter.tint(Color(0xFFFF4D00))
                )

                // 🐉 DRAGÓN PRINCIPAL (capa delante)
                Image(
                    painter = painterResource(id = getDragonSprite(state.level)),
                    contentDescription = "Drako",
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {

                            if (isBouncing) {

                                translationY = -bounceOffset * 30f

                                val squash = 1f + (bounceOffset * 0.03f)
                                scaleX = squash
                                scaleY = squash

                            } else {

                                val scale = 1f + breathOffset
                                scaleX = scale
                                scaleY = scale
                            }
                        }
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        "Pasos hoy 👣 ",
                        "${state.dailySteps}"
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        "Distancia hoy 📍",
                        " %.2f km".format(state.dailyDistanceKm)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        "Streak",
                        "${state.streak} días 🔥"
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    StatCard(
                        "Felicidad",
                        "${state.happiness} ❤️"
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = onStartActivity,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF132B45   ),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFF4DA3FF))
            ) {
                Text(
                    "Empezar actividad",
                    fontSize = 18.sp
                )
            }
        }
    }
}
@Composable
fun ActivityScreen(
    engine: DragonEngine,
    onFinish: (Float) -> Unit
) {

    val context = LocalContext.current
    var justResumed by remember { mutableStateOf(false) }


    // 🧭 CONTROL PRIMER FIX GPS
    var firstLocationReceived by remember {
        mutableStateOf(false)
    }

    val mapRef = remember {
        mutableStateOf<MapLibreMap?>(null)
    }

    val fusedClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var lastLocation by remember {
        mutableStateOf<Location?>(null)
    }

    var distanceMeters by remember {
        mutableStateOf(0f)
    }

    var isPaused by remember {
        mutableStateOf(false)
    }

    // 🟠 SOURCE RUTA
    var routeSource: GeoJsonSource? = null

    // 🧠 THROTTLING GPS
    var lastUpdateTime = 0L

    val callback = object : LocationCallback() {

        override fun onLocationResult(result: LocationResult) {

            if (isPaused) return

            val newLocation = result.lastLocation ?: return
            if (justResumed) {
                lastLocation = newLocation
                justResumed = false
                return
            }

            // 🧠 throttling
            val now = System.currentTimeMillis()

            if (now - lastUpdateTime < MIN_INTERVAL_MS) return

            lastUpdateTime = now

            // 🚨 filtro precisión
            if (newLocation.accuracy > 18f) return

            Log.d(
                "DRAGO_GPS",
                "lat=${newLocation.latitude}, lon=${newLocation.longitude}, acc=${newLocation.accuracy}"
            )

            val newPoint = org.maplibre.android.geometry.LatLng(
                newLocation.latitude,
                newLocation.longitude
            )

            // 🐉 ACTUALIZAR DRAGÓN
            mapRef.value?.getStyle { style ->

                val dragonSource =
                    style.getSourceAs<GeoJsonSource>("dragon-source")

                dragonSource?.setGeoJson(
                    org.maplibre.geojson.Feature.fromGeometry(
                        org.maplibre.geojson.Point.fromLngLat(
                            newPoint.longitude,
                            newPoint.latitude
                        )
                    )
                )
            }

            // 🟠 RUTA
            RouteBuffer.addPoint(newPoint)

            RouteManager.addPoint(
                newPoint,
                newLocation.accuracy
            )

            routeSource?.let { source ->

                val points = RouteBuffer.points

                if (points.size < 2) return@let

                source.setGeoJson(
                    org.maplibre.geojson.Feature.fromGeometry(
                        org.maplibre.geojson.LineString.fromLngLats(
                            points.map {
                                org.maplibre.geojson.Point.fromLngLat(
                                    it.longitude,
                                    it.latitude
                                )
                            }
                        )
                    )
                )
            }

            // 📷 PRIMER FIX → CENTRAR CÁMARA
            if (!firstLocationReceived) {

                firstLocationReceived = true

                mapRef.value?.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory
                        .newLatLngZoom(
                            newPoint,
                            17.5
                        )
                )
            }

            // 📏 DISTANCIA
            lastLocation?.let { oldLocation ->

                val results = FloatArray(1)

                android.location.Location.distanceBetween(
                    oldLocation.latitude,
                    oldLocation.longitude,
                    newLocation.latitude,
                    newLocation.longitude,
                    results
                )

                val delta = results[0]

                // 🚨 FILTRO ANTI-SALTO GPS
                if (delta in 0.1f..50f) {
                    distanceMeters += delta
                }

                // ❌ si es un salto, reseteamos base pero NO sumamos
                else {
                    lastLocation = newLocation
                    return@let
                }
            }

            lastLocation = newLocation
        }
    }

    LaunchedEffect(Unit) {

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) return@LaunchedEffect

        val request =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                4000
            )
                .setMinUpdateDistanceMeters(2f)
                .setMaxUpdateDelayMillis(4000)
                .build()

        fusedClient.requestLocationUpdates(
            request,
            callback,
            android.os.Looper.getMainLooper()
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // 🗺️ MAPA
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

                        // 🐉 IMAGE
                        val dragonBitmap =
                            BitmapFactory.decodeResource(
                                context.resources,
                                getDragonSprite(
                                    engine.getState().level
                                )
                            )

                        style.addImage(
                            "dragon-icon",
                            dragonBitmap
                        )

                        // 🐉 SOURCE
                        style.addSource(
                            GeoJsonSource(
                                "dragon-source",
                                org.maplibre.geojson.Feature.fromGeometry(
                                    org.maplibre.geojson.Point.fromLngLat(
                                        0.0,
                                        0.0
                                    )
                                )
                            )
                        )

                        // 🐉 LAYER
                        style.addLayer(
                            SymbolLayer(
                                "dragon-layer",
                                "dragon-source"
                            ).withProperties(
                                PropertyFactory.iconImage("dragon-icon"),
                                PropertyFactory.iconAllowOverlap(true),
                                PropertyFactory.iconIgnorePlacement(true),
                                PropertyFactory.iconSize(0.35f)
                            )
                        )

                        // 🟠 SOURCE RUTA
                        routeSource = GeoJsonSource(
                            "route-source"
                        )

                        style.addSource(routeSource!!)

                    }
                }

                mapView
            }
        )

        // 🎮 UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(20.dp))

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String
) {
    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(
                16.dp
            ),

        border = BorderStroke(
            1.dp,
            Color(0x33FFFFFF)
        ),

        colors =
            CardDefaults.cardColors(
                Color(0x8815263D)
            )
    ) {
        Column(
            Modifier.padding(
                16.dp
            )
        ) {
            Text(
                title,
                color =
                    Color.LightGray
            )

            Text(
                value,
                color =
                    Color.Yellow,
                fontSize =
                    22.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}
fun interpolateLatLng(
    start: org.maplibre.android.geometry.LatLng,
    end: org.maplibre.android.geometry.LatLng,
    factor: Float
): org.maplibre.android.geometry.LatLng {

    return org.maplibre.android.geometry.LatLng(
        start.latitude + (end.latitude - start.latitude) * factor,
        start.longitude + (end.longitude - start.longitude) * factor
    )
}
fun calculateBearing(
    start: org.maplibre.android.geometry.LatLng,
    end: org.maplibre.android.geometry.LatLng
): Float {

    val result = FloatArray(2)

    android.location.Location.distanceBetween(
        start.latitude,
        start.longitude,
        end.latitude,
        end.longitude,
        result
    )

    return result[1]
}
fun rotateBitmap(
    context: android.content.Context,
    drawableRes: Int,
    degrees: Float
): android.graphics.Bitmap {

    val original = android.graphics.BitmapFactory.decodeResource(
        context.resources,
        drawableRes
    )

    val matrix = android.graphics.Matrix()
    matrix.postRotate(degrees)

    return android.graphics.Bitmap.createBitmap(
        original,
        0,
        0,
        original.width,
        original.height,
        matrix,
        true
    )
}
fun getDirectionAngle(
    oldPoint: LatLng,
    newPoint: LatLng
): Float {

    val latDiff = newPoint.latitude - oldPoint.latitude
    val lonDiff = newPoint.longitude - oldPoint.longitude

    return if (kotlin.math.abs(latDiff) > kotlin.math.abs(lonDiff)) {
        if (latDiff > 0) 0f else 180f
    } else {
        if (lonDiff > 0) 90f else 270f
    }
}
fun scaleBitmap(bitmap: android.graphics.Bitmap, maxSize: Int): android.graphics.Bitmap {

    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()

    val width: Int
    val height: Int

    if (ratio > 1) {
        width = maxSize
        height = (maxSize / ratio).toInt()
    } else {
        height = maxSize
        width = (maxSize * ratio).toInt()
    }

    return android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true)
}