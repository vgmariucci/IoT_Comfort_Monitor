package com.example.comfortplaces.ui.language

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact row of language toggle buttons (flag + name when selected).
 * Used on the Login screen and in the post-login top bar.
 */
@Composable
fun LanguageSelector(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        AppLanguage.entries.forEach { lang ->
            val isSelected = lang == currentLanguage
            Surface(
                onClick  = { onLanguageChange(lang) },
                shape    = RoundedCornerShape(8.dp),
                color    = if (isSelected) Color(0xFFE94560) else Color(0xFF16213E),
                modifier = Modifier.height(30.dp)
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text     = lang.flag,
                        fontSize = 14.sp
                    )
                    if (isSelected) {
                        Text(
                            text       = lang.displayName,
                            color      = Color.White,
                            fontSize   = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
