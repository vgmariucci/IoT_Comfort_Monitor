package com.example.comfortplaces.data.model

data class Device(
    val name: String,
    val guid: String
)

data class DevicesResponse(
    val result: List<Device>,
    val status: String
)