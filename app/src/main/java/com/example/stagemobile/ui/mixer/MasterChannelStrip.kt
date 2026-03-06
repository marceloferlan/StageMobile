package com.example.stagemobile.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip

@Composable
fun MasterChannelStrip(
    volume: Float,
    level: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp) // Sem padding no lado "end" para grudar
            .width(122.dp) // Largura reduzida em 5%
            .fillMaxHeight()
            .background(
                color = Color(0xFF1E1E1E), 
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp) // Canto reto à direita
            )
            .padding(12.dp) // Um pouco mais de espaço interno respirável dado a nova largura
    ) {
        // Label
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(31.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = Color(0xFF2C2C2C),
            tonalElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "MASTER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // FADER + METER
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            VerticalFader(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier
            )

            Spacer(modifier = Modifier.width(4.dp))

            // VU Meter Segmented
            Column(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                val ledCount = 30
                val activeLeds = (level * ledCount).toInt().coerceIn(0, ledCount)

                for (i in 0 until ledCount) {
                    val ledIndexFromBottom = ledCount - 1 - i
                    val isLit = ledIndexFromBottom < activeLeds

                    val ledColor = when {
                        !isLit -> Color(0xFF2C2C2C)
                        i < 4 -> Color(0xFFFF3B30) // Red zone
                        i < 10 -> Color(0xFFFFCC00) // Yellow zone
                        else -> Color(0xFF4CAF50) // Green zone
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 2.dp, vertical = 0.5.dp)
                            .background(ledColor, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }
}
