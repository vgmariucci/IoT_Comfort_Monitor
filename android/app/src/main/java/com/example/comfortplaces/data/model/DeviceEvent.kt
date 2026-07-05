package com.example.comfortplaces.data.model

import com.google.gson.annotations.SerializedName

data class KonkerEvent(
    val timestamp: String,
    val channel: String,
    @SerializedName("payload") val payload: Map<String, Any>
)

data class KonkerEventsResponse(
    val result: List<KonkerEvent>,
    val status: String
)

data class KonkerDevice(
    val guid: String,
    val name: String
)

data class KonkerDevicesResponse(
    val result: List<KonkerDevice>,
    val status: String
)

fun KonkerEvent.toSensorReading(): SensorReading? = runCatching {
    SensorReading(
        timestamp      = (payload["reading_time"] as?  String) ?: timestamp,
        deviceLocation = (payload["device_location"] as? String) ?: channel,
        leqSpl         = (payload["leq_spl"]     as? Number)?.toFloat() ?: 0f,
        lmaxSpl        = (payload["lmax_spl"]    as? Number)?.toFloat() ?: 0f,
        lminSpl        = (payload["lmin_spl"]    as? Number)?.toFloat() ?: 0f,
        temperature    = (payload["temperature"] as? Number)?.toFloat() ?: 0f,
        humidity       = (payload["humidity"]    as? Number)?.toFloat() ?: 0f,
        wifiRssi       = (payload["wifi_rssi"]   as? Number)?.toInt()  ?: 0
    )
}.getOrNull()