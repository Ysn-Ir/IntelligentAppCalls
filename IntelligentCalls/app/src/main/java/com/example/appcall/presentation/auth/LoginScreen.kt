package com.example.appcall.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.presentation.theme.DarkIndigo
import com.example.appcall.presentation.theme.ElectricViolet
import com.example.appcall.presentation.theme.NeonTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("test@example.com") }
    var password by remember { mutableStateOf("password") }

    val prefs = remember { context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE) }
    var serverUrl by remember {
        mutableStateOf(prefs.getString("custom_base_url", "http://127.0.0.1:8000") ?: "http://127.0.0.1:8000")
    }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            viewModel.resetState()
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkIndigo, Color(0xFF070B19))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0x1F293754)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Intelligent Calls",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonTeal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connexion au tableau de bord",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ── SERVER IP CONFIGURATION CARD ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x330F172A))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SERVEUR BACKEND", color = NeonTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            if (testStatus != null) {
                                Text(testStatus!!, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = {
                                serverUrl = it
                                prefs.edit().putString("custom_base_url", it.trim()).apply()
                            },
                            label = { Text("URL Serveur", color = Color.Gray, fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonTeal,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    serverUrl = "http://127.0.0.1:8000"
                                    prefs.edit().putString("custom_base_url", "http://127.0.0.1:8000").apply()
                                    android.widget.Toast.makeText(context, "Mode USB activé (127.0.0.1:8000)", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("🔌 USB (127.0.0.1)", color = NeonTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    serverUrl = "http://192.168.1.177:8000"
                                    prefs.edit().putString("custom_base_url", "http://192.168.1.177:8000").apply()
                                    android.widget.Toast.makeText(context, "Mode Wi-Fi activé (192.168.1.177:8000)", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("📶 Wi-Fi (192.168.1.177)", color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    isTesting = true
                                    testStatus = "⏳ Test..."
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            var testUrl = serverUrl.trim()
                                            if (!testUrl.startsWith("http://") && !testUrl.startsWith("https://")) {
                                                testUrl = "http://$testUrl"
                                            }
                                            if (!testUrl.endsWith("/")) testUrl += "/"
                                            val url = java.net.URL("${testUrl}api/v1/calls")
                                            val conn = url.openConnection() as java.net.HttpURLConnection
                                            conn.connectTimeout = 3000
                                            conn.readTimeout = 3000
                                            conn.requestMethod = "GET"
                                            val code = conn.responseCode
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isTesting = false
                                                if (code in 200..499) {
                                                    testStatus = "🟢 En ligne (HTTP $code)"
                                                    android.widget.Toast.makeText(context, "✅ Backend connecté !", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    testStatus = "⚠️ Code $code"
                                                }
                                            }
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isTesting = false
                                                testStatus = "🔴 Erreur: ${e.javaClass.simpleName}"
                                                android.widget.Toast.makeText(context, "❌ Erreur: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(0.8f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("🔄 Test", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricViolet,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricViolet,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState is LoginUiState.Error) {
                    Text(
                        text = (uiState as LoginUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Button(
                    onClick = { viewModel.login(email, password) },
                    enabled = uiState !is LoginUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet
                    )
                ) {
                    if (uiState is LoginUiState.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("SE CONNECTER", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = onNavigateToRegister) {
                    Text("Créer un compte", color = NeonTeal, fontSize = 13.sp)
                }
            }
        }
    }
}
