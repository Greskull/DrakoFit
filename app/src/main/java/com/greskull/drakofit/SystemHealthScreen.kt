package com.greskull.drakofit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * FASE 5: pantalla de "estado del tracking".
 *
 * Explica, en lenguaje normal, por qué a veces se pierde una ruta y qué hay
 * que tocar en el móvil para que no pase. Cada fila lleva el atajo al ajuste
 * correspondiente; si el fabricante no expone ese ajuste, se abre la ficha de
 * la app.
 *
 * Se abre desde el menú (☰ -> Permisos y batería) y desde el aviso de primer
 * arranque.
 */
@Composable
fun SystemHealthScreen(
    onClose: () -> Unit,
) {

    val context = LocalContext.current

    // Se recalculan cada vez que se abre la pantalla, para que el usuario vea
    // el resultado después de volver de los ajustes del sistema.
    val checks = remember { SystemHealth.checks(context) }

    val gold = Color(0xFFD4AF37)
    val border = Color(0xFF6B4F24)
    val cream = Color(0xFFE8E0D0)
    val muted = Color(0xFF9A9484)
    val cardBg = Color(0xFF241D16)
    val panelBg = Color(0xFF181310)

    val okColor = Color(0xFF7ED957)
    val warnColor = Color(0xFFFFC542)
    val problemColor = Color(0xFFE24B4A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(150f)
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .background(panelBg, RoundedCornerShape(18.dp))
                .border(1.dp, border, RoundedCornerShape(18.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "ESTADO DEL TRACKING",
                color = gold,
                fontFamily = cinzel,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Detectado: ${SystemHealth.oemName()}",
                color = muted,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(border)
            )

            Spacer(Modifier.height(14.dp))

            checks.forEach { check ->

                val color = when (check.level) {
                    SystemHealth.Level.OK -> okColor
                    SystemHealth.Level.WARNING -> warnColor
                    SystemHealth.Level.PROBLEM -> problemColor
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(cardBg, RoundedCornerShape(12.dp))
                        .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(color, CircleShape)
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = check.title,
                            color = cream,
                            fontFamily = cinzel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = check.detail,
                        color = muted,
                        fontFamily = FontFamily.Serif,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (check.actionLabel != null && check.action != null) {

                        Spacer(Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                                .clickable { check.action.invoke(context) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .align(Alignment.End)
                        ) {
                            Text(
                                text = check.actionLabel,
                                color = color,
                                fontFamily = cinzel,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x22FFC542), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.Start
            ) {

                Text(
                    text = "Cómo llegar a mano en ${SystemHealth.oemName()}",
                    color = warnColor,
                    fontFamily = cinzel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = SystemHealth.manualSteps(),
                    color = muted,
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .border(1.5.dp, border, RoundedCornerShape(20.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "CERRAR",
                    color = gold,
                    fontFamily = cinzel,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
