package com.example.drakofit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.maplibre.android.geometry.LatLng
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.Context

private const val MIN_INTERVAL_MS = 800L

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    return if (h > 0)
        "%d:%02d:%02d".format(h, m, s)
    else
        "%d:%02d".format(m, s)
}

fun formatDate(timestamp: Long): String {

    val date = java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()

    val today = java.time.LocalDate.now()

    return when {

        date == today ->
            "Hoy"

        date == today.minusDays(1) ->
            "Ayer"

        date.isAfter(today.minusDays(7)) ->
            date.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.FULL,
                java.util.Locale("es", "ES")
            ).replaceFirstChar {
                it.uppercase()
            }

        else ->
            java.time.format.DateTimeFormatter
                .ofPattern("dd/MM/yyyy")
                .format(date)
    }
}

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

                Log.d("DRAKOFIT", "inActivity = $inActivity")
            val activityContext = this@MainActivity

            var lastActivityText by remember { mutableStateOf("") }
            var lastXpText by remember { mutableStateOf("") }
            var activityHighlightColor by remember { mutableStateOf(Color.White) }

            if (inActivity) {

                MapScreen(
                    context = this,
                    engine = engine,
                    onFinish = { resultKm ->

                        engine.registerActivity()

                        val intent = Intent(this, TrackingService::class.java)
                        stopService(intent)

                        inActivity = false
                    }
                )

            } else {

                HomeScreen(
                    engine = engine,
                    onStartActivity = {
                        requestLocationPermission {
                            inActivity = true

                            val intent = Intent(activityContext, TrackingService::class.java)
                            ContextCompat.startForegroundService(activityContext, intent)
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

val cinzel = FontFamily(
    Font(R.font.cinzel)
)

val shieldShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {

        val w = size.width
        val h = size.height

        val path = Path().apply {

            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.25f)
            lineTo(w * 0.8f, h * 0.75f)
            lineTo(w * 0.5f, h)
            lineTo(w * 0.2f, h * 0.75f)
            lineTo(0f, h * 0.25f)
            close()
        }

        return Outline.Generic(path)
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
    var showSettings by remember { mutableStateOf(false) }
    var dragonOffsetY by remember {
        mutableStateOf(state.dragonOffsetY.dp)
    }
    var hudOffsetY by remember { mutableStateOf(0.dp) }
    var showResetWarning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("drakofit_prefs", Context.MODE_PRIVATE)

    var currentBackground by remember {
        mutableStateOf(
            prefs.getInt("current_background", 1)
        )
    }
    val activeSkin = engine.getActiveSkin()
    // Boton Menu
    var showMenu by remember { mutableStateOf(false) }
    var showRewards by remember {
        mutableStateOf(false)
    }
    var showHistory by remember {
        mutableStateOf(false)
    }


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
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {

        // 🖼️ FONDO
        val backgroundImage = when (currentBackground) {
            2 -> R.drawable.background_homescreen_2
            3 -> R.drawable.background_homescreen_3
            4 -> R.drawable.background_homescreen_4
            5 -> R.drawable.background_homescreen_5
            else -> R.drawable.background_homescreen
        }
        Image(
            painterResource(backgroundImage),
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

        // Boton ajustes
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {

            IconButton(
                onClick = {
                    showSettings = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = 4.dp,
                        end = 4.dp
                    )
            ) {

                Text(
                    text = "⚙️",
                    fontSize = 28.sp
                )
            }
        }

        // Botón Menu
        IconButton(
            onClick = {
                showMenu = true
            },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menú",
                modifier = Modifier.size(36.dp)
            )
        }

        // 🔽 MENU DESPLEGABLE
        if (showMenu) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 60.dp, start = 12.dp)
                        .background(Color(0xFF222222))
                        .padding(12.dp)
                ) {

                    Text(
                        text = "AJUSTES",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = cinzel
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            showResetWarning = true
                            showMenu = false
                        }
                    ) {
                        Text(
                            text = "↻ Resetear dragón",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "🖼 Fondo:",
                        color = Color.White
                    )

                    Row {

                        for (i in 1..5) {

                            TextButton(
                                onClick = {

                                    currentBackground = i

                                    prefs.edit()
                                        .putInt("current_background", i)
                                        .apply()

                                }
                            ) {

                                Text(
                                    text = "[$i]",
                                    color =
                                        if (currentBackground == i)
                                            Color(0xFFFFD54F)
                                        else
                                            Color.White
                                )

                            }

                        }

                    }

                    TextButton(
                        onClick = {
                            showHistory = true
                            showMenu = false
                        }
                    ) {
                        Text(
                            text = "📜 Historial rutas largas",
                            color = Color.White
                        )
                    }

                    TextButton(
                        onClick = {
                            showRewards = true
                            showMenu = false
                        }
                    ) {
                        Text(
                            text = "🏆 Recompensas",
                            color = Color.White
                        )
                    }

                    TextButton(
                        onClick = {
                            engine.setMapDarkMode(!engine.state.value.isMapDarkMode)
                        }
                    ) {
                        Text(
                            text = if (engine.state.value.isMapDarkMode)
                                "\uD83C\uDF19 Mapa: Modo Oscuro"
                            else
                                "☀\uFE0F Mapa: Modo Claro",
                            color = Color.White
                        )
                    }

                    TextButton(
                        onClick = {
                            showMenu = false
                        }
                    ) {
                        Text(
                            text = "❌  Cerrar",
                            color = Color.White,
                            fontFamily = cinzel
                        )
                    }
                }
            }
        }
        if (showResetWarning) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.12f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    Text(
                        text = "⚠️ ¡CUIDADO!\nESTA ACCIÓN ES IRREVERSIBLE\n¿CONTINUAR?",
                        color = Color.Red,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    Row {

                        Button(
                            onClick = {
                                engine.resetDragon()
                                showResetWarning = false
                            }
                        ) {
                            Text("CONFIRMAR")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = {
                                showResetWarning = false
                            }
                        ) {
                            Text("CANCELAR")
                        }
                    }
                }
            }
        }
        // Historial Rutas
        if (showHistory) {
            val routeHistoryStorage = remember {
                RouteHistoryStorage(context)
            }

            val routes = remember(showHistory) {
                routeHistoryStorage.getRecentRoutes()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xFF222222))
                        .padding(24.dp)
                ) {

                    Text(
                        text = "📜 HISTORIAL RUTAS LARGAS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    LazyColumn {

                        items(routes) { route ->

                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {

                                    Text(
                                        text = "📅 ${formatDate(route.timestamp)}",
                                        color = Color.White
                                    )

                                    Text(
                                        text = "⏱ ${formatDuration(route.durationSeconds)}",
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {

                                    Text(
                                        text = "👣 ${route.steps} pasos",
                                        color = Color.White
                                    )

                                    Text(
                                        text = "📍 ${"%.2f".format(route.distanceKm)} km",
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {

                                    Text(
                                        text = "🔥 ${route.calories} kcal",
                                        color = Color.White
                                    )

                                    Text(
                                        text = "⭐ ${route.xpEarned} XP",
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "🏃 ${route.pace} min/km",
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.DarkGray)
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            showHistory = false
                        }
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }

        if (showRewards) {

            val currentDragon = engine.getCurrentDragon()

            val auraUnlocked = engine.isAuraUnlocked()

            val shinyUnlocked =
                engine.isSkinUnlocked(SkinType.SHINY)

            val goldUnlocked =
                engine.isSkinUnlocked(SkinType.GOLD)

            val waterUnlocked =
                engine.isDragonUnlocked(DragonType.WATER)

            val plantUnlocked =
                engine.isDragonUnlocked(DragonType.PLANT)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {

                Column(
                    modifier = Modifier
                        .background(Color(0xFF222222))
                        .padding(24.dp)
                        .width(320.dp)
                ) {

                    Text(
                        text = "🏆 RECOMPENSAS",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = cinzel,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Desbloquea nuevas apariencias\nrealizando actividades.",
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "────────────────────────────",
                        color = Color.DarkGray
                    )

                    Text(
                        "──────── ✨ COMUNES ─────────",
                        color = Color(0xFFFFD54F),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    // ✨ AURA
                    if (auraUnlocked) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "✨ Aura",
                                color = Color.White
                            )

                            TextButton(
                                onClick = {
                                    engine.setAuraEnabled(
                                        !engine.isAuraEnabled()
                                    )
                                }
                            ) {

                                Text(
                                    if (engine.isAuraEnabled())
                                        "DESACTIVAR"
                                    else
                                        "ACTIVAR"
                                )

                            }

                        }

                    } else {

                        Text(
                            text = "🔒 Aura",
                            color = Color.White
                        )

                    }

                    Spacer(modifier = Modifier.height(6.dp))


                    Text(
                        text = "────────────────────────────",
                        color = Color.DarkGray
                    )

                    Text(
                        "──────── 💎 RARAS ─────────",
                        color = Color(0xFF81D4FA),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    // 🤍 NORMAL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "🤍 Normal",
                            color = Color.White
                        )

                        if (activeSkin == SkinType.NORMAL) {

                            Text(
                                text = "ACTIVA",
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold
                            )

                        } else {

                            TextButton(
                                onClick = {
                                    engine.setActiveSkin(SkinType.NORMAL)
                                }
                            ) {
                                Text("USAR")
                            }

                        }

                    }
                    // 🌈 SHINY
                    if (shinyUnlocked) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "🌈 Shiny",
                                color = Color.White
                            )

                            if (activeSkin == SkinType.SHINY) {

                                Text(
                                    text = "ACTIVA",
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )

                            } else {

                                TextButton(
                                    onClick = {
                                        engine.setActiveSkin(SkinType.SHINY)
                                    }
                                ) {
                                    Text("USAR")
                                }

                            }

                        }

                    } else {

                        Text(
                            text = "🔒 Shiny",
                            color = Color.White
                        )

                    }

                    Spacer(modifier = Modifier.height(6.dp))


                    Text(
                        text = "────────────────────────────",
                        color = Color.DarkGray
                    )
                    Text(
                        text = "──────── 🏺 LEGENDARIAS ────────",
                        color = Color(0xFF9C4DFF),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "🐉 Dragón Fuego",
                            color = Color.White
                        )

                        if (currentDragon == DragonType.FIRE) {

                            Text(
                                text = "ACTIVO",
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold
                            )

                        } else {

                            TextButton(
                                onClick = {
                                    engine.setCurrentDragon(DragonType.FIRE)
                                }
                            ) {

                                Text("USAR")

                            }

                        }
                    }

                    if (waterUnlocked) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "🐉 Dragón Agua",
                                color = Color.White
                            )

                            if (currentDragon == DragonType.WATER) {

                                Text(
                                    text = "ACTIVO",
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )

                            } else {

                                TextButton(
                                    onClick = {
                                        engine.setCurrentDragon(DragonType.WATER)
                                    }
                                ) {

                                    Text("USAR")

                                }

                            }

                        }

                    } else {

                        Text(
                            text = "🔒 Dragón Agua",
                            color = Color.White
                        )

                    }

                    if (plantUnlocked) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "🐉 Dragón Planta",
                                color = Color.White
                            )

                            if (currentDragon == DragonType.PLANT) {

                                Text(
                                    text = "ACTIVO",
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )

                            } else {

                                TextButton(
                                    onClick = {
                                        engine.setCurrentDragon(DragonType.PLANT)
                                    }
                                ) {

                                    Text("USAR")

                                }

                            }

                        }

                    } else {

                        Text(
                            text = "🔒 Dragón Planta",
                            color = Color.White
                        )

                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "──────── ✨ SECRETA ────────",
                        color = Color(0xFFFFB74D),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 🤍 NORMAL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = "🤍 Normal",
                            color = Color.White
                        )

                        if (activeSkin == SkinType.NORMAL) {

                            Text(
                                text = "ACTIVA",
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold
                            )

                        } else {

                            TextButton(
                                onClick = {
                                    engine.setActiveSkin(SkinType.NORMAL)
                                }
                            ) {
                                Text("USAR")
                            }

                        }

                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 👑 DORADA
                    if (goldUnlocked) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "👑 Dorada",
                                color = Color.White
                            )

                            if (activeSkin == SkinType.GOLD) {

                                Text(
                                    text = "ACTIVA",
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )

                            } else {

                                TextButton(
                                    onClick = {
                                        engine.setActiveSkin(SkinType.GOLD)
                                    }
                                ) {
                                    Text("USAR")
                                }

                            }

                        }

                    } else {

                        Text(
                            text = "🔒 Dorada",
                            color = Color.White
                        )

                    }

                    Text(
                        text = "────────────────────────────",
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = {
                            showRewards = false
                        }
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }

        // 📱 CONTENIDO UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            val infiniteTransition = rememberInfiniteTransition(label = "titleGlow")

            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glowAlpha"
            )

            Text(
                text = buildAnnotatedString {

                    withStyle(SpanStyle(color = Color(0xFFE53935))) {
                        append("Drako")
                    }

                    withStyle(SpanStyle(color = Color(0xFFFFD700))) {
                        append("Fit")
                    }
                },

                fontFamily = cinzel,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color(0xFFFF4500).copy(alpha = glowAlpha),
                        blurRadius = 22f
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
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .defaultMinSize(minWidth = 72.dp, minHeight = 72.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = shieldShape,
                            ambientColor = Color(0x66FF6A00),
                            spotColor = Color(0xFFFF6A00)
                        ),
                    shape = shieldShape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xEE1E2F45)
                    ),
                    border = BorderStroke(
                        3.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFFD36A),
                                Color(0xFFFF6A00),
                                Color(0xFF8A2C00)
                            )
                        )
                    )
                ) {

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .defaultMinSize(minWidth = 72.dp, minHeight = 72.dp),
                        contentAlignment = Alignment.Center
                    ) {

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Text(
                                text = "NIVEL",
                                color = Color.LightGray,
                                fontSize = 16.sp
                            )

                            Text(
                                text = "${state.level}",
                                color = Color(0xFFFFD700),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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

            Spacer(Modifier.height(2.dp))

            Text(
                "¡Listo para hacer deporte a tu lado!",
                color = Color(0xFFFFD700),
                fontSize = 16.sp
            )

            Spacer(Modifier.height(86.dp))

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(y = dragonOffsetY),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(
                        id = getDragonSprite(
                            engine.getCurrentDragon(),
                            state.level
                        )
                    ),
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
                // ✨ AURA
                if (engine.isAuraEnabled()) {

                    Image(
                        painter = painterResource(id = R.drawable.aura),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {

                                // 🔥 Tamaño base del aura
                                val baseScale = 3f

                                if (isBouncing) {

                                    translationY = -22f - (bounceOffset * 30f)

                                    val scale = baseScale + bounceOffset * 0.10f

                                    scaleX = scale
                                    scaleY = scale

                                } else {

                                    translationY = -22f

                                    val scale = baseScale + breathOffset * 0.15f

                                    scaleX = scale
                                    scaleY = scale

                                }

                                alpha = 0.45f
                            }
                    )

                }
                // 🔥 GLOW (capa de atrás)
                Image(
                    painter = painterResource(id = getDragonSprite(
                        engine.getCurrentDragon(),
                        state.level
                    )),
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
                    painter = painterResource(
                        id = getDragonSprite(
                            engine.getCurrentDragon(),
                            state.level
                        )
                    ),
                    contentDescription = "Drako",
                    colorFilter = getDragonColorFilter(
                        engine.getActiveSkin(),
                        engine.getCurrentDragon()
                    ),
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

            Column(
                modifier = Modifier.offset(y = state.statsOffsetY.dp)
            ) {

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Box(modifier = Modifier.weight(1f)) {
                        StatCard(
                            "Pasos hoy 👣",
                            "${state.dailySteps}"
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        StatCard(
                            "Distancia hoy 📍",
                            "%.2f km".format(state.dailyDistanceKm)
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
                            title = "Streak",
                            value = "${state.streak} días 🔥"
                        )

                        if (state.streak > 0) {
                            Text(
                                text = "BONUS\nEXP!",
                                color = Color(0xFF4FC3F7),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {

                        StatCard(
                            title = "Felicidad",
                            value = "${state.happiness} ❤️"
                        )

                        if (state.happiness == 7) {
                            Text(
                                text = "BONUS\nREWARDS!",
                                color = Color(0xFF4FC3F7),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                            )
                        }
                    }
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
                    fontWeight = FontWeight.Bold,
                    fontFamily = cinzel,
                    fontSize = 18.sp
                )
            }
        }

        if (showSettings) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.20f)),
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
                            color = Color.White,
                            fontSize = 24.sp
                        )

                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = "🐉 Posición del dragón",
                            color = Color(0xFFFFB74D)
                        )

                        Spacer(Modifier.height(16.dp))

                        Row {

                            Button(
                                onClick = {

                                    if (dragonOffsetY > (-80).dp) {

                                        dragonOffsetY -= 8.dp

                                        engine.setDragonOffset(
                                            dragonOffsetY.value
                                        )

                                    }

                                }
                            ) {
                                Text("▲")
                            }

                            Spacer(Modifier.width(16.dp))

                            Button(
                                onClick = {

                                    if (dragonOffsetY < 80.dp) {

                                        dragonOffsetY += 8.dp

                                        engine.setDragonOffset(
                                            dragonOffsetY.value
                                        )

                                    }

                                }
                            ) {
                                Text("▼")
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        Text(
                            text = "📊 Posición estadísticas",
                            color = Color.LightGray
                        )

                        Spacer(Modifier.height(16.dp))

                        Row {

                            Button(
                                onClick = {

                                    if (hudOffsetY > (-80).dp) {

                                        hudOffsetY -= 8.dp

                                        engine.setStatsOffset(
                                            hudOffsetY.value
                                        )
                                    }

                                }
                            ) {
                                Text("▲")
                            }

                            Spacer(Modifier.width(16.dp))

                            Button(
                                onClick = {

                                    if (hudOffsetY < 80.dp) {

                                        hudOffsetY += 8.dp

                                        engine.setStatsOffset(
                                            hudOffsetY.value
                                        )
                                    }

                                }
                            ) {
                                Text("▼")
                            }
                        }

                        Spacer(Modifier.height(28.dp))

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
        if (state.isMapDarkMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
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