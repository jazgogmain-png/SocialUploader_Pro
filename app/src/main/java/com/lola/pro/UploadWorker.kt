package com.lola.pro

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun playSalute(isVictory: Boolean) {
        val soundRes = if (isVictory) R.raw.lola_victory else R.raw.lola_stall
        try {
            MediaPlayer.create(applicationContext, soundRes).apply {
                setOnCompletionListener { release() }
                start()
            }
        } catch (e: Exception) {
            Log.e("LOLA_DEBUG", "AUDIO_ERR: The victory horn is broken.")
        }
    }

    override suspend fun doWork(): Result {
        val rawToken = inputData.getString("TOKEN")?.trim() ?: ""
        val videoUriString = inputData.getString("VIDEO_URI") ?: return Result.failure()
        val rawCaption = inputData.getString("CAPTION") ?: "Lola is Drifting! 🏎️"

        // LOLA_SCRUB: Deep clean the token and caption
        val token = rawToken.replace("Bearer ", "").trim()
        val cleanCaption = rawCaption.replace("*", "").replace("\"", "").take(100).trim()

        if (token.isEmpty()) {
            Log.e("LOLA_DEBUG", "WORKER_FAIL: The Gatekeeper forgot the key (Token is empty).")
            playSalute(false)
            return Result.failure()
        }

        return try {
            val videoUri = Uri.parse(videoUriString)
            val videoBytes = applicationContext.contentResolver.openInputStream(videoUri)?.use { it.readBytes() }
                ?: throw Exception("Could not read video bytes.")

            Log.d("LOLA_DEBUG", "PHASE 1: CONTACTING ELVES (Init)...")

            // INIT REQUEST
            val initJson = JSONObject().apply {
                put("post_info", JSONObject().apply { put("title", cleanCaption) })
                put("source_info", JSONObject().apply {
                    put("source", "FILE_UPLOAD")
                    put("video_size", videoBytes.size)
                    put("chunk_size", videoBytes.size)
                    put("total_chunk_count", 1)
                })
            }

            val initRequest = Request.Builder()
                .url("https://open.tiktokapis.com/v2/post/publish/inbox/video/init/")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json; charset=UTF-8")
                .post(initJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(initRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("LOLA_DEBUG", "INIT_FAIL: Code ${response.code} | Body: $body")
                    playSalute(false)
                    return Result.failure()
                }

                val uploadUrl = JSONObject(body).getJSONObject("data").getString("upload_url")
                Log.d("LOLA_DEBUG", "PHASE 2: ELVES ACCEPTED CRATE. PUSHING BYTES...")

                // PUSH REQUEST
                val pushRequest = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Content-Range", "bytes 0-${videoBytes.size - 1}/${videoBytes.size}")
                    .addHeader("Content-Length", videoBytes.size.toString())
                    .addHeader("Content-Type", "video/mp4")
                    .put(videoBytes.toRequestBody("video/mp4".toMediaType()))
                    .build()

                client.newCall(pushRequest).execute().use { pushResponse ->
                    if (pushResponse.code == 201 || pushResponse.code == 200) {
                        Log.d("LOLA_DEBUG", "SALUTE: 201_CREATED. The birds are singing!")
                        playSalute(true)
                        Result.success()
                    } else {
                        Log.e("LOLA_DEBUG", "PUSH_FAIL: Code ${pushResponse.code}")
                        playSalute(false)
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LOLA_DEBUG", "CRASH: Elves tripped: ${e.message}")
            playSalute(false)
            Result.failure()
        }
    }
}