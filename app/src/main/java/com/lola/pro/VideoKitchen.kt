package com.lola.pro

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream

object VideoKitchen {

    data class SlopRecipe(
        val caption: String,
        val overlayText: String,
        val overlayTime: String,
        val hashtags: String
    )

    suspend fun burnSlop(
        context: Context,
        videoUri: Uri,
        recipe: SlopRecipe,
        onLog: (String) -> Unit // <--- NEW: Live Telemetry Channel
    ): Uri? {
        val inputName = "raw_input.mp4"
        val outputName = "cooked_lola_${System.currentTimeMillis()}.mp4"
        val fontName = "impact.ttf"

        try {
            onLog("KITCHEN: Initializing Stove...")

            // 1. Copy Video to Cache
            val inputSwipe = File(context.cacheDir, inputName)
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(inputSwipe).use { output ->
                    input.copyTo(output)
                }
            }
            onLog("KITCHEN: Video cached (${inputSwipe.length() / 1024} KB)")

            // 2. Setup Font
            val fontFile = File(context.cacheDir, fontName)
            if (!fontFile.exists()) {
                context.assets.open("font.ttf").use { input ->
                    FileOutputStream(fontFile).use { output ->
                        input.copyTo(output)
                    }
                }
                onLog("KITCHEN: Font asset deployed.")
            }

            // 3. Prepare Overlay Data
            // Escape colons and single quotes for FFmpeg
            val cleanText = recipe.overlayText.replace(":", "\\:").replace("'", "")
            val triggerTime = recipe.overlayTime.toFloatOrNull() ?: 2.0f
            val startTime = triggerTime
            val endTime = triggerTime + 1.5f // Flash for 1.5 seconds

            val outputFile = File(context.cacheDir, outputName)
            if (outputFile.exists()) outputFile.delete()

            // 4. The FFmpeg Command (The Magic Spell)
            // Draws text in center, appearing at startTime and disappearing at endTime
            // Yellow text, black border (classic meme style)
            val cmd = "-i ${inputSwipe.absolutePath} " +
                    "-vf \"drawtext=fontfile=${fontFile.absolutePath}:" +
                    "text='$cleanText':" +
                    "fontcolor=yellow:fontsize=H/10:" +
                    "borderw=5:bordercolor=black:" +
                    "x=(w-text_w)/2:y=(h-text_h)/2:" +
                    "enable='between(t,$startTime,$endTime)'\" " +
                    "-c:v libx264 -preset ultrafast -crf 28 -c:a copy ${outputFile.absolutePath}"

            onLog("KITCHEN: Firing FFmpeg Command...")

            // 5. Execute Synchronously
            val session = FFmpegKit.execute(cmd)

            if (ReturnCode.isSuccess(session.returnCode)) {
                onLog("KITCHEN: SUCCESS! Slop served @ ${outputFile.absolutePath}")
                return Uri.fromFile(outputFile)
            } else {
                onLog("KITCHEN: FAILURE. Return Code: ${session.returnCode}")
                onLog("KITCHEN: Logs: ${session.logsAsString}") // Dump error to nerd window
                return null
            }

        } catch (e: Exception) {
            onLog("KITCHEN CRASH: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    // Helper to parse Gemini response strictly
    fun parseGeminiRaw(raw: String): SlopRecipe {
        // Fallback defaults
        var cap = "Lola Drifting #fyp"
        var overlay = "WAIT FOR IT"
        var time = "2"

        // This is now handled mostly in MainActivity,
        // but kept here as a backup utility
        return SlopRecipe(cap, overlay, time, "")
    }
}