package eureto.opendoor.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import eureto.opendoor.R
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.location.LocationMonitoringService
import eureto.opendoor.logging.MyLog
import eureto.opendoor.network.EwelinkApiClient
import eureto.opendoor.network.EwelinkDevices
import eureto.opendoor.network.model.Device
import eureto.opendoor.ui.screens.MainScreen
import eureto.opendoor.ui.theme.OpenDoorTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val logFileName: String = "log.txt"
    private lateinit var appPreferences: AppPreferences
    private lateinit var ewelinkApiClient: EwelinkApiClient

    private var deviceListState by mutableStateOf<List<Device>>(emptyList())
    private var selectedDeviceIdState by mutableStateOf<String?>(null)
    private var selectedDeviceNameState by mutableStateOf("Brak")
    private var isDeviceOnState by mutableStateOf(false)
    private var isMonitoringActiveState by mutableStateOf(false)

    // Launcher do prośby o uprawnienia do lokalizacji
    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val backgroundLocationGranted = permissions[android.Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false

        if (fineLocationGranted && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || backgroundLocationGranted)) {
            Toast.makeText(this, "Uprawnienia lokalizacji przyznane.", Toast.LENGTH_SHORT).show()
            checkAndStartLocationMonitoring() // Uruchamianie usługi monitorowania lokalizacji
        } else {
            Toast.makeText(this, "Uprawnienia lokalizacji NIE przyznane. Automatyka może nie działać.", Toast.LENGTH_LONG).show()
            // TODO: Przekieruj użytkownika do ustawień, aby mógł ręcznie przyznać uprawnienia
            // Utwórz intencję, aby otworzyć ustawienia dla tej konkretnej aplikacji
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            // Sprawdź, czy jest aktywność, która może obsłużyć tę intencję
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                // Możesz dodać dodatkową informację dla użytkownika, np. w kolejnym Toast
                Toast.makeText(this, "Proszę włączyć uprawnienia lokalizacji w ustawieniach aplikacji.", Toast.LENGTH_LONG).show()
            } else {
                // Rzadki przypadek, ale warto obsłużyć
                Toast.makeText(this, "Nie można otworzyć ustawień aplikacji.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launcher do prośby o uprawnienia do powiadomień (Android 13+)
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Uprawnienia do powiadomień przyznane.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Uprawnienia do powiadomień NIE przyznane. Nie będziesz otrzymywać powiadomień.", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appPreferences = EwelinkApiClient.getAppPreferences()
        ewelinkApiClient = EwelinkApiClient

        // Domyślne wartości z appPreferences
        val savedId = appPreferences.getSelectedDeviceId()
        val savedName = appPreferences.getSelectedDeviceName()
        if (!savedId.isNullOrEmpty() && !savedName.isNullOrEmpty()) {
            selectedDeviceIdState = savedId
            selectedDeviceNameState = savedName
        }

        setContent {
            OpenDoorTheme {
                MainScreen(
                    selectedDeviceName = selectedDeviceNameState,
                    isDeviceOn = isDeviceOnState,
                    isMonitoringActive = isMonitoringActiveState,
                    deviceList = deviceListState,
                    onDeviceSelected = { selectedDevice ->
                        selectedDeviceIdState = selectedDevice.deviceid
                        selectedDeviceNameState = selectedDevice.name
                        appPreferences.saveSelectedDevice(selectedDevice.deviceid, selectedDevice.name)
                        isDeviceOnState = (selectedDevice.params.switch == "on")
                        Toast.makeText(this, "Wybrano: ${selectedDevice.name}", Toast.LENGTH_SHORT).show()
                    },
                    onSelectBluetoothClicked = {
                        val intent = Intent(this, SelectBluetoothDeviceActivity::class.java)
                        startActivity(intent)
                    },
                    onToggleDeviceClicked = { newState ->
                        selectedDeviceIdState?.let { id ->
                            val switchValue = if (newState) "on" else "off"
                            EwelinkDevices.toggleDevice(id, switchValue)
                            isDeviceOnState = newState
                        } ?: Toast.makeText(this, "Najpierw wybierz urządzenie z listy.", Toast.LENGTH_SHORT).show()
                    },
                    onToggleMonitoringClicked = {
                        if (isMonitoringActiveState) {
                            stopLocationMonitoringService()
                        } else {
                            checkAndStartLocationMonitoring()
                        }
                    },
                    onOpenMapClicked = {
                        val intent = Intent(this, MapActivity::class.java)
                        startActivity(intent)
                    },
                    onShowLogsClicked = {
                        readLogMessages()
                    },
                    onLogoutClicked = {
                        appPreferences.clearLoginData()
                        stopLocationMonitoringService()
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }

        // Sprawdź uprawnienia do powiadomień (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        MyLog.addLogMessageIntoFile(this, "MainActivity: onCreate called")
        fetchDevices()
    }

    override fun onResume() {
        super.onResume()
        updateMonitoringStatusUI()
    }

    private fun fetchDevices() {
        MyLog.addLogMessageIntoFile(this, "MainActivity fetchDevices called")
        lifecycleScope.launch {
            try {
                val apiService = ewelinkApiClient.createApiService()
                val response = apiService.getDevices()

                if (response.error == 0 && response.data != null && !response.data.thingList.isNullOrEmpty()) {
                    val fetchedList = response.data.thingList.mapNotNull { it.itemData }
                    deviceListState = fetchedList

                    Log.d("MainActivity", "Pobrano urządzenia: ${fetchedList.size}")
                    MyLog.addLogMessageIntoFile(this@MainActivity, "Pobrano urządzenia: ${fetchedList.size}")

                    selectedDeviceIdState?.let { id ->
                        val currentDevice = fetchedList.find { it.deviceid == id }
                        currentDevice?.let { device ->
                            isDeviceOnState = (device.params.switch == "on")
                        }
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Błąd pobierania urządzeń: ${response.msg ?: "Brak danych"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Błąd podczas pobierania urządzeń: ${e.message}", e)
            }
        }
    }

    private fun checkAndStartLocationMonitoring() {
        val selectedId = appPreferences.getSelectedDeviceId()
        val polygonJson = appPreferences.getPolygonCoordinates()

        if (selectedId.isNullOrEmpty() || polygonJson.isNullOrEmpty()) {
            Toast.makeText(this, "Wybierz urządzenie i zaznacz obszar na mapie przed rozpoczęciem monitorowania.", Toast.LENGTH_LONG).show()
            updateMonitoringStatusUI()
            return
        }

        val hasFineLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasFineLocation || !hasBackgroundLocation) {
            Toast.makeText(this, "Brak wymaganych uprawnień lokalizacji.", Toast.LENGTH_LONG).show()
            requestLocationPermissions()
            return
        }

        val serviceIntent = Intent(this, LocationMonitoringService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        MyLog.addLogMessageIntoFile(this, "Uruchomiono monitorowanie lokalizacji")
        Toast.makeText(this, "Rozpoczęto monitorowanie lokalizacji.", Toast.LENGTH_SHORT).show()
        updateMonitoringStatusUI()
    }

    private fun stopLocationMonitoringService() {
        val serviceIntent = Intent(this, LocationMonitoringService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Zatrzymano monitorowanie lokalizacji.", Toast.LENGTH_SHORT).show()
        updateMonitoringStatusUI()
    }

    private fun updateMonitoringStatusUI() {
        isMonitoringActiveState = isServiceRunning(LocationMonitoringService::class.java)
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun requestLocationPermissions() {
        MyLog.addLogMessageIntoFile(this, "zapytanie o uprawnienia do lokalizacji")
        val permissionsToRequest = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        requestLocationPermissions.launch(permissionsToRequest.toTypedArray())
    }

    private fun readLogMessages() {
        val log: String = try {
            this.openFileInput(logFileName).bufferedReader().useLines { lines ->
                lines.joinToString("\n")
            }
        } catch (e: Exception) {
            "Brak logów lub błąd odczytu."
        }

        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 60)
            setBackgroundResource(R.drawable.bg_bottom_sheet)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val handle = View(this).apply {
            val params = LinearLayout.LayoutParams(100, 12)
            params.setMargins(0, 0, 0, 40)
            params.gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = params
            setBackgroundResource(android.R.drawable.button_onoff_indicator_off)
            alpha = 0.5f
        }
        layout.addView(handle)

        val title = TextView(this).apply {
            text = "Logi Aplikacji"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 30)
        }
        layout.addView(title)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        val textView = TextView(this).apply {
            text = log
            textSize = 14f
            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        }

        scrollView.addView(textView)
        layout.addView(scrollView)

        val buttonClearText = TextView(this).apply {
            text = "Wyczyść logi"
            setTextColor(android.graphics.Color.RED)
            setPadding(0, 30, 0, 30)
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setOnClickListener {
                MyLog.clearLogFile(this@MainActivity)
                bottomSheetDialog.dismiss()
            }
        }
        layout.addView(buttonClearText)

        bottomSheetDialog.setContentView(layout)

        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = null
            (layout.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        bottomSheetDialog.show()
    }
}
