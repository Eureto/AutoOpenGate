package eureto.opendoor.network

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import eureto.opendoor.location.LocationMonitoringService.Companion.ACTION_LOG_UPDATE
import eureto.opendoor.location.LocationMonitoringService.Companion.EXTRA_LOG_MESSAGE
import eureto.opendoor.network.model.DeviceControlParams
import eureto.opendoor.network.model.DeviceControlRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


object EwelinkDevices{

    private lateinit var ewelinkApiClient: EwelinkApiClient
    private lateinit var applicationContext: Context

    fun initialize(context: Context, client: EwelinkApiClient) {
        applicationContext = context.applicationContext // Use application context
        ewelinkApiClient = client
        Log.d("EwelinkDevices", "EwelinkDevices initialized.")
    }

    private fun ensureInitialized() {
        if (!::applicationContext.isInitialized || !::ewelinkApiClient.isInitialized) {
            throw IllegalStateException("EwelinkDevices has not been initialized. Call EwelinkDevices.initialize() first.")
        }
    }

    fun toggleDevice(deviceId: String, state: String) {
        ensureInitialized()
        sendBroadcastLog("MainActivity toggleDevice called")
        sendBroadcastLog("Przerwarzanie toggleDevice")


        //return; //TODO: Usuń ten return, jest tylko do testów, fukcja działa


        val myScope = CoroutineScope(Dispatchers.IO+Job())
 
    myScope.launch {
        try {
            val apiService = ewelinkApiClient.createApiService()
            val requestBody = DeviceControlRequest(
                type = 1, // 1 dla jednego urządzenia
                id = deviceId,
                params = DeviceControlParams(switch = state)
            )
            val response = apiService.setDeviceStatus(requestBody)

            // I set OR in this if because when I toggle device I get no msg but response.error = 0 and device is switching so...
            if (response.error == 0) {
                Log.d("MainActivity", "Status urządzenia $deviceId zmieniony na $state przez REST API.")
                sendBroadcastLog("Status urządzenia $deviceId zmieniony na $state przez REST API.")

            } else {
                Log.e(
                    "MainActivity",
                    "Błąd zmiany statusu urządzenia $deviceId na $state: ${response.msg ?: "Nieznany błąd"} (Kod: ${response.error})"
                )
                sendBroadcastLog("Błąd zmiany statusu urządzenia $deviceId na $state: ${response.msg ?: "Nieznany błąd"} (Kod: ${response.error})")
            }
        } catch (e: Exception) {
            Toast.makeText(
                applicationContext,
                "Błąd sieci podczas zmiany statusu: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            Log.e("MainActivity", "Błąd sieci podczas zmiany statusu urządzenia: ${e.message}", e)
            sendBroadcastLog("Błąd sieci podczas zmiany statusu urządzenia lub inny błąd: ${e.message}")
        }
    }
}

    fun sendBroadcastLog(message: String) {
        val intent = Intent(ACTION_LOG_UPDATE)
        intent.putExtra(EXTRA_LOG_MESSAGE, message)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }
}
