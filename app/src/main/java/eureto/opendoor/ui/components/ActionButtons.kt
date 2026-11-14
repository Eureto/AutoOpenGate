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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eureto.opendoor.ui.theme.BlazeOrange
import eureto.opendoor.ui.theme.BlueSlate
import eureto.opendoor.ui.theme.CardBg
import eureto.opendoor.ui.theme.JetBlack

@Composable
fun ActionButtonWithInfoBlock(
    isSet: Boolean,
    text: String,
    onClick: () -> Unit,      // Co robi główny przycisk
    onInfoClick: () -> Unit   // Co robi przycisk (i)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSet) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Skonfigurowano",
                tint = Color(0xFF4CAF50),
                modifier = Modifier
                    .padding(end = 0.dp)
                    .size(26.dp)
            )
        }
        // Główny pomarańczowy przycisk
        Button(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSet) Color.Transparent else BlazeOrange.copy(alpha = 0.5f),
                contentColor = JetBlack,
                disabledContainerColor = Color.Transparent
            ),

            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (isSet) {
                    Text(
                        text = "›",
                        textAlign = TextAlign.Center,
                        color = if (isSet) Color.Gray else Color.Black,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .background(
                                color = BlazeOrange,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Ustaw",
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Przycisk informacji (i)
        IconButton(onClick = onInfoClick) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Informacje",
                tint = Color.Gray
            )
        }
    }
}

//Przycisk w formie obramowanej pigułki z jasnym tłem i cieniem.
@Composable
fun PillButtonBlock(
    isConfigurationSet: Boolean,
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
        border = BorderStroke(1.dp, if (isConfigurationSet) Color(0xFFD4E157) else BlazeOrange) // Zielona, gdy automatyka działa; pomarańczowa, gdy nie
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if(isConfigurationSet) JetBlack else Color.Gray
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
                .width(80.dp)
                .height(80.dp)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .background(Color.White, RoundedCornerShape(20.dp))

        )
    }
}