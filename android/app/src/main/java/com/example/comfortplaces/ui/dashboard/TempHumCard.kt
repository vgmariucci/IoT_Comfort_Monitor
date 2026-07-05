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

private val TempColor = Color(0xFFF97316)
private val HumColor  = Color(0xFF3B82F6)

@Composable
fun TempHumCard(readings: List<SensorReading>) {
    val strings  = LocalAppStrings.current
    val tempEntries = readings.mapIndexed { i, r -> entryOf(i.toFloat(), r.temperature) }
    val humEntries  = readings.mapIndexed { i, r -> entryOf(i.toFloat(), r.humidity) }

    val producer = remember { ChartEntryModelProducer() }
    producer.setEntries(tempEntries, humEntries)

    val chartLines = remember {
        listOf(
            LineChart.LineSpec(lineColor = TempColor.toArgb()),
            LineChart.LineSpec(lineColor = HumColor.toArgb()),
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
                    strings.tempAndHumidity,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    modifier   = Modifier.weight(1f)
                )
                MetricChip(strings.temperature.take(4), readings.lastOrNull()?.temperature, "°C", TempColor)
                Spacer(Modifier.width(8.dp))
                MetricChip(strings.humidity.take(3),    readings.lastOrNull()?.humidity,    "%",  HumColor)
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
