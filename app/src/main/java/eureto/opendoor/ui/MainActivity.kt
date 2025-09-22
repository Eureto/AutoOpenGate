package eureto.opendoor.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import eureto.opendoor.data.AppPreferences // Zmień nazwę pakietu
import eureto.opendoor.databinding.ActivityMainBinding // Zmień nazwę pakietu
import eureto.opendoor.location.LocationMonitoringService // Zmień nazwę pakietu
import eureto.opendoor.network.EwelinkApiClient // Zmień nazwę pakietu
import eureto.opendoor.network.EwelinkWebSocketClient // Zmień nazwę pakietu
import eureto.opendoor.network.model.Device // Zmień nazwę pakietu
import eureto.opendoor.network.model.DeviceControlParams
import eureto.opendoor.network.model.DeviceControlRequest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.Date // Upewnij się, że ten import jest obecny

class MainActivity : AppCompatActivity() {

    private val logMessages = mutableStateListOf<String>()
    private lateinit var binding: ActivityMainBinding
    private lateinit var appPreferences: AppPreferences
    private lateinit var ewelinkApiClient: EwelinkApiClient
    private lateinit var ewelinkWebSocketClient: EwelinkWebSocketClient
    private var deviceList: List<Device> = emptyList()
    private var selectedDeviceId: String? = null
    private var logReceiver: BroadcastReceiver? = null


