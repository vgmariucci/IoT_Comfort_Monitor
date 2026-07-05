package com.example.comfortplaces.ui.floorplan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.comfortplaces.ui.language.LocalAppStrings
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

// ── Colour helpers ───────────────────────────────────────────────────────────
private fun leqColor(v: Float) = when {
    v < 55f -> Color(0xFF4CAF50)
    v < 65f -> Color(0xFF8BC34A)
    v < 75f -> Color(0xFFFFC107)
    v < 85f -> Color(0xFFFF9800)
    else    -> Color(0xFFF44336)
}
private fun tempColor(v: Float) = when {
    v < 18f -> Color(0xFF2196F3)
    v < 22f -> Color(0xFF4CAF50)
    v < 28f -> Color(0xFFFFC107)
    v < 32f -> Color(0xFFFF9800)
    else    -> Color(0xFFF44336)
}
private fun humColor(v: Float) = when {
    v < 30f -> Color(0xFFF44336)
    v < 40f -> Color(0xFFFF9800)
    v < 60f -> Color(0xFF4CAF50)
    v < 80f -> Color(0xFF2196F3)
    else    -> Color(0xFF1565C0)
}
private fun rssiColor(v: Int) = when {
    v > -55 -> Color(0xFF4CAF50)
    v > -67 -> Color(0xFF8BC34A)
    v > -80 -> Color(0xFFFFC107)
    else    -> Color(0xFFF44336)
}

// ── Gauge card ───────────────────────────────────────────────────────────────
//
//  Layout (horizontal, fills the cell):
//
//  ┌──────────────────────────────────────────────┐
//  │  [icon]        ╭───────────╮                 │
//  │  [label]       │  value    │                 │
//  │  (left col)    │  unit     │  (arc centred)  │
//  │                ╰───────────╯                 │
//  └──────────────────────────────────────────────┘
//
//  The arc uses BoxWithConstraints so it fills the available height/width
//  of the right portion of the card, making it as large as possible.
//
@Composable
fun SensorGaugeCard(
    label: String,
    value: Float,
    unit: String,
    minVal: Float,
    maxVal: Float,
    color: Color,
    icon: String,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue    = value,
        animationSpec  = tween(800, easing = EaseOutCubic),
        label          = "gaugeValue"
    )
    val animatedColor by animateColorAsState(
        targetValue   = color,
        animationSpec = tween(600),
        label         = "gaugeColor"
    )
    val pulse = rememberInfiniteTransition(label = "pulse_$label")
    val glowAlpha by pulse.animateFloat(
        0.05f, 0.18f,
        infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        color    = Color(0xFF16213E),
        tonalElevation = 2.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(animatedColor.copy(alpha = glowAlpha), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Left: big icon + label ────────────────────────────────
                Column(
                    Modifier
                        .width(56.dp)
                        .fillMaxHeight(),
                    verticalArrangement   = Arrangement.Center,
                    horizontalAlignment   = Alignment.CenterHorizontally
                ) {
                    Text(
                        text     = icon,
                        fontSize = 32.sp          // ↑ large icon
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text      = label,
                        color     = Color(0xFFB0B0C0),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize  = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines  = 2
                    )
                }

                // ── Right: arc dial, centered and as large as possible ────
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Arc diameter = 85 % of the smaller dimension of this box,
                    // so it is always as large as the card allows.
                    val arcSize = min(maxWidth, maxHeight) * 0.85f

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier.size(arcSize)
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val strokeW = size.width * 0.09f   // 9 % of diameter
                            val stroke  = Stroke(width = strokeW, cap = StrokeCap.Round)
                            val pad     = strokeW / 2f + 2f
                            val rect    = Size(size.width - 2 * pad, size.height - 2 * pad)
                            val tl      = Offset(pad, pad)

                            // Track arc
                            drawArc(Color(0xFF2A2A4A), 135f, 270f, false, tl, rect, style = stroke)
                            // Value arc
                            val frac = ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                            drawArc(
                                animatedColor, 135f, 270f * frac, false, tl, rect,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                        }

                        // Value + unit centred inside the arc
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text       = if (value != 0f) "%.1f".format(animatedValue) else "--",
                                color      = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 18.sp
                            )
                            Text(
                                text       = unit,
                                color      = Color(0xFFB0B0C0),
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Per-measurement wrappers ─────────────────────────────────────────────────
@Composable
fun SoundGauge(leq: Float, modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    SensorGaugeCard(
        label = strings.soundLeq, value = leq, unit = "dB",
        minVal = 30f, maxVal = 100f, color = leqColor(leq),
        icon = "\uD83D\uDD0A", modifier = modifier
    )
}

@Composable
fun TempGauge(temp: Float, modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    SensorGaugeCard(
        label = strings.temperature, value = temp, unit = "°C",
        minVal = 10f, maxVal = 45f, color = tempColor(temp),
        icon = "\uD83C\uDF21", modifier = modifier
    )
}

@Composable
fun HumGauge(hum: Float, modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    SensorGaugeCard(
        label = strings.humidity, value = hum, unit = "%",
        minVal = 0f, maxVal = 100f, color = humColor(hum),
        icon = "\uD83D\uDCA7", modifier = modifier
    )
}

@Composable
fun RssiGauge(rssi: Int, modifier: Modifier = Modifier) {
    val strings = LocalAppStrings.current
    SensorGaugeCard(
        label = strings.wifiRssi, value = rssi.toFloat(), unit = "dBm",
        minVal = -100f, maxVal = -20f, color = rssiColor(rssi),
        icon = "\uD83D\uDCF6", modifier = modifier
    )
}

// ── Lmax / Lmin banner ────────────────────────────────────────────────────────
@Composable
fun SoundPeakCard(lmax: Float, lmin: Float, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = Color(0xFF16213E)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Lmax", color = Color(0xFFF44336),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    if (lmax > 0f) "%.1f".format(lmax) else "--",
                    color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, fontFamily = FontFamily.Monospace
                )
                Text("dB", color = Color(0xFFB0B0C0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Box(Modifier.width(1.dp).height(44.dp).background(Color(0xFF2A2A4A)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Lmin", color = Color(0xFF4CAF50),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    if (lmin > 0f) "%.1f".format(lmin) else "--",
                    color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, fontFamily = FontFamily.Monospace
                )
                Text("dB", color = Color(0xFFB0B0C0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
