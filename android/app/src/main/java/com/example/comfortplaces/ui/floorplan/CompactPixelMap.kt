package com.example.comfortplaces.ui.floorplan

import android.graphics.Paint as NativePaint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.comfortplaces.data.model.SensorReading
import com.example.comfortplaces.ui.language.AppStrings
import com.example.comfortplaces.ui.language.LocalAppStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Virtual grid 16x14 is kept only for tap-hit-testing and character positioning.

data class LocationDef(val key: String, val shortName: String, val col: Float, val row: Float)

val LOCATIONS = listOf(
    LocationDef("Close to Kitchen",   "Kitchen",   2f,  2f),
    LocationDef("Lunch Area",         "Dining",    7f,  6f),
    LocationDef("Entrance",           "Entrance",  8f,  12f),
    LocationDef("Close to Restrooms", "Restrooms", 12f, 2f),
    LocationDef("External Area",      "Outdoor",   8f,  15f),
)

// ── Helpers ────────────────────────────────────────────────────────

private fun leqToOverlayColor(leq: Float): Color = when {
    leq <= 0f -> Color.Transparent
    leq < 60f -> Color(0xFF4CAF50).copy(alpha = 0.12f)
    leq < 75f -> Color(0xFFFFC107).copy(alpha = 0.13f)
    else      -> Color(0xFFF44336).copy(alpha = 0.15f)
}

private fun leqToBorderColor(leq: Float): Color = when {
    leq <= 0f -> Color(0xFF888888)
    leq < 60f -> Color(0xFF4CAF50)
    leq < 75f -> Color(0xFFFFC107)
    else      -> Color(0xFFF44336)
}

// ── Legend item ────────────────────────────────────────────────────

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color(0xFFAAB4C8), fontSize = 9.sp,
            fontFamily = FontFamily.Default)
    }
}

// ── Composable ─────────────────────────────────────────────────────

