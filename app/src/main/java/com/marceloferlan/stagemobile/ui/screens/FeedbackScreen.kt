package com.marceloferlan.stagemobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.marceloferlan.stagemobile.viewmodel.FeedbackViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    viewModel: FeedbackViewModel,
    connectedMidiDevices: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val user = FirebaseAuth.getInstance().currentUser
    val autoName = user?.displayName ?: "Usuário"
    val autoEmail = user?.email ?: "Não logado"

    var phone by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    
    val types = listOf("Bug / Falha", "Melhoria / Evolução", "Nova Funcionalidade")
    var selectedType by remember { mutableStateOf(types[0]) }
    var expandedType by remember { mutableStateOf(false) }

    val features = listOf(
        // Canais e Instrumentos
        "Mixer: Adicionar canais",
        "Mixer: Painel LCD do Canal (Nomes dos Instrumentos)",
        "Mixer: Carregar canal (Carregar SF2)",
        "Mixer: Limpar canal (Descarregar SF2)",
        "Mixer: Volume do canal / Faders",
        "Mixer: Armar / Mute do canal",
        "Mixer: Personalizar / Colorir canal",
        "Mixer: Remover canal",
        "Mixer: Controle de Oitavas",
        "Mixer: Transpose",
        "Mixer: Panic Button (Reset de Vozes)",
        "Mixer: Filtros MIDI do canal (CCs / canais MIDI)",
        "Mixer: MIDI Learn",
        "Mixer: Selecionar/Alterar Set Stage",
        
        // Efeitos Digitais no Rack
        "Efeitos: Reverb",
        "Efeitos: Delay",
        "Efeitos: Tap Delay (Mixer)",
        "Efeitos: Chorus",
        "Efeitos: Low Pass",
        "Efeitos: High Pass",
        "Efeitos: Limiter Master / Saturador (Punch)",
        "Efeitos: Equalizador",
        "Efeitos: Compressor",
        
        // Arquivos e Sets
        "Biblioteca de SoundFonts",
        "Importar novo SF2",

        // Áudio & Controles do SO
        "Configurações Globais: Controladores MIDI USB",
        "Configurações Globais: Interfaces de Áudio USB Externas",
        "Configurações Globais: Driver de Áudio",
        "Configurações Globais: Controle de buffer",
        "Configurações Globais: Taxa de amostragem (Hz)",
        "Configurações Globais: Driver de Áudio/Modo de Saída",
        "Configurações Globais: Interpolação",
        "Configurações Globais: Polifonia",
        "Configurações Globais: Curva de velocity",

        // Telas Secundárias
        "Set Stages",
        "Teclado Virtual Interativo",
        "Drumpads (Samplers Visuais)",
        "Pads Contínuos (Sustain Hold)",
        
        // Acesso
        "Cadastro/Login/Autenticação no App",
     
        // Interface e UI
        "Interface gráfica/elementos visuais",
        "Responsividade UI",

        // Geral
        "Outros / Geral"
    )
    var selectedFeature by remember { mutableStateOf(features[0]) }
    var expandedFeature by remember { mutableStateOf(false) }

    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val submitResult by viewModel.submitResult.collectAsState()

    LaunchedEffect(submitResult) {
        if (submitResult?.isSuccess == true) {
            viewModel.clearResult()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF131313) // Fundo Escuro StageMobile
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enviar Feedback",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Seus logs de hardware, drivers e conexões MIDI serão anexados automaticamente para ajudar nossa análise.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // IDENTITY (Read-only)
                    OutlinedTextField(
                        value = "$autoName ($autoEmail)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Músico (Automático)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.LightGray,
                            disabledBorderColor = Color.DarkGray,
                            disabledLabelColor = Color.Gray
                        ),
                        enabled = false
                    )

                    // WHATSAPP
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("WhatsApp / Celular (Opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = feedbackTextFieldColors()
                    )

                    // FEEDBACK TYPE
                    ExposedDropdownMenuBox(
                        expanded = expandedType,
                        onExpandedChange = { expandedType = !expandedType }
                    ) {
                        OutlinedTextField(
                            value = selectedType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de Solicitação") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedType) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = feedbackTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedType,
                            onDismissRequest = { expandedType = false }
                        ) {
                            types.forEach { selection ->
                                DropdownMenuItem(
                                    text = { Text(selection) },
                                    onClick = {
                                        selectedType = selection
                                        expandedType = false
                                    }
                                )
                            }
                        }
                    }

                    // FEATURE TAG
                    ExposedDropdownMenuBox(
                        expanded = expandedFeature,
                        onExpandedChange = { expandedFeature = !expandedFeature }
                    ) {
                        OutlinedTextField(
                            value = selectedFeature,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Área Afetada") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedFeature) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = feedbackTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFeature,
                            onDismissRequest = { expandedFeature = false }
                        ) {
                            features.forEach { selection ->
                                DropdownMenuItem(
                                    text = { Text(selection) },
                                    onClick = {
                                        selectedFeature = selection
                                        expandedFeature = false
                                    }
                                )
                            }
                        }
                    }

                    // DETAILS
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Descreva detalhadamente...") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        maxLines = 10,
                        colors = feedbackTextFieldColors()
                    )

                    // ERRO RESULT
                    if (submitResult?.isFailure == true) {
                        Text(
                            text = submitResult?.exceptionOrNull()?.message ?: "Erro desconhecido",
                            color = Color(0xFFFF6B6B),
                            fontSize = 14.sp
                        )
                    }
                }

                // FOOTER / CTA
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.sendFeedback(
                            context = context,
                            type = selectedType,
                            featureTag = selectedFeature,
                            details = details,
                            phone = phone,
                            connectedMidiDevices = connectedMidiDevices
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isSubmitting && details.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26C6DA))
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ENVIAR DIAGNÓSTICO", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun feedbackTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF26C6DA),
    unfocusedBorderColor = Color(0x4AFFFFFF),
    focusedLabelColor = Color(0xFF26C6DA),
    unfocusedLabelColor = Color.Gray,
    focusedTrailingIconColor = Color.Gray,
    unfocusedTrailingIconColor = Color.Gray,
    cursorColor = Color(0xFF26C6DA)
)
