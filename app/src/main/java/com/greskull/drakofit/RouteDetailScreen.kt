package com.greskull.drakofit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * FASE 4: detalle de una ruta guardada.
 *
 * Mapa interactivo (MapLibre) encuadrado en el recorrido, con la línea naranja
 * y las mismas capas de "scorched" que el mapa en vivo, para que se reconozca.
 *
 * Si la ruta es antigua y no tiene puntos, se muestran solo las métricas y un
 * aviso: no hay nada que dibujar.
 */
@Composable
fun RouteDetailScreen(
    route: RouteHistoryEntry,
    isDarkMap: Boolean,
    onClose: () -> Unit,
) {

    val gold = Color(0xFFD4AF37)
    val border = Color(0xFF6B4F24)
    val cream = Color(0xFFE8E0D0)
    val muted = Color(0xFF9A9484)
    val panelBg = Color(0xFF181310)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .background(Color(0xFF0B1219))
    ) {

        // ---------------------------------------------------------- MAPA
        if (route.hasTrack) {

            val mapViewRef = remember { arrayOfNulls<MapView>(1) }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->

                    val mapView = MapView(ctx)
                    mapViewRef[0] = mapView

                    mapView.onCreate(null)
                    mapView.onStart()
                    mapView.onResume()

                    mapView.getMapAsync { map ->

                        val styleUrl = if (isDarkMap) {
                            "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
                        } else {
                            "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
                        }

                        map.setStyle(styleUrl) { style ->

                            val points = route.points.map {
                                Point.fromLngLat(it.lon, it.lat)
                            }

                            val source = GeoJsonSource("history-route-source")
                            source.setGeoJson(
                                Feature.fromGeometry(LineString.fromLngLats(points))
                            )
                            style.addSource(source)

                            // Halo
                            style.addLayer(
                                LineLayer("history-route-glow", "history-route-source")
                                    .withProperties(
                                        PropertyFactory.lineColor("#ff2a00"),
                                        PropertyFactory.lineWidth(12f),
                                        PropertyFactory.lineOpacity(0.30f),
                                        PropertyFactory.lineBlur(1.2f)
                                    )
                            )

                            // Línea naranja
                            style.addLayer(
                                LineLayer("history-route-line", "history-route-source")
                                    .withProperties(
                                        PropertyFactory.lineColor("#ff6a00"),
                                        PropertyFactory.lineWidth(4.5f),
                                        PropertyFactory.lineOpacity(1f)
                                    )
                            )

                            // Encuadrar el recorrido
                            try {
                                val builder = LatLngBounds.Builder()
                                points.forEach { builder.include(LatLng(it.latitude(), it.longitude())) }

                                // Un solo punto => el Builder falla; con >= 2 hay ruta.
                                map.animateCamera(
                                    CameraUpdateFactory.newLatLngBounds(
                                        builder.build(),
                                        90, // padding
                                    )
                                )
                            } catch (_: Exception) {
                                // Si la caja no se puede calcular, dejamos la
                                // cámara por defecto en vez de romper.
                            }
                        }
                    }

                    mapView
                }
            )

            DisposableEffect(Unit) {
                onDispose {
                    mapViewRef[0]?.onPause()
                    mapViewRef[0]?.onStop()
                    mapViewRef[0]?.onDestroy()
                    mapViewRef[0] = null
                }
            }
        }

        // Placeholder cuando la ruta es antigua y no hay recorrido guardado.
        // Antes quedaba un fondo oscuro vacío, que parecía un mapa que no
        // había cargado.
        if (!route.hasTrack) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🗺️",
                    fontSize = 54.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Esta ruta se guardó antes de que DrakoFit registrara el recorrido.",
                    color = Color(0xFF9A9484),
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Las rutas nuevas sí mostrarán el mapa con la línea naranja.",
                    color = Color(0xFF6B4F24),
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // -------------------------------------------------- PANEL INFERIOR
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(panelBg.copy(alpha = 0.94f), RoundedCornerShape(16.dp))
                    .border(1.dp, border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = formatDate(route.timestamp),
                    color = gold,
                    fontFamily = cinzel,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(10.dp))

                if (!route.hasTrack) {
                    Text(
                        text = "Ruta guardada antes de la Fase 4: solo hay métricas, " +
                            "sin recorrido que dibujar.",
                        color = muted,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailStat("Pasos", "${route.steps}", cream)
                    DetailStat("Distancia", "%.2f km".format(route.distanceKm), cream)
                    DetailStat("Kcal", "${route.calories}", cream)
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailStat("Ritmo", route.pace, cream)
                    DetailStat("XP", "${route.xpEarned}", cream)
                    DetailStat("Duración", formatDuration(route.durationSeconds), cream)
                }

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .border(BorderStroke(1.dp, border), RoundedCornerShape(20.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "VOLVER",
                        color = gold,
                        fontFamily = cinzel,
                        fontSize = 13.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(86.dp)
    ) {
        Text(
            text = value,
            color = color,
            fontFamily = cinzel,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = label,
            color = Color(0xFF9A9484),
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 11.sp
        )
    }
}