@Composable
fun CompactPixelMap(
    readingsByLocation: Map<String, List<SensorReading>>,
    selectedLocation: String?,
    onLocationTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    // Normal (slow) pulse — ambient animations
    val inf   = rememberInfiniteTransition(label = "pulse")
    val pulse by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "p"
    )

    // Alert (fast) pulse — critical readings > 80 dB
    val alertInf   = rememberInfiniteTransition(label = "alert")
    val alertPulse by alertInf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(220, easing = LinearEasing), RepeatMode.Reverse),
        label = "ap"
    )

    var tileWidth  by remember { mutableFloatStateOf(0f) }
    var tileHeight by remember { mutableFloatStateOf(0f) }

    val initial   = LOCATIONS.first()
    val charX     = remember { Animatable(initial.col) }
    val charY     = remember { Animatable(initial.row) }
    var walkFrame by remember { mutableIntStateOf(0) }

    // Track the location the character is currently AT (for thought bubble + plumb-bob color)
    var currentKey by remember { mutableStateOf<String?>(null) }
    val bubbleAlpha = remember { Animatable(0f) }

    LaunchedEffect(selectedLocation) {
        val target = LOCATIONS.find { it.key == selectedLocation } ?: return@LaunchedEffect
        val mX   = launch { charX.animateTo(target.col, tween(900, easing = LinearEasing)) }
        val mY   = launch { charY.animateTo(target.row, tween(900, easing = LinearEasing)) }
        val walk = launch { while (true) { delay(120); walkFrame = (walkFrame + 1) % 4 } }
        mX.join(); mY.join()
        walk.cancel(); walkFrame = 0
        currentKey = selectedLocation
        // Show thought bubble, then fade out
        launch {
            bubbleAlpha.snapTo(0f)
            bubbleAlpha.animateTo(1f, tween(300))
            delay(2800)
            bubbleAlpha.animateTo(0f, tween(600))
        }
    }

    // Pre-compute leq values once per recomposition
    val leqByLocation = LOCATIONS.associate { loc ->
        loc.key to (readingsByLocation[loc.key]?.lastOrNull()?.leqSpl ?: 0f)
    }

    // Is the current location critical?
    val currentLeq    = leqByLocation[currentKey]    ?: 0f
    val selectedLeq   = leqByLocation[selectedLocation] ?: 0f
    val isCritical    = selectedLeq >= 80f
    val bobColor      = if (isCritical) Color(0xFFF44336) else Color(0xFF22C84A)
    val bobPulse      = if (isCritical) alertPulse else pulse

    Column(
        modifier
            .background(Color(0xFF1E2330), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Text(
            "🍽  ${strings.floorPlanTitle}   (${strings.tapAZone})",
            color      = Color(0xFFF0F0F8),
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize   = 12.sp,
            modifier   = Modifier.padding(bottom = 6.dp)
        )

        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 16f)
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        if (tileWidth <= 0f) return@detectTapGestures
                        val c = off.x / tileWidth
                        val r = off.y / tileHeight
                        LOCATIONS
                            .minByOrNull { abs(it.col - c) + abs(it.row - r) }
                            ?.takeIf   { abs(it.col - c) + abs(it.row - r) < 4f }
                            ?.let { onLocationTapped(it.key) }
                    }
                }
        ) {
            val tw = size.width  / 16f
            val th = size.height / 16f
            tileWidth  = tw
            tileHeight = th

            // ── 1. Restaurant floor plan ───────────────────────────
            drawRestaurantFloorPlan(
                tw, th, pulse, alertPulse, strings,
                leqByLocation, selectedLocation
            )

            // ── 2. Sensor dots + reading badges ────────────────────
            LOCATIONS.forEach { loc ->
                val leq   = leqByLocation[loc.key] ?: 0f
                val isSel = loc.key == selectedLocation
                val dotC  = leqToBorderColor(leq)
                val ap    = if (leq >= 80f) alertPulse else pulse
                val cx    = (loc.col + 0.5f) * tw
                val cy    = (loc.row + 0.5f) * th
                val br    = tw * 0.40f

                // Outer glow (strobes fast when critical)
                drawCircle(dotC.copy(alpha = ap * if (isSel) 0.52f else 0.20f),
                    br * if (isSel) 2.4f else 1.6f, Offset(cx, cy))
                // Selection ring
                if (isSel) drawCircle(Color.White.copy(alpha = 0.75f),
                    br * 1.15f, Offset(cx, cy), style = Stroke(2.5f))
                // Dot body
                drawCircle(Color(0xFF111122), br, Offset(cx, cy))
                drawCircle(dotC.copy(alpha = 0.8f + ap * 0.2f), br * 0.65f, Offset(cx, cy))

                // Reading badge (shown when there is data)
                if (leq > 0f) drawReadingBadge(cx, cy, leq, br, tw)
            }

            // ── 3. Sims character ──────────────────────────────────
            val charPx = (charX.value + 0.5f) * tw
            val charPy = (charY.value + 0.5f) * th
            drawSimsCharacter(charPx, charPy, tw * 0.60f, walkFrame, pulse, bobPulse, bobColor)

            // ── 4. Thought bubble (fades in on arrival) ────────────
            if (bubbleAlpha.value > 0.01f) {
                drawThoughtBubble(charPx, charPy, tw * 0.60f,
                    leqByLocation[currentKey] ?: 0f, tw, bubbleAlpha.value)
            }

            // ── 5. "Comfort Inspector" label ───────────────────────
            val label  = strings.comfortInspector
            val lPaint = NativePaint().apply {
                color       = android.graphics.Color.WHITE
                textSize    = tw * 0.62f
                textAlign   = NativePaint.Align.CENTER
                isAntiAlias = true
                typeface    = Typeface.DEFAULT_BOLD
            }
            val bgPaint = NativePaint().apply {
                color = android.graphics.Color.argb(200, 20, 26, 50)
                isAntiAlias = true
            }
            val lY  = charPy - tw * 1.60f
            val tW  = lPaint.measureText(label)
            val pad = tw * 0.18f
            drawIntoCanvas { cv ->
                cv.nativeCanvas.drawRoundRect(
                    RectF(charPx - tW/2f - pad, lY - lPaint.textSize,
                          charPx + tW/2f + pad, lY + pad * 0.4f),
                    tw * 0.25f, tw * 0.25f, bgPaint)
                cv.nativeCanvas.drawText(label, charPx, lY, lPaint)
            }
        }

        // ── 6. Legend strip ────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(Color(0xFF888888), strings.noData)
            LegendDot(Color(0xFF4CAF50), "< 60 dB")
            LegendDot(Color(0xFFFFC107), "60–75 dB")
            LegendDot(Color(0xFFF44336), "> 75 dB")
        }
    }
}

// =================================================================
//  RESTAURANT FLOOR PLAN
// =================================================================

