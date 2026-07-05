package com.example.comfortplaces.ui.login

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.comfortplaces.ui.language.AppLanguage
import com.example.comfortplaces.ui.language.LanguageSelector
import com.example.comfortplaces.ui.language.LocalAppStrings

private object Lp {
    val bg        = Color(0xFF1A1A2E)
    val card      = Color(0xFF16213E)
    val accent    = Color(0xFFE94560)
    val gold      = Color(0xFFFFD93D)
    val cream     = Color(0xFFF8E8C8)
    val text1     = Color(0xFFF8F8F8)
    val text2     = Color(0xFFB0B0C0)
    val inputBg   = Color(0xFF0F3460)
    val inputBord  = Color(0xFF1A5276)
    val brown     = Color(0xFFA07048)
    val brownDark = Color(0xFF685030)
    val roof      = Color(0xFFE04040)
    val window    = Color(0xFF88C8F8)
    val green     = Color(0xFF48A048)
    val star      = Color(0xFFFFD700)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    onLanguageChange: (AppLanguage) -> Unit = {},
    onLoginSuccess: () -> Unit
) {
    val strings = LocalAppStrings.current
    val anim = rememberInfiniteTransition(label = "la")
    val smokeY by anim.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "smoke"
    )
    val starA by anim.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "star"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1A3E), Lp.bg)))
    ) {
        // Language selector — top-right corner
        LanguageSelector(
            currentLanguage  = currentLanguage,
            onLanguageChange = onLanguageChange,
            modifier         = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 12.dp)
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier
                    .weight(0.45f)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.3f)
                ) {
                    drawCompactRestaurant(smokeY, starA)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Ambi",
                    color = Lp.gold,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    strings.iotMonitor,
                    color = Lp.text2,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Surface(
                Modifier.weight(0.55f),
                shape = RoundedCornerShape(14.dp),
                color = Lp.card,
                tonalElevation = 4.dp
            ) {
                Column(
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.Center) {
                        listOf(Lp.accent, Color(0xFFF97316), Lp.gold, Color(0xFF4CAF50), Lp.window)
                            .forEach { c ->
                                Box(Modifier.size(6.dp).background(c, RoundedCornerShape(1.dp)))
                                Spacer(Modifier.width(3.dp))
                            }
                    }
                    Spacer(Modifier.height(14.dp))

                    Text(
                        strings.signIn,
                        color = Lp.text1,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        strings.enterCredentials,
                        color = Lp.text2,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = viewModel.username,
                        onValueChange = { viewModel.username = it.filter { !it.isWhitespace() } },
                        label = { Text(strings.username, color = Lp.text2, fontSize = 16.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lp.accent,
                            unfocusedBorderColor = Lp.inputBord,
                            focusedTextColor = Lp.text1,
                            unfocusedTextColor = Lp.text1,
                            cursorColor = Lp.accent,
                            focusedContainerColor = Lp.inputBg.copy(alpha = 0.4f),
                            unfocusedContainerColor = Lp.inputBg.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = viewModel.password,
                        onValueChange = { viewModel.password = it },
                        label = { Text(strings.password, color = Lp.text2, fontSize = 16.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lp.accent,
                            unfocusedBorderColor = Lp.inputBord,
                            focusedTextColor = Lp.text1,
                            unfocusedTextColor = Lp.text1,
                            cursorColor = Lp.accent,
                            focusedContainerColor = Lp.inputBg.copy(alpha = 0.4f),
                            unfocusedContainerColor = Lp.inputBg.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    viewModel.error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = Lp.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel.username = viewModel.username.trim()
                            viewModel.password = viewModel.password.trim()
                            viewModel.login { onLoginSuccess() }
                        },
                        enabled = !viewModel.isLoading,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Lp.accent,
                            disabledContainerColor = Lp.accent.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                strings.connect,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCompactRestaurant(smokePhase: Float, starAlpha: Float) {
    val w = size.width
    val h = size.height
    val px = w / 48f

    listOf(3f to 1f, 10f to 3f, 18f to 1f, 28f to 2f, 38f to 1f, 44f to 3f, 8f to 5f)
        .forEach { (sx, sy) ->
            drawRect(Lp.star.copy(alpha = starAlpha * (0.4f + (sx * 7 % 5) / 10f)),
                Offset(sx * px, sy * px), Size(px, px))
        }

    val gy = h * 0.78f
    drawRect(Color(0xFF2D5016), Offset(0f, gy), Size(w, h - gy))
    drawRect(Color(0xFF3A6B1E), Offset(0f, gy), Size(w, px * 1.5f))

    val bx = w * 0.18f; val bw = w * 0.64f
    val by = gy - h * 0.42f; val bh = gy - by

    for (i in 0..5) {
        val s = i * px * 1.8f
        drawRect(Lp.roof, Offset(bx - px * 3 + s, by - px * 8 + i * px * 1.5f), Size(bw + px * 6 - s * 2, px * 1.5f))
    }

    drawRect(Lp.cream, Offset(bx, by), Size(bw, bh))
    for (i in 1..4) drawRect(Color(0x10000000), Offset(bx, by + i * bh / 5), Size(bw, 1f))

    for (i in 0..2) {
        val wx = bx + bw * 0.1f + i * bw * 0.3f; val wy = by + bh * 0.12f
        val ww = bw * 0.22f; val wh = bh * 0.28f
        drawRect(Lp.brownDark, Offset(wx - px, wy - px), Size(ww + 2 * px, wh + 2 * px))
        drawRect(Lp.window, Offset(wx, wy), Size(ww, wh))
        drawRect(Color(0x30FFD700), Offset(wx + 2, wy + 2), Size(ww - 4, wh - 4))
        drawRect(Lp.brownDark, Offset(wx + ww / 2 - 0.5f, wy), Size(1f, wh))
        drawRect(Lp.brownDark, Offset(wx, wy + wh / 2 - 0.5f), Size(ww, 1f))
    }

    val dx = bx + bw * 0.38f; val dw = bw * 0.24f; val dh = bh * 0.48f; val dy = gy - dh
    drawRect(Lp.brownDark, Offset(dx - px, dy - px * 2), Size(dw + 2 * px, dh + px * 2))
    drawRect(Lp.brown, Offset(dx, dy), Size(dw, dh))
    drawCircle(Lp.gold, px, Offset(dx + dw * 0.8f, dy + dh * 0.5f))

    val sw = bw * 0.3f; val sx = bx + (bw - sw) / 2; val sy = by + bh * 0.6f
    drawRoundRect(Color(0xFF2D1810), Offset(sx, sy), Size(sw, px * 4.5f), CornerRadius(px))
    drawRoundRect(Lp.gold, Offset(sx + px * 2, sy + px), Size(sw - px * 4, px * 2.5f), CornerRadius(px * 0.5f))

    drawRect(Lp.roof, Offset(bx + bw * 0.72f, by - px * 14), Size(px * 4, px * 10))
    val csx = bx + bw * 0.72f + px * 2
    for (i in 0..1) {
        val p = (smokePhase + i * 0.4f) % 1f
        drawCircle(
            Color(0x40FFFFFF).copy(alpha = (1f - p) * 0.3f),
            px * (1.5f + p * 1.5f),
            Offset(csx + kotlin.math.sin(p * 6.28).toFloat() * px * 2, by - px * 16 - p * px * 10)
        )
    }

    fun tree(tx: Float) {
        drawRect(Lp.brown, Offset(tx + px * 2, gy - px * 10), Size(px * 2.5f, px * 10))
        drawCircle(Color(0xFF2D6B1E), px * 5, Offset(tx + px * 3.2f, gy - px * 14))
        drawCircle(Lp.green, px * 3.5f, Offset(tx + px * 3.2f, gy - px * 16))
    }
    tree(px); tree(w - px * 10)
}
