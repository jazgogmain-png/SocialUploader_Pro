package com.lola.pro

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.tiktok.open.sdk.auth.AuthApi
import com.tiktok.open.sdk.auth.AuthRequest
import com.tiktok.open.sdk.auth.utils.PKCEUtils
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TikTokAuthManager(private val activity: Activity) {

    private val clientKey = BuildConfig.TIKTOK_KEY.trim()
    private val clientSecret = BuildConfig.TIKTOK_SECRET.trim()
    private val redirectUri = "https://io.github.com/jazgogmain-png/callback"
    private val client = HttpClient(Android)

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create(
        "lola_vault", masterKeyAlias, activity,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKeys(keys: String) = prefs.edit().putString("gemini_pool", keys).apply()
    fun getApiKeys(): String = prefs.getString("gemini_pool", "") ?: ""
    fun savePrompts(prompts: String) = prefs.edit().putString("prompt_library", prompts).apply()
    fun getPrompts(): String = prefs.getString("prompt_library", "Provide viral captions...") ?: ""

    fun saveToken(token: String) = prefs.edit().putString("access_token", token.trim()).apply()
    fun getSavedToken(): String? = prefs.getString("access_token", null)?.trim()

    fun login() {
        val codeVerifier = PKCEUtils.generateCodeVerifier()
        prefs.edit().putString("pending_verifier", codeVerifier).apply()
        Log.d("LOLA_DEBUG", "AUTH_START")
        AuthApi(activity).authorize(AuthRequest(clientKey, "user.info.basic,video.upload,video.publish", redirectUri, codeVerifier))
    }

    fun handleAuthResponse(intent: Intent): String? {
        val response = AuthApi(activity).getAuthResponseFromIntent(intent, redirectUri)
        return if (response != null && response.isSuccess) response.authCode?.trim() else null
    }

    suspend fun getAccessToken(authCode: String): String? = withContext(Dispatchers.IO) {
        val verifier = prefs.getString("pending_verifier", "")?.trim() ?: ""
        try {
            Log.d("LOLA_DEBUG", "TOKEN_EXCHANGE_START")
            val rawBody = "client_key=$clientKey" +
                    "&client_secret=$clientSecret" +
                    "&code=${authCode.trim()}" +
                    "&grant_type=authorization_code" +
                    "&redirect_uri=$redirectUri" +
                    "&code_verifier=$verifier"

            val res: HttpResponse = client.post("https://open.tiktokapis.com/v2/oauth/token/") {
                header("Content-Type", "application/x-www-form-urlencoded")
                setBody(TextContent(rawBody, ContentType.Application.FormUrlEncoded))
            }

            val body = res.bodyAsText()
            Log.d("LOLA_DEBUG", "HTTP_STATUS: ${res.status} | BODY: $body")

            val json = JSONObject(body)
            if (res.status == HttpStatusCode.OK && json.has("access_token")) {
                val token = json.getString("access_token")
                saveToken(token)
                token
            } else null
        } catch (e: Exception) {
            Log.e("LOLA_DEBUG", "EXCHANGE_ERR: ${e.message}")
            null
        }
    }
}