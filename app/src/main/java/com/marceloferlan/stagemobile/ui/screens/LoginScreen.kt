package com.marceloferlan.stagemobile.ui.screens

import android.app.Activity
import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.marceloferlan.stagemobile.R
import com.marceloferlan.stagemobile.data.AuthRepository
import com.marceloferlan.stagemobile.utils.UiUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Firebase Console → Authentication → Sign-in method → Google → Web client ID
private const val WEB_CLIENT_ID = "251104267267-71jjev6aluq5c8fqkirvphra9raqon33.apps.googleusercontent.com"

private val ColorPrimary = Color(0xFF26C6DA) // Ciano/Teal: Contraste vibrante estilo sintetizador synthwave
private val ColorBackground = Color(0xFF131313)
private val ColorSurface = Color(0x66000000) 
private val ColorSurfaceElevated = Color(0x1AFFFFFF) // Branco translúcido bem suave (mais light)
private val ColorTextPrimary = Color.White
private val ColorTextSecondary = Color(0xFFC0C0C0)
private val ColorError = Color(0xFFFF6B6B) 
private val ColorDivider = Color(0x4AFFFFFF) 


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit
) {
    val isTablet = UiUtils.rememberIsTablet()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Estado do formulário
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Entrar, 1 = Cadastrar
    var signUpStep by remember { mutableIntStateOf(0) } // 0 = Passo Identidade, 1 = Passo Senhas
    var firstName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Estado de UI
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showVerificationDialog by remember { mutableStateOf(false) }

    // Validações inline
    val emailError = remember(email) {
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches())
            "E-mail inválido" else null
    }
    val passwordError = remember(password, selectedTab) {
        if (password.isNotEmpty() && selectedTab == 1 && password.length < 6)
            "Mínimo 6 caracteres" else null
    }
    val firstNameError = remember(firstName, selectedTab) {
        if (selectedTab == 1 && firstName.trim().isEmpty())
            "Nome obrigatório" else null
    }
    val confirmPasswordError = remember(confirmPassword, password, selectedTab) {
        if (selectedTab == 1 && confirmPassword.isNotEmpty() && confirmPassword != password)
            "As senhas não coincidem" else null
    }

    val canSubmit = if (selectedTab == 0) {
        email.isNotEmpty() && password.isNotEmpty() && emailError == null && !isLoading
    } else {
        if (signUpStep == 0) {
            email.isNotEmpty() && firstName.trim().isNotEmpty() && emailError == null && firstNameError == null && !isLoading
        } else {
            password.isNotEmpty() && passwordError == null && confirmPassword == password && confirmPasswordError == null && !isLoading
        }
    }

    // ─── Handlers ───────────────────────────────────────────────────────────

    fun handleEmailAuth() {
        if (!canSubmit) return
        errorMessage = null
        isLoading = true
        focusManager.clearFocus()
        scope.launch {
            val result = if (selectedTab == 0) {
                authRepository.signInWithEmail(email, password)
            } else {
                authRepository.createAccountWithEmail(firstName, email, password)
            }
            isLoading = false
            result.fold(
                onSuccess = { user -> 
                    if (user.isEmailVerified) {
                        onAuthSuccess()
                    } else {
                        showVerificationDialog = true
                    }
                },
                onFailure = { errorMessage = it.message }
            )
        }
    }

    fun handleGoogleSignIn() {
        errorMessage = null
        isLoading = true
        scope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val response = credentialManager.getCredential(
                    request = request,
                    context = context as Activity
                )
                val googleCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
                val result = authRepository.signInWithGoogle(googleCredential.idToken)
                isLoading = false
                result.fold(
                    onSuccess = { onAuthSuccess() },
                    onFailure = { errorMessage = it.message }
                )
            } catch (e: GetCredentialException) {
                isLoading = false
                errorMessage = when {
                    e.message?.contains("CANCELED") == true -> null // Usuário cancelou
                    else -> "Falha no login com Google. Tente novamente."
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Falha no login com Google. Tente novamente."
            }
        }
    }

    // ─── Layout ─────────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF110B4A), Color(0xFF8F3131))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val contentModifier = if (isTablet) {
            Modifier
                .width(480.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 48.dp)
        } else {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp)
        }

        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Logo / Header ──────────────────────────────────────────────
            Spacer(modifier = Modifier.height(if (isTablet) 0.dp else 16.dp))

            Image(
                painter = painterResource(id = R.drawable.logo_topbar),
                contentDescription = "App Logo",
                modifier = Modifier
                    .width(180.dp)
                    .height(98.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Seu Stage onde você estiver!",
                color = ColorTextSecondary, // Mantendo a cor consistente do tema para o texto secundário
                fontSize = if (isTablet) 18.sp else 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Card de Login ──────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ColorSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (isTablet) 32.dp else 24.dp,
                        vertical = 24.dp
                    )
                ) {
                    // Tab Entrar / Cadastrar
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = ColorSurfaceElevated,
                        contentColor = ColorPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = ColorPrimary
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                signUpStep = 0
                                errorMessage = null
                            },
                            text = {
                                Text(
                                    "ENTRAR",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == 0) ColorPrimary else ColorTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                signUpStep = 0
                                errorMessage = null
                            },
                            text = {
                                Text(
                                    "CADASTRAR",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == 1) ColorPrimary else ColorTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Mensagem de erro global
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorError.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = ColorError,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = if (selectedTab == 0) "LOGIN" else if (signUpStep == 0) "SIGNUP_1" else "SIGNUP_2",
                        transitionSpec = {
                            if (targetState == "SIGNUP_2" && initialState == "SIGNUP_1") {
                                slideInHorizontally { width -> width } + fadeIn() togetherWith slideOutHorizontally { width -> -width } + fadeOut()
                            } else if (targetState == "SIGNUP_1" && initialState == "SIGNUP_2") {
                                slideInHorizontally { width -> -width } + fadeIn() togetherWith slideOutHorizontally { width -> width } + fadeOut()
                            } else {
                                fadeIn() togetherWith fadeOut()
                            }
                        },
                        label = "form_animation"
                    ) { state ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Campo Primeiro Nome
                            if (state == "SIGNUP_1") {
                                OutlinedTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it; errorMessage = null },
                                    label = { Text("Primeiro Nome") },
                                    leadingIcon = { Icon(Icons.Default.Person, null, tint = ColorTextSecondary) },
                                    isError = firstNameError != null,
                                    supportingText = firstNameError?.let { { Text(it, color = ColorError) } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = authTextFieldColors()
                                )
                            }
                            
                            // Campo E-mail
                            if (state == "LOGIN" || state == "SIGNUP_1") {
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it.trim(); errorMessage = null },
                                    label = { Text("E-mail") },
                                    leadingIcon = { Icon(Icons.Default.Email, null, tint = ColorTextSecondary) },
                                    isError = emailError != null,
                                    supportingText = emailError?.let { { Text(it, color = ColorError) } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (state == "SIGNUP_1") ImeAction.Done else ImeAction.Next),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                        onDone = { if (state == "SIGNUP_1") focusManager.clearFocus() }
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = authTextFieldColors()
                                )
                            }
                            
                            // Campo Senhas
                            if (state == "LOGIN" || state == "SIGNUP_2") {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it; errorMessage = null },
                                    label = { Text("Senha") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = ColorTextSecondary) },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = ColorTextSecondary)
                                        }
                                    },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    isError = passwordError != null,
                                    supportingText = passwordError?.let { { Text(it, color = ColorError) } },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (state == "SIGNUP_2") ImeAction.Next else ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                        onDone = { handleEmailAuth() }
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = authTextFieldColors()
                                )

                                if (state == "SIGNUP_2") {
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = { confirmPassword = it; errorMessage = null },
                                        label = { Text("Confirmar Senha") },
                                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = ColorTextSecondary) },
                                        trailingIcon = {
                                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                                Icon(if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = ColorTextSecondary)
                                            }
                                        },
                                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        isError = confirmPasswordError != null,
                                        supportingText = confirmPasswordError?.let { { Text(it, color = ColorError) } },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { handleEmailAuth() }),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = authTextFieldColors()
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Botão principal Dinâmico
                    if (selectedTab == 1 && signUpStep == 1) {
                        // ROW COM VOLTAR E CRIAR CONTA NO FINAL DO ONBOARDING
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { signUpStep = 0 },
                                modifier = Modifier.weight(0.4f).height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ColorDivider)
                            ) {
                                Text("VOLTAR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Button(
                                onClick = { handleEmailAuth() },
                                enabled = canSubmit,
                                modifier = Modifier.weight(0.6f).height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, disabledContainerColor = ColorPrimary.copy(alpha = 0.3f))
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("CRIAR CONTA", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        // BOTÃO PADRÃO FULL WIDTH (ENTRAR ou AVANÇAR)
                        Button(
                            onClick = { 
                                if (selectedTab == 1 && signUpStep == 0) {
                                    signUpStep = 1 // Avança para senhas
                                } else {
                                    handleEmailAuth() // Login comum
                                }
                            },
                            enabled = canSubmit,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, disabledContainerColor = ColorPrimary.copy(alpha = 0.3f))
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = if (selectedTab == 0) "ENTRAR" else "AVANÇAR",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    // Botão Google e Visibilidade Dinâmica
                    AnimatedVisibility(visible = selectedTab == 0) {
                        Column {
                            Spacer(modifier = Modifier.height(20.dp))
                            // Divisor "— OU —"
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = ColorDivider)
                                Text("  OU  ", color = ColorTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                HorizontalDivider(modifier = Modifier.weight(1f), color = ColorDivider)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Botão Google Sign-In
                            OutlinedButton(
                                onClick = { handleGoogleSignIn() },
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (!isLoading) ColorDivider else ColorDivider.copy(alpha = 0.4f))
                            ) {
                                Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Continuar com Google", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Rodapé de informação de privacidade
            Text(
                text = "Suas configurações ficam salvas na nuvem\ne sincronizadas entre seus dispositivos.",
                color = ColorTextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Dialog de Verificação de E-mail (Strict Route) ────────────────
        if (showVerificationDialog) {
            Dialog(onDismissRequest = { 
                showVerificationDialog = false 
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ColorBackground,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Verificar Email",
                            tint = ColorPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Verifique seu E-mail",
                            color = ColorTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Enviamos um link de confirmação para:\n$email\n\nPor favor, valide o seu e-mail para ter acesso aos estúdios do StageMobile.",
                            color = ColorTextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                    user?.reload()?.await()
                                    if (user?.isEmailVerified == true) {
                                        showVerificationDialog = false
                                        onAuthSuccess()
                                    } else {
                                        errorMessage = "E-mail ainda não validado."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("JÁ CONFIRMEI!", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { 
                                showVerificationDialog = false 
                                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                            }
                        ) {
                            Text("Sair", color = ColorTextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ColorTextPrimary,
    unfocusedTextColor = ColorTextPrimary,
    focusedBorderColor = ColorPrimary,
    unfocusedBorderColor = ColorDivider,
    focusedLabelColor = ColorPrimary,
    unfocusedLabelColor = ColorTextSecondary,
    cursorColor = ColorPrimary,
    focusedLeadingIconColor = ColorPrimary,
    errorBorderColor = ColorError,
    errorLabelColor = ColorError
)
