package com.greskull.drakofit.tracking.persistence

import com.greskull.drakofit.tracking.domain.Status
import com.greskull.drakofit.tracking.domain.StopReason
import com.greskull.drakofit.tracking.domain.TrackPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests de la persistencia (Fase 3). Sin Android: `SessionStore` solo usa
 * `java.io`, así que corre en la JVM con un directorio temporal.
 */
class SessionStoreTest {

    private lateinit var dir: File
    private lateinit var store: SessionStore

    private val start = 1_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("sessionstore").toFile()
        store = SessionStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun summary(
        distanceM: Double = 0.0,
        points: Int = 0,
        segments: Int = 0,
    ) = SessionSummary(
        startedAtElapsedMs = start,
        savedAtElapsedMs = start + 5_000,
        elapsedMs = 5_000,
        activeMs = 4_000,
        pausedMs = 0,
        distanceM = distanceM,
        pointCount = points,
        segmentCount = segments,
        status = Status.TRACKING,
    )

    private fun point(i: Int, segment: Int = 0) = TrackPoint(
        lat = 40.4168 + i * 0.0001,
        lon = -3.7038,
        elapsedMs = start + i * 1000L,
        accuracyM = 5f,
        distanceM = i * 100.0,
        segmentIndex = segment,
    )

    // ------------------------------------------------------------- ciclo

    @Test
    fun `una sesion nueva deja el resumen en disco`() {
        store.begin(summary())
        assertTrue(store.hasPending())
        assertEquals(0, store.pendingPointCountOnDisk())
    }

    @Test
    fun `clear borra todo`() {
        store.begin(summary())
        store.appendPoint(point(1))
        assertTrue(store.hasPending())

        store.clear()

        assertFalse(store.hasPending())
        assertNull(store.readSummary())
        assertTrue(store.readPoints().isEmpty())
    }

    @Test
    fun `sin resumen no hay sesion pendiente`() {
        assertFalse(store.hasPending())
    }

    // ------------------------------------------------------------ puntos

    @Test
    fun `los puntos van y vuelven con precision suficiente`() {
        store.begin(summary())
        val originales = (1..10).map { point(it) }
        originales.forEach { store.appendPoint(it) }

        val leidos = store.readPoints()

        assertEquals(10, leidos.size)
        leidos.zip(originales).forEach { (leido, orig) ->
            assertEquals(orig.lat, leido.lat, 0.0000001) // ~1 cm
            assertEquals(orig.lon, leido.lon, 0.0000001)
            assertEquals(orig.elapsedMs, leido.elapsedMs)
            assertEquals(orig.distanceM, leido.distanceM, 0.01)
            assertEquals(orig.segmentIndex, leido.segmentIndex)
        }
    }

    @Test
    fun `escribir 3600 puntos es rapido y no pierde ninguno`() {
        store.begin(summary())
        repeat(3600) { store.appendPoint(point(it)) }
        assertEquals(3600, store.readPoints().size)
    }

    @Test
    fun `se conservan los tramos de los huecos de GPS`() {
        store.begin(summary())
        store.appendPoint(point(1, segment = 0))
        store.appendPoint(point(2, segment = 0))
        store.appendPoint(point(3, segment = 1))

        val leidos = store.readPoints()
        assertEquals(listOf(0, 0, 1), leidos.map { it.segmentIndex })
    }

    @Test
    fun `un fichero truncado devuelve solo los registros completos`() {
        store.begin(summary())
        repeat(5) { store.appendPoint(point(it)) }

        // Simula muerte del proceso a mitad de escritura: cortar 7 bytes
        val f = File(dir, SessionStore.POINTS_FILE)
        val bytes = f.readBytes()
        f.writeBytes(bytes.copyOf(bytes.size - 7))

        val leidos = store.readPoints()
        assertEquals("debe devolver 4 completos y descartar el parcial", 4, leidos.size)
    }

    @Test
    fun `el fichero vacio no rompe la lectura`() {
        store.begin(summary())
        assertTrue(store.readPoints().isEmpty())
    }

    // ----------------------------------------------------------- resumen

    @Test
    fun `el resumen sobrevive a la ida y vuelta`() {
        val s = summary(distanceM = 1234.5, points = 300, segments = 2)
        store.writeSummary(s)

        val leido = store.readSummary()
        assertNotNull(leido)
        assertEquals(s, leido)
    }

    @Test
    fun `un resumen corrupto no rompe y devuelve null`() {
        File(dir, SessionStore.SUMMARY_FILE).writeText("basura sin formato")
        assertNull(store.readSummary())
    }

    // ------------------------------------------- reconstruir la sesión (F3)

    @Test
    fun `la sesion recuperada arranca en pausa y conserva sus numeros`() {
        val s = summary(distanceM = 1500.0, points = 200, segments = 1)
        val snap = SessionSummary.toFilterSnapshot(s, lastPoint = point(199))

        assertEquals(Status.PAUSED, snap.status)
        assertEquals(StopReason.NONE, snap.stopReason)
        assertEquals(1500.0, snap.distanceM, 0.001)
        assertEquals(200, snap.pointCount)
        assertEquals(0, snap.segmentIndex)
        assertNotNull("debe conservar el último punto como origen", snap.lastAccepted)
        assertFalse("nunca debe reanudarse sola", snap.justResumed)
    }

    @Test
    fun `ciclo completo de una sesion interrumpida`() {
        // 1) sesión en marcha
        store.begin(summary())
        repeat(3) { store.appendPoint(point(it)) }
        store.writeSummary(summary(distanceM = 300.0, points = 3, segments = 1))

        // 2) el proceso muere; un SessionStore nuevo lee lo mismo
        val reabierto = SessionStore(dir)
        assertTrue(reabierto.hasPending())

        val resumen = reabierto.readSummary()
        val puntos = reabierto.readPoints()
        assertNotNull(resumen)
        assertEquals(3, puntos.size)
        // los tiempos deben seguir siendo válidos tras reabrir el fichero
        assertEquals(start + 2000L, puntos[2].elapsedMs)
        assertEquals(200.0, puntos[2].distanceM, 0.01)

        val snap = SessionSummary.toFilterSnapshot(resumen!!, puntos.lastOrNull())
        assertEquals(300.0, snap.distanceM, 0.001)
        assertEquals(Status.PAUSED, snap.status)

        // 3) el usuario guarda y cierra
        reabierto.clear()
        assertFalse(reabierto.hasPending())
    }
}
