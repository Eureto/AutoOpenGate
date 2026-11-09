package eureto.opendoor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    selectedDeviceName: String = "Brak urządzenia",
    username: String = "",
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showDeviceDialog by remember { mutableStateOf(false) }

    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogDesc by remember { mutableStateOf<String?>(null) }
    var infoDialogImage by remember { mutableStateOf<Any?>(null) }

    AppDrawer(
        drawerState = drawerState,
        scope = scope,
        onShowLogsClicked = onShowLogsClicked,
        onLogoutClicked = onLogoutClicked
    ) {
        MainScreenContent(
            content = {
                HeaderBlock(
                    titleText = if (username.isNotBlank()) "Witaj $username!" else "Witaj!",
                    deviceName = if (selectedDeviceName.isNotBlank()) selectedDeviceName else "Brak urządzenia",
                    isDeviceOn = isDeviceOn,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ExpandableCardBlock(title = "Konfiguracja") {
                    ActionButtonWithInfoBlock(
                        text = "Wybierz urządzenie (${if (selectedDeviceName.isNotBlank()) selectedDeviceName else "Brak"})",
                        onClick = { showDeviceDialog = true },
                        onInfoClick = {
                            infoDialogTitle = "Urządzenie Ewelink"
                            infoDialogDesc =
                                "Wybierz urządzenie Sonoff / Ewelink z listy pobranej z Twojego konta. To urządzenie będzie automatycznie sterowane po wejściu w wyznaczony obszar."
                            infoDialogImage = null
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ActionButtonWithInfoBlock(
                        text = "Urządzenia Bluetooth",
                        onClick = onSelectBluetoothClicked,
                        onInfoClick = {
                            infoDialogTitle = "Urządzenia Bluetooth"
                            infoDialogDesc =
                                "Możesz powiązać automatyzację z połączeniem Bluetooth (np. z zestawem głośnomówiącym w samochodzie). Brama otworzy się tylko wtedy, gdy Twój telefon jest połączony z wybranym autem."
                            infoDialogImage = null
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ActionButtonWithInfoBlock(
                        text = "Mapa i obszar (Geofencing)",
                        onClick = onOpenMapClicked,
                        onInfoClick = {
                            infoDialogTitle = "Konfiguracja obszaru (Geofence)"
                            infoDialogDesc =
                                "Zaznacz na mapie wielokąt wokół Twojego domu lub wjazdu. Przekroczenie tej granicy automatycznie wyśle sygnał do otwarcia bramy."
                            infoDialogImage = eureto.opendoor.R.drawable.ic_launcher_foreground
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PillButtonBlock(
                    text = if (isMonitoringActive) "Zatrzymaj automatyzacje" else "Rozpocznij automatyzacje",
                    onClick = onToggleMonitoringClicked
                )

                Spacer(modifier = Modifier.height(16.dp))
            },
            bottomContent = {
                ManualSwitchBlock(
                    isOn = isDeviceOn,
                    onToggle = onToggleDeviceClicked
                )
            }
        )
    }

    // Okienko informacyjne (po kliknięciu ikony 'i')
    if (infoDialogTitle != null && infoDialogDesc != null) {
        InfoDialog(
            title = infoDialogTitle!!,
            description = infoDialogDesc!!,
            imageOrGifResId = infoDialogImage,
            onDismiss = {
                infoDialogTitle = null
                infoDialogDesc = null
                infoDialogImage = null
            }
        )
    }

    // Dialog wyboru urządzenia Ewelink
    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text("Wybierz urządzenie", color = JetBlack, fontWeight = FontWeight.Bold) },
            text = {
                if (deviceList.isEmpty()) {
                    Text("Brak pobranych urządzeń Ewelink lub trwa ich pobieranie...", color = JetBlack)
                } else {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        deviceList.forEach { device ->
                            TextButton(
                                onClick = {
                                    onDeviceSelected(device)
                                    showDeviceDialog = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SilverColor, RoundedCornerShape(8.dp))
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
                    Text("Anuluj", color = JetBlack)
                }
            }
        )
    }
}

@Preview
@Composable
fun MainScreenContent(
    content: @Composable () -> Unit = {},
    bottomContent: @Composable () -> Unit = {}
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
                .padding(bottom = 140.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            bottomContent()
        }
// Na ten moment funkcjonalnośc nie zaimplemenowana ale ui może zostać na przyszłość, nie odkomentywać!!!
//        BottomNavigationBarBlock(
//            currentPage = 1,
//            totalPages = 5,
//            onMinusClick = {},
//            onPlusClick = {},
//            modifier = Modifier.align(Alignment.BottomCenter)
//        )
    }
}

@Composable
fun HeaderBlock(
    titleText: String,
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
                fontSize = 28.sp,
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
                color = JetBlack,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )

            val bgColor = if (isDeviceOn) Color(0xFFFFF9C4) else Color(0xFFE0E0E0)
            val iconColor = if (isDeviceOn) Color(0xFFFBC02D) else Color.Gray

            Box(
                modifier = Modifier
                    .size(44.dp)
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
                    color = JetBlack,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                // Zmieniająca się ikona strzałki w górę / w dół
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = JetBlack
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
