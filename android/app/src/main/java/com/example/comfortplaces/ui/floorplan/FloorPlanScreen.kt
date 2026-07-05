package com.example.comfortplaces.ui.floorplan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.comfortplaces.data.model.SensorReading
import com.example.comfortplaces.ui.language.LocalAppStrings

@Composable
fun FloorPlanScreen(
    readingsByLocation: Map<String, List<SensorReading>>,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedLocation by remember { mutableStateOf(LOCATIONS.first().key) }

    val selectedReadings = readingsByLocation[selectedLocation] ?: emptyList()
    val latest           = selectedReadings.lastOrNull()
    val selectedDef      = LOCATIONS.find { it.key == selectedLocation } ?: LOCATIONS.first()

    val localizedShortName: (LocationDef) -> String = { loc ->
        when (loc.key) {
            "Close to Kitchen"   -> strings.kitchen
            "Lunch Area"         -> strings.lunch
            "Entrance"           -> strings.entrance
            "Close to Restrooms" -> strings.restrooms
            "External Area"      -> strings.externalLunch
            else                 -> loc.shortName
        }
    }

    Row(
        modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(8.dp)
    ) {
        Column(
            Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .padding(end = 6.dp)
        ) {
            CompactPixelMap(
                readingsByLocation = readingsByLocation,
                selectedLocation   = selectedLocation,
                onLocationTapped   = { selectedLocation = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LOCATIONS.forEach { loc ->
                    val isSelected = loc.key == selectedLocation
                    Surface(
                        onClick = { selectedLocation = loc.key },
                        shape   = RoundedCornerShape(6.dp),
                        color   = if (isSelected) Color(0xFFE94560) else Color(0xFF16213E),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            localizedShortName(loc),
                            color      = if (isSelected) Color.White else Color(0xFFB0B0C0),
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier   = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            maxLines   = 1,
                            textAlign  = TextAlign.Center
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .padding(start = 6.dp)
        ) {
            Surface(
                shape    = RoundedCornerShape(8.dp),
                color    = Color(0xFF16213E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        localizedShortName(selectedDef).uppercase(),
                        color         = Color(0xFFFFD93D),
                        fontFamily    = FontFamily.Monospace,
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 14.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (latest != null) strings.live else strings.noData,
                        color      = if (latest != null) Color(0xFF4CAF50) else Color(0xFFB0B0C0),
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SoundGauge(
                    leq      = latest?.leqSpl ?: 0f,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                TempGauge(
                    temp     = latest?.temperature ?: 0f,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HumGauge(
                    hum      = latest?.humidity ?: 0f,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                RssiGauge(
                    rssi     = latest?.wifiRssi ?: 0,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(Modifier.height(6.dp))

            SoundPeakCard(
                lmax     = latest?.lmaxSpl ?: 0f,
                lmin     = latest?.lminSpl ?: 0f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
