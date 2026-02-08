package com.lola.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineRoom(
    currentKeys: String,
    currentPrompts: String,
    logs: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var keys by remember { mutableStateOf(currentKeys) }
    var prompts by remember { mutableStateOf(currentPrompts) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1C1E)) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("⚙️ ENGINE ROOM", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = keys,
                onValueChange = { keys = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                label = { Text("GEMINI 3 KEY POOL (One per line)") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = prompts,
                onValueChange = { prompts = it },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                label = { Text("LOLA PROMPT CONFIG") }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("LIVE TELEMETRY (LOLA_DEBUG)", fontSize = 12.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)

            Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.Black, RoundedCornerShape(4.dp)).padding(8.dp)) {
                Text(
                    text = logs,
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onSave(keys, prompts) },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("SAVE & REBOOT ENGINE", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}