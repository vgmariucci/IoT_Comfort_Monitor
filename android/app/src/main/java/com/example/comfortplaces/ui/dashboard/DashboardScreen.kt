package com.example.comfortplaces.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {

    // Full dataset — used by HeatmapCard for the 7-day day-of-week aggregation
    val allReadings = viewModel.readingsByLocation.values
        .flatten()
        .sortedBy { it.timestamp }

    // Last 10 readings only — used by the three line-chart cards
    val chartReadings = allReadings.takeLast(10)

    when {
        viewModel.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        viewModel.error != null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(viewModel.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                // Heatmap receives the full dataset for accurate weekly aggregation
                HeatmapCard(viewModel.readingsByLocation)

                // Charts receive only the last 10 readings for a clean, readable view
                SoundCard(chartReadings)
                TempHumCard(chartReadings)
                RssiCard(chartReadings)

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}