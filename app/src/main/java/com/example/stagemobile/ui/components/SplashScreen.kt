package com.example.stagemobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stagemobile.R

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF131313)),
        contentAlignment = Alignment.Center
    ) {
        // 1. O Logo Âncora: Deve estar exatamente no centro para coincidir com o Splash Nativo
        Image(
            painter = painterResource(id = R.drawable.logo_stage_mobile),
            contentDescription = "App Icon",
            modifier = Modifier.size(120.dp)
        )

        // 2. Elementos de Branding: Título e Slogan orbitando o centro
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Título: Stage Mobile
            Text(
                text = "Stage Mobile",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            // Espaçador calculado: Pula o logo central (120dp) + margens de 32dp em cada lado
            Spacer(modifier = Modifier.height(184.dp))

            // Slogan: Seu Stage onde você estiver!
            Text(
                text = "Seu Stage onde você estiver!",
                color = Color(0xFFB0B0B0),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
