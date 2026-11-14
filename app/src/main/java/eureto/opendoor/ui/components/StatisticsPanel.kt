package eureto.opendoor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eureto.opendoor.ui.theme.BlueSlate
import eureto.opendoor.ui.theme.CardBg
import eureto.opendoor.ui.theme.JetBlack

private val StatusGreen = Color(0xFF2E7D32)
private val DividerColor = Color(0xFFDCE5C6)
private val MutedText = Color(0xFF8A9284)

/**
 * Karta statusu automatyki — 3 wiersze: Bluetooth, Strefa, Ostatnia akcja.
 *
 * @param bluetoothConnected true = "Połączono", false = "Brak połączenia", null = "Brak danych"
 * @param zoneRadiusMeters promień strefy w metrach, null = "Brak danych"
 * @param lastActionText gotowy tekst, np. "Otwarto dziś, 18:42", null = "Brak akcji"
 */
@Composable
fun AutomationStatusCard(
    bluetoothConnected: Boolean? = null,
    zoneRadiusMeters: Int? = null,
    lastActionText: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {

            StatusRow(
                icon = Icons.Filled.Bluetooth,
                iconTint = StatusGreen,
                label = "Bluetooth",
                value = when (bluetoothConnected) {
                    true -> "Połączono"
                    false -> "Brak połączenia"
                    null -> "Brak danych"
                },
                valueColor = when (bluetoothConnected) {
                    true -> StatusGreen
                    false -> JetBlack
                    null -> MutedText
                }
            )

            HorizontalDivider(color = DividerColor, thickness = 1.dp)

            StatusRow(
                icon = Icons.Outlined.Place,
                iconTint = StatusGreen,
                label = "Strefa",
                value = zoneRadiusMeters?.let { "promień $it m" } ?: "Brak danych",
                valueColor = if (zoneRadiusMeters != null) JetBlack else MutedText
            )

            HorizontalDivider(color = DividerColor, thickness = 1.dp)

            StatusRow(
                icon = Icons.Outlined.Schedule,
                iconTint = BlueSlate,
                label = "Ostatnia akcja",
                value = lastActionText ?: "Brak akcji",
                valueColor = if (lastActionText != null) JetBlack else MutedText
            )
        }
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            color = JetBlack,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AutomationStatusCardPreview() {
    AutomationStatusCard(
        bluetoothConnected = true,
        zoneRadiusMeters = 150,
        lastActionText = "Otwarto dziś, 18:42",
        modifier = Modifier.padding(16.dp)
    )
}