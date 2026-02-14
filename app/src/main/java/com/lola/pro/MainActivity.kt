package com.lola.pro

import android.app.Activity
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object SlopLogger {
    val logs = mutableStateListOf<String>()
    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        if (logs.size > 200) logs.removeAt(0)
        logs.add("[$time] $msg")
    }
}

class MainActivity : ComponentActivity() {
    private val PREFS_NAME = "lola_vault"
    private var isLoggedIn by mutableStateOf(false)
    private var uploadCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SlopLogger.log("PHASE 1: AWAKENING THE GATEKEEPER...")
        checkLoginStatus()
        checkQuota()

        setContent {
            LolaDashboard(
                isLoggedIn = isLoggedIn,
                uploadCount = uploadCount,
                onLoginClick = { startLogin() },
                onUploadTrigger = { uri, caption -> startUploadWorker(uri, caption) }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val authManager = TikTokAuthManager(this)
        val authCode = authManager.handleAuthResponse(intent)
        if (authCode != null) {
            SlopLogger.log("AUTH: Code intercepted! Trading for token...")
            lifecycleScope.launch {
                val token = authManager.getAccessToken(authCode)
                if (token != null) {
                    SlopLogger.log("SUCCESS: Sentinel is now LIVE. Token secured.")
                    isLoggedIn = true
                } else {
                    SlopLogger.log("ERR: Token exchange failed.")
                }
            }
        }
    }

    private fun checkLoginStatus() {
        val token = TikTokAuthManager(this).getSavedToken()
        isLoggedIn = !token.isNullOrEmpty()
    }

    private fun checkQuota() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        if (prefs.getString("LAST_DATE", "") != today) {
            uploadCount = 0
            prefs.edit().putString("LAST_DATE", today).putInt("COUNT", 0).apply()
        } else {
            uploadCount = prefs.getInt("COUNT", 0)
        }
    }

    private fun startLogin() {
        SlopLogger.log("AUTH: Initializing Handshake...")
        try {
            TikTokAuthManager(this).login()
            SlopLogger.log("AUTH: Handshake Dispatched.")
        } catch (e: Exception) {
            SlopLogger.log("ERR: Handshake Failed: ${e.message}")
        }
    }

