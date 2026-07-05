package com.example.comfortplaces.data.remote

import com.example.comfortplaces.data.model.KonkerDevicesResponse
import com.example.comfortplaces.data.model.KonkerEventsResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface KonkerApi {

    @GET("v1/{application}/devices/")
    suspend fun getDevices(
        @Header("Authorization") token: String,
        @Path("application") application: String = "default"
    ): KonkerDevicesResponse

    @GET("v1/{application}/outgoingEvents")
    suspend fun getOutgoingEvents(
        @Header("Authorization") token: String,
        @Path("application") application: String,
        @Query("q") query: String,
        @Query("sort") sort: String = "oldest",
        @Query("limit") limit: Int = 10000
    ): KonkerEventsResponse
}