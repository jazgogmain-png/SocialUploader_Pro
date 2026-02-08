package com.lola.pro

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var authManager: TikTokAuthManager
    private var selectedVideoUri by mutableStateOf<Uri?>(null)
    private var accessToken by mutableStateOf<String?>(null)
    private var isGenerating by mutableStateOf(false)
    private var isUploading by mutableStateOf(false)
    private var telemetryLog by mutableStateOf("THE ELVES ARE ASLEEP...")
    private var systemLog by mutableStateOf(listOf<String>())
    private var fullScript by mutableStateOf("")
    private var finalCaptionForUpload by mutableStateOf("")
    private var showSettings by mutableStateOf(false)
    private var apiKeyInput by mutableStateOf("")
    private var basePromptInput by mutableStateOf("")

    // THE QUOTA COUNTER
    private var dailyUploadCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = TikTokAuthManager(this)
        accessToken = authManager.getSavedToken()
        apiKeyInput = authManager.getApiKeys()
        basePromptInput = authManager.getPrompts()

        handleIntent(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(surface = Color(0xFF1A1C1E), background = Color(0xFF111315))) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardScreen(
                        onLoginClick = {
                            logEvent("PHASE 1: AWAKENING THE GATEKEEPER")
                            authManager.login()
                        },
                        videoUri = selectedVideoUri,
                        onVideoSelected = { uri ->
                            try {
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                selectedVideoUri = uri
                                logEvent("MAP_FOUND: Found path to ${uri.path?.takeLast(10)}")
                            } catch (e: Exception) {
                                selectedVideoUri = uri
                                logEvent("ERR: Path blocked by trolls.")
                            }
                        },
                        onGenerateMagic = { isTaglish -> generateMagic(isTaglish) },
                        onUltraMagic = { runUltraSlop() },
                        onUploadTrigger = { caption -> startUploadWorker(caption) },
                        isLoggedIn = accessToken != null,
                        isGenerating = isGenerating,
                        isUploading = isUploading,
                        fullScript = fullScript,
                        finalCaption = finalCaptionForUpload,
                        onCaptionChange = { text -> finalCaptionForUpload = text },
                        onSettingsClick = { showSettings = true },
                        telemetry = telemetryLog,
                        logs = systemLog,
                        uploadCount = dailyUploadCount,
                        onClearCount = { dailyUploadCount = 0 }
                    )

                    if (showSettings) {
                        EngineRoom(
                            currentKeys = apiKeyInput,
                            currentPrompts = basePromptInput,
                            logs = systemLog.joinToString("\n"),
                            onSave = { keys, prompts ->
                                apiKeyInput = keys
                                basePromptInput = prompts
                                authManager.saveApiKeys(keys)
                                authManager.savePrompts(prompts)
                                logEvent("ALCHEMY: Formula altered.")
                                showSettings = false
                            },
                            onDismiss = { showSettings = false }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val authCode = authManager.handleAuthResponse(intent)
        if (authCode != null) {
            lifecycleScope.launch {
                logEvent("PHASE 2: BARTERING WITH TIKTOK ELVES")
                telemetryLog = "BARTERING..."
                accessToken = authManager.getAccessToken(authCode)
                if (accessToken != null) {
                    telemetryLog = "VIBE CHECK: PASSED."
                    logEvent("HANDSHAKE: Elves accepted offering.")
                } else {
                    logEvent("ERR: Elves are angry. Check Redirect.")
                }
            }
        }
    }

    private fun logEvent(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$time] $message"
        val newList = systemLog.toMutableList()
        newList.add(entry)

        // SENTINEL: Detect Spam Risk and trigger Quota Lock
        if (message.contains("spam_risk", ignoreCase = true)) {
            dailyUploadCount = 5
        }

        systemLog = newList
        Log.d("LOLA_DEBUG", entry)
    }

    private fun generateMagic(isTaglish: Boolean) {
        val uri = selectedVideoUri ?: return
        val keys = apiKeyInput.split("\n").filter { it.isNotBlank() }.shuffled()
        isGenerating = true
        telemetryLog = "BIRDS ARE HUMMING..."

        lifecycleScope.launch(Dispatchers.IO) {
            val videoBytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) { null }

            withContext(Dispatchers.Main) {
                if (videoBytes == null) {
                    logEvent("ERR: Birds flew away (Null Bytes).")
                    isGenerating = false
                    return@withContext
                }

                logEvent("INGEST: Swallowing ${videoBytes.size / 1024} KB of slop.")
                val style = if (isTaglish) "Taglish" else "English"

                for ((index, key) in keys.withIndex()) {
                    try {
                        logEvent("WHISPERING: Talking to Elf #${index + 1}")
                        val model = GenerativeModel("gemini-3-flash-preview", key.trim())
                        val response = model.generateContent(content {
                            blob("video/mp4", videoBytes)
                            text("Analyze drifting lola. 3 hooks. 5 tags. Style: $style. FORMAT: CAPTION 1: [text] CAPTION 2: [text] CAPTION 3: [text] HASHTAGS: #tags MUSIC: [vibe] OVERLAY_TEXT: [text] OVERLAY_TIME: [time] OVERLAY_STYLE: [style]")
                        })

                        response.text?.let { raw ->
                            fullScript = raw
                            logEvent("LLM_PIPE_G3: Birds finished song.")
                            telemetryLog = "MAGIC READY."
                            isGenerating = false
                            return@withContext
                        }
                    } catch (e: Exception) {
                        logEvent("ERR: Elf #${index + 1} fell asleep.")
                        continue
                    }
                }
                logEvent("ERR: Workshop empty.")
                isGenerating = false
            }
        }
    }

    private fun startUploadWorker(caption: String) {
        logEvent("DISPATCH: Elves carrying crate...")
        isUploading = true
        val inputData = Data.Builder()
            .putString("TOKEN", accessToken)
            .putString("VIDEO_URI", selectedVideoUri.toString())
            .putString("CAPTION", caption.replace("*", "").take(100))
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>().setInputData(inputData).build()
        WorkManager.getInstance(this).enqueue(uploadRequest)

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(uploadRequest.id).observe(this) { info ->
            if (info != null) {
                logEvent("LOG: ${info.state}")
                if (info.state.isFinished) {
                    isUploading = false
                    if (info.state == WorkInfo.State.SUCCEEDED) {
                        logEvent("SALUTE: 201_CREATED (Draft Ready!)")
                        dailyUploadCount++
                        telemetryLog = "DRAFT DELIVERED."
                    } else {
                        // The logEvent sentinel will catch "spam_risk" here if the worker logs it
                        logEvent("ERR: The Elves dropped the crate.")
                        telemetryLog = "CRASHED."
                    }
                }
            }
        }
    }

    private fun runUltraSlop() { /* ... */ }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLoginClick: () -> Unit,
    videoUri: Uri?,
    onVideoSelected: (Uri) -> Unit,
    onGenerateMagic: (Boolean) -> Unit,
    onUltraMagic: () -> Unit,
    onUploadTrigger: (String) -> Unit,
    isLoggedIn: Boolean,
    isGenerating: Boolean,
    isUploading: Boolean,
    fullScript: String,
    finalCaption: String,
    onCaptionChange: (String) -> Unit,
    onSettingsClick: () -> Unit,
    telemetry: String,
    logs: List<String>,
    uploadCount: Int,
    onClearCount: () -> Unit
) {
    val scrollState = rememberScrollState()
    val nerdScrollState = rememberScrollState()
    val context = LocalContext.current
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onVideoSelected(uri)
    }

    LaunchedEffect(logs.size) {
        nerdScrollState.animateScrollTo(nerdScrollState.maxValue)
    }

    var lastTapTime by remember { mutableLongStateOf(0L) }

    fun scrub(text: String) = text.replace(Regex("[\\*\\\"_]"), "").trim()
    fun extract(script: String, prefix: String): String {
        val regex = Regex("(?i)$prefix[:\\s]+(.*?)(?=\\n[A-Z_\\s]+:|$)", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(script)
        return match?.groupValues?.get(1)?.split("\n")?.first()?.trim() ?: ""
    }

    fun copyToClipboard(text: String, label: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label Pinned!", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("@DriftingGrandma", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = "Pending: $uploadCount / 5",
                    fontSize = 10.sp,
                    color = if (uploadCount >= 5) Color.Red else Color.Gray,
                    modifier = Modifier.clickable { onClearCount() }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, null, tint = Color.Gray) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // NERD WINDOW with Sentinel Color Logic
        val nerdColor = if (uploadCount >= 5) Color.Red else Color.Green
        Box(modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(4.dp))
            .border(1.dp, nerdColor.copy(0.2f), RoundedCornerShape(4.dp))
            .padding(8.dp)
        ) {
            Column {
                Text(text = "> $telemetry", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = nerdColor)
                Box(modifier = Modifier.height(80.dp).verticalScroll(nerdScrollState)) {
                    Text(
                        text = buildAnnotatedString {
                            logs.forEach { line ->
                                val isError = line.contains("ERR:") || line.contains("FAIL") || line.contains("risk")
                                withStyle(style = SpanStyle(color = if (isError) Color.Red else nerdColor.copy(alpha = 0.7f))) {
                                    append(line + "\n")
                                }
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
        }

        if (isGenerating || isUploading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = if (isGenerating) Color.Cyan else Color(0xFFFE2C55),
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Load Slop Button - Hardened Contrast
        Button(
            onClick = { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF333333),
                contentColor = Color.White // Force white text for readability
            )
        ) {
            Text(if (videoUri != null) "✨ SOURCE LOADED" else "FIND THE SLOP", fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onGenerateMagic(true) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2185B))) { Text("LOLA MIX") }
            Button(onClick = { onGenerateMagic(false) }, modifier = Modifier.weight(1f)) { Text("INT'L HOOK") }
        }

        if (fullScript.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            val hashtags = scrub(extract(fullScript, "HASHTAGS"))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CAPTION 1", "CAPTION 2", "CAPTION 3").forEachIndexed { index, tag ->
                    val cap = scrub(extract(fullScript, tag))
                    Button(
                        onClick = { onCaptionChange("$cap\n\n$hashtags") },
                        modifier = Modifier.weight(1f).height(45.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
                    ) { Text("SLOP ${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = finalCaption,
            onValueChange = onCaptionChange,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            label = { Text("MAGIC POTION", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onUploadTrigger(finalCaption) },
            enabled = isLoggedIn && finalCaption.isNotBlank() && uploadCount < 5,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (uploadCount >= 5) Color.DarkGray else Color(0xFFFE2C55))
        ) {
            Text("RELEASE THE BIRDS 🦅", fontWeight = FontWeight.Bold, color = Color.White)
        }

        if (uploadCount >= 5) {
            Text(
                "ELVES ARE ON STRIKE (QUOTA REACHED)",
                color = Color.Red,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
            Text(if (isLoggedIn) "WAKE THE ELVES" else "INVITE THE ELVES", color = Color.White)
        }

        Spacer(modifier = Modifier.height(40.dp))
        Box(modifier = Modifier.align(Alignment.CenterHorizontally).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).clickable {
            val current = System.currentTimeMillis()
            if (current - lastTapTime < 500) onUltraMagic()
            lastTapTime = current
        }.padding(8.dp)) { Text("u-slop", fontSize = 10.sp, color = Color.DarkGray) }
    }
}