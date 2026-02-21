package eureto.opendoor.ui

import android.R.attr.fontFamily
import eureto.opendoor.R
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
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.databinding.ActivityMainBinding
import eureto.opendoor.location.LocationMonitoringService
import eureto.opendoor.network.EwelinkApiClient
import eureto.opendoor.network.model.Device
import kotlinx.coroutines.launch
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import eureto.opendoor.network.EwelinkDevices
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private val logFileName: String = "log.txt"
    private lateinit var binding: ActivityMainBinding
    private lateinit var appPreferences: AppPreferences
    private lateinit var ewelinkApiClient: EwelinkApiClient
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

        // Sprawdź uprawnienia do powiadomień (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }


        addLogMessage("MainActivity: onCreate called")
        setupLogReceiver()
        setupUI()
        fetchDevices()
    }

    override fun onResume() {
        super.onResume()
        // Odśwież status monitorowania po powrocie z MapActivity
        updateMonitoringStatusUI()
    }

    override fun onDestroy() {
        super.onDestroy()
//        logReceiver?.let {
//            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
//        }
    }

    private fun setupUI() {
        binding.btnLogout.setOnClickListener {
            appPreferences.clearLoginData()
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
                EwelinkDevices.toggleDevice(id, newState)
            } ?: Toast.makeText(this, "Wybierz urządzenie do sterowania.", Toast.LENGTH_SHORT).show()
        }

        binding.btnOpenMap.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        }

        binding.btnStartMonitoring.setOnClickListener {
            requestLocationPermissions() // each time when user starts monitoring firstly check if permissions are given then start monitoring
        }

        binding.btnStopMonitoring.setOnClickListener {
            stopLocationMonitoringService()
            updateMonitoringStatusUI()
        }
        binding.btnShowLogMessages.setOnClickListener {
            readLogMessages()
        }


        // Ustaw domyślne wartości lub pobierz z SharedPreferences
        val savedDeviceId = appPreferences.getSelectedDeviceId()
        val savedDeviceName = appPreferences.getSelectedDeviceName()
        if (!savedDeviceId.isNullOrEmpty() && !savedDeviceName.isNullOrEmpty()) {
            selectedDeviceId = savedDeviceId
            binding.tvSelectedDevice.text = "Wybrane urządzenie: $savedDeviceName"
            binding.btnToggleDevice.isEnabled = true
        }
        else {
            binding.tvSelectedDevice.text = "Wybrane urządzenie: Brak"
            binding.btnToggleDevice.isEnabled = false
        }
        updateMonitoringButtons()
        updateMonitoringStatusUI()
    }

    private fun addLogMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(Date())
        val fullMessage = "[$timestamp] $message\n"

        Log.d("LogMessage", message)

        try {
            this.openFileOutput(logFileName, Context.MODE_APPEND).use { output ->
                output.write(fullMessage.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Błąd zapisu logów: ${e.message}")
        }
    }

    private fun readLogMessages(){
        var log: String = try {
            this.openFileInput(logFileName).bufferedReader().useLines { lines ->
                lines.joinToString("\n")
            }
        } catch (e: Exception) {
            "Brak logów lub błąd odczytu."
        }

        // Create the BottomSheetDialog
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
            setBackgroundResource(android.R.drawable.button_onoff_indicator_off) // Generic grey pill shape
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
                //LinearLayout.LayoutParams.WRAP_CONTENT
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
                clearLogFile()
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
    private fun clearLogFile(){
        try {
            this.openFileOutput(logFileName, Context.MODE_PRIVATE).use { output ->
                output.write("".toByteArray())}

            Toast.makeText(this, "Wyczyszczono historię logów", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Błąd podczas czyszczenia logów: ${e.message}")
        }
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

    private fun fetchDevices() {
        addLogMessage("MainActivity fetchDevices called")
        lifecycleScope.launch {
            try {
                val apiService = ewelinkApiClient.createApiService()
                val response = apiService.getDevices()

                if (response.error == 0 && response.data != null && !response.data.thingList.isNullOrEmpty()) {
                    // if succesfully fetched devics from server crate list

                    deviceList = response.data.thingList.mapNotNull { it.itemData }

                    val adapterData = mutableListOf("Wybierz urządzenie")
                    adapterData.addAll(deviceList.map { "${it.name} (${it.deviceid})" })

                    val adapter = ArrayAdapter(this@MainActivity,
                        android.R.layout.simple_spinner_item, adapterData)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.spinnerDevices.adapter = adapter

                    val savedDeviceId = appPreferences.getSelectedDeviceId()
                    val savedDevicePosition = deviceList.indexOfFirst { it.deviceid == savedDeviceId }
                    if (savedDevicePosition != -1) {
                        binding.spinnerDevices.setSelection(savedDevicePosition + 1) // +1 bo "Wybierz urządzenie"
                    }
                    Log.d("MainActivity", "Pobrano urządzenia: ${deviceList.size}")
                    addLogMessage("Pobrano urządzenia: ${deviceList.size}")

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

    private fun requestLocationPermissions() {
        addLogMessage("zapytanie o uprawnienia do lokalizacji")
        Log.d("MainActivity", "Zapytanie o uprawnienia do lokalizacji")
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
        val polygonCenter = appPreferences.getPolygonCenter()

        // Check if device and polygon are selected
        if (selectedId.isNullOrEmpty() || polygonJson.isNullOrEmpty()) {
            Toast.makeText(this, "Wybierz urządzenie i zaznacz obszar na mapie przed rozpoczęciem monitorowania.", Toast.LENGTH_LONG).show()
            updateMonitoringButtons()
            return
        }

        // check location permission before starting monitoring
        val hasFineLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // for older versions there is no need to check background location permission
        }
        if (!hasFineLocation || !hasBackgroundLocation) {
            Toast.makeText(this, "Brak wymaganych uprawnień lokalizacji.", Toast.LENGTH_LONG).show()
            requestLocationPermissions()
            return
        }

        // Starts the monitoring service
        val serviceIntent = Intent(this, LocationMonitoringService::class.java)
        // This intent servers as bridge to the service passing the selected device ID and polygon JSON
//        serviceIntent.putExtra("deviceId", selectedId)
//        serviceIntent.putExtra("polygonJson", polygonJson)
//        serviceIntent.putExtra("polygonCenter", polygonCenter)

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
        logReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
        }
    }


    // Updates the button states based on selected device and polygon
    private fun updateMonitoringButtons() {
        val isDeviceSelected = !appPreferences.getSelectedDeviceId().isNullOrEmpty()
        val isPolygonSet = !appPreferences.getPolygonCoordinates().isNullOrEmpty()

        binding.btnStartMonitoring.isEnabled = isDeviceSelected && isPolygonSet
    }

    private fun updateMonitoringStatusUI() {
        // Check if service is running and update the UI accordingly
        val isMonitoringActive = isServiceRunning(LocationMonitoringService::class.java)
        binding.tvMonitoringStatus.text = "Status monitorowania: ${if (isMonitoringActive) "Aktywne" else "Nieaktywne"}"
        binding.btnStartMonitoring.visibility = if (isMonitoringActive) View.GONE else View.VISIBLE
        binding.btnStopMonitoring.visibility = if (isMonitoringActive) View.VISIBLE else View.GONE
        updateMonitoringButtons() // Odśwież stan przycisków
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
}