private fun DrawScope.drawRestaurantFloorPlan(
    tw: Float, th: Float,
    pulse: Float, alertPulse: Float,
    str: AppStrings,
    leqByLocation: Map<String, Float>,
    selectedLocation: String?
) {
    val W  = size.width
    val H  = size.height
    val wt = tw * 0.55f

    // Section boundaries  (grid is now 16×16)
    val kX2       = tw * 9.5f
    val backY     = th * 4.0f    // kitchen/restroom zone (4 rows = 25%)
    val dineEndY  = th * 10.0f   // dining ends (6 rows = 37.5%)
    val extStartY = th * 13.0f   // entrance/external boundary (external = 3 rows = 18.75%)

    // Per-zone leq and selection flags
    val kLeq  = leqByLocation["Close to Kitchen"]   ?: 0f
    val dLeq  = leqByLocation["Lunch Area"]          ?: 0f
    val eLeq  = leqByLocation["Entrance"]            ?: 0f
    val rLeq  = leqByLocation["Close to Restrooms"]  ?: 0f
    val xLeq  = leqByLocation["External Area"]       ?: 0f
    val kSel  = selectedLocation == "Close to Kitchen"
    val dSel  = selectedLocation == "Lunch Area"
    val eSel  = selectedLocation == "Entrance"
    val rSel  = selectedLocation == "Close to Restrooms"
    val xSel  = selectedLocation == "External Area"

    // ─── Floor backgrounds ──────────────────────────────────────────
    drawRect(Color(0xFFD5D1CB), Offset(0f, 0f),    Size(kX2, backY))          // kitchen
    drawTileGrid(0f, 0f, kX2, backY, tw * 0.85f,    Color.Black.copy(alpha = 0.055f))
    drawRect(Color(0xFFF0EDE8), Offset(kX2, 0f),   Size(W - kX2, backY))     // restrooms
    drawTileGrid(kX2, 0f, W - kX2, backY, tw * 0.75f, Color.Black.copy(alpha = 0.065f))
    drawRect(Color(0xFFDFBD6A), Offset(0f, backY), Size(W, dineEndY - backY)) // dining
    var gy = backY + th * 0.55f
    while (gy < dineEndY) {
        drawRect(Color(0xFFC9A840).copy(alpha = 0.35f), Offset(0f, gy), Size(W, 2f))
        gy += th * 0.70f
    }
    drawRect(Color(0xFFCDC3B5), Offset(0f, dineEndY), Size(W, extStartY - dineEndY)) // entrance
    drawTileGrid(0f, dineEndY, W, extStartY - dineEndY, tw * 1.1f, Color.Black.copy(alpha = 0.055f))
    drawRect(Color(0xFFC2B89E), Offset(0f, extStartY), Size(W, H - extStartY)) // outdoor patio
    drawTileGrid(0f, extStartY, W, H - extStartY, tw * 0.95f, Color.Black.copy(alpha = 0.06f))

    // ─── IMPROVEMENT 1: Zone comfort overlays ──────────────────────
    val oc = leqToOverlayColor(kLeq)
    if (oc != Color.Transparent) drawRect(oc, Offset(0f, 0f), Size(kX2, backY))
    val or2 = leqToOverlayColor(rLeq)
    if (or2 != Color.Transparent) drawRect(or2, Offset(kX2, 0f), Size(W - kX2, backY))
    val od = leqToOverlayColor(dLeq)
    if (od != Color.Transparent) drawRect(od, Offset(0f, backY), Size(W, dineEndY - backY))
    val oe = leqToOverlayColor(eLeq)
    if (oe != Color.Transparent) drawRect(oe, Offset(0f, dineEndY), Size(W, extStartY - dineEndY))
    val ox = leqToOverlayColor(xLeq)
    if (ox != Color.Transparent) drawRect(ox, Offset(0f, extStartY), Size(W, H - extStartY))

    // ─── Kitchen equipment ──────────────────────────────────────────
    val ki = wt + tw * 0.12f
    drawSteelCounter(ki, ki, kX2 - ki - wt, th * 0.85f)
    drawStove(kX2 * 0.38f, ki + th * 0.42f, tw * 2.6f, th * 0.70f)
    drawShelf(kX2 - ki - wt - tw * 1.8f, ki, tw * 1.8f, th * 0.85f)
    drawSteelCounter(ki, backY * 0.40f, tw * 0.80f, th * 1.90f)
    drawSteelCounter(ki + tw * 1.0f, backY * 0.42f, tw * 3.0f, th * 1.50f)
    drawFridge(kX2 - ki - wt - tw * 1.30f, ki + th * 0.08f, tw * 1.30f, th * 1.90f)
    drawSteelCounter(tw * 4.2f, backY - th * 0.55f, tw * 2.8f, th * 0.50f)
    drawRoomLabel(kX2 / 2f, backY - th * 0.20f,
        str.kitchen.uppercase(), tw, android.graphics.Color.argb(170, 55, 45, 35))

    // ─── Restrooms ──────────────────────────────────────────────────
    val rmMidX = kX2 + (W - kX2) / 2f
    val ri     = wt * 0.6f + tw * 0.08f
    drawRect(Color(0xFF7A6858), Offset(rmMidX - wt / 2f, 0f), Size(wt, backY))
    val mLX = kX2 + ri
    drawToiletStall(mLX,              ki,              tw * 1.10f, th * 1.35f)
    drawToiletStall(mLX,              ki + th * 1.55f, tw * 1.10f, th * 1.35f)
    drawWallSink(mLX + tw * 1.30f,   ki,              tw * 0.85f, th * 0.70f)
    val fLX = rmMidX + wt * 0.5f + ri * 0.6f
    drawToiletStall(fLX,              ki,              tw * 1.05f, th * 1.35f)
    drawToiletStall(fLX + tw * 1.25f, ki,              tw * 1.05f, th * 1.35f)
    drawToiletStall(fLX,              ki + th * 1.55f, tw * 1.05f, th * 1.35f)
    drawWallSink(fLX + tw * 1.25f,   ki + th * 1.55f, tw * 0.85f, th * 0.70f)
    drawIconLabel((kX2 + rmMidX) / 2f, backY * 0.88f, "♂", tw,
        android.graphics.Color.argb(200, 40, 90, 200))
    drawIconLabel((rmMidX + W) / 2f,   backY * 0.88f, "♀", tw,
        android.graphics.Color.argb(200, 200, 40, 110))
    drawRoomLabel((kX2 + W) / 2f, backY - th * 0.20f,
        str.restrooms.uppercase(), tw, android.graphics.Color.argb(170, 55, 45, 35))

    // ─── Dining ─────────────────────────────────────────────────────
    val tableR = tw * 0.70f; val chairR = tw * 0.24f
    val row1Y  = backY + (dineEndY - backY) * 0.31f
    listOf(tw * 2.2f, tw * 5.5f, tw * 9.0f, tw * 12.6f).forEach { x ->
        drawDiningTable(x, row1Y, tableR, chairR)
    }
    val row2Y = backY + (dineEndY - backY) * 0.71f
    listOf(tw * 3.5f, tw * 7.5f, tw * 11.5f).forEach { x ->
        drawDiningTable(x, row2Y, tableR, chairR)
    }
    drawPottedPlant(tw * 0.85f,     backY   + th * 0.70f, tw * 0.72f)
    drawPottedPlant(W - tw * 0.85f, backY   + th * 0.70f, tw * 0.72f)
    drawPottedPlant(tw * 0.85f,     dineEndY - th * 0.70f, tw * 0.72f)
    drawPottedPlant(W - tw * 0.85f, dineEndY - th * 0.70f, tw * 0.72f)
    drawFramedPicture(tw * 0.20f,     backY + (dineEndY - backY) * 0.50f, tw * 0.22f, th * 1.10f)
    drawFramedPicture(W - tw * 0.20f, backY + (dineEndY - backY) * 0.50f, tw * 0.22f, th * 1.10f)
    drawRoomLabel(W / 2f, (backY + dineEndY) / 2f,
        str.lunch.uppercase(), tw, android.graphics.Color.argb(80, 100, 70, 20))

    // ─── Entrance ───────────────────────────────────────────────────
    drawHostDesk(W / 2f - tw * 3.8f, dineEndY + th * 0.50f, tw * 2.40f, th * 0.95f)
    val matY = extStartY - th * 1.20f
    drawWelcomeMat(W / 2f - tw * 1.4f, matY, tw * 2.8f, th * 0.95f)
    drawRoomLabel(W / 2f + tw * 2.2f, dineEndY + (extStartY - dineEndY) / 2f,
        str.entrance.uppercase(), tw, android.graphics.Color.argb(160, 55, 45, 35))

    // ─── External Lunch Area (outdoor patio) ────────────────────────
    val oTableR = tw * 0.58f; val oChairR = tw * 0.21f
    listOf(tw * 2.5f, tw * 6.0f, tw * 10.2f, tw * 13.8f).forEach { ox2 ->
        drawDiningTable(ox2, extStartY + (H - extStartY) * 0.50f, oTableR, oChairR)
    }
    drawPottedPlant(tw * 0.80f,     extStartY + th * 0.42f, tw * 0.68f)
    drawPottedPlant(W - tw * 0.80f, extStartY + th * 0.42f, tw * 0.68f)
    // Pergola beam at the top of the patio
    drawRect(Color(0xFF7A5E38).copy(alpha = 0.72f),
        Offset(0f, extStartY - wt * 0.30f), Size(W, wt * 0.60f))
    // Perimeter fence at the bottom
    drawRect(Color(0xFF7A6848), Offset(0f, H - wt * 0.85f), Size(W, wt * 0.85f))
    drawRoomLabel(W / 2f, extStartY + (H - extStartY) * 0.56f,
        str.externalLunch.uppercase(), tw, android.graphics.Color.argb(115, 55, 45, 20))

    // ─── IMPROVEMENT 2: Selected zone highlight ─────────────────────
    val strokeW = wt * 0.75f
    val ins     = wt * 1.10f
    fun zonePulse(leq: Float) = if (leq >= 80f) alertPulse else pulse

    if (kSel) {
        val bc = leqToBorderColor(kLeq)
        drawRect(bc.copy(alpha = 0.22f + zonePulse(kLeq) * 0.58f),
            Offset(ins, ins), Size(kX2 - ins * 1.5f, backY - ins * 1.5f),
            style = Stroke(strokeW))
    }
    if (rSel) {
        val bc = leqToBorderColor(rLeq)
        drawRect(bc.copy(alpha = 0.22f + zonePulse(rLeq) * 0.58f),
            Offset(kX2 + ins * 0.5f, ins), Size(W - kX2 - ins * 1.2f, backY - ins * 1.5f),
            style = Stroke(strokeW))
    }
    if (dSel) {
        val bc = leqToBorderColor(dLeq)
        drawRect(bc.copy(alpha = 0.22f + zonePulse(dLeq) * 0.58f),
            Offset(ins, backY + ins * 0.5f), Size(W - ins * 2f, dineEndY - backY - ins * 1.2f),
            style = Stroke(strokeW))
    }
    if (eSel) {
        val bc = leqToBorderColor(eLeq)
        drawRect(bc.copy(alpha = 0.22f + zonePulse(eLeq) * 0.58f),
            Offset(ins, dineEndY + ins * 0.5f), Size(W - ins * 2f, extStartY - dineEndY - ins * 1.2f),
            style = Stroke(strokeW))
    }
    if (xSel) {
        val bc = leqToBorderColor(xLeq)
        drawRect(bc.copy(alpha = 0.22f + zonePulse(xLeq) * 0.58f),
            Offset(ins, extStartY + ins * 0.5f), Size(W - ins * 2f, H - extStartY - ins * 1.2f),
            style = Stroke(strokeW))
    }

    // ─── Walls ──────────────────────────────────────────────────────
    val wc  = Color(0xFF7A6858)
    val ws  = Color(0xFF3A2818)
    val shd = Color.Black.copy(alpha = 0.12f)

    drawRect(wc, Offset(0f, 0f),     Size(W, wt));  drawRect(ws, Offset(0f, wt),     Size(W, wt*0.22f))
    drawRect(wc, Offset(0f, 0f),     Size(wt, H));  drawRect(ws, Offset(wt, 0f),     Size(wt*0.22f, H))
    drawRect(wc, Offset(W - wt, 0f), Size(wt, H))
    val doorW = tw * 3.0f; val doorL = W / 2f - doorW / 2f; val doorR = W / 2f + doorW / 2f
    drawRect(wc, Offset(0f,    H - wt), Size(doorL,     wt))
    drawRect(wc, Offset(doorR, H - wt), Size(W - doorR, wt))
    drawRect(ws, Offset(doorL - wt*0.5f, H - wt*1.8f), Size(wt*0.55f, wt*1.8f))
    drawRect(ws, Offset(doorR,            H - wt*1.8f), Size(wt*0.55f, wt*1.8f))
    drawRect(wc,  Offset(kX2 - wt/2f, 0f),          Size(wt, backY))
    drawRect(shd, Offset(kX2 + wt/2f, 0f),          Size(wt*0.22f, backY))
    val swL = tw * 4.2f; val swR = tw * 7.0f
    drawRect(wc, Offset(0f,  backY - wt/2f), Size(swL,     wt))
    drawRect(wc, Offset(swR, backY - wt/2f), Size(W - swR, wt))
    drawRect(shd, Offset(0f, backY + wt/2f), Size(W, wt*0.22f))
    drawRect(ws, Offset(swL - wt*0.45f, backY - th*0.55f), Size(wt*0.55f, th*0.55f + wt))
    drawRect(ws, Offset(swR,             backY - th*0.55f), Size(wt*0.55f, th*0.55f + wt))
    drawRect(Color(0xFFBEBCB8), Offset(swL, backY + wt*0.5f), Size(swR - swL, th*0.22f))
    drawRect(wc,  Offset(0f, dineEndY - wt/2f), Size(W, wt))
    drawRect(shd, Offset(0f, dineEndY + wt/2f), Size(W, wt*0.22f))
    // Open-air divider between entrance and external patio
    drawRect(Color(0xFF7A5E38), Offset(0f, extStartY - wt * 0.45f), Size(W, wt * 0.90f))
    drawRect(shd, Offset(0f, extStartY + wt * 0.45f), Size(W, wt * 0.18f))
}

