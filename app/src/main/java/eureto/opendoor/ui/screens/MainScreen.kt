package eureto.opendoor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.core.graphics.rotationMatrix
import eureto.opendoor.ui.components.*
import eureto.opendoor.ui.theme.*


@Composable
fun MainScreen(
    onWybierzUrzadzenieClicked: () -> Unit,
    onWlaczUrzadzenieToggled: (Boolean) -> Unit
) {
    MainScreenContainer {
        HeaderBlock(titleText = "Sezamie, otwórz się!", deviceName = "Mój Garaż", onMenuClick = {})

        ExpandableCardBlock(title = "Konfiguracja") {
            ActionButtonWithInfoBlock(
                text = "Wybierz urządzenie",
                onClick = onWybierzUrzadzenieClicked, // Przekazujesz callback wyżej
                onInfoClick = { /* Akcja Info */ }
            )
        }
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
                .verticalScroll(rememberScrollState()), // Umożliwia przewijanie ekranu w pionie
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderBlock("Witaj Mateusz", "urządzenie1", {})
            Spacer(modifier = Modifier.height(10.dp))
            ExpandableCardBlock("Konfiguracja", {
                ActionButtonWithInfoBlock("Wybierz urządzenie", {},{})
                Spacer(modifier = Modifier.height(10.dp))
                ActionButtonWithInfoBlock("Wybierz urządzenie", {},{})
                Spacer(modifier = Modifier.height(10.dp))
            })
            Spacer(modifier = Modifier.height(10.dp))
            PillButtonBlock("Uruchom automatyzacje", {})
            Spacer(modifier = Modifier.height(10.dp))
            ManualSwitchBlock(false, {})
            Spacer(modifier = Modifier.height(10.dp))


        }

//        BottomNavigationBarBlock(
//            currentPage = 1,
//            totalPages = 5,
//            onMinusClick = {},
//            onPlusClick = {},
//            modifier = Modifier.align(Alignment.BottomCenter)
//        )
    }
}


// burger menu, powitanie, nazwa wybranego urządzenia, status urządzenia
@Composable
fun HeaderBlock(
    titleText: String,               // np. "Sezamie, otwórz się!"
    deviceName: String,              // np. "Moja Brama"
    onMenuClick: () -> Unit          // Co ma się stać po kliknięciu ikony menu
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Górny wiersz z menu i tytułem
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

            Spacer(modifier = Modifier.weight(1f)) // Rozpychacz

            Text(
                text = titleText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = JetBlack
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Wiersz z nazwą urządzenia i ikonką żarówki
        Row(
            modifier = Modifier.fillMaxWidth().
            padding(start=10.dp, end=10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = deviceName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Ikona w okręgu (np. wskaźnik statusu)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF9C4)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFBC02D)
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
                    content() // Tutaj wstawiasz elementy wewnątrz karty
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