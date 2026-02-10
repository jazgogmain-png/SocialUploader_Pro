package com.lola.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border // The Iron-Clad Import

@Composable
fun EngineRoom(
    currentKeys: String,
    currentPrompts: String,
    logs: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var keyState by remember { mutableStateOf(currentKeys) }
    var promptState by remember { mutableStateOf(currentPrompts) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0A0A0A)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "🛠️ ENGINE ROOM",
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Text("V2.3 - PANSIT SPEED (MULTILINE RELAY)", color = Color.Gray, fontSize = 12.sp)

                Spacer(Modifier.height(24.dp))

                // --- ROLLING KEY-POOL ---
                Text("GEMINI KEY-POOL (One per line)", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Just paste your stack here. The Elves will handle the rest.", color = Color.DarkGray, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyState,
                    onValueChange = { keyState = it },
                    modifier = Modifier.fillMaxWidth().height(250.dp), // Bigger box for the stack
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.Cyan
                    ),
                    placeholder = { Text("key_alpha\nkey_beta\nkey_gamma...", color = Color.DarkGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Yellow,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedContainerColor = Color(0xFF111111)
                    )
                )

                Spacer(Modifier.height(24.dp))

                // --- SYSTEM PROMPT ---
                Text("JAILBREAK PROTOCOL", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = promptState,
                    onValueChange = { promptState = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.LightGray),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Cyan,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedContainerColor = Color(0xFF111111)
                    )
                )

                Spacer(Modifier.height(30.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onSave(keyState, promptState) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("SAVE & REBOOT", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Text("CANCEL", color = Color.White)
                    }
                }

                Spacer(Modifier.height(40.dp))

                // --- TELEMETRY ---
                Text("RAW TELEMETRY SNIPPET", color = Color.Gray, fontSize = 10.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF1E1E1E))
                        .padding(8.dp)
                ) {
                    Text(
                        logs,
                        color = Color(0xFF00FF00),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}