// =================================================================
//  FLOOR / TILE
// =================================================================

private fun DrawScope.drawTileGrid(x: Float, y: Float, w: Float, h: Float, ts: Float, c: Color) {
    var xi = x; while (xi < x + w) { drawRect(c, Offset(xi, y), Size(1.5f, h)); xi += ts }
    var yi = y; while (yi < y + h) { drawRect(c, Offset(x, yi), Size(w, 1.5f)); yi += ts }
}

// =================================================================
//  KITCHEN FURNITURE
// =================================================================

private fun DrawScope.drawSteelCounter(lx: Float, ty: Float, w: Float, h: Float) {
    drawRect(Color.Black.copy(alpha = 0.13f), Offset(lx + w*0.04f, ty + h*0.05f), Size(w, h))
    drawRect(Color(0xFFCECCC8), Offset(lx, ty), Size(w, h))
    drawRect(Color.White.copy(alpha = 0.28f), Offset(lx + w*0.04f, ty + h*0.07f), Size(w*0.92f, h*0.32f))
}
private fun DrawScope.drawShelf(lx: Float, ty: Float, w: Float, h: Float) {
    drawRect(Color(0xFFBCBAB6), Offset(lx, ty), Size(w, h))
    val sh = h / 3f
    for (i in 0..2) drawRect(Color(0xFFABA9A5), Offset(lx, ty + sh * i), Size(w, 1.5f))
}
private fun DrawScope.drawStove(cx: Float, cy: Float, w: Float, h: Float) {
    drawRect(Color(0xFF222222), Offset(cx - w/2f, cy - h/2f), Size(w, h))
    val bR = minOf(w, h) * 0.155f
    listOf(
        Offset(cx - w*0.27f, cy - h*0.26f), Offset(cx + w*0.27f, cy - h*0.26f),
        Offset(cx - w*0.27f, cy + h*0.26f), Offset(cx + w*0.27f, cy + h*0.26f)
    ).forEach { p ->
        drawCircle(Color(0xFF383838), bR, p)
        drawCircle(Color(0xFF555555), bR*0.48f, p)
        drawCircle(Color(0xFF6E6E6E), bR*0.20f, p)
    }
}
private fun DrawScope.drawFridge(lx: Float, ty: Float, w: Float, h: Float) {
    drawRect(Color.Black.copy(alpha = 0.12f), Offset(lx + w*0.05f, ty + h*0.04f), Size(w, h))
    drawRect(Color(0xFFE2E0DC), Offset(lx, ty), Size(w, h))
    drawRect(Color(0xFFD0CECC), Offset(lx + w*0.06f, ty + h*0.06f), Size(w*0.88f, h*0.33f))
    drawRect(Color(0xFFB0AEAC), Offset(lx + w*0.78f, ty + h*0.12f), Size(w*0.08f, h*0.20f))
    drawRect(Color(0xFFB0AEAC), Offset(lx + w*0.78f, ty + h*0.52f), Size(w*0.08f, h*0.20f))
}

