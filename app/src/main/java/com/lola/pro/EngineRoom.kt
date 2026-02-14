package com.lola.pro

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var keyPool by remember { mutableStateOf(currentKeys) }
    var prompts by remember { mutableStateOf(currentPrompts) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1A1A1A)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("⚙️ ENGINE ROOM", color = Color.Yellow, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            Text("GEMINI KEY POOL (Comma separated)", color = Color.Gray, fontSize = 12.sp)
            OutlinedTextField(
                value = keyPool,
                onValueChange = { keyPool = it },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
            )

            Spacer(Modifier.height(16.dp))

            Text("SYSTEM DIRECTIVE", color = Color.Gray, fontSize = 12.sp)
            OutlinedTextField(
                value = prompts,
                onValueChange = { prompts = it },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onSave(keyPool, prompts) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black)
            ) {
                Text("REBOOT RELAY & SAVE")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}