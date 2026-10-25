package com.greskull.drakofit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * FASE 5: diagnóstico del estado del sistema para el tracking.
 *
 * Comprueba lo que de verdad decide si una sesión sobrevive con el móvil
 * bloqueado, y ofrece el atajo para arreglarlo.
 *
 * DECISIÓN DE DISEÑO (importante para Google Play): aquí NO se pide la
 * exclusión de optimización de batería con ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
 * Esa acción está restringida por política y exige justificación en la ficha.
 * Lo que hacemos es:
 *   • LEER el estado con isIgnoringBatteryOptimizations (esto es libre).
 *   • ABRIR la lista general de optimización de batería del sistema
 *     (ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), donde el usuario elige.
 *   • ABRIR los ajustes de la app y, si el fabricante lo permite, el arranque
 *     automático específico de ColorOS / MIUI / EMUI / One UI.
 */
object SystemHealth {

    /** Estado de cada comprobación. */
    enum class Level { OK, WARNING, PROBLEM }

    data class Check(
        val title: String,
        val detail: String,
        val level: Level,
        /** Texto del botón de acción; null si no hay acción posible. */
        val actionLabel: String? = null,
        /** Qué hará el botón. */
        val action: ((Context) -> Unit)? = null,
    )

    // ------------------------------------------------------------ permisos

    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // antes de Android 13 no hace falta pedirlo
        }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isPowerSaveMode(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return pm.isPowerSaveMode
    }

    /** Fabricante normalizado, para los textos y los atajos. */
    fun manufacturer(): String =
        (Build.MANUFACTURER ?: "").trim().lowercase()

    fun oemName(): String = when {
        manufacturer().contains("realme") -> "realme (ColorOS)"
        manufacturer().contains("oppo") -> "OPPO (ColorOS)"
        manufacturer().contains("xiaomi") || manufacturer().contains("redmi") -> "Xiaomi (MIUI/HyperOS)"
        manufacturer().contains("huawei") -> "Huawei (EMUI)"
        manufacturer().contains("honor") -> "Honor (MagicOS)"
        manufacturer().contains("samsung") -> "Samsung (One UI)"
        manufacturer().contains("vivo") -> "vivo (Funtouch/OriginOS)"
        manufacturer().contains("oneplus") -> "OnePlus (OxygenOS/ColorOS)"
        manufacturer().contains("motorola") -> "Motorola"
        else -> Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: "tu móvil"
    }

    // ---------------------------------------------------------- lista

    /** Todas las comprobaciones, en orden de importancia. */
    fun checks(context: Context): List<Check> {

        val fine = hasFineLocation(context)
        val notif = hasNotifications(context)
        val ignoring = isIgnoringBatteryOptimizations(context)
        val saver = isPowerSaveMode(context)

        return listOf(

            Check(
                title = "Ubicación precisa",
                detail = if (fine) {
                    "Concedida. El GPS puede funcionar."
                } else {
                    "Sin este permiso no se puede registrar ninguna ruta."
                },
                level = if (fine) Level.OK else Level.PROBLEM,
                actionLabel = if (fine) null else "Abrir ajustes de la app",
                action = if (fine) null else ::openAppSettings,
            ),

            Check(
                title = "Notificaciones",
                detail = if (notif) {
                    "Concedidas. Verás el tiempo y la distancia en la notificación."
                } else {
                    "Sin notificaciones el servicio sigue grabando, pero no verás " +
                        "el progreso ni podrás pausar desde ahí."
                },
                level = if (notif) Level.OK else Level.WARNING,
                actionLabel = if (notif) null else "Abrir ajustes de la app",
                action = if (notif) null else ::openAppSettings,
            ),

            Check(
                title = "Optimización de batería",
                detail = if (ignoring) {
                    "DrakoFit está excluida del ahorro de batería. Es lo correcto " +
                        "para que el sistema no la duerma a mitad de ruta."
                } else {
                    "El sistema puede cerrar DrakoFit a mitad de actividad. " +
                        "En ${oemName()}, quítale la optimización de batería."
                },
                level = if (ignoring) Level.OK else Level.PROBLEM,
                actionLabel = if (ignoring) null else "Abrir ajustes de batería",
                action = if (ignoring) null else ::openBatterySettings,
            ),

            Check(
                title = "Arranque automático",
                detail = if (isOemKnown()) {
                    "En ${oemName()} hay que permitir además el arranque en segundo " +
                        "plano (\"Inicio automático\" o \"Autoarranque\"). Si no, el " +
                        "fabricante puede cortar el servicio aunque la batería esté bien."
                } else {
                    "Si tu móvil tiene un ajuste de \"autoarranque\" o \"inicio " +
                        "automático\", permítelo para DrakoFit."
                },
                level = Level.WARNING,
                actionLabel = "Abrir ajustes de arranque",
                action = ::openAutostartSettings,
            ),

            Check(
                title = "Modo de ahorro de energía",
                detail = if (saver) {
                    "Está ACTIVADO ahora mismo. Puede reducir la frecuencia del GPS " +
                        "y retrasar las notificaciones. Desactívalo durante las rutas."
                } else {
                    "Desactivado. Correcto."
                },
                level = if (saver) Level.WARNING else Level.OK,
                actionLabel = if (saver) "Abrir ajustes de batería" else null,
                action = if (saver) ::openBatterySettings else null,
            ),
        )
    }

    /**
     * Pasos a mano para el fabricante detectado. Se muestran siempre porque
     * los atajos de arranque automático no son API pública y pueden cambiar
     * de una versión de ROM a otra.
     */
    fun manualSteps(): String = when {
        manufacturer().contains("realme") || manufacturer().contains("oppo") ->
            "1. Ajustes -> Batería -> Optimización de batería -> DrakoFit -> No optimizar\n" +
            "2. Ajustes -> Batería -> Más ajustes -> Inicio automático -> DrakoFit: ACTIVADO\n" +
            "3. Recientes: mantén pulsada la tarjeta de DrakoFit -> Bloquear (candado)"

        manufacturer().contains("xiaomi") || manufacturer().contains("redmi") ->
            "1. Ajustes -> Aplicaciones -> Administrar aplicaciones -> DrakoFit\n" +
            "2. Ahorro de batería -> Sin restricciones\n" +
            "3. Permisos -> Inicio automático: ACTIVADO\n" +
            "4. Recientes: baja la tarjeta de DrakoFit y pulsa el candado"

        manufacturer().contains("huawei") || manufacturer().contains("honor") ->
            "1. Ajustes -> Batería -> Inicio de aplicaciones -> DrakoFit: gestionar manualmente\n" +
            "2. Activa: Inicio automático, Inicio secundario y Ejecutar en segundo plano"

        manufacturer().contains("samsung") ->
            "1. Ajustes -> Batería y cuidado del dispositivo -> Batería\n" +
            "2. Límites de uso en segundo plano -> quita DrakoFit de las apps que se suspenden\n" +
            "3. Ajustes -> Aplicaciones -> DrakoFit -> Batería -> Sin restricciones"

        manufacturer().contains("vivo") ->
            "1. Ajustes -> Batería -> Consumo de energía en segundo plano -> DrakoFit: permitir\n" +
            "2. iManager -> Gestión de aplicaciones -> Autoarranque -> DrakoFit: ACTIVADO"

        else ->
            "1. Ajustes -> Aplicaciones -> DrakoFit -> Batería -> Sin restricciones\n" +
            "2. Si tu móvil tiene \"inicio automático\" o \"autoarranque\", permítelo\n" +
            "3. Bloquea la app en recientes (candado), si tu capa lo ofrece"
    }

    private fun isOemKnown(): Boolean = when {
        manufacturer().contains("realme") -> true
        manufacturer().contains("oppo") -> true
        manufacturer().contains("xiaomi") -> true
        manufacturer().contains("redmi") -> true
        manufacturer().contains("huawei") -> true
        manufacturer().contains("honor") -> true
        manufacturer().contains("samsung") -> true
        manufacturer().contains("vivo") -> true
        manufacturer().contains("oneplus") -> true
        else -> false
    }

    // ---------------------------------------------------------- acciones

    /** Ajustes de la app (permisos, batería en segundo plano). */
    fun openAppSettings(context: Context) {
        safeStart(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    /**
     * Lista GENERAL de optimización de batería. El usuario elige DrakoFit y
     * "No optimizar". Play-safe: no pedimos la exclusión directamente.
     */
    fun openBatterySettings(context: Context) {
        safeStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /**
     * Arranque automático del fabricante. Estos ajustes no tienen una API
     * estándar: se prueban los componentes conocidos de cada OEM y, si
     * ninguno existe, se abre la ficha de la app para que el usuario llegue
     * a mano. Nunca lanza excepción al usuario.
     */
    fun openAutostartSettings(context: Context) {

        val candidates = listOf(
            // ColorOS / realme (OPPO)
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.coloros.oppoguardelf" to "com.coloros.oppoguardelf.powermanager.PowerUsageDetailActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            // MIUI / HyperOS (Xiaomi, Redmi, POCO)
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            // EMUI / MagicOS (Huawei, Honor)
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            // One UI (Samsung)
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            // Funtouch / OriginOS (vivo)
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            // OxygenOS / ColorOS (OnePlus)
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        )

        for ((pkg, cls) in candidates) {
            val intent = Intent().apply {
                component = android.content.ComponentName(pkg, cls)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                safeStart(context, intent)
                return
            }
        }

        // Sin atajo para este fabricante: a la ficha de la app.
        openAppSettings(context)
    }

    private fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            try {
                openAppSettings(context)
            } catch (_: Exception) {
                // Nada más que hacer: no se rompe la app por esto.
            }
        }
    }
}
