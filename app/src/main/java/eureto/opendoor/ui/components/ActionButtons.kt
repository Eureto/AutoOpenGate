package eureto.opendoor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eureto.opendoor.ui.theme.BlazeOrange
import eureto.opendoor.ui.theme.CardBg
import eureto.opendoor.ui.theme.JetBlack

@Composable
fun ActionButtonWithInfoBlock(
    text: String,
    onClick: () -> Unit,      // Co robi główny przycisk
    onInfoClick: () -> Unit   // Co robi przycisk (i)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Główny pomarańczowy przycisk
        Button(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlazeOrange),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(text = text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Przycisk informacji (i)
        IconButton(onClick = onInfoClick) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Informacje",
                tint = JetBlack
            )
        }
    }
}

//Przycisk w formie obramowanej pigułki z jasnym tłem i cieniem.
@Composable
fun PillButtonBlock(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(2.dp, RoundedCornerShape(25.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(25.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Color(0xFFD4E157)) // Jasnozielona ramka
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = JetBlack
            )
        }
    }
}

//Niestandardowy przycisk-suwak, który zmienia stan po kliknięciu.
@Composable
fun ManualSwitchBlock(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit // Zwraca nowy stan włączenia (true/false)
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(BlazeOrange)
            .clickable { onToggle(!isOn) }, // Kliknięcie odwraca stan
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Suwak / Gałka
            Box(
                modifier = Modifier
                    .width(65.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFFFFAB91))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = if (isOn) "Wyłącz urządzenie" else "Włącz urządzenie",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}