    private fun startUploadWorker(uri: Uri, caption: String) {
        if (uploadCount >= 5) return
        val token = TikTokAuthManager(this).getSavedToken()
        val data = Data.Builder()
            .putString("TOKEN", token)
            .putString("VIDEO_URI", uri.toString())
            .putString("CAPTION", caption)
            .build()
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<UploadWorker>().setInputData(data).build())
        uploadCount++
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("COUNT", uploadCount).apply()
        SlopLogger.log("SUCCESS: 201 Salute! Task $uploadCount dispatched.")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LolaDashboard(isLoggedIn: Boolean, uploadCount: Int, onLoginClick: () -> Unit, onUploadTrigger: (Uri, String) -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var cookedUri by remember { mutableStateOf<Uri?>(null) }

    var taglishCaptions by remember { mutableStateOf(listOf<String>()) }
    var taglishHashtags by remember { mutableStateOf("") }
    var englishCaptions by remember { mutableStateOf(listOf<String>()) }
    var englishHashtags by remember { mutableStateOf("") }
    var directorText by remember { mutableStateOf("") }

    var viewMode by remember { mutableStateOf("TAGLISH") }
    var isCooking by remember { mutableStateOf(false) }
    var isGeminiThinking by remember { mutableStateOf(false) }

    val authManager = remember { TikTokAuthManager(context as Activity) }
    var geminiKeyPool by remember { mutableStateOf(authManager.getApiKeys()) }
    var systemPrompt by remember { mutableStateOf(authManager.getPrompts()) }
    var showEngineRoom by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedUri = uri
        cookedUri = null
        SlopLogger.log("VIDEO: Source URI acquired.")
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("🏎️ LOLA PRO v2.7", color = Color.Yellow, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212)),
                actions = {
                    IconButton(onClick = { showEngineRoom = true }) {
                        Icon(Icons.Outlined.Settings, "Engine", tint = Color.Gray)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {

            Box(modifier = Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF0D0D0D)).border(1.dp, Color(0xFF1E1E1E))) {
                val listState = rememberLazyListState()
                LaunchedEffect(SlopLogger.logs.size) { if (SlopLogger.logs.isNotEmpty()) listState.animateScrollToItem(SlopLogger.logs.size - 1) }
                LazyColumn(state = listState, modifier = Modifier.padding(8.dp).fillMaxSize()) {
                    items(items = SlopLogger.logs) { log ->
                        val color = if (log.contains("ERR") || log.contains("FAILED")) Color.Red else Color(0xFF00FF00)
                        Text(log, color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp)
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedUri == null) Color(0xFF2196F3) else Color(0xFF4CAF50))
                ) {
                    Text(if (selectedUri == null) "FIND THE SLOP" else "SLOP LOADED ✅")
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (selectedUri == null) return@Button
                        scope.launch {
                            isGeminiThinking = true
                            // ROLLING KEY POOL: Always fetch fresh from manager
                            val currentKeys = authManager.getApiKeys().split(Regex("[\\n\\r, ]+")).filter { it.isNotBlank() }
                            val activeKey = if (currentKeys.isNotEmpty()) currentKeys.random() else ""

                            if (activeKey.isEmpty()) {
                                SlopLogger.log("ERR: No keys in Engine Room!")
                                isGeminiThinking = false
                                return@launch
                            }

                            SlopLogger.log("GEMINI: Bartering with G3 FLASH (Key: ...${activeKey.takeLast(4)})")

                            try {
                                val model = GenerativeModel(
                                    modelName = "gemini-3-flash-preview",
                                    apiKey = activeKey,
                                    safetySettings = listOf(SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE))
                                )
                                val masterPrompt = """
                                    CONTEXT: $systemPrompt
                                    STRICT OUTPUT FORMAT:
                                    TAGLISH_CAPTIONS: [C1, C2, C3]
                                    TAGLISH_TAGS: [#LolaDrifting #AdoboTurbo]
                                    ENGLISH_CAPTIONS: [E1, E2]
                                    ENGLISH_TAGS: [#GrandmaChaos]
                                """.trimIndent()

                                val response = withContext(Dispatchers.IO) { model.generateContent(content { text(masterPrompt) }) }
                                val rawText = response.text ?: ""

                                // GREEDY PARSER
                                taglishCaptions = Regex("TAGLISH_CAPTIONS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
                                taglishHashtags = Regex("TAGLISH_TAGS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: ""
                                englishCaptions = Regex("ENGLISH_CAPTIONS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
                                englishHashtags = Regex("ENGLISH_TAGS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: ""

                                SlopLogger.log("SUCCESS: Content refined.")
                            } catch (e: Exception) { SlopLogger.log("ERR: Gemini stalled: ${e.message}") }
                            isGeminiThinking = false
                        }
                    },
                    enabled = selectedUri != null && !isGeminiThinking,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                ) {
                    if (isGeminiThinking) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    else Text("REFINE CONTENT (PANSIT SPEED)")
                }

                val currentOptions = if (viewMode == "TAGLISH") taglishCaptions else englishCaptions
                val currentTags = if (viewMode == "TAGLISH") taglishHashtags else englishHashtags

                if (currentOptions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        currentOptions.forEach { caption ->
                            Button(
                                onClick = {
                                    directorText = "$caption $currentTags"
                                    clipboardManager.setText(AnnotatedString(directorText))
                                    SlopLogger.log("UI: Hook selected & copied.")
                                },
                                modifier = Modifier.padding(bottom = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) { Text(caption, fontSize = 10.sp) }
                        }
                    }
                    OutlinedTextField(
                        value = directorText,
                        onValueChange = { directorText = it },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { if (!isLoggedIn) onLoginClick() else onUploadTrigger(selectedUri!!, directorText) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
                ) { Text(if (!isLoggedIn) "🔑 CONNECT TO TIKTOK" else "🦅 RELEASE THE BIRDS", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showEngineRoom) {
        EngineRoom(
            currentKeys = geminiKeyPool,
            currentPrompts = systemPrompt,
            logs = SlopLogger.logs.takeLast(6).joinToString("\n"),
            onSave = { newKeys, newPrompt ->
                // DIRECT PERSISTENCE
                authManager.saveApiKeys(newKeys)
                authManager.savePrompts(newPrompt)
                // LOCAL STATE SYNC
                geminiKeyPool = newKeys
                systemPrompt = newPrompt
                SlopLogger.log("SYSTEM: Key-Pool Relay rebooted.")
                showEngineRoom = false
            },
            onDismiss = { showEngineRoom = false }
        )
    }
}