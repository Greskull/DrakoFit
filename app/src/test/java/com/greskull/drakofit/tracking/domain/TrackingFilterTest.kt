package com.greskull.drakofit.tracking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la lógica de filtrado (Fase 1).
 *
 * Sin Android, sin Robolectric, sin emulador: `./gradlew :app:testDebugUnitTest`.
 *
 * El contrato que se verifica es el de la app real: cuando el filtro salta, la
 * sesión se FINALIZA como sospechosa y no suma ninguna métrica más.
 */
class TrackingFilterTest {

    private val cfg = FilterConfig()

    private fun accepted(outcome: FixOutcome): FixOutcome.Accepted {
        assertTrue("se esperaba un fix aceptado, llegó $outcome", outcome is FixOutcome.Accepted)
        return outcome as FixOutcome.Accepted
    }

    private fun reason(outcome: FixOutcome): Rejection {
        assertTrue("se esperaba rechazo, llegó $outcome", outcome is FixOutcome.Rejected)
        return (outcome as FixOutcome.Rejected).reason
    }

    private fun stopped(outcome: FixOutcome): StopReason {
        assertTrue("se esperaba finalización, llegó $outcome", outcome is FixOutcome.SessionStopped)
        return (outcome as FixOutcome.SessionStopped).reason
    }

    // ------------------------------------------------------------- haversine

    @Test
    fun `haversine mide 100 m al norte con error despreciable`() {
        val d = Geo.haversineMeters(40.4168, -3.7038, 40.4168 + 100 * FakeGps.LAT_M_TO_DEG, -3.7038)
        assertEquals(100.0, d, 0.5)
    }

    @Test
    fun `haversine de distancia cero es cero`() {
        assertEquals(0.0, Geo.haversineMeters(40.0, -3.0, 40.0, -3.0), 0.0001)
    }

    // ------------------------------------------------------- precision (25 m)