// =================================================================
//  RESTROOM FURNITURE
// =================================================================

private fun DrawScope.drawToiletStall(lx: Float, ty: Float, w: Float, h: Float) {
    val r = CornerRadius(w * 0.28f)
    drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(lx + w*0.05f, ty + h*0.05f), Size(w, h), r)
    drawRoundRect(Color(0xFFF4F0EC), Offset(lx, ty), Size(w, h), r)
    drawRoundRect(Color(0xFFE8E4DE),
        Offset(lx + w*0.11f, ty + h*0.11f), Size(w*0.78f, h*0.78f), CornerRadius(w*0.20f))
}
private fun DrawScope.drawWallSink(cx: Float, ty: Float, w: Float, h: Float) {
    val r = CornerRadius(w * 0.28f)
    drawRoundRect(Color.Black.copy(alpha = 0.09f), Offset(cx - w/2f + w*0.05f, ty + h*0.05f), Size(w, h), r)
    drawRoundRect(Color(0xFFEEEAE4), Offset(cx - w/2f, ty), Size(w, h), r)
    drawCircle(Color(0xFF9CBAD4), w*0.17f, Offset(cx, ty + h*0.55f))
    drawCircle(Color(0xFFB0B2B8), w*0.06f, Offset(cx, ty + h*0.55f))
}

