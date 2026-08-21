package eureto.opendoor.location

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import eureto.opendoor.location.LocationMonitoringService.Companion.ACTION_LOCATION_CHECK_IN_CAR
import eureto.opendoor.logging.MyLog
import eureto.opendoor.network.EwelinkApiClient

class BluetoothDeviceConnectedReceiver: BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }
        val appPreferences = EwelinkApiClient.getAppPreferences()
        val actionBluetooth = intent.action

        if (BluetoothDevice.ACTION_ACL_CONNECTED == actionBluetooth) {
            // Get the device that just connected
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val connectedAddress = device?.address

            // Get saved MAC address
            val savedDevices = appPreferences.getSelectedBluetoothDevices()
            if(savedDevices == null){
                MyLog.addLogMessageIntoFile(context, "Brak zapisanych urządzeń Bluetooth, kończe działanie")
                return
            }

            if (savedDevices.containsKey(device?.address)) {
                MyLog.addLogMessageIntoFile(context, "Połączono z wybranym urządzeniem: ${connectedAddress}")
                // If user successfully connected to saved device check for location and decide if turn on gate
                val locationIntent = Intent(context, LocationMonitoringService::class.java).apply {
                    action = ACTION_LOCATION_CHECK_IN_CAR
                }
                ContextCompat.startForegroundService(context, locationIntent)
            }
        }
    }
}