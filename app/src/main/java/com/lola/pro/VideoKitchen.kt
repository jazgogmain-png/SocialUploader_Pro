package com.lola.pro

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream

object VideoKitchen {

    data class SlopRecipe(
        val caption: String,
        val overlayText: String,
        val overlayTime: String,
        val placement: String = "TOP",
        val style: String = "IMPACT_WHITE"
    )

    suspend fun burnSlop(
        context: Context,
        videoUri: Uri,
        recipe: SlopRecipe,
        onLog: (String) -> Unit
    ): Uri? {
        val inputName = "raw_input.mp4"
        val outputName = "lola_pro_v53.mp4"

        try {
            onLog("KITCHEN: Executing Orientation-Safe Burn (V5.3)...")

            val inputFile = File(context.cacheDir, inputName)
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(inputFile).use { output -> input.copyTo(output) }
            }

            val masterFont = copyFontToInternal(context, "font.ttf")
            val fontPath = masterFont.absolutePath.replace(":", "\\:")
            val outputFile = File(context.cacheDir, outputName)

            // 1. Precise Timing
            val startTime = recipe.overlayTime.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 1.0f
            val endTime = startTime + 2.5f

            // 2. THE EMOJI EXORCISM
            // Strips everything but letters, numbers, and basic punctuation
            val cleanText = recipe.overlayText.replace(Regex("[^\\p{L}\\p{N}\\s?!.,]"), "")
                .replace("'", "")
                .trim()

            val wrappedText = wrapText(cleanText, 20).replace(":", "\\:")

            // 3. THE 9:16 NUCLEAR FILTER GRAPH
            // Force 1080p, then draw text at h/10 (High Hook Zone)
            val filterGraph = "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1," +
                    "drawtext=fontfile='$fontPath':text='$wrappedText':fontcolor=white:borderw=12:bordercolor=black:" +
                    "fontsize=90:box=1:boxcolor=black@0.4:boxborderw=20:line_spacing=15:x=(w-tw)/2:y=h/10:" +
                    "enable=between(t\\,$startTime\\,$endTime)," +
                    "drawtext=fontfile='$fontPath':text='@YesAiDidThis':fontcolor=white@0.2:fontsize=35:x=w-tw-40:y=h-th-220"

            // 4. THE COMMAND (Back to a single string for type compatibility)
            val cmd = "-y -i ${inputFile.absolutePath} -vf \"$filterGraph\" -c:v libx264 -preset superfast -crf 18 -c:a copy ${outputFile.absolutePath}"

            onLog("PHASE 2: BARTERING WITH FFMPEG...")
            val session = FFmpegKit.execute(cmd)

            return if (ReturnCode.isSuccess(session.returnCode)) {
                onLog("SUCCESS: @YesAiDidThis RC3 complete. Check the high-zone!")
                Uri.fromFile(outputFile)
            } else {
                onLog("ERR: Filter Stall. Check Nerd Window.")
                null
            }
        } catch (e: Exception) {
            onLog("ERR: Kitchen Crash: ${e.message}")
            return null
        }
    }

    private fun wrapText(text: String, lineLength: Int): String {
        val words = text.split(" ")
        val sb = StringBuilder()
        var currentLineLength = 0
        for (word in words) {
            if (currentLineLength + word.length > lineLength) {
                sb.append("\n")
                currentLineLength = 0
            }
            sb.append(word).append(" ")
            currentLineLength += word.length + 1
        }
        return sb.toString().trim()
    }

    private fun copyFontToInternal(context: Context, fontName: String): File {
        val folder = File(context.cacheDir, "fonts")
        if (!folder.exists()) folder.mkdirs()
        val fontFile = File(folder, fontName)
        context.assets.open("fonts/$fontName").use { input ->
            FileOutputStream(fontFile).use { output -> input.copyTo(output) }
        }
        return fontFile
    }
}