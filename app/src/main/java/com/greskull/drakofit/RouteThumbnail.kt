package com.greskull.drakofit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greskull.drakofit.tracking.domain.RoutePoint
import kotlin.math.max
import kotlin.math.min

/**
 * FASE 4: miniatura del recorrido, dibujada con Canvas.
 *
 * Sin red, sin MapLibre, sin ficheros: se normaliza la ruta a su caja
 * envolvente y se traza. Es la primera versión de la hoja de ruta; la
 * alternativa (MapSnapshotter + WebP) añade dependencia de red y de estilo,
 * así que se puede poner después sin cambiar nada de aquí.
 *
 * Si la ruta no tiene puntos (rutas antiguas, guardadas solo con métricas) se
 * muestra un marcador de posición con el aviso.
 */
@Composable
fun RouteThumbnail(
    points: List<RoutePoint>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 110.dp,
    lineColor: Color = Color(0xFFFF6A00),
    glowColor: Color = Color(0xFFFF2A00),
    backgroundColor: Color = Color(0x1AFFFFFF),
    borderColor: Color = Color(0xFF6B4F24),
) {

    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape)
    ) {

        if (points.size < 2) {

            // Ruta antigua: no hay recorrido guardado
            Text(
                text = "Sin recorrido guardado",
                color = Color(0xFF9A9484),
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            )

        } else {

            Canvas(modifier = Modifier.fillMaxWidth().height(height)) {

                val pad = 14f

                val minLat = points.minOf { it.lat }
                val maxLat = points.maxOf { it.lat }
                val minLon = points.minOf { it.lon }
                val maxLon = points.maxOf { it.lon }

                val spanLat = max(maxLat - minLat, 1e-9)
                val spanLon = max(maxLon - minLon, 1e-9)

                // Se escala con el mismo factor en los dos ejes para no deformar
                // el recorrido; luego se centra en la caja disponible.
                val usableW = size.width - pad * 2
                val usableH = size.height - pad * 2
                val scale = min(usableW / spanLon.toFloat(), usableH / spanLat.toFloat())

                val drawW = spanLon.toFloat() * scale
                val drawH = spanLat.toFloat() * scale
                val offsetX = pad + (usableW - drawW) / 2f
                val offsetY = pad + (usableH - drawH) / 2f

                fun toOffset(p: RoutePoint) = Offset(
                    x = offsetX + ((p.lon - minLon).toFloat() * scale),
                    // La latitud crece hacia el norte y la pantalla hacia abajo
                    y = offsetY + ((maxLat - p.lat).toFloat() * scale),
                )

                val path = Path()
                val primero = toOffset(points.first())
                path.moveTo(primero.x, primero.y)
                for (i in 1 until points.size) {
                    val o = toOffset(points[i])
                    path.lineTo(o.x, o.y)
                }

                // Halo (mismo estilo "scorched" del mapa en vivo)
                drawPath(
                    path = path,
                    color = glowColor.copy(alpha = 0.35f),
                    style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Línea principal
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Salida y llegada
                drawCircle(color = Color(0xFF7ED957), radius = 5f, center = primero)
                drawCircle(color = lineColor, radius = 5f, center = toOffset(points.last()))
            }
        }
    }
}
