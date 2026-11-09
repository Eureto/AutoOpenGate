package eureto.opendoor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eureto.opendoor.R
import eureto.opendoor.ui.components.AppBackground
import eureto.opendoor.ui.theme.BlazeOrange
import eureto.opendoor.ui.theme.JetBlack

@Composable
fun LoginScreen(
    onLoginClick: () -> Unit = {}
) {
    AppBackground {
        Spacer(modifier = Modifier.weight(1f))

        // Logo i tytuł aplikacji
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Logo OpenDoor",
                modifier = Modifier.size(100.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "OpenDoor",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = JetBlack
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Zaloguj się do konta eWeLink",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = JetBlack
            )
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Główny przycisk logowania w stylu aplikacji
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BlazeOrange),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "Zaloguj przez eWeLink",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
fun LoginScreenPreview() {
    LoginScreen()
}