// =================================================================
//  DINING FURNITURE
// =================================================================

private fun DrawScope.drawDiningTable(cx: Float, cy: Float, tableR: Float, chairR: Float) {
    drawCircle(Color.Black.copy(alpha = 0.14f), tableR, Offset(cx + tableR*0.09f, cy + tableR*0.09f))
    drawCircle(Color(0xFFF5EFD8), tableR, Offset(cx, cy))
    drawCircle(Color(0xFFD8CC88), tableR, Offset(cx, cy), style = Stroke(tableR * 0.065f))
    drawCircle(Color(0xFFE0A040).copy(alpha = 0.60f), tableR * 0.17f, Offset(cx, cy))
    val dist = tableR + chairR * 1.30f
    listOf(Offset(cx, cy - dist), Offset(cx, cy + dist), Offset(cx - dist, cy), Offset(cx + dist, cy))
        .forEach { p ->
            drawCircle(Color.Black.copy(alpha = 0.12f), chairR, Offset(p.x + chairR*0.07f, p.y + chairR*0.07f))
            drawCircle(Color(0xFF7A4820), chairR, p)
            drawCircle(Color(0xFF9A6030).copy(alpha = 0.50f), chairR*0.58f,
                Offset(p.x - chairR*0.08f, p.y - chairR*0.08f))
        }
}
private fun DrawScope.drawPottedPlant(cx: Float, cy: Float, r: Float) {
    drawRect(Color(0xFFC07838), Offset(cx - r*0.55f, cy - r*0.10f), Size(r*1.10f, r*0.80f))
    drawRect(Color(0xFFB06030), Offset(cx - r*0.65f, cy - r*0.20f), Size(r*1.30f, r*0.18f))
    drawCircle(Color(0xFF2A7828), r*0.72f, Offset(cx, cy - r*0.65f))
    drawCircle(Color(0xFF3A9038), r*0.48f, Offset(cx - r*0.40f, cy - r*0.82f))
    drawCircle(Color(0xFF3A9038), r*0.48f, Offset(cx + r*0.40f, cy - r*0.82f))
    drawCircle(Color(0xFF50C050).copy(alpha = 0.48f), r*0.28f, Offset(cx, cy - r*1.02f))
}
private fun DrawScope.drawFramedPicture(cx: Float, cy: Float, w: Float, h: Float) {
    drawRoundRect(Color(0xFF5A4830), Offset(cx - w/2f - w*0.12f, cy - h/2f - h*0.08f),
        Size(w*1.24f, h*1.16f), CornerRadius(w*0.14f))
    drawRoundRect(Color(0xFFEAE0C0), Offset(cx - w/2f, cy - h/2f), Size(w, h), CornerRadius(w*0.08f))
    drawLine(Color(0xFFB08040), Offset(cx - w*0.28f, cy - h*0.28f), Offset(cx + w*0.28f, cy + h*0.20f), 1.8f)
    drawLine(Color(0xFF3878A0), Offset(cx - w*0.18f, cy + h*0.28f), Offset(cx + w*0.28f, cy - h*0.18f), 1.8f)
    drawCircle(Color(0xFFD03830).copy(alpha = 0.52f), w*0.11f, Offset(cx - w*0.08f, cy))
}

