package com.greskull.drakofit.tracking.domain

/**
 * Simulador de GPS para los tests: reloj manual + generador de fixes.
 *
 * Sin `Thread.sleep` ni reloj real, así que 30 minutos simulados corren en
 * milisegundos y los tests son deterministas.
 */
class FakeGps(startMs: Long = 1_000_000L) {

    var now: Long = startMs
        private set

    var lat: Double = 40.4168
        private set
    var lon: Double = -3.7038
        private set

    fun advance(ms: Long): FakeGps {
        now += ms
        return this
    }

    fun fix(accuracyM: Float = 5f, speedMps: Double? = null): Fix =
        Fix(lat, lon, now, accuracyM, speedMps)

    fun fixAt(
        dNorthM: Double = 0.0,
        dEastM: Double = 0.0,
        accuracyM: Float = 5f,
        speedMps: Double? = null,
    ): Fix = Fix(
        lat + dNorthM * LAT_M_TO_DEG,
        lon + dEastM * LON_M_TO_DEG,
        now,
        accuracyM,
        speedMps,
    )

    fun moveNorth(meters: Double): FakeGps {
        lat += meters * LAT_M_TO_DEG
        return this
    }

    /** Camina en línea recta con fixes cada [intervalMs]. */
    fun walk(
        steps: Int,
        speedMps: Double = 1.4,
        intervalMs: Long = 1_000L,
        accuracyM: Float = 5f,
        emit: (Fix) -> Unit,
    ) {
        repeat(steps) {
            advance(intervalMs)
            moveNorth(speedMps * (intervalMs / 1000.0))
            emit(fix(accuracyM = accuracyM, speedMps = speedMps))
        }
    }

    /** Conduce a [kmh] en línea recta. */
    fun drive(
        seconds: Int,
        kmh: Double,
        intervalMs: Long = 1_000L,
        accuracyM: Float = 5f,
        emit: (Fix) -> Unit,
    ) {
        val speed = kmh / 3.6
        val steps = (seconds * 1000 / intervalMs).toInt()
        repeat(steps) {
            advance(intervalMs)
            moveNorth(speed * (intervalMs / 1000.0))
            emit(fix(accuracyM = accuracyM, speedMps = speed))
        }
    }

    companion object {
        const val LAT_M_TO_DEG = 1.0 / 111_320.0
        const val LON_M_TO_DEG = 1.0 / (111_320.0 * 0.7604) // ~cos(40,4°)
    }
}
