package eureto.opendoor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eureto.opendoor.ui.components.AppBackground
import eureto.opendoor.ui.theme.BlazeOrange
import eureto.opendoor.ui.theme.CardBg
import eureto.opendoor.ui.theme.JetBlack

data class BluetoothDeviceInfo(
    val name: String,
    val address: String
)

@Composable
fun BluetoothDeviceScreen(
    devices: List<BluetoothDeviceInfo>,
    selectedDevices: Map<String, String>,
    onDeviceClick: (BluetoothDeviceInfo) -> Unit,
    onSaveClick: () -> Unit
) {
    AppBackground(scrollable = false) {
        Spacer(modifier = Modifier.height(16.dp))

        // Nagłówek
        Text(
            text = "Wybór urządzeń Bluetooth",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = JetBlack
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Zaznacz urządzenia, które reprezentują Twoje samochody.\nPamiętaj aby zaznaczyć odpowiedni obszar na mapie.",
            fontSize = 16.sp,
            color = JetBlack,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Lista urządzeń Bluetooth
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Brak sparowanych urządzeń Bluetooth",
                        color = JetBlack,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(devices) { device ->
                        val isSelected = selectedDevices.containsKey(device.address)
                        val cardBgColor = if (isSelected) Color(0xFFFFCC80) else Color.White

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceClick(device) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = device.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JetBlack
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = device.address,
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Przycisk Zapisz
        Button(
            onClick = onSaveClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlazeOrange),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "Zapisz",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
