package com.example.comfortplaces.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.comfortplaces.data.model.SensorReading
import com.example.comfortplaces.ui.language.AppStrings
import com.example.comfortplaces.ui.language.LocalAppStrings

private fun leqColor(leq: Float?): Color {
    if (leq == null || leq == 0f) return Color(0xFF2D2D2D)
    val fraction = ((leq - 45f) / 45f).coerceIn(0f, 1f)
    return if (fraction < 0.5f)
        lerp(Color(0xFF22C55E), Color(0xFFFFC107), fraction * 2f)
    else
        lerp(Color(0xFFFFC107), Color(0xFFEF4444), (fraction - 0.5f) * 2f)
}

private val LOCATION_ORDER = listOf(
    "Lunch Area",
    "Entrance",
    "Close to Kitchen",
    "Close to Restrooms",
    "External Area"
)

private fun localLocationShort(loc: String?, strings: AppStrings): String = when (loc) {
    null                 -> strings.filterAll
    "Lunch Area"         -> strings.lunch
    "Entrance"           -> strings.entrance
    "Close to Kitchen"   -> strings.kitchen
    "Close to Restrooms" -> strings.restrooms
    "External Area"      -> strings.externalLunch
    else                 -> loc
}

private fun DayPeriod.localLabel(strings: AppStrings): String = when (this) {
    DayPeriod.MORNING   -> strings.periodMorning
    DayPeriod.AFTERNOON -> strings.periodAfternoon
    DayPeriod.EVENING   -> strings.periodEvening
}

private val CELL_HEIGHT = 28.dp

@Composable
fun HeatmapCard(readingsByLocation: Map<String, List<SensorReading>>) {
    val strings = LocalAppStrings.current
    val weekDays = listOf(
        strings.dayMon, strings.dayTue, strings.dayWed, strings.dayThu,
        strings.dayFri, strings.daySat, strings.daySun
    )

    val heatmaps = remember(readingsByLocation) { buildLocationHeatmaps(readingsByLocation) }
    var selectedLocation by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(10.dp)) {

            Text(
                strings.noiseHeatmapTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                strings.heatmapSubtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf<String?>(null).plus(LOCATION_ORDER).forEach { loc ->
                    val label  = localLocationShort(loc, strings)
                    val active = selectedLocation == loc
                    Surface(
                        onClick = { selectedLocation = if (active) null else loc },
                        shape   = RoundedCornerShape(6.dp),
                        color   = if (active) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            label,
                            fontSize    = 11.sp,
                            fontWeight  = if (active) FontWeight.Bold else FontWeight.Normal,
                            color       = if (active) MaterialTheme.colorScheme.onPrimary
                                          else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign   = TextAlign.Center,
                            modifier    = Modifier.padding(vertical = 5.dp, horizontal = 2.dp),
                            maxLines    = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val locationsToShow = if (selectedLocation != null)
                listOf(selectedLocation!!)
            else
                LOCATION_ORDER.filter { readingsByLocation.containsKey(it) }

            locationsToShow.forEach { location ->
                val heatmap = heatmaps.find { it.location == location }
                val hasData = heatmap != null &&
                        heatmap.cells.values.any { it.sampleCount > 0 }

                Column(Modifier.fillMaxWidth()) {
                    Text(
                        localLocationShort(location, strings),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(bottom = 3.dp)
                    )

                    if (!hasData) {
                        Text(
                            strings.noData,
                            fontSize = 11.sp,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().height(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(58.dp))
                            weekDays.forEach { day ->
                                Text(
                                    day,
                                    modifier  = Modifier.weight(1f),
                                    fontSize  = 11.sp,
                                    textAlign = TextAlign.Center,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DayPeriod.entries.forEach { period ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    period.localLabel(strings),
                                    modifier  = Modifier.width(58.dp),
                                    fontSize  = 11.sp,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines  = 1
                                )

                                for (dow in 1..7) {
                                    val cell  = heatmap!!.cells[dow to period]
                                    val color = leqColor(cell?.avgLeq)
                                    val textColor = if (cell?.avgLeq != null && cell.avgLeq > 0f)
                                        Color.White else Color(0xFF777777)

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(CELL_HEIGHT)
                                            .padding(horizontal = 1.dp, vertical = 1.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(color)
                                    ) {
                                        Text(
                                            text       = cell?.avgLeq?.let { "%.0f".format(it) } ?: "—",
                                            fontSize   = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color      = textColor,
                                            textAlign  = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("45 dB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(Color(0xFF22C55E), Color(0xFFFFC107), Color(0xFFEF4444))
                            )
                        )
                )
                Text("90 dB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF2D2D2D))
                        .border(0.5.dp, Color(0xFF666666), RoundedCornerShape(2.dp))
                )
                Text(strings.noData, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
