package eureto.opendoor.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.logging.MyLog
import eureto.opendoor.network.EwelinkApiClient
import eureto.opendoor.ui.screens.BluetoothDeviceScreen
import eureto.opendoor.ui.screens.BluetoothDeviceInfo
import eureto.opendoor.ui.theme.OpenDoorTheme

class SelectBluetoothDeviceActivity : AppCompatActivity() {

    private lateinit var appPreferences: AppPreferences
    private var devicesState by mutableStateOf<List<BluetoothDeviceInfo>>(emptyList())
    private var selectedDevicesState by mutableStateOf<Map<String, String>>(emptyMap())
    private val selectedDevicesMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appPreferences = EwelinkApiClient.getAppPreferences()

        // Load saved devices
        appPreferences.getSelectedBluetoothDevices()?.let { saved ->
            selectedDevicesMap.putAll(saved)
            selectedDevicesState = selectedDevicesMap.toMap()
        }

        setContent {
            OpenDoorTheme {
                BluetoothDeviceScreen(
                    devices = devicesState,
                    selectedDevices = selectedDevicesState,
                    onDeviceClick = { device ->
                        if (selectedDevicesMap.containsKey(device.address)) {
                            selectedDevicesMap.remove(device.address)
                            Toast.makeText(this, "Usunięto: ${device.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedDevicesMap[device.address] = device.name
                            Toast.makeText(this, "Wybrano: ${device.name}", Toast.LENGTH_SHORT).show()
                        }
                        selectedDevicesState = selectedDevicesMap.toMap()
                    },
                    onSaveClick = {
                        appPreferences.saveSelectedBluetoothDevices(selectedDevicesMap)
                        Toast.makeText(this, "Zapisano urządzenia Bluetooth", Toast.LENGTH_SHORT).show()
                        MyLog.addLogMessageIntoFile(this, "Zapisano urządzenia Bluetooth \n $selectedDevicesMap")
                        finish()
                    }
                )
            }
        }

        checkPermissionsAndLoadDevices()
    }

    private fun checkPermissionsAndLoadDevices() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            loadPairedDevices()
        } else {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }
    }

    private fun loadPairedDevices() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = try {
            bluetoothAdapter.bondedDevices
        } catch (e: SecurityException) {
            Toast.makeText(this, "Security Exception: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }

        val list = mutableListOf<BluetoothDeviceInfo>()
        pairedDevices?.forEach { device ->
            val deviceName = try { device.name } catch (e: SecurityException) { "Unknown Device" } ?: "Unknown Device"
            val deviceAddress = device.address
            list.add(BluetoothDeviceInfo(name = deviceName, address = deviceAddress))
        }

        devicesState = list
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadPairedDevices()
            } else {
                Toast.makeText(this, "Permission denied. Cannot list devices.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