    @Test
    fun `fix con precision peor de 25 m se descarta en silencio y no finaliza`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)

        assertEquals(Rejection.ACCURACY, reason(filter.onFix(gps.fix(accuracyM = 60f))))
        assertEquals("un fix malo no puede finalizar la sesión", Status.TRACKING, filter.status)
        assertEquals(0, filter.pointCount)
    }

    @Test
    fun `un fix malo no puede confirmar una sospecha pendiente`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000) // fuera de la gracia

        // B: salto imposible => sospecha pendiente
        gps.moveNorth(5_000.0)
        assertEquals(Rejection.SPEED_SUSPECT, reason(filter.onFix(gps.fix())))

        // C con mala precisión: se descarta, la sospecha sigue pendiente
        gps.moveNorth(500.0)
        gps.advance(1000)
        assertEquals(Rejection.ACCURACY, reason(filter.onFix(gps.fix(accuracyM = 80f))))
        assertEquals(Status.TRACKING, filter.status)
        assertTrue(filter.suspicionPending)
    }

    @Test
    fun `un fix sin dato de precision se acepta como hoy`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        accepted(filter.onFix(gps.fix(accuracyM = -1f))) // hasAccuracy() == false
    }

    // ------------------------------------------------- escenarios de vehículo

    /** Devuelve (distancia, puntos, motivo de parada, estado). */
    private fun coche(kmh: Double, seconds: Int = 120): SessionMetrics {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.drive(seconds = seconds, kmh = kmh) { filter.onFix(it) }
        return filter.metrics(gps.now)
    }

    @Test
    fun `coche a 60 km h finaliza la sesion como sospechosa`() {
        val m = coche(60.0)
        assertEquals(StopReason.SUSPICIOUS_SPEED, m.stopReason)
        assertEquals(Status.FINISHED, m.status)
        assertTrue(m.isSuspicious)
    }

    @Test
    fun `coche a 80 km h finaliza la sesion como sospechosa`() {
        assertEquals(StopReason.SUSPICIOUS_SPEED, coche(80.0).stopReason)
    }

    @Test
    fun `coche a 110 km h finaliza la sesion como sospechosa`() {
        assertEquals(StopReason.SUSPICIOUS_SPEED, coche(110.0).stopReason)
    }

    @Test
    fun `una sesion sospechosa no suma ninguna metrica`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.drive(seconds = 60, kmh = 60.0) { filter.onFix(it) }

        val m = filter.metrics(gps.now)
        assertEquals("una ruta invalidada no puede tener distancia", 0.0, m.distanceM, 0.001)
        assertEquals("una ruta invalidada no puede tener puntos", 1, m.pointCount)
        assertFalse("no debe haber ruta guardable", filter.hasUsableRoute())
    }

    @Test
    fun `un unico fix ruidoso no finaliza la sesion`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.walk(steps = 10, speedMps = 1.4) { filter.onFix(it) }
        val distanciaAntes = filter.metrics(gps.now).distanceM

        // B: glitch de 400 m en 1 s (1440 km/h)
        gps.advance(1000)
        gps.moveNorth(400.0)
        assertEquals(Rejection.SPEED_SUSPECT, reason(filter.onFix(gps.fix())))
        assertTrue(filter.suspicionPending)

        // C: vuelve a la posición real. Media A→C plausible => B era ruido.
        gps.advance(1000)
        gps.moveNorth(-400.0)
        gps.moveNorth(1.4)
        val resultado = filter.onFix(gps.fix())

        assertEquals("un pico aislado NO debe invalidar la actividad", Status.TRACKING, filter.status)
        assertTrue(resultado is FixOutcome.Accepted)
        assertFalse(filter.suspicionPending)
        assertTrue(
            "la distancia debe seguir creciendo",
            filter.metrics(gps.now).distanceM > distanciaAntes,
        )
    }

    @Test
    fun `B seguido de salto imposible en C confirma la sospecha`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)

        gps.moveNorth(500.0)
        gps.advance(1000)
        filter.onFix(gps.fix()) // B

        gps.moveNorth(500.0)
        gps.advance(1000)
        assertEquals(StopReason.SUSPICIOUS_SPEED, stopped(filter.onFix(gps.fix()))) // C
    }

    // ------------------------------------------------------ periodo de gracia

    @Test
    fun `dentro de la gracia un coche no finaliza la sesion`() {
        val gps = FakeGps(startMs = 0L)
        val filter = TrackingFilter(FilterConfig(graceDurationMs = 5_000L))
        filter.start(gps.now)
        filter.onFix(gps.fix())

        gps.drive(seconds = 4, kmh = 60.0) { filter.onFix(it) }

        assertEquals(
            "los 5 s de gracia son justo para que un Redmi no invalide la actividad",
            Status.TRACKING,
            filter.status,
        )
        assertEquals(StopReason.NONE, filter.stopReason)
    }

    @Test
    fun `durante la gracia un salto no regala distancia`() {
        val gps = FakeGps(startMs = 0L)
        val filter = TrackingFilter(FilterConfig(graceDurationMs = 5_000L))
        filter.start(gps.now)
        filter.onFix(gps.fix())

        // salto de 85 m en 1 s (306 km/h) dentro de la gracia
        gps.advance(1_000)
        gps.moveNorth(85.0)
        filter.onFix(gps.fix())

        assertEquals("la gracia no puede regalar 85 m", 0.0, filter.metrics(gps.now).distanceM, 0.001)
    }

    @Test
    fun `la gracia arranca con el primer fix valido no con start`() {
        val gps = FakeGps(startMs = 0L)
        val filter = TrackingFilter(FilterConfig(graceDurationMs = 5_000L))
        filter.start(0L)

        // el primer fix válido llega 30 s después (GPS frío)
        gps.advance(30_000)
        filter.onFix(gps.fix())

        // a los 3 s de ese primer fix seguimos en gracia
        gps.advance(3_000)
        gps.moveNorth(50.0) // salto imposible, pero estamos en gracia
        filter.onFix(gps.fix())
        assertEquals(Status.TRACKING, filter.status)
        assertEquals(StopReason.NONE, filter.stopReason)
    }

    @Test
    fun `fuera de la gracia el filtro vuelve a estar armado`() {
        val gps = FakeGps(startMs = 0L)
        val filter = TrackingFilter(FilterConfig(graceDurationMs = 5_000L))
        filter.start(gps.now)
        filter.onFix(gps.fix())

        gps.advance(6_000) // fuera de gracia
        gps.moveNorth(500.0)
        filter.onFix(gps.fix()) // B
        gps.advance(1000)
        gps.moveNorth(500.0)
        assertEquals(StopReason.SUSPICIOUS_SPEED, stopped(filter.onFix(gps.fix())))
    }

    // --------------------------------------------------- pausa / reanudación

    @Test
    fun `reanudar lejos finaliza la actividad como sospechosa`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000) // salir de la gracia
        gps.walk(steps = 60, speedMps = 1.4) { filter.onFix(it) }

        filter.pause(gps.now)
        gps.advance(600_000) // 10 min en pausa
        filter.resume(gps.now)

        // 300 m de salto, más del tope de 150 m
        gps.advance(3_500)
        gps.moveNorth(300.0)
        assertEquals(StopReason.SUSPICIOUS_RESUME, stopped(filter.onFix(gps.fix())))
        assertEquals(Status.FINISHED, filter.status)
    }

    @Test
    fun `reanudar cerca continua la sesion`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 60, speedMps = 1.4) { filter.onFix(it) }
        val antes = filter.metrics(gps.now).distanceM

        filter.pause(gps.now)
        gps.advance(300_000)
        filter.resume(gps.now)

        gps.advance(3_500)
        gps.moveNorth(30.0) // 30 m: dentro del tope
        filter.onFix(gps.fix())

        assertEquals(Status.TRACKING, filter.status)
        assertTrue("debe seguir sumando", filter.metrics(gps.now).distanceM > antes)
    }

    @Test
    fun `el chequeo de reanudar es de un solo uso`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 60, speedMps = 1.4) { filter.onFix(it) }

        filter.pause(gps.now)
        gps.advance(60_000)
        filter.resume(gps.now)

        // pase el chequeo o no, se consume: aquí pasa (10 m)
        gps.advance(3_500)
        gps.moveNorth(10.0)
        filter.onFix(gps.fix())

        // Un salto posterior ya lo juzga el filtro de velocidad, no el de reanudar
        gps.advance(3_500)
        gps.moveNorth(300.0)
        val outcome = filter.onFix(gps.fix())
        assertTrue("no debe ser SUSPICIOUS_RESUME", outcome !is FixOutcome.SessionStopped)
    }

    @Test
    fun `los fixes durante la pausa se ignoran sin tocar nada`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 10, speedMps = 1.4) { filter.onFix(it) }
        val antes = filter.metrics(gps.now)

        filter.pause(gps.now)
        gps.advance(1000)
        gps.moveNorth(50.0)
        assertEquals(Rejection.PAUSED, reason(filter.onFix(gps.fix())))
        assertEquals(antes.distanceM, filter.metrics(gps.now).distanceM, 0.001)
    }

    @Test
    fun `la pausa no cuenta en el cronometro ni en el ritmo`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)

        gps.walk(steps = 600, speedMps = 1.0) { filter.onFix(it) }

        filter.pause(gps.now)
        gps.advance(300_000) // 5 min
        filter.resume(gps.now)

        val m = filter.metrics(gps.now)
        assertEquals("la pausa no suma al cronómetro", 600_000.0, m.totalMs.toDouble(), 8_000.0)
        assertEquals(300_000.0, m.pausedMs.toDouble(), 8_000.0)
        assertNotNull(m.averagePaceSecPerKm)
        assertTrue("el ritmo no debe arrastrar la pausa", m.averagePaceSecPerKm!! < 1200.0)
    }

    // --------------------------------------------------------- caminata

    @Test
    fun `caminata de 30 minutos acumula la distancia real`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)

        gps.walk(steps = 1800, speedMps = 1.4) { filter.onFix(it) }

        val esperado = 1800 * 1.4 + 1.4 * 6 // incluye el tramo fuera de gracia
        val m = filter.metrics(gps.now)
        assertEquals(StopReason.NONE, m.stopReason)
        assertEquals(Status.TRACKING, m.status)
        assertEquals("distancia fuera de tolerancia", esperado, m.distanceM, esperado * 0.04)
        assertTrue(filter.hasUsableRoute())
    }

    // ------------------------------------------------------- robustez

    @Test
    fun `fixes fuera de orden se rechazan`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(5000)
        filter.onFix(gps.fix())
        val antiguo = Fix(gps.lat, gps.lon, gps.now - 3000, 5f)
        assertEquals(Rejection.OUT_OF_ORDER, reason(filter.onFix(antiguo)))
    }

    @Test
    fun `un fix sin sesion iniciada se rechaza`() {
        assertEquals(Rejection.NOT_STARTED, reason(TrackingFilter(cfg).onFix(FakeGps().fix())))
    }

    @Test
    fun `tras finalizar no se aceptan mas fixes`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.walk(steps = 10, speedMps = 1.4) { filter.onFix(it) }
        filter.finish(gps.now)
        assertEquals(StopReason.USER, filter.stopReason)
        assertEquals(Rejection.FINISHED, reason(filter.onFix(gps.fix())))
    }

    @Test
    fun `finalizar a mano no se confunde con sospechoso`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 10, speedMps = 1.4) { filter.onFix(it) }
        filter.finish(gps.now)
        val m = filter.metrics(gps.now)
        assertFalse(m.isSuspicious)
        assertTrue(filter.hasUsableRoute())
    }

    @Test
    fun `metricas no dividen por cero al arrancar`() {
        val filter = TrackingFilter(cfg)
        filter.start(0L)
        val m = filter.metrics(0L)
        assertEquals(0.0, m.distanceM, 0.0)
        assertEquals(0.0, m.averageSpeedMps, 0.0)
        assertNull(m.averagePaceSecPerKm)
        assertFalse(m.isSuspicious)
    }

    @Test
    fun `msSinceLastFix devuelve null antes del primer fix y ms despues`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        assertNull(filter.msSinceLastFix(gps.now))
        filter.onFix(gps.fix())
        gps.advance(61_000)
        assertEquals(61_000L, filter.msSinceLastFix(gps.now))
    }

    // ------------------------------------------------- persistencia (F3)

    @Test
    fun `una foto del estado restaura la sesion exactamente igual`() {
        val gps = FakeGps()
        val original = TrackingFilter(cfg)
        original.start(gps.now)
        original.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 200, speedMps = 1.4) { original.onFix(it) }

        val restored = TrackingFilter.restore(cfg, original.snapshot())

        assertEquals(original.metrics(gps.now).distanceM, restored.metrics(gps.now).distanceM, 0.0001)
        assertEquals(original.pointCount, restored.pointCount)
        assertEquals(Status.TRACKING, restored.status)

        gps.advance(1000)
        gps.moveNorth(1.4)
        val cont = accepted(restored.onFix(gps.fix()))
        assertFalse(cont.newSegment)
        assertTrue(
            "debe continuar desde la distancia acumulada",
            restored.metrics(gps.now).distanceM > original.metrics(gps.now).distanceM,
        )
    }

    @Test
    fun `una foto de una sesion sospechosa conserva el motivo`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.drive(seconds = 60, kmh = 70.0) { filter.onFix(it) }

        val restored = TrackingFilter.restore(cfg, filter.snapshot())
        assertEquals(StopReason.SUSPICIOUS_SPEED, restored.stopReason)
        assertEquals(Status.FINISHED, restored.status)
    }
}