// =================================================================
//  ENTRANCE FURNITURE
// =================================================================

private fun DrawScope.drawHostDesk(lx: Float, ty: Float, w: Float, h: Float) {
    drawRoundRect(Color.Black.copy(alpha = 0.14f), Offset(lx + w*0.04f, ty + h*0.05f), Size(w, h), CornerRadius(w*0.12f))
    drawRoundRect(Color(0xFF6A4020), Offset(lx, ty), Size(w, h), CornerRadius(w*0.12f))
    drawRoundRect(Color(0xFF8A5830), Offset(lx + w*0.07f, ty + h*0.07f), Size(w*0.86f, h*0.38f), CornerRadius(w*0.08f))
    drawRoundRect(Color(0xFFC89050), Offset(lx + w*0.20f, ty + h*0.55f), Size(w*0.60f, h*0.32f), CornerRadius(h*0.06f))
}
private fun DrawScope.drawWelcomeMat(lx: Float, ty: Float, w: Float, h: Float) {
    drawRoundRect(Color(0xFF5A3820), Offset(lx, ty), Size(w, h), CornerRadius(h*0.22f))
    var sx = lx + w * 0.14f
    while (sx < lx + w - w*0.11f) {
        drawRect(Color(0xFF7A5030), Offset(sx, ty + h*0.10f), Size(w*0.06f, h*0.80f))
        sx += w * 0.14f
    }
}

// =================================================================
//  TEXT LABELS
// =================================================================

private fun DrawScope.drawRoomLabel(cx: Float, cy: Float, text: String, tw: Float, color: Int) {
    val paint = NativePaint().apply {
        this.color  = color
        textSize    = tw * 0.50f
        textAlign   = NativePaint.Align.CENTER
        isAntiAlias = true
        typeface    = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
    }
    drawIntoCanvas { cv -> cv.nativeCanvas.drawText(text, cx, cy, paint) }
}
private fun DrawScope.drawIconLabel(cx: Float, cy: Float, icon: String, tw: Float, color: Int) {
    val paint = NativePaint().apply {
        this.color  = color
        textSize    = tw * 0.78f
        textAlign   = NativePaint.Align.CENTER
        isAntiAlias = true
    }
    drawIntoCanvas { cv -> cv.nativeCanvas.drawText(icon, cx, cy, paint) }
}

// =================================================================
//  IMPROVEMENT 3: Reading badge
// =================================================================

private fun DrawScope.drawReadingBadge(cx: Float, cy: Float, leq: Float, br: Float, tw: Float) {
    val label  = "${leq.toInt()} dB"
    val tPaint = NativePaint().apply {
        color       = android.graphics.Color.WHITE
        textSize    = tw * 0.48f
        textAlign   = NativePaint.Align.LEFT
        isAntiAlias = true
        typeface    = Typeface.DEFAULT_BOLD
    }
    val bgPaint = NativePaint().apply {
        color       = android.graphics.Color.argb(185, 18, 20, 42)
        isAntiAlias = true
    }
    val tx  = cx + br * 1.55f
    val ty2 = cy + tPaint.textSize * 0.35f
    val tW  = tPaint.measureText(label)
    val pad = tw * 0.09f
    drawIntoCanvas { cv ->
        cv.nativeCanvas.drawRoundRect(
            RectF(tx - pad, ty2 - tPaint.textSize, tx + tW + pad, ty2 + pad * 0.5f),
            tw * 0.12f, tw * 0.12f, bgPaint)
        cv.nativeCanvas.drawText(label, tx, ty2, tPaint)
    }
}

// =================================================================
//  IMPROVEMENT 4: Thought bubble
// =================================================================

