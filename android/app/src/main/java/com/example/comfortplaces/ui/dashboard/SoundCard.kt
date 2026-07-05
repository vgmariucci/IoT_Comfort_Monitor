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
import com.example.comfortplaces.ui.language.LocalAppStrings
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

private val LeqColor  = Color(0xFF14B8A6)
private val LmaxColor = Color(0xFFEF4444)
private val LminColor = Color(0xFF22C55E)

@Composable
fun SoundCard(readings: List<SensorReading>) {
    val strings = LocalAppStrings.current
    val leqEntries  = readings.mapIndexed { i, r -> entryOf(i.toFloat(), r.leqSpl) }
    val lmaxEntries = readings.mapIndexed { i, r -> entryOf(i.toFloat(), r.lmaxSpl) }
    val lminEntries = readings.mapIndexed { i, r -> entryOf(i.toFloat(), r.lminSpl) }

    val producer = remember { ChartEntryModelProducer() }
    producer.setEntries(leqEntries, lmaxEntries, lminEntries)

    val chartLines = remember {
        listOf(
            LineChart.LineSpec(lineColor = LeqColor.toArgb()),
            LineChart.LineSpec(lineColor = LmaxColor.toArgb()),
            LineChart.LineSpec(lineColor = LminColor.toArgb()),
        )
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
                    strings.soundLevel,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    modifier   = Modifier.weight(1f)
                )
                MetricChip("Leq",  readings.lastOrNull()?.leqSpl,  "dB", LeqColor)
                Spacer(Modifier.width(8.dp))
                MetricChip("Lmax", readings.lastOrNull()?.lmaxSpl, "dB", LmaxColor)
                Spacer(Modifier.width(8.dp))
                MetricChip("Lmin", readings.lastOrNull()?.lminSpl, "dB", LminColor)
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

@Composable
fun MetricChip(label: String, value: Float?, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        Text(
            text       = value?.let { "%.1f %s".format(it, unit) } ?: "--",
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
