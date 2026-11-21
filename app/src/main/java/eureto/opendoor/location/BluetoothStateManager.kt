package eureto.opendoor.location

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import eureto.opendoor.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BluetoothStateManager {
    private val _isConnected = MutableStateFlow<Boolean?>(null)
    val isConnected: StateFlow<Boolean?> = _isConnected.asStateFlow()

    fun updateConnectionState(connected: Boolean) {
        _isConnected.value = connected
    }

    /**
     * Odświeża stan połączenia na podstawie adaptera Bluetooth oraz zapisanych preferencji.
     */
    fun refreshState(context: Context, appPreferences: AppPreferences) {
        val savedDevices = appPreferences.getSelectedBluetoothDevices()
        if (savedDevices.isNullOrEmpty()) {
            _isConnected.value = null
            return
        }

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        // Jeśli Bluetooth jest wyłączony w systemie — brak połączenia
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _isConnected.value = false
            appPreferences.setBluetoothDeviceConnected(false)
            return
        }

        // Sprawdzamy profile A2DP i HEADSET przez BluetoothAdapter (Bluetooth Classic)
        var hasConnectedAudioProfile = false
        try {
            val a2dpState = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP)
            val headsetState = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)
            hasConnectedAudioProfile = (a2dpState == BluetoothProfile.STATE_CONNECTED ||
                    headsetState == BluetoothProfile.STATE_CONNECTED)
        } catch (_: SecurityException) {
            // Brak uprawnienia BLUETOOTH_CONNECT na Androidzie 12+
        }

        val lastKnownConnected = appPreferences.isBluetoothDeviceConnected()

        if (!hasConnectedAudioProfile) {
            appPreferences.setBluetoothDeviceConnected(false)
            _isConnected.value = false
        } else {
            _isConnected.value = lastKnownConnected
        }
    }
}
