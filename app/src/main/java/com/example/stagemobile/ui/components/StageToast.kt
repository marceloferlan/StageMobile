package com.example.stagemobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stagemobile.viewmodel.MixerViewModel
import kotlinx.coroutines.delay

@Composable
fun StageToastHost(viewModel: MixerViewModel?) {
    if (viewModel == null) return
    
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.lastSystemEvent.collect { msg ->
            if (msg.isNotEmpty()) {
                toastMessage = null // Clear first to allow re-trigger on same message
                toastMessage = msg
                delay(3500)
                if (toastMessage == msg) toastMessage = null
            }
        }
    }

    toastMessage?.let { msg ->
        StageToast(
            message = msg,
            onDismiss = { toastMessage = null }
        )
    }
}

@Composable
fun StageToast(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF222222).copy(alpha = 0.9f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .clickable { onDismiss() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = message.uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
