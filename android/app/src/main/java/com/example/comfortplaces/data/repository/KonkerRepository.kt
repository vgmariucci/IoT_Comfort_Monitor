package com.example.comfortplaces.data.repository

import com.example.comfortplaces.data.model.SensorReading
import com.example.comfortplaces.data.model.toSensorReading
import com.example.comfortplaces.data.remote.KonkerApi
import com.example.comfortplaces.data.remote.TokenManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KonkerRepository @Inject constructor(
    private val api: KonkerApi,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val APP         = "default"
        private const val DEVICE_NAME = "comfort_places_app"
    }

    private var deviceGuid: String? = null

    // ── Auth ─────────────────────────────────────────────────────────────────
    // Always verifies credentials against the server (never uses cached token).
    suspend fun login(username: String, password: String): Result<Unit> =
        runCatching { tokenManager.verifyAndLogin(username, password) }.map { }

    // ── Data fetching ─────────────────────────────────────────────────────────
    suspend fun getSensorReadings(
        username: String,
        password: String,
        since: String
    ): Result<List<SensorReading>> = runCatching {
        val token = "Bearer ${tokenManager.getValidToken(username, password)}"
        val guid  = resolveGuid(token)
        api.getOutgoingEvents(
            token, APP,
            "device:$guid timestamp:>$since", sort = "newest"
        ).result.mapNotNull { it.toSensorReading() }
    }

    // ── Private ───────────────────────────────────────────────────────────────
    private suspend fun resolveGuid(token: String): String {
        deviceGuid?.let { return it }
        val guid = api.getDevices(token).result
            .first { it.name == DEVICE_NAME }.guid
        deviceGuid = guid
        return guid
    }
}
