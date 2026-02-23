package eureto.opendoor.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import eureto.opendoor.databinding.ActivitySelectBluetoothCarAudioBinding

class SelectBluetoothDeviceActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySelectBluetoothCarAudioBinding
    private val deviceListStrings = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectBluetoothCarAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSave.setOnClickListener {
            // TODO: save devices to shared preferences
            finish()
        }

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            deviceListStrings
        )
        binding.bluetoothDevicesList.adapter = adapter

        binding.bluetoothDevicesList.setOnItemClickListener { _, _, position, _ ->
            val selectedInfo = deviceListStrings[position]
            val macAddress = selectedInfo.substringAfter("\n")
            Toast.makeText(this, "Wybrano: $macAddress", Toast.LENGTH_SHORT).show()
            // Save this MAC address to your settings/logs here
        }

        checkPermissionsAndLoadDevices()
    }

    private fun checkPermissionsAndLoadDevices() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Below Android 12, BLUETOOTH and BLUETOOTH_ADMIN are normal permissions
            // but we still check them to be safe or if some OEMs handle them differently.
            // Usually they are granted at install time.
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
        // Double check for Android 12+ runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

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