package eureto.opendoor.location

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import eureto.opendoor.location.LocationMonitoringService.Companion.ACTION_LOCATION_CHECK_IN_CAR
import eureto.opendoor.logging.MyLog
import eureto.opendoor.network.EwelinkApiClient

class BluetoothDeviceConnectedReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }
        val appPreferences = EwelinkApiClient.getAppPreferences()
        val actionBluetooth = intent.action

        // Obsługa wyłączenia Bluetooth w ustawieniach telefonu
        if (BluetoothAdapter.ACTION_STATE_CHANGED == actionBluetooth) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                appPreferences.setBluetoothDeviceConnected(false)
                BluetoothStateManager.updateConnectionState(false)
            }
            return
        }

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val connectedAddress = try {
            device?.address
        } catch (_: SecurityException) {
            null
        } ?: return

        val savedDevices = appPreferences.getSelectedBluetoothDevices()
        if (savedDevices == null) {
            MyLog.addLogMessageIntoFile(context, "Brak zapisanych urządzeń Bluetooth, kończę działanie")
            return
        }

        if (savedDevices.containsKey(connectedAddress)) {
            when (actionBluetooth) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    MyLog.addLogMessageIntoFile(context, "Połączono z wybranym urządzeniem Bluetooth: $connectedAddress")
                    appPreferences.setBluetoothDeviceConnected(true)
                    BluetoothStateManager.updateConnectionState(true)

                    // Uruchomienie sprawdzania lokalizacji i otwarcia bramy
                    val locationIntent = Intent(context, LocationMonitoringService::class.java).apply {
                        action = ACTION_LOCATION_CHECK_IN_CAR
                    }
                    ContextCompat.startForegroundService(context, locationIntent)
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    MyLog.addLogMessageIntoFile(context, "Rozłączono z wybranym urządzeniem Bluetooth: $connectedAddress")
                    appPreferences.setBluetoothDeviceConnected(false)
                    BluetoothStateManager.updateConnectionState(false)
                }
            }
        }
    }
}