    // Launcher do prośby o uprawnienia do lokalizacji
    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val backgroundLocationGranted = permissions[android.Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false

        if (fineLocationGranted && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || backgroundLocationGranted)) {
            Toast.makeText(this, "Uprawnienia lokalizacji przyznane.", Toast.LENGTH_SHORT).show()
            checkAndStartLocationMonitoring()
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
            binding.btnStartMonitoring.isEnabled = false
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = EwelinkApiClient.getAppPreferences()
        ewelinkApiClient = EwelinkApiClient
        ewelinkWebSocketClient = ewelinkApiClient.createWebSocketClient()

        // Sprawdź uprawnienia do powiadomień (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.composeLogView.setContent { // Use the ID from your XML
            // You can wrap this in your app's MaterialTheme if you have one defined for Compose
            MaterialTheme { // Using a default MaterialTheme for simplicity
                LoggingScreen(logMessages = logMessages)
            }
        }
        // Example: Log when the activity is created
        addLogMessage("MainActivity onCreate called")
        setupLogReceiver()
        setupUI()
        fetchDevices()
        observeDeviceStatusUpdates()
    }

    override fun onResume() {
        super.onResume()
        // Odśwież status monitorowania po powrocie z MapActivity
        updateMonitoringStatusUI()
    }

    fun addLogMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(Date())
        val fullMessage = "[$timestamp] $message"
        logMessages.add(fullMessage)
    }

    private fun setupUI() {
        binding.btnLogout.setOnClickListener {
            appPreferences.clearLoginData()
            ewelinkWebSocketClient.disconnect()
            stopLocationMonitoringService() // Zatrzymaj usługę lokalizacji
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.spinnerDevices.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) { // Omiń "Wybierz urządzenie"
                    val selectedDevice = deviceList[position - 1] // -1 bo "Wybierz urządzenie" jest na poz. 0
                    selectedDeviceId = selectedDevice.deviceid
                    appPreferences.saveSelectedDevice(selectedDevice.deviceid, selectedDevice.name)
                    binding.btnToggleDevice.isEnabled = true
                    binding.tvSelectedDevice.text = "Wybrane urządzenie: ${selectedDevice.name}"
                } else {
                    selectedDeviceId = null
                    appPreferences.saveSelectedDevice("", "") // Wyczyść wybrane urządzenie
                    binding.btnToggleDevice.isEnabled = false
                    binding.tvSelectedDevice.text = "Wybrane urządzenie: Brak"
                }
                updateMonitoringButtons()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnToggleDevice.setOnClickListener {
            selectedDeviceId?.let { id ->
                val currentSwitchState = binding.btnToggleDevice.text.toString()
                val newState = if (currentSwitchState == "Włącz") "on" else "off"
                toggleDevice(id, newState)
            } ?: Toast.makeText(this, "Wybierz urządzenie do sterowania.", Toast.LENGTH_SHORT).show()
        }

        binding.btnOpenMap.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        }

        binding.btnStartMonitoring.setOnClickListener {
            requestLocationPermissions()
        }

        binding.btnStopMonitoring.setOnClickListener {
            stopLocationMonitoringService()
            updateMonitoringStatusUI()
        }

        // Ustaw domyślne wartości lub pobierz z SharedPreferences
        val savedDeviceId = appPreferences.getSelectedDeviceId()
        val savedDeviceName = appPreferences.getSelectedDeviceName()
        if (!savedDeviceId.isNullOrEmpty() && !savedDeviceName.isNullOrEmpty()) {
            selectedDeviceId = savedDeviceId
            binding.tvSelectedDevice.text = "Wybrane urządzenie: $savedDeviceName"
            binding.btnToggleDevice.isEnabled = true
        } else {
            binding.tvSelectedDevice.text = "Wybrane urządzenie: Brak"
            binding.btnToggleDevice.isEnabled = false
        }
        updateMonitoringButtons()
        updateMonitoringStatusUI()
    }

    private fun fetchDevices() {
        addLogMessage("MainActivity fetchDevices called")
        lifecycleScope.launch {
            try {
                val apiService = ewelinkApiClient.createApiService()
                val response = apiService.getDevices()

                if (response.error == 0 && response.data != null && !response.data.thingList.isNullOrEmpty()) {
                    // Mapuj ThingListItem.itemData do listy Device
                    deviceList = response.data.thingList.mapNotNull { it.itemData }

                    val adapterData = mutableListOf("Wybierz urządzenie")
                    adapterData.addAll(deviceList.map { "${it.name} (${it.deviceid})" })

                    val adapter = ArrayAdapter(this@MainActivity,
                        android.R.layout.simple_spinner_item, adapterData)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerDevices.adapter = adapter

                    // Ustawia poprzednio wybrane urządzenie
                    val savedDeviceId = appPreferences.getSelectedDeviceId()
                    val savedDevicePosition = deviceList.indexOfFirst { it.deviceid == savedDeviceId }
                    if (savedDevicePosition != -1) {
                        binding.spinnerDevices.setSelection(savedDevicePosition + 1) // +1 bo "Wybierz urządzenie"
                    }
                    Log.d("MainActivity", "Pobrano urządzenia: ${deviceList.size}")
                    addLogMessage("Pobrano urządzenia: ${deviceList.size}")

                    // Po pobraniu urządzeń, zaktualizuj wyświetlany status wybranego urządzenia
                    selectedDeviceId?.let { id ->
                        val currentDevice = deviceList.find { it.deviceid == id }
                        currentDevice?.let { device ->
                            val switchState = device.params.switch
                            if (switchState == "on") {
                                binding.btnToggleDevice.text = "Wyłącz"
                                binding.tvDeviceCurrentStatus.text = "Status: Włączone"
                            } else if (switchState == "off") {
                                binding.btnToggleDevice.text = "Włącz"
                                binding.tvDeviceCurrentStatus.text = "Status: Wyłączone"
                            }
                        }
                    }

                } else {
                    Toast.makeText(this@MainActivity, "Błąd pobierania urządzeń: ${response.msg ?: "Brak danych"} (Kod: ${response.error})", Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", "Błąd pobierania urządzeń: ${response.msg ?: "Brak danych"} (Kod: ${response.error})")
                    deviceList = emptyList() // Upewnij się, że lista jest pusta w przypadku błędu
                    binding.spinnerDevices.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listOf("Brak urządzeń"))
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Błąd sieci: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "Błąd podczas pobierania urządzeń: ${e.message}", e)
                deviceList = emptyList() // Upewnij się, że lista jest pusta w przypadku błędu
                binding.spinnerDevices.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listOf("Brak urządzeń"))
            }
        }
    }

    private fun toggleDevice(deviceId: String, state: String) {
        addLogMessage("MainActivity toggleDevice called")
        addLogMessage("Przerwarzanie toggleDevice")
        return; //TODO: Usuń ten return, jest tylko do testów, fukcja działa
        lifecycleScope.launch {
            try {
                val apiService = ewelinkApiClient.createApiService()
                val requestBody = DeviceControlRequest(
                    type = 1, // 1 dla urządzenia
                    id = deviceId,
                    params = DeviceControlParams(switch = state)
                )
                val response = apiService.setDeviceStatus(requestBody)

                if (response.error == 0 && response.msg == "ok") {
                    Toast.makeText(this@MainActivity, "Pomyślnie zmieniono status urządzenia: $state", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "Status urządzenia $deviceId zmieniony na $state przez REST API.")
                    // Po udanej zmianie statusu przez REST API, odśwież listę urządzeń,
                    // aby uzyskać najnowszy stan (alternatywnie: zaktualizuj lokalnie deviceList)
                    fetchDevices() // Odświeżenie całej listy
                } else {
                    Toast.makeText(this@MainActivity, "Błąd zmiany statusu: ${response.msg ?: "Nieznany błąd"} (Kod: ${response.error})", Toast.LENGTH_LONG).show()
                    Log.e("MainActivity", "Błąd zmiany statusu urządzenia $deviceId na $state: ${response.msg ?: "Nieznany błąd"} (Kod: ${response.error})")
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Błąd sieci podczas zmiany statusu: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "Błąd sieci podczas zmiany statusu urządzenia: ${e.message}", e)
            }
        }
    }

    // Nasłuchiwanie aktualizacji statusu urządzeń przez WebSocket
    private fun observeDeviceStatusUpdates() {
        lifecycleScope.launch {
            ewelinkWebSocketClient.deviceStatusUpdatesFlow.collectLatest { device ->
                // Zaktualizuj UI na podstawie otrzymanego statusu
                if (device.deviceid == selectedDeviceId) {
                    val newSwitchState = device.params.switch
                    if (newSwitchState == "on") {
                        binding.btnToggleDevice.text = "Wyłącz"
                        binding.tvDeviceCurrentStatus.text = "Status: Włączone"
                    } else if (newSwitchState == "off") {
                        binding.btnToggleDevice.text = "Włącz"
                        binding.tvDeviceCurrentStatus.text = "Status: Wyłączone"
                    }
                    Toast.makeText(this@MainActivity, "Status urządzenia ${device.name} zmieniony na: $newSwitchState", Toast.LENGTH_SHORT).show()
                }
                // Możesz również zaktualizować status na liście spinnera, jeśli chcesz
            }
        }
    }

    private fun requestLocationPermissions() {
        addLogMessage("zapytanie o uprawnienia do lokalizacji")
        val permissionsToRequest = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        requestLocationPermissions.launch(permissionsToRequest.toTypedArray())
    }

    private fun checkAndStartLocationMonitoring() {
        val selectedId = appPreferences.getSelectedDeviceId()
        val polygonJson = appPreferences.getPolygonCoordinates()

        // Sprawdź, czy wybrane urządzenie i obszar są ustawione
        if (selectedId.isNullOrEmpty() || polygonJson.isNullOrEmpty()) {
            Toast.makeText(this, "Wybierz urządzenie i zaznacz obszar na mapie przed rozpoczęciem monitorowania.", Toast.LENGTH_LONG).show()
            updateMonitoringButtons()
            return
        }

        // Sprawdź uprawnienia do lokalizacji ponownie przed uruchomieniem serwisu
        val hasFineLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Dla starszych wersji nie ma osobnego uprawnienia do tła
        }

        if (!hasFineLocation || !hasBackgroundLocation) {
            Toast.makeText(this, "Brak wymaganych uprawnień lokalizacji.", Toast.LENGTH_LONG).show()
            // Ponownie poproś o uprawnienia lub przekieruj do ustawień
            requestLocationPermissions()
            return
        }

        // Uruchom usługę monitorowania lokalizacji
        val serviceIntent = Intent(this, LocationMonitoringService::class.java)
        serviceIntent.putExtra("deviceId", selectedId)
        serviceIntent.putExtra("polygonJson", polygonJson)
        // Jeśli chcesz przekazać referencję do WebSocketClienta, musisz użyć bindService
        // lub stworzyć instancję w serwisie i komunikować się poprzez BroadcastReceiver/LocalBroadcastManager
        ContextCompat.startForegroundService(this, serviceIntent)
        addLogMessage("Uruchomionono LocationMonitorigService")
        Toast.makeText(this, "Rozpoczęto monitorowanie lokalizacji.", Toast.LENGTH_SHORT).show()
        updateMonitoringStatusUI()
    }

    private fun stopLocationMonitoringService() {
        val serviceIntent = Intent(this, LocationMonitoringService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Zatrzymano monitorowanie lokalizacji.", Toast.LENGTH_SHORT).show()
        updateMonitoringStatusUI()
    }


    // Aktualizuje stan przycisków start/stop monitorowania na podstawie wybranego urządzenia i zaznaczonego obszaru
    private fun updateMonitoringButtons() {
        val isDeviceSelected = !appPreferences.getSelectedDeviceId().isNullOrEmpty()
        val isPolygonSet = !appPreferences.getPolygonCoordinates().isNullOrEmpty()

        binding.btnStartMonitoring.isEnabled = isDeviceSelected && isPolygonSet
    }

    private fun updateMonitoringStatusUI() {
        // Sprawdź, czy serwis działa (przez globalną flagę, np. z preferencji, lub przez sprawdzanie RunningServices)
        val isMonitoringActive = isServiceRunning(LocationMonitoringService::class.java)
        binding.tvMonitoringStatus.text = "Status monitorowania: ${if (isMonitoringActive) "Aktywne" else "Nieaktywne"}"
        binding.btnStartMonitoring.visibility = if (isMonitoringActive) View.GONE else View.VISIBLE
        binding.btnStopMonitoring.visibility = if (isMonitoringActive) View.VISIBLE else View.GONE
        updateMonitoringButtons() // Odśwież stan przycisków
    }

    private fun setupLogReceiver() {
        logReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra(LocationMonitoringService.EXTRA_LOG_MESSAGE)?.let { message ->
                    addLogMessage("Service: $message") // Dodaj prefix, aby odróżnić logi z serwisu
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(
            logReceiver!!,
            IntentFilter(LocationMonitoringService.ACTION_LOG_UPDATE)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        logReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
        }
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

    // --- Composable for the Logging Box ---
    //TODO: Dodaj możliwość przewijania tekstu
    @Composable
    fun LoggingScreen(logMessages: List<String>) {
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(logMessages.size) {
            if (logMessages.isNotEmpty()) {
                coroutineScope.launch {
                    // Animate scroll to the last item only if the list is not empty
                    // and potentially if the user hasn't manually scrolled up (more advanced)
                    listState.animateScrollToItem(logMessages.lastIndex)
                }
            }
        }

        if (logMessages.isEmpty()) {
            // Możesz wyświetlić coś, gdy nie ma logów, lub po prostu pusty Box
            // np. Text("Brak logów do wyświetlenia.")
            // Dla zachowania wrap_content, pusty Box lub brak renderowania jest ok.
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth() // Chcemy pełną szerokość
                    .wrapContentHeight() // KLUCZ: Wysokość dopasuje się do zawartości
                    .heightIn(min = 50.dp, max = 300.dp) // OPCJONALNIE: Ogranicz wysokość
                    .border(1.dp, Color.Gray)
                    .padding(vertical = 4.dp) // Dodaj padding wewnątrz ramki
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth() // LazyColumn powinien wypełniać szerokość Boxa
                        .wrapContentHeight() // Wysokość LazyColumn również powinna dopasować się do elementów
                    // W połączeniu z heightIn na Box, to będzie działać
                ) {
                    items(logMessages) { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        )
                        HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}