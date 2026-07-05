package com.example.comfortplaces.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.comfortplaces.data.model.SensorReading
import com.example.comfortplaces.data.repository.KonkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: KonkerRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Credentials come from SavedStateHandle (passed via navigation or
    // stored there by LoginViewModel after successful login).
    // Fall back to empty string — refresh() will surface an auth error
    // rather than crash if they are missing.
    private val username: String
        get() = savedStateHandle.get<String>("username") ?: ""
    private val password: String
        get() = savedStateHandle.get<String>("password") ?: ""

    var readingsByLocation by mutableStateOf<Map<String, List<SensorReading>>>(emptyMap())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selectedLocation by mutableStateOf<String?>(null)

    init {
        refresh()
        // Auto-refresh every 30 s (7-day window is large; lighter cadence is fine)
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = readingsByLocation.isEmpty()
            error = null

            // Fetch last 7 days so the day-of-week heatmap has full coverage
            val since = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))
                .minusDays(7)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            repository.getSensorReadings(username, password, since)
                .onSuccess { readings ->
                    readingsByLocation = readings.groupBy { it.deviceLocation }
                }
                .onFailure { error = it.message }

            isLoading = false
        }
    }
}