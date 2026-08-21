package com.example.appcall.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.R
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // ── SECRET ADMIN SERVER CONFIG MODAL (5 TAPS ON LOGO) ──
    var logoTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var showServerDialog by remember { mutableStateOf(false) }
    var customServerUrl by remember {
        mutableStateOf(prefs.getString("custom_base_url", "https://intelligent-calls-api.onrender.com") ?: "https://intelligent-calls-api.onrender.com")
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
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Secret 5-tap developer trigger on Logo
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x1A00E5FF))
                        .border(1.dp, NeonTeal.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 800) {
                                logoTapCount++
                                if (logoTapCount >= 5) {
                                    showServerDialog = true
                                    logoTapCount = 0
                                }
                            } else {
                                logoTapCount = 1
                            }
                            lastTapTime = now
                        }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.verbai_logo),
                        contentDescription = "VerbAI call Logo",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "VerbAI call",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonTeal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connexion à votre espace sécurisé",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", color = Color.Gray) },
                    placeholder = { Text("nom@entreprise.com", color = Color.DarkGray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricViolet,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

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

                Spacer(modifier = Modifier.height(20.dp))

                if (uiState is LoginUiState.Error) {
                    Text(
                        text = (uiState as LoginUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Button(
                    onClick = { viewModel.login(email.trim(), password) },
                    enabled = uiState !is LoginUiState.Loading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
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
                        Text("SE CONNECTER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(onClick = onNavigateToRegister) {
                    Text("Pas encore de compte ? Créer un compte", color = NeonTeal, fontSize = 13.sp)
                }
            }
        }
    }

    // ── SECRET SERVER OVERRIDE DIALOG ──
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = {
                Text(
                    text = "⚙️ Configuration Serveur Backend",
                    fontWeight = FontWeight.Bold,
                    color = NeonTeal,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Modifiez l'URL du serveur sans recompiler l'APK. Elle s'appliquera immédiatement et sera sauvegardée.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customServerUrl,
                        onValueChange = { customServerUrl = it },
                        label = { Text("URL Serveur Backend", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonTeal,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                customServerUrl = "https://intelligent-calls-api.onrender.com"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("☁️ Render", fontSize = 11.sp, color = NeonTeal)
                        }
                        Button(
                            onClick = {
                                customServerUrl = "http://127.0.0.1:8000"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text("🔌 USB Local", fontSize = 11.sp, color = Color(0xFF60A5FA))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (testStatus != null) {
                        Text(
                            text = testStatus!!,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (testStatus!!.contains("🟢")) NeonTeal else Color(0xFFF87171)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanUrl = customServerUrl.trim().removeSuffix("/")
                        prefs.edit().putString("custom_base_url", cleanUrl).apply()
                        showServerDialog = false
                        android.widget.Toast.makeText(context, "✅ Serveur mis à jour: $cleanUrl", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                ) {
                    Text("Enregistrer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isTesting = true
                        testStatus = "⏳ Test en cours..."
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                var testUrl = customServerUrl.trim()
                                if (!testUrl.startsWith("http://") && !testUrl.startsWith("https://")) {
                                    testUrl = "https://$testUrl"
                                }
                                if (!testUrl.endsWith("/")) testUrl += "/"
                                val url = java.net.URL("${testUrl}health")
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.connectTimeout = 4000
                                conn.readTimeout = 4000
                                conn.requestMethod = "GET"
                                val code = conn.responseCode
                                withContext(Dispatchers.Main) {
                                    isTesting = false
                                    if (code in 200..299) {
                                        testStatus = "🟢 Connecté avec succès (HTTP $code)"
                                    } else {
                                        testStatus = "⚠️ Réponse HTTP $code"
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isTesting = false
                                    testStatus = "🔴 Erreur: ${e.localizedMessage ?: e.javaClass.simpleName}"
                                }
                            }
                        }
                    }
                ) {
                    Text("Tester", color = NeonTeal)
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp)
        )
    }
}
