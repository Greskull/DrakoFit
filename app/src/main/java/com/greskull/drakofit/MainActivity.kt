package com.greskull.drakofit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.delay

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

// 🗑️ FIX: se elimina `data class RoutePoint` — nunca se instanciaba,
// remanente de antes de usar `Point` de MapLibre directamente.


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
        MobileAds.initialize(this)

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

            val storage = remember {
                DragonStorage(this@MainActivity)
            }

            var showBackgroundWarning by remember {
                mutableStateOf(!storage.hasShownBackgroundWarning())
            }


            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    fontScale = 1f
                )
            ) {


                var inActivity by rememberSaveable { mutableStateOf(false) }


                val activityContext = this@MainActivity


                if (inActivity) {

                    MapScreen(
                        context = this,
                        engine = engine,
                        onFinish = { reason ->
                            if (reason == FinishReason.NORMAL) {
                                engine.registerActivity()
                            }
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

                                engine.clearRewardFlag()   // ← NUEVA

                                inActivity = true

                                val intent = Intent(activityContext, TrackingService::class.java)
                                ContextCompat.startForegroundService(activityContext, intent)
                            }
                        }
                    )
                }


                // Aviso inicial de ejecución en segundo plano
                if (showBackgroundWarning) {

                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = LocalDensity.current.density,
                            fontScale = 1f
                        )
                    ) {

                        AlertDialog(

                            onDismissRequest = {
                                // Obligamos al usuario a pulsar "Entendido"
                            },

                            title = {
                                Text(
                                    "🐉 DrakoFit necesita ejecutarse en segundo plano"
                                )
                            },

                            text = {

                                Box(
                                    modifier = Modifier
                                        .heightIn(max = 350.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {

                                    Text(
                                        """
                                        Para registrar tus rutas y pasos durante una actividad,
                                        DrakoFit necesita seguir funcionando aunque apagues
                                        la pantalla o cambies de aplicación.

                                        Algunos dispositivos pueden cerrar aplicaciones por
                                        ahorro de batería y detener el seguimiento GPS.

                                        Para evitar perder una actividad:
                                        
                                        • Permite la ejecución en segundo plano.
                                        
                                        • Si tu móvil lo solicita, desactiva la optimización
                                          de batería para DrakoFit.
                                        
                                        • Los pasos a seguir son:
                                        
                                        Ajustes - Aplicaciones -  Gestionar aplicaciones -
                                        DrakoFit - Uso de batería -
                                        Permitir ejecución en segundo plano.
                                        """.trimIndent()
                                    )
                                }

                            },

                            confirmButton = {

                                Button(
                                    onClick = {

                                        storage.setBackgroundWarningShown()
                                        showBackgroundWarning = false

                                    }
                                ) {

                                    Text("Entendido")

                                }
                            }
                        )
                    }
                }
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
    onStartActivity: () -> Unit
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
                // 🔥 FIX: sin esto, la Column con scroll (que ahora ocupa
                // toda la pantalla y se compone DESPUÉS que este botón)
                // se dibuja encima y le roba el toque, aunque el botón
                // siga siendo visible.
                .zIndex(10f)
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
            modifier = Modifier
                .align(Alignment.TopStart)
                // 🔥 FIX: mismo motivo que el botón de ajustes.
                .zIndex(10f)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menú",
                tint = Color(0xFFD4AF37),
                modifier = Modifier.size(36.dp)
            )
        }

        // 🔽 MENU DESPLEGABLE
        if (showMenu) {

            val menuGold = Color(0xFFD4AF37)
            val menuBorder = Color(0xFF6B4F24)
            val menuCream = Color(0xFFE8E0D0)
            val menuDanger = Color(0xFFE24B4A)

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
                        .width(240.dp)
                        .background(Color(0xFF181310), RoundedCornerShape(14.dp))
                        .border(1.dp, menuBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {

                    Text(
                        text = "AJUSTES",
                        color = menuGold,
                        fontWeight = FontWeight.Bold,
                        fontFamily = cinzel,
                        fontSize = 15.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(menuBorder)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 🌗 Modo del mapa: sol si está claro, luna si está oscuro
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                engine.setMapDarkMode(!engine.state.value.isMapDarkMode)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(menuGold.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (engine.state.value.isMapDarkMode)
                                    Icons.Filled.NightsStay
                                else
                                    Icons.Filled.WbSunny,
                                contentDescription = "Modo del mapa",
                                tint = menuGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = if (engine.state.value.isMapDarkMode)
                                "Mapa: modo oscuro"
                            else
                                "Mapa: modo claro",
                            color = menuCream,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Fondo",
                        color = menuCream,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        for (i in 1..5) {

                            val selected = currentBackground == i

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        if (selected) menuGold.copy(alpha = 0.15f) else Color.Transparent,
                                        CircleShape
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) menuGold else menuBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        currentBackground = i
                                        prefs.edit()
                                            .putInt("current_background", i)
                                            .apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$i",
                                    color = if (selected) menuGold else menuCream,
                                    fontFamily = cinzel,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(menuBorder)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 📜 Historial
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showHistory = true
                                showMenu = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(menuGold.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = menuGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Historial rutas largas",
                            color = menuCream,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 15.sp
                        )
                    }

                    // 🏆 Recompensas
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRewards = true
                                showMenu = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(menuGold.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                tint = menuGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Recompensas",
                            color = menuCream,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(menuBorder)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ↻ Resetear dragón (acción destructiva, se conserva en rojo)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showResetWarning = true
                                showMenu = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(menuDanger.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = menuDanger,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Resetear dragón",
                            color = menuDanger,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "CERRAR",
                        color = menuGold,
                        fontFamily = cinzel,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .border(1.dp, menuBorder, RoundedCornerShape(20.dp))
                            .clickable { showMenu = false }
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                    )
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
        // Historiales Rutas
        if (showHistory) {
            val routeHistoryStorage = remember {
                RouteHistoryStorage(context)
            }

            val routes = remember(showHistory) {
                routeHistoryStorage.getRecentRoutes()
            }

            val histGold = Color(0xFFD4AF37)
            val histBorder = Color(0xFF6B4F24)
            val histCream = Color(0xFFE8E0D0)
            val histMuted = Color(0xFF9A9484)
            val histCardBg = Color(0xFF241D16)

            val StatChip: @Composable (
                icon: androidx.compose.ui.graphics.vector.ImageVector,
                value: String,
                label: String
            ) -> Unit = { icon, value, label ->

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(80.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(histGold.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = histGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = value,
                        color = histCream,
                        fontFamily = cinzel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = label,
                        color = histMuted,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp
                    )
                }
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
                        .width(320.dp)
                        .background(Color(0xFF181310), RoundedCornerShape(14.dp))
                        .border(1.dp, histBorder, RoundedCornerShape(14.dp))
                        .padding(20.dp)
                        // 🔥 FIX (punto 4): el contenedor entero (título +
                        // lista + botón) ahora puede desplazarse si no cabe.
                        // Sin ningún Modifier.weight() en esta Column, así
                        // que el scroll no tiene ningún conflicto.
                        .verticalScroll(rememberScrollState())
                ) {

                    Text(
                        text = "HISTORIAL DE RUTAS",
                        color = histGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 2.sp,
                        fontFamily = cinzel
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(histBorder)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (routes.isEmpty()) {

                        Text(
                            text = "Todavía no hay rutas largas registradas.",
                            color = histMuted,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 🔥 FIX: LazyColumn sustituido por Column + forEach.
                    // Máximo 5 rutas guardadas (MAX_ROUTES en
                    // RouteHistoryStorage), no aporta nada usar Lazy aquí,
                    // y anidar dos scrolls (este nuevo + el de LazyColumn)
                    // habría roto la app en tiempo de ejecución.
                    routes.forEach { route ->

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(histCardBg, RoundedCornerShape(14.dp))
                                .border(1.dp, histBorder, RoundedCornerShape(14.dp))
                                .padding(16.dp)
                        ) {

                            Text(
                                text = formatDate(route.timestamp),
                                color = histGold,
                                fontFamily = cinzel,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatChip(
                                    Icons.AutoMirrored.Filled.DirectionsWalk,
                                    "${route.steps}",
                                    "pasos"
                                )
                                StatChip(
                                    Icons.Filled.LocationOn,
                                    "%.2f km".format(route.distanceKm),
                                    "distancia"
                                )
                                StatChip(
                                    Icons.Filled.LocalFireDepartment,
                                    "${route.calories}",
                                    "kcal"
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatChip(
                                    Icons.AutoMirrored.Filled.DirectionsRun,
                                    route.pace,
                                    "min/km"
                                )
                                StatChip(
                                    Icons.Filled.Star,
                                    "${route.xpEarned}",
                                    "xp"
                                )
                                StatChip(
                                    Icons.Filled.Timer,
                                    formatDuration(route.durationSeconds),
                                    "duración"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "CERRAR",
                        color = histGold,
                        fontFamily = cinzel,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .border(1.dp, histBorder, RoundedCornerShape(20.dp))
                            .clickable { showHistory = false }
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                    )
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

            val rewGreen = Color(0xFF97C459)
            val rewBlue = Color(0xFF378ADD)
            val rewViolet = Color(0xFF7F77DD)
            val rewGold = Color(0xFFD4AF37)
            val rewLockedBorder = Color(0xFF3A352C)
            val rewLockedIcon = Color(0xFF8A8578)
            val rewCardBg = Color(0xFF241D16)
            val rewLockedBg = Color(0xFF1C1815)
            val rewCream = Color(0xFFE8E0D0)
            val rewMuted = Color(0xFF9A9484)
            val rewBorderNeutral = Color(0xFF6B4F24)

            // state: 0 = bloqueada, 1 = desbloqueada sin activar, 2 = activa
            // 🔥 FIX: fillMaxWidth() en vez de weight() (weight requiere
            // RowScope, y esta lambda no lo tiene). El reparto de espacio
            // entre las 3 opciones se hace ahora en cada Box(weight(1f))
            // que envuelve la llamada, en el propio Row.
            val OptionChip: @Composable (
                icon: androidx.compose.ui.graphics.vector.ImageVector,
                label: String,
                color: Color,
                state: Int,
                onClick: () -> Unit
            ) -> Unit = { icon, label, color, state, onClick ->

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when (state) {
                                2 -> color.copy(alpha = 0.10f)
                                0 -> rewLockedBg
                                else -> rewCardBg
                            },
                            RoundedCornerShape(15.dp)
                        )
                        .border(
                            width = if (state == 2) 2.5.dp else 1.5.dp,
                            color = if (state == 0) rewLockedBorder else color,
                            shape = RoundedCornerShape(15.dp)
                        )
                        .then(
                            if (state == 1)
                                Modifier.clickable(onClick = onClick)
                            else
                                Modifier
                        )
                        .padding(vertical = 16.dp, horizontal = 6.dp)
                        .alpha(if (state == 0) 0.55f else 1f)
                ) {
                    Icon(
                        imageVector = if (state == 0) Icons.Filled.Lock else icon,
                        contentDescription = null,
                        tint = if (state == 0) rewLockedIcon else color,
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        color = rewCream,
                        fontFamily = cinzel,
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = when (state) {
                            0 -> "Bloqueada"
                            2 -> "Activa"
                            else -> "Usar"
                        },
                        color = if (state == 2) color else rewMuted,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = if (state == 2) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        // 🔥 FIX: 30% de transparencia SOLO en el relleno
                        // del panel (alpha = 0.7 → 70% opaco / 30%
                        // transparente). El borde, los textos y los iconos
                        // no llevan alpha, así que se quedan totalmente
                        // nítidos — solo se transparenta el fondo marrón.
                        .background(
                            Color(0xFF181310).copy(alpha = 0.7f),
                            RoundedCornerShape(18.dp)
                        )
                        .border(1.dp, rewBorderNeutral, RoundedCornerShape(18.dp))
                        .padding(26.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    Text(
                        text = "RECOMPENSAS",
                        color = rewGold,
                        fontSize = 19.sp,
                        letterSpacing = 3.sp,
                        fontFamily = cinzel,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Desbloquea nuevas apariencias realizando actividades.",
                        color = rewMuted,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ✨ COMÚN — Aura
                    Text(
                        text = "COMÚN",
                        color = rewGreen,
                        fontFamily = cinzel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (auraUnlocked) rewCardBg else rewLockedBg,
                                RoundedCornerShape(15.dp)
                            )
                            .border(
                                1.5.dp,
                                if (auraUnlocked) rewGreen else rewLockedBorder,
                                RoundedCornerShape(15.dp)
                            )
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (auraUnlocked)
                                    Icons.Filled.AutoAwesome
                                else
                                    Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (auraUnlocked) rewGreen else rewLockedIcon,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(13.dp))
                            Text(
                                text = "Aura",
                                color = rewCream,
                                fontFamily = cinzel,
                                fontSize = 17.sp
                            )
                        }

                        if (auraUnlocked) {

                            val auraOn = engine.isAuraEnabled()

                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(28.dp)
                                    .background(
                                        if (auraOn) rewGreen.copy(alpha = 0.25f) else Color(0xFF3A352C),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        1.5.dp,
                                        if (auraOn) rewGreen else rewLockedBorder,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        engine.setAuraEnabled(!auraOn)
                                    },
                                contentAlignment = if (auraOn) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .size(20.dp)
                                        .background(
                                            if (auraOn) rewGreen else rewLockedIcon,
                                            CircleShape
                                        )
                                )
                            }

                        } else {

                            Text(
                                text = "Bloqueada",
                                color = rewLockedIcon,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // 💎 RARA — Apariencia (Normal / Shiny / Dorada, mutuamente excluyentes)
                    Text(
                        text = "APARIENCIA",
                        color = rewBlue,
                        fontFamily = cinzel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Box(modifier = Modifier.weight(1f)) {
                            OptionChip(
                                Icons.Filled.Circle,
                                "Normal",
                                rewCream,
                                if (activeSkin == SkinType.NORMAL) 2 else 1
                            ) {
                                engine.setActiveSkin(SkinType.NORMAL)
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OptionChip(
                                Icons.Filled.Diamond,
                                "Shiny",
                                rewBlue,
                                if (!shinyUnlocked) 0
                                else if (activeSkin == SkinType.SHINY) 2
                                else 1
                            ) {
                                engine.setActiveSkin(SkinType.SHINY)
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OptionChip(
                                Icons.Filled.WorkspacePremium,
                                "Dorada",
                                rewGold,
                                if (!goldUnlocked) 0
                                else if (activeSkin == SkinType.GOLD) 2
                                else 1
                            ) {
                                engine.setActiveSkin(SkinType.GOLD)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // 🏺 LEGENDARIA — Dragones (orden fijo: fuego → agua → planta)
                    Text(
                        text = "DRAGONES",
                        color = rewViolet,
                        fontFamily = cinzel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Box(modifier = Modifier.weight(1f)) {
                            OptionChip(
                                Icons.Filled.LocalFireDepartment,
                                "Fuego",
                                rewViolet,
                                if (currentDragon == DragonType.FIRE) 2 else 1
                            ) {
                                engine.setCurrentDragon(DragonType.FIRE)
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OptionChip(
                                Icons.Filled.WaterDrop,
                                "Agua",
                                rewViolet,
                                if (!waterUnlocked) 0
                                else if (currentDragon == DragonType.WATER) 2
                                else 1
                            ) {
                                engine.setCurrentDragon(DragonType.WATER)
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OptionChip(
                                Icons.Filled.Eco,
                                "Planta",
                                rewViolet,
                                if (!plantUnlocked) 0
                                else if (currentDragon == DragonType.PLANT) 2
                                else 1
                            ) {
                                engine.setCurrentDragon(DragonType.PLANT)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "CERRAR",
                        color = rewGold,
                        fontFamily = cinzel,
                        fontSize = 15.sp,
                        letterSpacing = 2.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .border(1.5.dp, rewBorderNeutral, RoundedCornerShape(20.dp))
                            .clickable { showRewards = false }
                            .padding(horizontal = 22.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 📱 CONTENIDO UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 🔥 FIX (punto 4): toda la pantalla principal puede
                // desplazarse verticalmente si el contenido no cabe (p. ej.
                // en el Redmi). Ya no queda ningún Modifier.weight() en esta
                // Column, así que es seguro combinarlo con scroll.
                .verticalScroll(rememberScrollState())
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
                modifier = Modifier.offset(y = 28.dp + state.statsOffsetY.dp)
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
                            value = "${state.streak} días🔥"
                        )

                        if (state.streak > 0) {
                            Text(
                                text = "BONUS\nEXP!",
                                color = Color(0xFF4FC3F7),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {

                        StatCard(
                            title = "Felicidad",
                            value = "${state.happiness}❤️"
                        )

                        if (state.happiness == 7) {
                            Text(
                                text = "BONUS\nREWARDS!",
                                color = Color(0xFF4FC3F7),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                            )
                        }
                    }
                }

                // 🔥 FIX (punto 4): el botón ahora vive DENTRO de este mismo
                // Column, que es el que se desplaza con statsOffsetY. Así,
                // se mueva donde se mueva el HUD con los botones ▲▼, el
                // botón guarda SIEMPRE la misma distancia fija respecto a
                // las stats — cero riesgo de superposición, sin tener que
                // calcular márgenes según el offset máximo/mínimo posible.
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onStartActivity,
                    // .align() centra el botón horizontalmente dentro de
                    // este Column sin cambiar su tamaño, fuente ni estilo.
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF132B45),
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
// 🗑️ FIX: se eliminan funciones muertas (nunca llamadas desde ningún sitio):
// interpolateLatLng, calculateBearing, rotateBitmap (duplicada de
// DragonBitmapUtils, que también se elimina), getDirectionAngle
// (duplicada) y scaleBitmap. Eran restos de una función de "el dragón
// rota para mirar la dirección de movimiento" que se descartó.