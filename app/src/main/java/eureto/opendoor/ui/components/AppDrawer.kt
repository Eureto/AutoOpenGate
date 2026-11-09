package eureto.opendoor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eureto.opendoor.R
import eureto.opendoor.ui.theme.CardBg
import eureto.opendoor.ui.theme.JetBlack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    drawerState: DrawerState,
    scope: CoroutineScope,
    onShowLogsClicked: () -> Unit,
    onLogoutClicked: () -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CardBg,
                drawerContentColor = JetBlack
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Logo aplikacji na samej górze
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo OpenDoor",
                        modifier = Modifier
                            .size(72.dp)
                            .padding(bottom = 8.dp)
                    )
                    Text(
                        text = "OpenDoor",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = JetBlack
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Przycisk 1: Logi aplikacji
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "📋 Logi aplikacji",
                            color = JetBlack,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onShowLogsClicked()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Przycisk 2: Wyloguj się
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "🚪 Wyloguj się",
                            color = Color.Red,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogoutClicked()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        },
        content = content
    )
}
