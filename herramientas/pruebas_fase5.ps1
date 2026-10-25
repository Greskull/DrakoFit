# ============================================================================
#  DrakoFit - Pruebas largas de la Fase 5
#  Requiere: ADB en el PATH y el movil conectado por USB con depuracion USB.
#
#  Uso, desde la carpeta del proyecto:
#      powershell -ExecutionPolicy Bypass -File herramientas\pruebas_fase5.ps1 -Accion ayuda
#
#  Acciones:
#      info       - datos del movil y estado de los ajustes que importan
#      preparar   - reinicia las estadisticas de bateria y limpia el log
#      iniciar    - marca el inicio de la prueba (guarda la hora y la bateria)
#      finalizar  - calcula consumo y saca el resumen de la sesion
#      doze_on    - fuerza Doze (pantalla apagada simulada)
#      doze_off   - quita el Doze forzado
#      kill       - mata el proceso de la app estando en segundo plano
#      log        - vuelca el log de DrakoFit a un archivo
# ============================================================================

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("info", "preparar", "iniciar", "finalizar", "doze_on", "doze_off", "kill", "log", "ayuda")]
    [string]$Accion
)

$ErrorActionPreference = "Stop"
$PKG = "com.greskull.drakofit"
$TAG = "DrakoFit"
$carpeta = "pruebas_fase5"
$estado = Join-Path $carpeta "estado.txt"

function Comprobar-Adb {
    try {
        $null = adb version 2>&1
    } catch {
        Write-Host "No encuentro adb en el PATH. Abre Android Studio -> SDK Manager -> Platform Tools y anadelo al PATH." -ForegroundColor Red
        exit 1
    }
    $devices = adb devices | Select-String -Pattern "device$"
    if (-not $devices) {
        Write-Host "No hay ningun dispositivo conectado o autorizado." -ForegroundColor Red
        Write-Host "Comprueba el cable y acepta el dialogo de 'Permitir depuracion USB' en el movil." -ForegroundColor Yellow
        exit 1
    }
}

function Leer-Bateria {
    $out = adb shell dumpsys battery
    $level = ($out | Select-String -Pattern "level:\s*(\d+)").Matches.Groups[1].Value
    return [int]$level
}

Comprobar-Adb
if (-not (Test-Path $carpeta)) { New-Item -ItemType Directory -Path $carpeta | Out-Null }