/**
 * FASE 3: pruebas de la partición de ruta en los huecos de GPS.
 * El filtro por defecto NO parte la ruta (enableGapSplitting = false), así que
 * aquí se activa explícitamente.
 */
class GapSplittingTest {

    private val cfg = FilterConfig(enableGapSplitting = true)

    private fun reason(o: FixOutcome): Rejection = (o as FixOutcome.Rejected).reason

    @Test
    fun `un hueco de 90 s parte la ruta y no suma la distancia del hueco`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 60, speedMps = 1.4) { filter.onFix(it) }

        val antes = filter.metrics(gps.now)

        // túnel: 90 s sin señal, 100 m recorridos de verdad
        gps.advance(90_000)
        gps.moveNorth(100.0)
        val outcome = filter.onFix(gps.fix())

        assertTrue("debe aceptarse y abrir tramo", outcome is FixOutcome.Accepted)
        assertTrue("debe marcar tramo nuevo", (outcome as FixOutcome.Accepted).newSegment)
        assertEquals("no debe sumar la distancia del hueco", 0.0, outcome.distanceDeltaM, 0.001)

        val despues = filter.metrics(gps.now)
        assertEquals(antes.distanceM, despues.distanceM, 0.001)
        assertEquals("deben quedar 2 tramos", 2, despues.segmentCount)
    }

    @Test
    fun `tras el hueco la sesion sigue viva y suma con normalidad`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 30, speedMps = 1.4) { filter.onFix(it) }

        gps.advance(90_000)
        gps.moveNorth(100.0)
        filter.onFix(gps.fix()) // abre tramo 2

        gps.walk(steps = 60, speedMps = 1.4) { filter.onFix(it) }

        val m = filter.metrics(gps.now)
        assertEquals(Status.TRACKING, m.status)
        assertEquals(StopReason.NONE, m.stopReason)
        assertTrue("debe seguir sumando tras el hueco", m.distanceM > 80.0)
    }

    @Test
    fun `un hueco con salto imposible invalida la actividad`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 30, speedMps = 1.4) { filter.onFix(it) }

        // 5 km en 90 s = 200 km/h
        gps.advance(90_000)
        gps.moveNorth(5_000.0)

        val outcome = filter.onFix(gps.fix())
        assertTrue(
            "se esperaba finalización, llegó $outcome",
            outcome is FixOutcome.SessionStopped
        )
        assertEquals(StopReason.SUSPICIOUS_GAP, (outcome as FixOutcome.SessionStopped).reason)
        assertEquals(Status.FINISHED, filter.status)
    }

    @Test
    fun `con la particion desactivada el hueco se une como antes`() {
        val gps = FakeGps()
        val filter = TrackingFilter(FilterConfig()) // valor por defecto: false
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 30, speedMps = 1.4) { filter.onFix(it) }

        gps.advance(90_000)
        gps.moveNorth(100.0)
        filter.onFix(gps.fix())

        val m = filter.metrics(gps.now)
        assertEquals("sin partición debe haber un solo tramo", 1, m.segmentCount)
    }

    @Test
    fun `un hueco corto no parte la ruta`() {
        val gps = FakeGps()
        val filter = TrackingFilter(cfg)
        filter.start(gps.now)
        filter.onFix(gps.fix())
        gps.advance(6_000)
        gps.walk(steps = 30, speedMps = 1.4) { filter.onFix(it) }

        gps.advance(20_000) // por debajo de 60 s
        gps.moveNorth(28.0)
        filter.onFix(gps.fix())

        assertEquals(1, filter.metrics(gps.now).segmentCount)
    }
}
