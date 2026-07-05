package com.example.comfortplaces.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val BASE_URL   = "https://api-ucmp.soneca.dev"
        private const val PREF_FILE  = "konker_auth"
        private const val KEY_TOKEN  = "access_token"
        private const val KEY_EXPIRY = "token_expiry_ms"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient()

    /** Returns a cached token when valid, otherwise fetches a new one.
     *  Used for data-fetch calls that already authenticated via [verifyAndLogin]. */
    suspend fun getValidToken(username: String, password: String): String {
        val cached = prefs.getString(KEY_TOKEN, null)
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)
        if (cached != null && System.currentTimeMillis() < expiry - 300_000L) {
            return cached
        }
        return fetchNewToken(username, password)
    }

    /** ALWAYS verifies credentials against the server — used only for the login flow.
     *  Throws if credentials are blank or if the server rejects them. */
    suspend fun verifyAndLogin(username: String, password: String): String {
        if (username.isBlank() || password.isBlank()) {
            throw IllegalArgumentException("Username and password cannot be empty")
        }
        return fetchNewToken(username, password)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_EXPIRY).apply()
    }

    private suspend fun fetchNewToken(username: String, password: String): String =
        withContext(Dispatchers.IO) {
            val credentials = Base64.encodeToString(
                "$username:$password".toByteArray(), Base64.NO_WRAP
            )
            val body = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/v1/oauth/token")
                .addHeader("Authorization", "Basic $credentials")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Authentication failed: HTTP ${response.code}")
            }
            val json      = JSONObject(response.body!!.string())
            val token     = json.getString("access_token")
            val expiresIn = if (json.has("expires_in")) json.getLong("expires_in") else 3600L

            prefs.edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_EXPIRY, System.currentTimeMillis() + expiresIn * 1000L)
                .apply()
            token
        }
}