switch ($Accion) {

    "ayuda" {
        Write-Host ""
        Write-Host "PRUEBAS LARGAS - FASE 5" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "  Protocolo recomendado (por cada movil, 60+ minutos):"
        Write-Host ""
        Write-Host "   1. powershell -File herramientas\pruebas_fase5.ps1 -Accion info"
        Write-Host "   2. powershell -File herramientas\pruebas_fase5.ps1 -Accion preparar"
        Write-Host "   3. Abre DrakoFit y empieza una actividad"
        Write-Host "   4. powershell -File herramientas\pruebas_fase5.ps1 -Accion iniciar"
        Write-Host "   5. Bloquea el movil y dejalo quieto 60 minutos (o sal a andar)"
        Write-Host "   6. powershell -File herramientas\pruebas_fase5.ps1 -Accion finalizar"
        Write-Host ""
        Write-Host "  En mitad de la prueba, para simular el peor caso:"
        Write-Host "      -Accion doze_on    (fuerza Doze; quitarlo con doze_off)"
        Write-Host "      -Accion kill       (mata la app en segundo plano)"
        Write-Host ""
        Write-Host "  Resultado: carpeta '$carpeta' con el resumen y el log."
        Write-Host ""
    }

    "info" {
        Write-Host ""
        Write-Host "=== DISPOSITIVO ===" -ForegroundColor Cyan
        $model = (adb shell getprop ro.product.model).Trim()
        $brand = (adb shell getprop ro.product.brand).Trim()
        $android = (adb shell getprop ro.build.version.release).Trim()
        $sdk = (adb shell getprop ro.build.version.sdk).Trim()
        Write-Host ("  " + $brand + " " + $model + "  -  Android " + $android + " (API " + $sdk + ")")

        Write-Host ""
        Write-Host "=== ESTADO QUE IMPORTA PARA EL TRACKING ===" -ForegroundColor Cyan

        $bateria = Leer-Bateria
        Write-Host ("  Bateria: " + $bateria + " %")

        $opt = adb shell dumpsys deviceidle whitelist
        $excluida = if ($opt -match [regex]::Escape($PKG)) { "SI (correcto)" } else { "NO  <<< problema" }
        Write-Host ("  Excluida del ahorro de bateria: " + $excluida)

        $loc = adb shell dumpsys package $PKG | Select-String -Pattern "ACCESS_FINE_LOCATION|ACCESS_BACKGROUND_LOCATION"
        $fina = if ($loc -match "ACCESS_FINE_LOCATION: granted=true") { "concedida" } else { "NO concedida  <<< problema" }
        Write-Host ("  Ubicacion precisa: " + $fina)

        $fgs = adb shell dumpsys activity services $PKG | Select-String -Pattern "foreground"
        if ($fgs) {
            Write-Host "  Servicio en primer plano: activo ahora mismo"
        } else {
            Write-Host "  Servicio en primer plano: no activo (normal si no hay sesion)"
        }

        Write-Host ""
        Write-Host "  Guarda esta salida en el informe de la fase." -ForegroundColor Gray
        Write-Host ""
    }

    "preparar" {
        Write-Host "Reiniciando estadisticas de bateria..." -ForegroundColor Cyan
        adb shell dumpsys batterystats --reset | Out-Null
        adb logcat -c
        Write-Host "Hecho. Estadisticas a cero y log limpio." -ForegroundColor Green
        Write-Host "Ahora abre DrakoFit y empieza una actividad." -ForegroundColor Gray
    }

    "iniciar" {
        $hora = Get-Date
        $bateria = Leer-Bateria

        adb shell dumpsys batterystats --charged > (Join-Path $carpeta "batterystats_inicio.txt") 2>&1

        "inicio=$($hora.ToString('o'))`nbateria_inicio=$bateria" | Set-Content $estado

        Write-Host ""
        Write-Host ("Prueba iniciada: " + $hora.ToString("HH:mm:ss") + "  -  bateria " + $bateria + " %") -ForegroundColor Green
        Write-Host "Bloquea el movil y dejalo quieto. Cuando termines: -Accion finalizar" -ForegroundColor Gray
        Write-Host ""
    }

    "finalizar" {
        if (-not (Test-Path $estado)) {
            Write-Host "No hay una prueba en marcha (falta $estado). Ejecuta antes -Accion iniciar." -ForegroundColor Red
            exit 1
        }

        $datos = Get-Content $estado | ConvertFrom-StringData
        $inicio = [datetime]::Parse($datos.inicio)
        $bateriaInicio = [int]$datos.bateria_inicio

        $fin = Get-Date
        $bateriaFin = Leer-Bateria
        $minutos = [math]::Round(($fin - $inicio).TotalMinutes, 1)

        adb shell dumpsys batterystats --charged > (Join-Path $carpeta "batterystats_fin.txt") 2>&1
        adb shell dumpsys deviceidle > (Join-Path $carpeta "deviceidle_fin.txt") 2>&1

        $consumo = $bateriaInicio - $bateriaFin
        $porHora = if ($minutos -gt 0) { [math]::Round($consumo * 60 / $minutos, 2) } else { 0 }

        $lineas = @()
        $lineas += "PRUEBA LARGA - RESUMEN"
        $lineas += "====================="
        $lineas += ("Inicio:   " + $inicio.ToString("HH:mm:ss") + "   bateria " + $bateriaInicio + " %")
        $lineas += ("Fin:      " + $fin.ToString("HH:mm:ss") + "   bateria " + $bateriaFin + " %")
        $lineas += ("Duracion: " + $minutos + " minutos")
        $lineas += ("Consumo:  " + $consumo + " puntos de bateria  ->  " + $porHora + " %/hora")
        $lineas += ""
        $lineas += "REFERENCIAS ORIENTATIVAS (GPS de alta precision activo):"
        $lineas += "  < 4 %/hora   excelente"
        $lineas += "  4-7 %/hora   normal"
        $lineas += "  > 8 %/hora   revisar: intervalo de 3500 ms, precisión, o exceso de pantalla encendida"

        $lineas | Write-Host
        $lineas | Set-Content (Join-Path $carpeta ("resumen_" + $fin.ToString("yyyyMMdd_HHmmss") + ".txt"))

        Remove-Item $estado -ErrorAction SilentlyContinue

        Write-Host ""
        Write-Host "Ahora comprueba en la app que la ruta tiene los minutos correctos." -ForegroundColor Yellow
        Write-Host ""
    }

    "doze_on" {
        Write-Host "Forzando Doze..." -ForegroundColor Cyan
        adb shell dumpsys deviceidle force-idle
        adb shell dumpsys deviceidle get deep
        Write-Host "El sistema cree que el movil lleva horas quieto." -ForegroundColor Yellow
        Write-Host "Comprueba que la sesion sigue sumando. Para salir: -Accion doze_off" -ForegroundColor Gray
    }

    "doze_off" {
        Write-Host "Quitando Doze forzado..." -ForegroundColor Cyan
        adb shell dumpsys deviceidle unforce
        adb shell dumpsys deviceidle get deep
        Write-Host "Hecho." -ForegroundColor Green
    }

    "kill" {
        Write-Host "Matando el proceso de la app (estando en segundo plano)..." -ForegroundColor Cyan
        adb shell am kill $PKG
        Write-Host "Hecho. Abre DrakoFit: debe ofrecer 'Continuar' o 'Guardar y cerrar'." -ForegroundColor Yellow
        Write-Host "Si no aparece el dialogo, mira el log: -Accion log" -ForegroundColor Gray
    }

    "log" {
        $archivo = Join-Path $carpeta ("log_" + (Get-Date).ToString("yyyyMMdd_HHmmss") + ".txt")
        adb logcat -d -s "$TAG" > $archivo
        Write-Host ("Log guardado en " + $archivo) -ForegroundColor Green
        Write-Host "Lineas mas relevantes:" -ForegroundColor Gray
        Get-Content $archivo | Select-String -Pattern "Sesión|Sesion|restaurada|ANULADA|guardada|Fix descartado" | Select-Object -Last 20
    }
}
