package eureto.opendoor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eureto.opendoor.network.model.Device
import eureto.opendoor.ui.components.*
import eureto.opendoor.ui.theme.*

@Composable
fun MainScreen(
    selectedDeviceName: String = "Brak urządzenia",
    isDeviceOn: Boolean = false,
    isMonitoringActive: Boolean = false,
    deviceList: List<Device> = emptyList(),
    onDeviceSelected: (Device) -> Unit = {},
    onSelectBluetoothClicked: () -> Unit = {},
    onToggleDeviceClicked: (Boolean) -> Unit = {},
    onToggleMonitoringClicked: () -> Unit = {},
    onOpenMapClicked: () -> Unit = {},
    onShowLogsClicked: () -> Unit = {},
    onLogoutClicked: () -> Unit = {}
) {
    var showMenuDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    MainScreenContainer {
        HeaderBlock(
            titleText = "Sezamie, otwórz się!",
            deviceName = if (selectedDeviceName.isNotBlank()) selectedDeviceName else "Brak urządzenia",
            isDeviceOn = isDeviceOn,
            onMenuClick = { showMenuDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExpandableCardBlock(title = "Konfiguracja") {
            ActionButtonWithInfoBlock(
                text = "Wybierz urządzenie (${if (selectedDeviceName.isNotBlank()) selectedDeviceName else "Brak"})",
                onClick = { showDeviceDialog = true },
                onInfoClick = { }
            )
            Spacer(modifier = Modifier.height(10.dp))
            ActionButtonWithInfoBlock(
                text = "Urządzenia Bluetooth",
                onClick = onSelectBluetoothClicked,
                onInfoClick = { }
            )
            Spacer(modifier = Modifier.height(10.dp))
            ActionButtonWithInfoBlock(
                text = "Mapa i obszar (Geofencing)",
                onClick = onOpenMapClicked,
                onInfoClick = { }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        PillButtonBlock(
            text = if (isMonitoringActive) "Zatrzymaj automatyzacje" else "Rozpocznij automatyzacje",
            onClick = onToggleMonitoringClicked
        )

        Spacer(modifier = Modifier.height(16.dp))

        ManualSwitchBlock(
            isOn = isDeviceOn,
            onToggle = onToggleDeviceClicked
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Dialog menu burgera
    if (showMenuDialog) {
        AlertDialog(
            onDismissRequest = { showMenuDialog = false },
            title = { Text("Menu opcji", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showMenuDialog = false
                            onShowLogsClicked()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📋 Logi aplikacji")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(
                        onClick = {
                            showMenuDialog = false
                            onLogoutClicked()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🚪 Wyloguj się", color = Color.Red)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenuDialog = false }) {
                    Text("Zamknij")
                }
            }
        )
    }

    // Dialog wyboru urządzenia Ewelink
    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text("Wybierz urządzenie", fontWeight = FontWeight.Bold) },
            text = {
                if (deviceList.isEmpty()) {
                    Text("Brak pobranych urządzeń Ewelink lub trwa ich pobieranie...")
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        deviceList.forEach { device ->
                            TextButton(
                                onClick = {
                                    onDeviceSelected(device)
                                    showDeviceDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${device.name} (${device.deviceid})",
                                    color = JetBlack,
                                    fontWeight = if (device.name == selectedDeviceName) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

@Preview
@Composable
fun MainScreenContainer(
    content: @Composable ColumnScope.() -> Unit = {}
) {
    // Gradient pionowy od bieli do jasnej żółci
    val gradientTla = Brush.linearGradient(
        colors = listOf(Color.White, LightYellowBg),
        start = Offset(0f, 0f),       // Lewy górny róg
        end = Offset(1000f, 1000f)    // Prawy dolny róg (przekątna)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientTla)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 70.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }

        BottomNavigationBarBlock(
            currentPage = 1,
            totalPages = 5,
            onMinusClick = {},
            onPlusClick = {},
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun HeaderBlock(
    titleText: String,               // np. "Sezamie, otwórz się!"
    deviceName: String,              // np. "Moja Brama"
    isDeviceOn: Boolean = false,
    onMenuClick: () -> Unit          // Co ma się stać po kliknięciu ikony menu
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Otwórz Menu",
                    tint = JetBlack
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = titleText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = JetBlack
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = deviceName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            val bgColor = if (isDeviceOn) Color(0xFFFFF9C4) else Color(0xFFE0E0E0)
            val iconColor = if (isDeviceOn) Color(0xFFFBC02D) else Color.Gray

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = iconColor
                )
            }
        }
    }
}

// panel konfiguracji panelu
@Composable
fun ExpandableCardBlock(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    // STAN: Czy karta jest rozwinięta? Odpowiednik setState w Flutterze
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Nagłówek karty (kliknięcie zmienia stan isExpanded)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }, // Zmiana stanu rozwinięcia
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                // Zmieniająca się ikona strzałki w górę / w dół
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }

            // Zawartość pokazywana tylko wtedy, gdy isExpanded == true (z płynną animacją)
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    content()
                }
            }
        }
    }
}


@Composable
fun BottomNavigationBarBlock(
    currentPage: Int = 1,
    totalPages: Int = 5,
    onMinusClick: () -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        // Przycisk Minus (-) w lewym dolnym rogu (narożnik)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-22).dp, y = 16.dp)
                .size(76.dp)
                .shadow(6.dp, CircleShape)
                .background(BlazeOrange, CircleShape)
                .clickable { onMinusClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                color = Color.Black,
                fontSize = 31.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.offset(x = 8.dp, y = (-8).dp)
            )
        }

        // Kropki wskaźnika stron na środku
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { index ->
                val active = index == currentPage
                if (active) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .shadow(4.dp, CircleShape, spotColor = BlazeOrange, ambientColor = BlazeOrange)
                            .background(BlazeOrange, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(JetBlack, CircleShape)
                    )
                }
            }
        }

        // Przycisk Plus (+) w prawym dolnym rogu (narożnik)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 22.dp, y = 16.dp)
                .size(76.dp)
                .shadow(6.dp, CircleShape)
                .background(BlazeOrange, CircleShape)
                .clickable { onPlusClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color.Black,
                fontSize = 31.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.offset(x = (-8).dp, y = (-8).dp)
            )
        }
    }
}
