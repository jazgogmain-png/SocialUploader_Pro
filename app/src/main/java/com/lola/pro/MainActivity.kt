package com.lola.pro

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.VideoView
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
import androidx.compose.ui.viewinterop.AndroidView
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
    private var lastUsedKeyIndex = -1

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
                onUploadTrigger = { uri, caption -> startUploadWorker(uri, caption) },
                keyIndexProvider = { lastUsedKeyIndex },
                onKeyIndexUpdate = { lastUsedKeyIndex = it }
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
        SlopLogger.log("SUCCESS: Task $uploadCount dispatched.")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LolaDashboard(
    isLoggedIn: Boolean,
    uploadCount: Int,
    onLoginClick: () -> Unit,
    onUploadTrigger: (Uri, String) -> Unit,
    keyIndexProvider: () -> Int,
    onKeyIndexUpdate: (Int) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var taglishCaptions by remember { mutableStateOf(listOf<String>()) }
    var taglishHashtags by remember { mutableStateOf("") }
    var englishCaptions by remember { mutableStateOf(listOf<String>()) }
    var englishHashtags by remember { mutableStateOf("") }

    var overlaySuggestion by remember { mutableStateOf("WAIT FOR IT...") }
    var musicTips by remember { mutableStateOf("Pinoy Budots Remix") }
    var directorText by remember { mutableStateOf("") }

    var viewMode by remember { mutableStateOf("TAGLISH") }
    var isGeminiThinking by remember { mutableStateOf(false) }

    var isSeasonalMode by remember { mutableStateOf(false) }
    var manualSeason by remember { mutableStateOf("Valentine's Day") }

    val authManager = remember { TikTokAuthManager(context as Activity) }
    var geminiKeyPool by remember { mutableStateOf(authManager.getApiKeys()) }
    var systemPrompt by remember { mutableStateOf(authManager.getPrompts()) }
    var showEngineRoom by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedUri = uri
        SlopLogger.log("VIDEO: Source URI acquired.")
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("🏎️ LOLA PRO v2.9", color = Color.Yellow, fontWeight = FontWeight.Bold) },
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

            Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF0D0D0D)).border(1.dp, Color(0xFF1E1E1E))) {
                val listState = rememberLazyListState()
                LaunchedEffect(SlopLogger.logs.size) { if (SlopLogger.logs.isNotEmpty()) listState.animateScrollToItem(SlopLogger.logs.size - 1) }
                LazyColumn(state = listState, modifier = Modifier.padding(8.dp).fillMaxSize()) {
                    items(items = SlopLogger.logs) { log ->
                        val color = if (log.contains("ERR")) Color.Red else Color(0xFF00FF00)
                        Text(log, color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }

            if (selectedUri != null) {
                Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.DarkGray).padding(8.dp)) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(selectedUri)
                                setOnPreparedListener { it.isLooping = true; start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedUri == null) Color(0xFF2196F3) else Color(0xFF4CAF50))
                ) {
                    Text(if (selectedUri == null) "FIND THE SLOP" else "SWAP VIDEO 🔄")
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manualSeason,
                        onValueChange = { manualSeason = it },
                        modifier = Modifier.weight(1f).height(56.dp),
                        label = { Text("Season/Event", color = Color.Gray) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF4081))
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { isSeasonalMode = !isSeasonalMode; SlopLogger.log("SEASON: ${if(isSeasonalMode) "ON" else "OFF"}") },
                        modifier = Modifier.background(if(isSeasonalMode) Color(0xFFFF4081) else Color(0xFF333333), RoundedCornerShape(8.dp))
                    ) {
                        Icon(if(isSeasonalMode) Icons.Default.Favorite else Icons.Default.DateRange, "", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (selectedUri == null) return@Button
                        scope.launch {
                            isGeminiThinking = true
                            val keys = authManager.getApiKeys().split(Regex("[\\n\\r, ]+")).filter { it.isNotBlank() }

                            var success = false
                            var attempt = 0
                            while (!success && attempt < keys.size) {
                                val nextIndex = (keyIndexProvider() + 1) % keys.size
                                onKeyIndexUpdate(nextIndex)
                                val activeKey = keys[nextIndex]
                                attempt++
                                try {
                                    val model = GenerativeModel("gemini-3-flash-preview", activeKey)
                                    val seasonalContext = if (isSeasonalMode) "THEME: Focus heavily on $manualSeason." else ""

                                    val masterPrompt = """
                                        $systemPrompt
                                        $seasonalContext
                                        LINGO: Pinoy TikTok Brainrot / Conyo (rizz, aura, dasurv, lock-in, forda ferson, yarn, no cap).
                                        TASK: Output viral TikTok metadata. 
                                        STRICT FORMAT:
                                        TAGLISH_CAPTIONS: [C1, C2, C3]
                                        TAGLISH_TAGS: [#LolaDrifting #PinoyBrainrot]
                                        ENGLISH_CAPTIONS: [E1, E2]
                                        ENGLISH_TAGS: [#GrandmaChaos]
                                        OVERLAY: [PEAK BRAINROT HOOK - ALL CAPS - TAGLISH OK]
                                        MUSIC: [Style]
                                    """.trimIndent()

                                    val response = withContext(Dispatchers.IO) { model.generateContent(content { text(masterPrompt) }) }
                                    val rawText = response.text ?: ""

                                    taglishCaptions = Regex("TAGLISH_CAPTIONS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
                                    taglishHashtags = Regex("TAGLISH_TAGS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: ""
                                    englishCaptions = Regex("ENGLISH_CAPTIONS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
                                    englishHashtags = Regex("ENGLISH_TAGS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: ""
                                    overlaySuggestion = Regex("OVERLAY: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: Regex("OVERLAY: (.*)").find(rawText)?.groupValues?.get(1) ?: "PEAK AURA MOMENT"
                                    musicTips = Regex("MUSIC: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: "Budots Remix"

                                    SlopLogger.log("SUCCESS: Relay #${nextIndex + 1} delivered."); success = true
                                } catch (e: Exception) {
                                    if (e.message?.contains("quota") == true) SlopLogger.log("RELAY: #${nextIndex + 1} Depleted.")
                                    else { SlopLogger.log("ERR: ${e.message}"); break }
                                }
                            }
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

                if (taglishCaptions.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("🎬 DIRECTOR'S NOTES", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("OVERLAY: $overlaySuggestion", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("MUSIC: $musicTips", color = Color.Cyan, fontSize = 12.sp)

                            Spacer(Modifier.height(8.dp))
                            val currentOptions = if (viewMode == "TAGLISH") taglishCaptions else englishCaptions
                            val currentTags = if (viewMode == "TAGLISH") taglishHashtags else englishHashtags

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                currentOptions.forEach { caption ->
                                    Button(
                                        onClick = {
                                            directorText = "$caption $currentTags"
                                            clipboardManager.setText(AnnotatedString(directorText))
                                            SlopLogger.log("UI: Hook Copied.")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                    ) { Text(caption, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { if (!isLoggedIn) onLoginClick() else onUploadTrigger(selectedUri!!, directorText) },
                    modifier = Modifier.fillMaxWidth().height(55.dp),
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
                authManager.saveApiKeys(newKeys)
                authManager.savePrompts(newPrompt)
                geminiKeyPool = newKeys
                systemPrompt = newPrompt
                SlopLogger.log("SYSTEM: Engine Updated.")
                showEngineRoom = false
            },
            onDismiss = { showEngineRoom = false }
        )
    }
}