package eureto.opendoor.location

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eureto.opendoor.logging.MyLog
import eureto.opendoor.network.EwelinkApiClient

class BluetoothDeviceConnectedReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }
        val appPreferences = EwelinkApiClient.getAppPreferences()
        val action = intent.action

        if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
            // Get the device that just connected
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val connectedAddress = device?.address

            // Get saved MAC address
            val savedDevices = appPreferences.getSelectedBluetoothDevices()
            val savedAddress = savedDevices?.keys?.firstOrNull()

            if (connectedAddress == savedAddress) {
                MyLog.addLogMessageIntoFile(context, "Połączono z wybranym urządzeniem: ${connectedAddress}")


            }
        }
    }
}