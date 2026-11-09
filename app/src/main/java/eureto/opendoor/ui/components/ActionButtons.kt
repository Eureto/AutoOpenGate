package eureto.opendoor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eureto.opendoor.ui.theme.BlazeOrange
import eureto.opendoor.ui.theme.BlueSlate
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
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlazeOrange),
            shape = RoundedCornerShape(26.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
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
            .height(56.dp)
            .shadow(2.dp, RoundedCornerShape(28.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(28.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Color(0xFFD4E157)) // Jasnozielona ramka
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = JetBlack
            )
        }
    }
}

//Niestandardowy przycisk-suwak: gałka przesuwa się w prawo, gdy urządzenie jest włączone.
@Composable
fun ManualSwitchBlock(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit // Zwraca nowy stan włączenia (true/false)
) {
    // Kolor tła płynnie przechodzi między stanami zamiast przeskakiwać
    val trackColor by animateColorAsState(
        targetValue = if (isOn) BlazeOrange else BlueSlate,
        label = "trackColor"
    )

    // -1f = gałka przy lewej krawędzi, 1f = przy prawej
    val knobBias by animateFloatAsState(
        targetValue = if (isOn) 1f else -1f,
        label = "knobBias"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(trackColor)
            .toggleable(
                value = isOn,
                onValueChange = onToggle, // Kliknięcie odwraca stan
                role = Role.Switch
            )
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = if (isOn) "Wyłącz urządzenie" else "Włącz urządzenie",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )

        // Suwak / Gałka
        Box(
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = knobBias, verticalBias = 0f))
                .width(68.dp)
                .height(80.dp)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .background(Color.White, RoundedCornerShape(20.dp))
        )
    }
}