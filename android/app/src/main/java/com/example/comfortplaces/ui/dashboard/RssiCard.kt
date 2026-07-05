package com.example.comfortplaces.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.comfortplaces.data.model.SensorReading
import com.example.comfortplaces.ui.language.AppStrings
import com.example.comfortplaces.ui.language.LocalAppStrings
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

private fun rssiColor(rssi: Int): Color = when {
    rssi > -55 -> Color(0xFF22C55E)
    rssi > -67 -> Color(0xFF84CC16)
    rssi > -80 -> Color(0xFFF59E0B)
    else       -> Color(0xFFEF4444)
}

private fun rssiLabel(rssi: Int, strings: AppStrings): String = when {
    rssi > -55 -> strings.signalExcellent
    rssi > -67 -> strings.signalGood
    rssi > -80 -> strings.signalFair
    else       -> strings.signalPoor
}

@Composable
fun RssiCard(readings: List<SensorReading>) {
    val strings    = LocalAppStrings.current
    val entries    = readings.mapIndexed { i, r -> entryOf(i.toFloat(), r.wifiRssi.toFloat()) }
    val producer   = remember { ChartEntryModelProducer() }
    producer.setEntries(entries)

    val latestRssi = readings.lastOrNull()?.wifiRssi ?: -100
    val lineColor  = rssiColor(latestRssi)
    val chartLines = remember(lineColor) {
        listOf(LineChart.LineSpec(lineColor = lineColor.toArgb()))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    strings.wifiSignal,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    modifier   = Modifier.weight(1f)
                )
                Surface(
                    color = lineColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        rssiLabel(latestRssi, strings),
                        color      = Color.White,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "$latestRssi dBm",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = lineColor
                )
            }

            Spacer(Modifier.height(6.dp))

            if (readings.size >= 2) {
                Chart(
                    chart              = lineChart(lines = chartLines),
                    chartModelProducer = producer,
                    startAxis          = rememberStartAxis(),
                    bottomAxis         = rememberBottomAxis(),
                    modifier           = Modifier.fillMaxWidth().height(110.dp)
                )
            } else {
                Box(
                    modifier         = Modifier.fillMaxWidth().height(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strings.waitingForData,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
