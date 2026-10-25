package com.greskull.drakofit

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.greskull.drakofit.tracking.SessionFinalizer
import com.greskull.drakofit.tracking.TrackingState
import com.greskull.drakofit.tracking.TrackingUiState
import com.greskull.drakofit.tracking.domain.FilterConfig
import com.greskull.drakofit.tracking.domain.FixOutcome
import com.greskull.drakofit.tracking.domain.SessionMath
import com.greskull.drakofit.tracking.domain.Status
import com.greskull.drakofit.tracking.domain.TrackPoint
import com.greskull.drakofit.tracking.domain.TrackingFilter
import com.greskull.drakofit.tracking.persistence.SessionStore
import com.greskull.drakofit.tracking.persistence.SessionSummary
import com.greskull.drakofit.tracking.system.toFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FASE 2: el servicio pasa a ser el DUEÑO del GPS.
 *
 * Antes el `FusedLocationProviderClient` vivía en un `LaunchedEffect` de
 * `MapScreen`, así que el tracking moría con la Activity (y con ella, con
 * cualquier kill del sistema). Ahora:
 *
 *  • El servicio registra el GPS y pasa cada fix por `TrackingFilter`, la misma
 *    clase de la Fase 1: **ni un umbral, ni una fórmula ni una regla cambia.**
 *  • Publica todo en [TrackingState] (StateFlow), que la pantalla observa. Si
 *    la Activity muere y vuelve, se reconecta a un estado que nunca dejó de
 *    actualizarse.
 *  • Mantiene un `WakeLock` parcial + `startForeground` de tipo `location`, que
 *    es lo que evita que ColorOS/EMUI/MIUI congelen el proceso.
 *  • Muestra una notificación con tiempo y distancia en vivo.
 *  • Si el filtro marca GPS sospechoso, es el servicio quien cierra la sesión.
 */
class TrackingService : Service() {

