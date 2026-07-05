package com.example.comfortplaces.ui.dashboard

import com.example.comfortplaces.data.model.SensorReading
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// ── Period definitions ─────────────────────────────────────────────────────
enum class DayPeriod(val label: String, val hourRange: IntRange) {
    MORNING  ("Morning",   6..11),
    AFTERNOON("Afternoon", 12..17),
    EVENING  ("Evening",   18..23),
}

/** ISO day-of-week labels (Mon=1 … Sun=7) */
val WEEK_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * One cell of the heatmap matrix: average Leq for a
 * (location, ISO day-of-week 1-7, period) combination.
 */
data class HeatmapCell(
    val location: String,
    val dayOfWeek: Int,       // 1 = Monday … 7 = Sunday (ISO)
    val period: DayPeriod,
    val avgLeq: Float?,       // null = no readings for this cell
    val sampleCount: Int
)

/** Full heatmap dataset for one location — a 7 × 3 matrix. */
data class LocationHeatmap(
    val location: String,
    // key = Pair(dayOfWeek 1-7, DayPeriod)
    val cells: Map<Pair<Int, DayPeriod>, HeatmapCell>
)

// ── Aggregation ────────────────────────────────────────────────────────────
private val KONKER_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd'T'HH:mm:ss")
private val BRT        = ZoneId.of("America/Sao_Paulo")

fun SensorReading.zonedTime(): ZonedDateTime? = runCatching {
    // 1. Parse the string directly into a LocalDateTime since it matches the visual pattern
    val localDateTime = java.time.LocalDateTime.parse(timestamp, KONKER_FMT)

    // 2. Treat this time directly as America/Sao_Paulo without applying any offset shifts
    localDateTime.atZone(BRT)
}.getOrNull()

/**
 * Aggregate [readingsByLocation] into one [LocationHeatmap] per location.
 * Readings between 00:00–05:59 (night) are excluded.
 */
fun buildLocationHeatmaps(
    readingsByLocation: Map<String, List<SensorReading>>
): List<LocationHeatmap> = readingsByLocation.map { (location, readings) ->

    val groups = mutableMapOf<Pair<Int, DayPeriod>, MutableList<Float>>()

    readings.forEach { r ->
        val zdt  = r.zonedTime() ?: return@forEach
        val dow  = zdt.dayOfWeek.value                              // 1=Mon … 7=Sun
        val hour = zdt.hour
        val period = DayPeriod.entries.firstOrNull { hour in it.hourRange }
            ?: return@forEach                                       // night hours skipped

        groups.getOrPut(dow to period) { mutableListOf() }.add(r.leqSpl)
    }

    val cells = buildMap<Pair<Int, DayPeriod>, HeatmapCell> {
        for (dow in 1..7) {
            for (period in DayPeriod.entries) {
                val key    = dow to period
                val values = groups[key]
                put(key, HeatmapCell(
                    location    = location,
                    dayOfWeek   = dow,
                    period      = period,
                    avgLeq      = values?.average()?.toFloat(),
                    sampleCount = values?.size ?: 0
                ))
            }
        }
    }

    LocationHeatmap(location = location, cells = cells)
}