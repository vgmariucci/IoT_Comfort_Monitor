package com.example.comfortplaces.data.model

data class SensorReading(
    val timestamp: String,
    val deviceLocation: String,
    val leqSpl: Float,
    val lmaxSpl: Float,
    val lminSpl: Float,
    val temperature: Float,
    val humidity: Float,
    val wifiRssi: Int
)