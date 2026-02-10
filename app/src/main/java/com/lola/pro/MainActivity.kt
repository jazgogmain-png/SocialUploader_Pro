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
    private val PREFS_NAME = "LolaVault"
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

    private fun checkLoginStatus() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isLoggedIn = !prefs.getString("ACCESS_TOKEN", null).isNullOrEmpty()
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
        SlopLogger.log("AUTH: Handshake requested.")
        Toast.makeText(this, "Connect TikTok logic preserved.", Toast.LENGTH_SHORT).show()
    }

    private fun startUploadWorker(uri: Uri, caption: String) {
        if (uploadCount >= 5) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = Data.Builder()
            .putString("TOKEN", prefs.getString("ACCESS_TOKEN", ""))
            .putString("VIDEO_URI", uri.toString())
            .putString("CAPTION", caption)
            .build()
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<UploadWorker>().setInputData(data).build())
        uploadCount++
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

    // UI Metadata Lists
    var taglishCaptions by remember { mutableStateOf(listOf<String>()) }
    var taglishHashtags by remember { mutableStateOf("") }
    var englishCaptions by remember { mutableStateOf(listOf<String>()) }
    var englishHashtags by remember { mutableStateOf("") }

    var overlayText by remember { mutableStateOf("WAIT FOR IT") }
    var overlayTime by remember { mutableStateOf("1.5") }
    var overlayPlacement by remember { mutableStateOf("MIDDLE") }
    var overlayStyle by remember { mutableStateOf("IMPACT_WHITE") }

    var viewMode by remember { mutableStateOf("TAGLISH") }
    var directorText by remember { mutableStateOf("") }
    var isCooking by remember { mutableStateOf(false) }
    var isGeminiThinking by remember { mutableStateOf(false) }

    var geminiKeyPool by remember { mutableStateOf(BuildConfig.GEMINI_API_KEY) }
    var systemPrompt by remember { mutableStateOf("Professional stunt production. Grandma Drifting actor.") }
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFF0D0D0D))
                    .border(width = 1.dp, color = Color(0xFF1E1E1E), shape = RoundedCornerShape(0.dp))
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(SlopLogger.logs.size) { if (SlopLogger.logs.isNotEmpty()) listState.animateScrollToItem(SlopLogger.logs.size - 1) }
                LazyColumn(state = listState, modifier = Modifier.padding(8.dp).fillMaxSize()) {
                    items(SlopLogger.logs) { log ->
                        val color = if (log.contains("ERR") || log.contains("FAILED")) Color.Red else Color(0xFF00FF00)
                        Text(log, color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp)
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = if (uploadCount >= 5) Color.Red else if (isLoggedIn) Color.Green else Color.Yellow
                    Badge(containerColor = statusColor) {
                        Text(if (uploadCount >= 5) "QUOTA DEPLETED" else if (isLoggedIn) "SENTINEL ACTIVE" else "OFFLINE", color = Color.Black)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Salutes: $uploadCount/5", color = Color.Gray, fontSize = 12.sp)
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedUri == null) Color(0xFF2196F3) else Color(0xFF4CAF50))
                ) {
                    Text(if (selectedUri == null) "FIND THE SLOP" else "SLOP LOADED ✅")
                }

                Spacer(Modifier.height(20.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = viewMode == "TAGLISH", onClick = { viewMode = "TAGLISH"; SlopLogger.log("UI: Set -> Taglish") }, label = { Text("🇵🇭 Lola Mix") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Yellow))
                    FilterChip(selected = viewMode == "ENGLISH", onClick = { viewMode = "ENGLISH"; SlopLogger.log("UI: Set -> English") }, label = { Text("🌎 Int'l Hook") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Cyan))
                }

                Button(
                    onClick = {
                        if (selectedUri == null) return@Button
                        scope.launch {
                            isGeminiThinking = true
                            val keys = geminiKeyPool.split(Regex("[\\n\\r,]+")).map { it.trim() }.filter { it.isNotEmpty() }
                            val activeKey = if (keys.isNotEmpty()) keys.random() else ""

                            SlopLogger.log("GEMINI: Bartering with G3 FLASH (Pool: ${keys.size})...")

                            try {
                                val safety = listOf(
                                    SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                                    SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
                                )
                                val model = GenerativeModel(modelName = "gemini-3-flash-preview", apiKey = activeKey, safetySettings = safety)

                                val masterPrompt = """
                                    CONTEXT: $systemPrompt
                                    TASK: Analyze video. Output viral metadata. 
                                    STYLE: Fresh Filipino/Conyo Taglish + mandatory emojis 🔥.
                                    STRICT OUTPUT FORMAT:
                                    TAGLISH_CAPTIONS: [C1, C2, C3, C4, C5, C6]
                                    TAGLISH_TAGS: [#Tag1 #Tag2 #Tag3 #Tag4 #LolaDrifting]
                                    ENGLISH_CAPTIONS: [E1, E2, E3]
                                    ENGLISH_TAGS: [#Tag1 #Tag2 #Tag3 #Tag4 #LolaDrifting]
                                    OVERLAY_TEXT: [Viral Hook]
                                    OVERLAY_TIME: [1.5]
                                    OVERLAY_PLACEMENT: [MIDDLE]
                                    OVERLAY_STYLE: [IMPACT_WHITE]
                                """.trimIndent()

                                val response = model.generateContent(content { text(masterPrompt) })
                                val rawText = response.text ?: ""

                                Log.d("lola_gemini", "RAW: $rawText")

                                // PARSER (Capped for Scroll-Safety)
                                val taglishData = Regex("TAGLISH_CAPTIONS: \\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(rawText)?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
                                taglishCaptions = taglishData.take(6)
                                taglishHashtags = Regex("TAGLISH_TAGS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: ""

                                val englishData = Regex("ENGLISH_CAPTIONS: \\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(rawText)?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()
                                englishCaptions = englishData.take(3)
                                englishHashtags = Regex("ENGLISH_TAGS: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: ""

                                overlayText = Regex("OVERLAY_TEXT: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: Regex("OVERLAY_TEXT: (.*)").find(rawText)?.groupValues?.get(1) ?: "WAIT FOR IT"
                                overlayTime = Regex("OVERLAY_TIME: \\[(.*?)\\]").find(rawText)?.groupValues?.get(1) ?: Regex("OVERLAY_TIME: (.*)").find(rawText)?.groupValues?.get(1) ?: "1.5"

                                SlopLogger.log("SUCCESS: Byte-buffer refined by Elf (Keys: ${keys.size})")
                            } catch (e: Exception) { SlopLogger.log("ERR: Elf fell asleep: ${e.message}") }
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

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = 3
                    ) {
                        currentOptions.forEachIndexed { index, caption ->
                            Button(
                                onClick = {
                                    val cleanCaption = caption.replace("[", "").replace("]", "").trim()
                                    directorText = "$cleanCaption $currentTags"
                                    clipboardManager.setText(AnnotatedString(directorText))
                                    SlopLogger.log("UI: Option #${index+1} selected.")
                                },
                                modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("#${index + 1}")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = directorText,
                        onValueChange = { directorText = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp).padding(top = 8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.LightGray, focusedContainerColor = Color(0xFF111111))
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isCooking = true
                            SlopLogger.log("KITCHEN: Hardening text for burn...")
                            val recipe = VideoKitchen.SlopRecipe(directorText, overlayText, overlayTime, overlayPlacement, overlayStyle)
                            cookedUri = withContext(Dispatchers.IO) { VideoKitchen.burnSlop(context, selectedUri!!, recipe) { msg -> SlopLogger.log(msg) } }
                            isCooking = false
                        }
                    },
                    enabled = selectedUri != null && !isCooking && directorText.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text(if (isCooking) "BURNING..." else "🔥 THE KITCHEN (Final Burn)", color = Color.Black)
                }

                if (cookedUri != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        try {
                            val uri = FileProvider.getUriForFile(context, "com.lola.pro.fileprovider", File(cookedUri!!.path!!))
                            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "video/mp4"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            context.startActivity(Intent.createChooser(intent, "Verify the Slop"))
                        } catch (e: Exception) { SlopLogger.log("PREVIEW_ERR: ${e.message}") }
                    }, modifier = Modifier.fillMaxWidth()) { Text("👁️ PREVIEW SLOP", color = Color.Cyan) }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { if (!isLoggedIn) onLoginClick() else onUploadTrigger(cookedUri!!, directorText) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
                    ) { Text(if (!isLoggedIn) "🔑 CONNECT TO TIKTOK" else "🦅 RELEASE THE BIRDS", fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }
    }

    if (showEngineRoom) {
        EngineRoom(
            currentKeys = geminiKeyPool,
            currentPrompts = systemPrompt,
            logs = SlopLogger.logs.takeLast(6).joinToString("\n"),
            onSave = { newKeys, newPrompt ->
                geminiKeyPool = newKeys
                systemPrompt = newPrompt
                SlopLogger.log("SYSTEM: Key-Pool Relay rebooted.")
                showEngineRoom = false
            },
            onDismiss = { showEngineRoom = false }
        )
    }
}