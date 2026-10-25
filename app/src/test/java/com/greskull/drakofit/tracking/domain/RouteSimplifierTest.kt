package com.greskull.drakofit.tracking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la simplificación de rutas (Fase 4). Kotlin puro, JVM.
 */
class RouteSimplifierTest {

    private val latPerMeter = 1.0 / 111_320.0

    /** Línea recta hacia el norte con [n] puntos separados [stepM]. */
    private fun lineaRecta(n: Int, stepM: Double = 5.0): List<RoutePoint> =
        (0 until n).map { RoutePoint(40.4168 + it * stepM * latPerMeter, -3.7038) }

    @Test
    fun `una linea recta queda reducida a sus dos extremos`() {
        val ruta = lineaRecta(200)
        val simple = RouteSimplifier.simplify(ruta, epsilonM = 5.0)

        assertEquals("una recta no necesita puntos intermedios", 2, simple.size)
        assertEquals(ruta.first(), simple.first())
        assertEquals(ruta.last(), simple.last())
    }

    @Test
    fun `se conservan los giros pronunciados`() {
        // cuadrado de 100 m de lado: 4 esquinas
        val ruta = mutableListOf<RoutePoint>()
        var lat = 40.4168
        var lon = -3.7038
        val d = 100.0 * latPerMeter

        for (i in 0..100) ruta.add(RoutePoint(lat + i * d / 100, lon))
        for (i in 0..100) ruta.add(RoutePoint(lat + d, lon + i * d / 100))
        for (i in 0..100) ruta.add(RoutePoint(lat + d - i * d / 100, lon + d))
        for (i in 0..100) ruta.add(RoutePoint(lat, lon + d - i * d / 100))

        val simple = RouteSimplifier.simplify(ruta, epsilonM = 5.0)

        assertTrue("debe conservar las esquinas (quedaron ${simple.size})", simple.size in 4..8)
        assertEquals(ruta.first(), simple.first())
        assertEquals(ruta.last(), simple.last())
    }

    @Test
    fun `la longitud de la ruta simplificada apenas cambia`() {
        val ruta = lineaRecta(300, stepM = 4.0)
        val original = RouteSimplifier.pathLengthM(ruta)
        val simple = RouteSimplifier.pathLengthM(RouteSimplifier.simplify(ruta))

        assertEquals("no debe perder longitud", original, simple, original * 0.01)
    }

    @Test
    fun `epsilon grande simplifica mas que epsilon pequeno`() {
        // ruta con zigzag de 3 m: con epsilon 1 m se conserva, con 10 m no
        val ruta = (0 until 400).map { i ->
            RoutePoint(
                40.4168 + i * 4.0 * latPerMeter,
                -3.7038 + if (i % 2 == 0) 3.0 / 85_000.0 else -3.0 / 85_000.0,
            )
        }

        val fino = RouteSimplifier.simplify(ruta, epsilonM = 1.0)
        val grueso = RouteSimplifier.simplify(ruta, epsilonM = 10.0)

        assertTrue("epsilon 1 m debe conservar el zigzag", fino.size > grueso.size)
        assertEquals("epsilon 10 m debe dejarlo casi recto", 2, grueso.size)
    }

    @Test
    fun `una sesion real de una hora baja de cientos a decenas de puntos`() {
        // ~1000 puntos andando en una curva suave
        val ruta = (0 until 1000).map { i ->
            val t = i / 1000.0
            RoutePoint(
                lat = 40.4168 + t * 0.02,
                lon = -3.7038 + kotlin.math.sin(t * 6.28) * 0.002,
            )
        }

        val simple = RouteSimplifier.simplify(ruta, epsilonM = 5.0)

        assertTrue("1000 puntos deben bajar mucho (quedaron ${simple.size})", simple.size < 100)
        assertTrue("pero no a una recta", simple.size > 10)
    }

    @Test
    fun `rutas de 0, 1 o 2 puntos se devuelven intactas`() {
        assertEquals(emptyList<RoutePoint>(), RouteSimplifier.simplify(emptyList()))
        val uno = listOf(RoutePoint(40.0, -3.0))
        assertEquals(uno, RouteSimplifier.simplify(uno))
        val dos = listOf(RoutePoint(40.0, -3.0), RoutePoint(40.001, -3.0))
        assertEquals(dos, RouteSimplifier.simplify(dos))
    }

    @Test
    fun `epsilon cero no simplifica nada`() {
        val ruta = lineaRecta(50)
        assertEquals(ruta.size, RouteSimplifier.simplify(ruta, epsilonM = 0.0).size)
    }

    @Test
    fun `no desborda la pila con una ruta muy larga`() {
        val ruta = (0 until 20_000).map { i ->
            RoutePoint(40.4168 + i * 1e-6, -3.7038 + kotlin.math.sin(i / 50.0) * 1e-5)
        }
        val simple = RouteSimplifier.simplify(ruta, epsilonM = 3.0)
        // La sinusoide es de ~1 m de amplitud, así que con epsilon 3 m se
        // convierte en recta: lo que se comprueba aquí es que NO desborda la
        // pila con 20.000 puntos y que reduce.
        assertTrue("debe reducir (quedaron ${simple.size})", simple.size in 2 until ruta.size)
        assertEquals(ruta.first(), simple.first())
        assertEquals(ruta.last(), simple.last())
    }
}

/**
 * Tests de la serialización de rutas (Fase 4).
 */
class RouteCodecTest {

    private val ruta = listOf(
        RoutePoint(40.41680, -3.70380),
        RoutePoint(40.41751, -3.70312),
        RoutePoint(40.41822, -3.70250),
    )

    @Test
    fun `ida y vuelta conserva los puntos con precision de un metro`() {
        val decodificada = RouteCodec.decode(RouteCodec.encode(ruta))

        assertEquals(ruta.size, decodificada.size)
        ruta.zip(decodificada).forEach { (orig, dec) ->
            assertEquals(orig.lat, dec.lat, 0.00001) // ~1 m
            assertEquals(orig.lon, dec.lon, 0.00001)
        }
    }

    @Test
    fun `el formato es el esperado y no usa comas decimales`() {
        val texto = RouteCodec.encode(ruta)

        assertTrue("no debe haber comas decimales del locale", texto.startsWith("40.4168,"))
        assertTrue(texto.contains(";"))
        assertTrue("2 puntos => 1 separador", !texto.endsWith(";"))
    }

    @Test
    fun `una ruta vacia se codifica vacia y se decodifica vacia`() {
        assertEquals("", RouteCodec.encode(emptyList()))
        assertEquals(emptyList<RoutePoint>(), RouteCodec.decode(""))
        assertEquals(emptyList<RoutePoint>(), RouteCodec.decode(null))
        assertEquals(emptyList<RoutePoint>(), RouteCodec.decode("   "))
    }

    @Test
    fun `un texto corrupto no lanza excepcion y descarta lo invalido`() {
        val raro = "40.1,-3.7;basura;40.2;40.3,-3.9;,,"
        val decodificada = RouteCodec.decode(raro)

        assertEquals("solo los pares válidos", 2, decodificada.size)
        assertEquals(40.1, decodificada[0].lat, 0.00001)
        assertEquals(40.3, decodificada[1].lat, 0.00001)
    }

    @Test
    fun `una ruta de 80 puntos ocupa poco`() {
        val muchos = (0 until 80).map { RoutePoint(40.0 + it * 0.0001, -3.0 - it * 0.0002) }
        val texto = RouteCodec.encode(muchos)

        assertTrue("80 puntos deben ocupar menos de 3 KB (${texto.length} B)", texto.length < 3_000)
    }
}