private fun DrawScope.drawThoughtBubble(
    cx: Float, cy: Float, r: Float, leq: Float, tw: Float, alpha: Float
) {
    val text = when {
        leq <= 0f -> "No data"
        leq < 60f -> "${leq.toInt()} dB 😊"
        leq < 75f -> "${leq.toInt()} dB 😐"
        else      -> "${leq.toInt()} dB 😣"
    }
    val a = alpha.coerceIn(0f, 1f)

    // Small trailing dots (classic thought-bubble style)
    drawCircle(Color(0xFF1A1F3A).copy(alpha = a * 0.88f), tw * 0.09f,
        Offset(cx + tw * 0.24f, cy - r * 1.08f))
    drawCircle(Color(0xFF1A1F3A).copy(alpha = a * 0.88f), tw * 0.06f,
        Offset(cx + tw * 0.38f, cy - r * 1.28f))

    // Bubble box
    val bCX   = cx + tw * 1.90f
    val bCY   = cy - r * 1.72f
    val bubW  = tw * 3.70f
    val bubH  = tw * 1.05f

    val bgPaint = NativePaint().apply {
        color = android.graphics.Color.argb((a * 215).toInt(), 22, 28, 58)
        isAntiAlias = true
    }
    val tPaint = NativePaint().apply {
        color = android.graphics.Color.argb((a * 255).toInt(), 235, 240, 255)
        textSize    = tw * 0.58f
        textAlign   = NativePaint.Align.CENTER
        isAntiAlias = true
        typeface    = Typeface.DEFAULT_BOLD
    }
    drawIntoCanvas { cv ->
        cv.nativeCanvas.drawRoundRect(
            RectF(bCX - bubW/2f, bCY - bubH/2f, bCX + bubW/2f, bCY + bubH/2f),
            tw * 0.22f, tw * 0.22f, bgPaint)
        cv.nativeCanvas.drawText(text, bCX, bCY + tPaint.textSize / 3f, tPaint)
    }
}

// =================================================================
//  SIMS CHARACTER  (IMPROVEMENT 5: alert strobe + plumb-bob color)
// =================================================================

private fun DrawScope.drawSimsCharacter(
    cx: Float, cy: Float, r: Float, frame: Int,
    pulse: Float, bobPulse: Float, bobColor: Color
) {
    drawOval(Color.Black.copy(alpha = 0.22f),
        topLeft = Offset(cx - r*0.75f, cy - r*0.25f), size = Size(r*1.50f, r*0.55f))

    val bob      = if (frame % 2 == 1) -r * 0.06f else 0f
    val legShift = when (frame) { 1 -> -r*0.14f; 3 -> r*0.14f; else -> 0f }
    val legSep   = r * 0.22f

    drawOval(Color(0xFF303890), Offset(cx - legSep - r*0.18f + bob*0.5f, cy - r*0.18f + bob), Size(r*0.36f, r*0.44f))
    drawOval(Color(0xFF303890), Offset(cx + legSep - r*0.18f - bob*0.5f, cy - r*0.18f - bob), Size(r*0.36f, r*0.44f))
    drawOval(Color(0xFF1A1A1A), Offset(cx - legSep - r*0.18f + legShift,  cy + r*0.22f + bob), Size(r*0.36f, r*0.22f))
    drawOval(Color(0xFF1A1A1A), Offset(cx + legSep - r*0.18f - legShift,  cy + r*0.22f - bob), Size(r*0.36f, r*0.22f))
    drawOval(Color(0xFF2CB860), Offset(cx - r*0.58f, cy - r*0.55f + bob), Size(r*1.16f, r*0.80f))
    drawOval(Color(0xFFF5C580), Offset(cx - r*0.14f, cy - r*0.55f + bob), Size(r*0.28f, r*0.28f))
    drawOval(Color(0xFF2CB860), Offset(cx - r*0.70f, cy - r*0.40f + bob), Size(r*0.22f, r*0.55f))
    drawOval(Color(0xFF2CB860), Offset(cx + r*0.48f, cy - r*0.40f + bob), Size(r*0.22f, r*0.55f))
    drawCircle(Color(0xFFF5C580), r*0.44f, Offset(cx, cy - r*0.82f + bob))
    drawCircle(Color(0xFF302010), r*0.06f, Offset(cx - r*0.14f, cy - r*0.86f + bob))
    drawCircle(Color(0xFF302010), r*0.06f, Offset(cx + r*0.14f, cy - r*0.86f + bob))
    drawOval(Color(0xFF5A3010), Offset(cx - r*0.44f, cy - r*1.30f + bob), Size(r*0.88f, r*0.58f))

    // Plumb-bob — color driven by current zone reading (green = OK, red = alert)
    val floatY = cy - r*1.80f + bob - bobPulse * r * 0.10f
    val bW = r*0.28f; val bHT = r*0.34f; val bHB = r*0.52f

    drawCircle(bobColor.copy(alpha = 0.18f + bobPulse * 0.30f), bW * 2.4f, Offset(cx, floatY))

    val diamond = Path().apply {
        moveTo(cx,      floatY - bHT)
        lineTo(cx + bW, floatY)
        lineTo(cx,      floatY + bHB)
        lineTo(cx - bW, floatY)
        close()
    }
    drawPath(diamond, bobColor)

    val highlight = Path().apply {
        moveTo(cx,      floatY - bHT)
        lineTo(cx + bW, floatY)
        lineTo(cx,      floatY + bHB * 0.15f)
        lineTo(cx - bW, floatY)
        close()
    }
    drawPath(highlight, Color(0xFFFFFFFF).copy(alpha = 0.30f + bobPulse * 0.25f))

    drawCircle(Color.White.copy(alpha = 0.45f), bW*0.22f, Offset(cx - bW*0.20f, floatY - bHT*0.55f))
    drawLine(bobColor.copy(alpha = 0.65f),
        Offset(cx, floatY + bHB), Offset(cx, cy - r*1.26f + bob), strokeWidth = 1.8f)
}
