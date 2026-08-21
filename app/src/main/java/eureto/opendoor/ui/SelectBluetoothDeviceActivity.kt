package eureto.opendoor.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.databinding.ActivitySelectBluetoothCarAudioBinding
import eureto.opendoor.logging.MyLog
import eureto.opendoor.network.EwelinkApiClient
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class SelectBluetoothDeviceActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySelectBluetoothCarAudioBinding
    private val deviceListStrings = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private var selectedDevice: MutableMap<String, String> = mutableMapOf()
    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectBluetoothCarAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appPreferences = EwelinkApiClient.getAppPreferences()


        binding.btnSave.setOnClickListener {
            // TODO: save devices to shared preferences
            appPreferences.saveSelectedBluetoothDevices(selectedDevice)
            Toast.makeText(this, "Zapisano urządzenia Bluetooth", Toast.LENGTH_SHORT).show()
            MyLog.addLogMessageIntoFile(this, "Zapisano urządzenia Bluetooth \n ${selectedDevice.toString()} ")
            finish()
        }

        adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            deviceListStrings
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val item = getItem(position)
                val macAddress = item?.substringAfter("\n")
                if (macAddress != null && selectedDevice.containsKey(macAddress)) {
                    view.setBackgroundColor(Color.LTGRAY)
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
                return view
            }
        }
        binding.bluetoothDevicesList.adapter = adapter

        binding.bluetoothDevicesList.setOnItemClickListener { _, _, position, _ ->
                val selectedInfo = deviceListStrings[position]
                if (selectedInfo == "No paired devices found") return@setOnItemClickListener

                val name = selectedInfo.substringBefore("\n")
                val macAddress = selectedInfo.substringAfter("\n")
            if(selectedDevice.containsKey(macAddress)) {
                selectedDevice.remove(macAddress)
                Toast.makeText(this, "Usunięto: ${name}", Toast.LENGTH_SHORT).show()
            }else{
                selectedDevice.put(macAddress, name)
                Toast.makeText(this, "Wybrano: $name", Toast.LENGTH_SHORT).show()
            }
            adapter.notifyDataSetChanged()
        }

        checkPermissionsAndLoadDevices()

    }

    // TODO: make this function more elegant
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

        deviceListStrings.clear()
        pairedDevices?.forEach { device ->
            val deviceName = try { device.name } catch (e: SecurityException) { "Unknown Device" } ?: "Unknown Device"
            val deviceAddress = device.address
            deviceListStrings.add("$deviceName\n$deviceAddress")
        }
        
        if (deviceListStrings.isEmpty()) {
            deviceListStrings.add("No paired devices found")
        }

        // Load saved devices and populate selectedDevice map
        val savedDevices = appPreferences.getSelectedBluetoothDevices()
        if(savedDevices != null) {
            selectedDevice.clear()
            selectedDevice.putAll(savedDevices)
        }
        
        adapter.notifyDataSetChanged()
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
