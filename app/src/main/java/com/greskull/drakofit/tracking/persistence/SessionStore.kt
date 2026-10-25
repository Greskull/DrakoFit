package com.greskull.drakofit.tracking.persistence

import com.greskull.drakofit.tracking.domain.TrackPoint
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Persistencia incremental de la sesión en curso.
 *
 * Diseño pensado para que **morir el proceso no pierda la ruta**:
 *
 *  • Los puntos se van AÑADIENDO a un fichero binario en cada fix. Nunca se
 *    reescribe el fichero entero, así que el coste por fix es constante y el
 *    riesgo de corrupción es mínimo.
 *  • Un resumen pequeño en texto se reescribe cada pocos segundos.
 *  • La escritura es "append + flush" (no `sync`): sobrevive a la muerte del
 *    proceso, que es el caso real. Un corte de energía podría perder los
 *    últimos cientos de milisegundos, y eso es aceptable.
 *
 * Formato de cada punto: 20 bytes, big-endian.
 *
 *   lat      Int    grados × 1e7   (resolución ~1,1 cm)
 *   lon      Int    grados × 1e7
 *   tRel     Int    ms desde el inicio de la sesión (cubre 24 días)
 *   dist     Float  metros acumulados
 *   segment  Short  índice de tramo (para los huecos de GPS)
 *   acc      Byte?  -> se usa Short para no perder precisión: ver abajo
 *
 * Realmente son 4+4+4+4+2+2 = 20 bytes: el último Short es la precisión en
 * metros (0-32767, sobra).
 *
 * No usa `org.json` ni ninguna librería: solo `java.io`, así que es testeable
 * en la JVM sin Android (`SessionStoreTest`).
 */
class SessionStore(private val dir: File) {

    companion object {
        const val POINTS_FILE = "session_points.bin"
        const val SUMMARY_FILE = "session_summary.txt"
        const val RECORD_BYTES = 20

        /** Carpeta dentro de `filesDir` para no mezclar con otros datos. */
        fun forContextFilesDir(filesDir: File): SessionStore {
            val d = File(filesDir, "tracking")
            if (!d.exists()) d.mkdirs()
            return SessionStore(d)
        }
    }

    private val pointsFile: File get() = File(dir, POINTS_FILE)
    private val summaryFile: File get() = File(dir, SUMMARY_FILE)

    /** base de tiempo de los `tRel` del fichero de puntos. */
    private var sessionStartMs: Long = 0L

    // ------------------------------------------------------------- ciclo

    /**
     * Empieza una sesión nueva: borra lo anterior y escribe la cabecera del
     * resumen. Se llama al arrancar la sesión, NO en cada fix.
     */
    fun begin(summary: SessionSummary) {
        dir.mkdirs()
        sessionStartMs = summary.startedAtElapsedMs
        pointsFile.delete()
        pointsFile.createNewFile()
        writeSummary(summary)
    }

    /** Borra todo. Se llama al guardar la ruta o al descartar la sesión. */
    fun clear() {
        pointsFile.delete()
        summaryFile.delete()
    }

    /** true si hay una sesión sin cerrar en disco. */
    fun hasPending(): Boolean = summaryFile.exists() && summaryFile.length() > 0L

    fun pendingPointCountOnDisk(): Int {
        if (!pointsFile.exists()) return 0
        return (pointsFile.length() / RECORD_BYTES).toInt()
    }

    // ------------------------------------------------------------ puntos

    /** Añade un punto al fichero. Llamado en cada fix aceptado. */
    fun appendPoint(point: TrackPoint) {
        appendPoints(listOf(point))
    }

    /**
     * Añade varios puntos de golpe (una sola apertura de fichero).
     * Pensado para el ticker, que puede acumular más de un punto entre
     * escrituras si el GPS va rápido.
     */
    fun appendPoints(points: List<TrackPoint>) {
        if (points.isEmpty()) return

        val buffer = ByteArrayOutputStream(RECORD_BYTES * points.size)
        val out = DataOutputStream(buffer)

        for (p in points) {
            out.writeInt((p.lat * 1e7).toInt())
            out.writeInt((p.lon * 1e7).toInt())
            out.writeInt((p.elapsedMs - sessionStartMs).toInt())
            out.writeFloat(p.distanceM.toFloat())
            out.writeShort(p.segmentIndex.coerceIn(0, Short.MAX_VALUE.toInt()))
            out.writeShort(p.accuracyM.coerceIn(0f, 32767f).toInt())
        }
        out.flush()

        try {
            dir.mkdirs()
            java.io.FileOutputStream(pointsFile, true).use { fos ->
                fos.write(buffer.toByteArray())
                fos.flush()
            }
        } catch (_: Exception) {
            // Si el disco falla no se puede hacer nada: la sesión sigue en
            // memoria y, en el peor caso, se pierde el tramo no escrito.
        }
    }

    /**
     * Lee los puntos guardados.
     *
     * Tolerante a un fichero truncado (muerte del proceso a mitad de una
     * escritura): se devuelven los registros completos y se ignora el resto.
     */
    fun readPoints(): List<TrackPoint> {
        if (!pointsFile.exists()) return emptyList()

        val bytes = try {
            pointsFile.readBytes()
        } catch (_: Exception) {
            return emptyList()
        }

        val complete = bytes.size / RECORD_BYTES
        if (complete == 0) return emptyList()

        // Si el store se acaba de abrir (proceso nuevo), la base de tiempos
        // está en el resumen, no en memoria.
        val base = if (sessionStartMs != 0L) sessionStartMs else {
            readSummary()?.startedAtElapsedMs ?: 0L
        }

        val result = ArrayList<TrackPoint>(complete)
        for (i in 0 until complete) {
            val o = i * RECORD_BYTES
            val lat = readInt(bytes, o) / 1e7
            val lon = readInt(bytes, o + 4) / 1e7
            val tRel = readInt(bytes, o + 8)
            val dist = readFloat(bytes, o + 12)
            val segment = readShort(bytes, o + 16).toInt()
            val acc = readShort(bytes, o + 18).toFloat()

            result.add(
                TrackPoint(
                    lat = lat,
                    lon = lon,
                    elapsedMs = base + tRel,
                    accuracyM = acc,
                    distanceM = dist.toDouble(),
                    segmentIndex = segment,
                )
            )
        }
        return result
    }

    // ----------------------------------------------------------- resumen

    /** Reescribe el resumen. Barato: son ~200 bytes. */
    fun writeSummary(summary: SessionSummary) {
        try {
            dir.mkdirs()
            summaryFile.writeText(summary.serialize())
        } catch (_: Exception) {
        }
    }

    fun readSummary(): SessionSummary? {
        if (!summaryFile.exists()) return null
        return try {
            SessionSummary.parse(summaryFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------- binarios

    private fun readInt(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or
            ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or
            (b[o + 3].toInt() and 0xFF)

    private fun readShort(b: ByteArray, o: Int): Short =
        (((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)).toShort()

    private fun readFloat(b: ByteArray, o: Int): Float =
        Float.fromBits(readInt(b, o))
}
