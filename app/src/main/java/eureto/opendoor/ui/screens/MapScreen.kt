package eureto.opendoor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import eureto.opendoor.ui.components.AppBackground
import eureto.opendoor.ui.theme.BlazeOrange
import eureto.opendoor.ui.theme.CardBg
import eureto.opendoor.ui.theme.JetBlack
import org.osmdroid.views.MapView

@Composable
fun MapScreen(
    mapView: MapView,
    radiusText: String,
    onRadiusChange: (String) -> Unit,
    onRadiusDone: () -> Unit,
    onClearClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    AppBackground(scrollable = false) {
        Spacer(modifier = Modifier.height(12.dp))

        // Nagłówek
        Text(
            text = "Konfiguracja obszaru (Geofence)",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = JetBlack
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Pole wprowadzania promienia
        OutlinedTextField(
            value = radiusText,
            onValueChange = onRadiusChange,
            label = { Text("Promień geofence (w metrach)", color = JetBlack) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onRadiusDone() }
            ),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BlazeOrange,
                unfocusedBorderColor = JetBlack,
                focusedLabelColor = BlazeOrange,
                cursorColor = BlazeOrange
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Mapa osmdroid wbudowana przez AndroidView
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            AndroidView(
                factory = { context ->
                    mapView
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dolne przyciski: Wyczyść i Zapisz
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onClearClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = "Wyczyść",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlazeOrange),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text(
                    text = "Zapisz obszar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