    companion object {
        private const val TAG = "DrakoFit"

        const val CENTRAL_ID = "tracking_channel"
        const val NOTIF_ID = 1

        const val ACTION_START = "com.greskull.drakofit.action.START"
        const val ACTION_PAUSE = "com.greskull.drakofit.action.PAUSE"
        const val ACTION_RESUME = "com.greskull.drakofit.action.RESUME"
        const val ACTION_FINISH = "com.greskull.drakofit.action.FINISH"

        /** FASE 3: retomar una sesión que quedó a medias (proceso muerto). */
        const val ACTION_RESTORE = "com.greskull.drakofit.action.RESTORE"

        /** FASE 3: cada cuánto se reescribe el resumen en disco. */
        private const val SUMMARY_INTERVAL_MS = 5_000L

        /** El mismo intervalo de 3500 ms que tenía MapScreen. */
        private const val LOCATION_INTERVAL_MS = 3500L

        /** Los umbrales de siempre (Fase 1, valores por defecto = MapScreen). */
        private val CONFIG = FilterConfig(
            // FASE 3: partir la ruta cuando el GPS se pierde >60 s.
            enableGapSplitting = true,
        )

        /** La notificación se refresca como mucho una vez cada 2 s. */
        private const val NOTIFICATION_THROTTLE_MS = 2_000L

        // ------------------------------------------------ órdenes desde la UI

        fun start(context: Context) = send(context, ACTION_START)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun finish(context: Context) = send(context, ACTION_FINISH)

        /** Retoma una sesión guardada en disco. Arranca en PAUSA. */
        fun restore(context: Context) = send(context, ACTION_RESTORE)

        private fun send(context: Context, action: String) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TrackingService::class.java).setAction(action)
                )
            } catch (e: Exception) {
                // P. ej. arrancar el servicio desde background sin sesión
                Log.e(TAG, "No se pudo enviar '$action' al TrackingService", e)
            }
        }
    }

    // ------------------------------------------------------------- estado

    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var filter = TrackingFilter(CONFIG)

    // FASE 3: persistencia incremental
    private val store by lazy { SessionStore.forContextFilesDir(filesDir) }
    private var lastSummaryAtMs = 0L

    private var locationCallback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var points: MutableList<TrackPoint> = mutableListOf()
    private var publishedPoints: List<TrackPoint> = emptyList()
    private var revision = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    private var lastNotificationMs = 0L
    private var engine: DragonEngine? = null

    /** true cuando la sesión ya se ha cerrado y guardado (evita doble guardado). */
    private var finalized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        engine = (application as? DrakoFitApp)?.engine
    }

    // --------------------------------------------------------- start/stop

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // 1) startForeground SIEMPRE y de inmediato: en Android 14 el sistema
        //    mata el servicio si tarda más de unos segundos en promocionarse.
        goForeground(buildNotification())

        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_FINISH -> finishSession(byUser = true)
            ACTION_RESTORE -> restoreSession()
            null -> {
                // Reinicio del sistema (START_STICKY) sin sesión que recuperar.
                // La persistencia real llega en la Fase 3.
                if (!filter.status.isRunning()) stopSelfSafely()
            }
        }

        return START_STICKY
    }

    /**
     * El usuario ha cerrado la app desde el gestor de tareas.
     *
     * Decisión tomada contigo: si la actividad pasa el filtro de rutas largas,
     * se guarda y suma métricas y XP; si no, se descarta y el proceso muere
     * como si la actividad nunca hubiera existido.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        if (filter.status.isRunning()) {
            Log.d(TAG, "onTaskRemoved con sesión activa -> guardar y cerrar")
            finishSession(byUser = false)
        } else {
            stopSelfSafely()
        }
    }

    override fun onDestroy() {
        stopLocationUpdates()
        releaseWakeLock()
        tickerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // -------------------------------------------------------------- sesión

    private fun startSession() {

        // Idempotente: si ya hay sesión (p. ej. la Activity se recrea y vuelve
        // a mandar ACTION_START) no se reinicia nada.
        if (filter.status.isRunning()) {
            publish()
            return
        }

        points = mutableListOf()
        publishedPoints = emptyList()
        revision = 0
        finalized = false

        filter.start(SystemClock.elapsedRealtime())

        // FASE 3: si hay restos de una sesión anterior que no se cerró, se
        // descartan: empezar de cero con ficheros viejos sería peor.
        store.begin(currentSummary())

        acquireWakeLock()
        startLocationUpdates()
        startTicker()

        publish()
        Log.d(TAG, "Sesión iniciada por el servicio")
    }

    private fun pauseSession() {
        if (filter.status != Status.TRACKING) return
        filter.pause(SystemClock.elapsedRealtime())
        publish()
        refreshNotification(force = true)
    }

    private fun resumeSession() {
        if (filter.status != Status.PAUSED) return
        filter.resume(SystemClock.elapsedRealtime())

        // FASE 3: una sesión restaurada está en pausa y SIN GPS en marcha;
        // el primer fix tras reanudar pasará por el chequeo de distancia de
        // reanudar (150 m), que es justo lo que queremos.
        if (locationCallback == null) {
            startLocationUpdates()
            startTicker()
        }

        publish()
        refreshNotification(force = true)
    }

    /**
     * Cierra la sesión, guarda lo que corresponda y se apaga.
     *
     * [byUser] true si viene del botón "Finalizar" (o de la notificación).
     * Si la sesión se cierra sola por GPS sospechoso, [filter.stopReason] ya
     * vendrá informado y no se guarda nada.
     */
    private fun finishSession(byUser: Boolean) {

        if (finalized) return

        val now = SystemClock.elapsedRealtime()

        if (byUser) {
            filter.finish(now) // pone StopReason.USER si no había otro
        } else if (filter.status.isRunning()) {
            // Cierre sin decisión del usuario ni sospecha: equivale a finalizar
            // normal (el filtro no marca sospechoso por sí solo aquí).
            filter.finish(now)
        }

        finalized = true

        // Estado final ANTES de apagar: la pantalla (si está viva) necesita el
        // resumen para mostrar la tarjeta de resultado.
        publish()

        val state = TrackingState.current()
        val engineRef = engine

        if (engineRef != null && state.points.size >= 1) {
            try {
                val saved = SessionFinalizer.finalize(this, engineRef, state)
                Log.d(TAG, "Sesión cerrada: guardada=$saved km=${state.km}")
                // Mantiene la racha como el onFinish(NORMAL) de MainActivity.
                // registerActivity() es idempotente por día, así que no duplica.
                if (!state.isSuspicious) engineRef.registerActivity()
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando la sesión", e)
            }
        } else if (engineRef == null) {
            Log.e(TAG, "Sin engine disponible: sesión no guardada")
        }

        stopLocationUpdates()
        releaseWakeLock()
        tickerJob?.cancel()
        tickerJob = null

        // FASE 3: la sesión está cerrada (guardada o descartada), así que los
        // ficheros de recuperación ya no sirven.
        store.clear()

        stopSelfSafely()
    }

    /**
     * FASE 3: retomar una sesión que quedó a medias.
     *
     * Se restaura el filtro desde el resumen de disco, con la distancia,
     * tramos y reloj que tenía, y se deja **en pausa**: el usuario pulsa
     * "Continuar" en la pantalla y se llama a ACTION_RESUME. Ese primer fix
     * pasa por el chequeo de reanudar, así que si el usuario se ha movido
     * 300 m con el proceso muerto, la actividad se invalida como siempre.
     */
    private fun restoreSession() {

        if (filter.status.isRunning()) {
            // Ya hay una sesión viva: no se restaura nada encima.
            publish()
            return
        }

        val summary = store.readSummary()
        val persistedPoints = store.readPoints()

        if (summary == null || !summary.isRecoverable || persistedPoints.isEmpty()) {
            Log.w(TAG, "restore: no hay sesión recuperable en disco")
            store.clear()
            stopSelfSafely()
            return
        }

        val snapshot = SessionSummary.toFilterSnapshot(summary, persistedPoints.lastOrNull())
        filter = TrackingFilter.restore(CONFIG, snapshot)

        points = persistedPoints.toMutableList()
        revision = persistedPoints.size
        publishedPoints = emptyList() // fuerza el redibujado de la ruta completa
        finalized = false
        lastSummaryAtMs = 0L

        // Sin GPS todavía: la sesión está en pausa esperando al usuario.
        acquireWakeLock()
        startTicker()

        publish()
        refreshNotification(force = true)

        Log.d(TAG, "Sesión restaurada en pausa: ${summary.pointCount} puntos, " +
            "${"%.0f".format(summary.distanceM)} m")
    }

    /** Resumen del estado actual, para escribir en disco. */
    private fun currentSummary(): SessionSummary {
        val metrics = filter.metrics(SystemClock.elapsedRealtime())
        return SessionSummary(
            startedAtElapsedMs = filter.sessionStartMs,
            savedAtElapsedMs = SystemClock.elapsedRealtime(),
            elapsedMs = metrics.totalMs,
            activeMs = metrics.activeMs,
            pausedMs = metrics.pausedMs,
            distanceM = metrics.distanceM,
            pointCount = points.size,
            segmentCount = metrics.segmentCount,
            status = filter.status,
            stopReason = filter.stopReason,
            wasPaused = filter.status == Status.PAUSED,
        )
    }

    private fun stopSelfSafely() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    // ----------------------------------------------------------------- GPS

    private fun startLocationUpdates() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Sin permiso de ubicación: no se puede iniciar el tracking")
            return
        }

        // Misma configuración que tenía MapScreen.
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        ).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {

                val loc = result.lastLocation ?: return

                when (val outcome = filter.onFix(loc.toFix())) {

                    is FixOutcome.Accepted -> {
                        points.add(outcome.point)
                        revision++

                        // FASE 3: a disco en el acto (append + flush). Si el
                        // proceso muere justo después, este punto ya está.
                        store.appendPoint(outcome.point)

                        publish()
                        refreshNotification(force = false)
                    }

                    is FixOutcome.Rejected -> {
                        Log.d(TAG, "Fix descartado: ${outcome.reason}")
                    }

                    is FixOutcome.SessionStopped -> {
                        // 🚫 Decisión de diseño: con el móvil bloqueado es el
                        // SERVICIO quien cierra la sesión. La pantalla lo verá
                        // al abrirse (TrackingState.isSuspicious).
                        Log.d(TAG, "SESIÓN ANULADA por el filtro: ${outcome.reason}")
                        finishSession(byUser = false)
                    }
                }
            }
        }

        locationCallback = callback
        fusedClient.requestLocationUpdates(request, callback, mainLooper)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            try {
                fusedClient.removeLocationUpdates(it)
            } catch (_: Exception) {
            }
        }
        locationCallback = null
    }

    // ------------------------------------------------------------ corrutinas

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(1_000L)
                // El cronómetro avanza aunque el GPS tarde 3,5 s en dar el
                // siguiente fix, igual que hacía el `tick++` de MapScreen.
                publish()

                // FASE 3: resumen cada 5 s (son ~200 bytes, coste nulo)
                val now = SystemClock.elapsedRealtime()
                if (now - lastSummaryAtMs >= SUMMARY_INTERVAL_MS) {
                    lastSummaryAtMs = now
                    store.writeSummary(currentSummary())
                }

                refreshNotification(force = false)
            }
        }
    }

    // ---------------------------------------------------------------- estado

    private fun publish() {
        val metrics = filter.metrics(SystemClock.elapsedRealtime())
        val currentPoints = publishedPoints

        if (currentPoints.size != points.size) {
            publishedPoints = points.toList()
        }

        TrackingState.set(
            TrackingUiState(
                status = filter.status,
                stopReason = filter.stopReason,
                distanceM = metrics.distanceM,
                elapsedMs = metrics.totalMs,
                points = publishedPoints,
                revision = revision,
            )
        )
    }

    // ---------------------------------------------------------- notificación

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun refreshNotification(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotificationMs < NOTIFICATION_THROTTLE_MS) return
        lastNotificationMs = now

        if (!filter.status.isRunning()) return

        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo actualizar la notificación", e)
        }
    }

    private fun buildNotification(): Notification {

        val state = TrackingState.current()
        val running = filter.status.isRunning()
        val paused = filter.status == Status.PAUSED

        val title = when {
            paused -> "DrakoFit · en pausa"
            running -> "DrakoFit · registrando actividad"
            else -> "DrakoFit activo"
        }

        val text = if (running) {
            "${SessionMath.formatTime(state.elapsedMs)}  ·  " +
                "%.2f km".format(state.km)
        } else {
            "Registrando actividad en segundo plano"
        }

        return NotificationCompat.Builder(this, CENTRAL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .apply {
                if (running) {
                    addAction(
                        0,
                        if (paused) "Reanudar" else "Pausar",
                        servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, 1)
                    )
                    addAction(0, "Finalizar", servicePendingIntent(ACTION_FINISH, 2))
                }
            }
            .build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TrackingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CENTRAL_ID,
                "Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canal de tracking de actividad"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // ------------------------------------------------------------- wakelock

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DrakoFit::Tracking"
        ).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // tope de 6 h por seguridad
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }
}

/** Helper privado: ¿hay sesión en marcha? */
private fun Status.isRunning(): Boolean = this == Status.TRACKING || this == Status.PAUSED
