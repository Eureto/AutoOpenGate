package eureto.opendoor.location
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.gson.Gson
import eureto.opendoor.R
import eureto.opendoor.network.EwelinkApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver to handle geofence transition events.
 * It processes ENTER and EXIT events, checks if the user is within a defined polygon,
 * and controls a device via the eWeLink API accordingly.
 * It also manages notifications and logs events to MainActivity.
 */

class GeofenceTransitionsReceiver : BroadcastReceiver() {

    private val notificationId = 1001
    private val notificationChannelId = LocationMonitoringService.NOTIFICATION_CHANNEL_ID
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val TAG = "GeofenceTransitionsReceiver"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    /**
     * Constants for local broadcasts to MainActivity. It is used to send log messages to MainActivity and display them in the UI.
     */
    companion object {
        const val ACTION_LOG_UPDATE =
            "eureto.opendoor.action.LOG_UPDATE" // Same action as in LocationMonitoringService
        const val EXTRA_LOG_MESSAGE =
            "eureto.opendoor.extra.LOG_MESSAGE" // Same key for messages
    }

    //
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("GeofenceReceiver", "Otrzymano złgłoszenie Geofence")
        sendLogToMainActivity( context ?: return, "Otrzymano złgłoszenie Geofence")

        if (context == null || intent?.action != LocationMonitoringService.ACTION_GEOFENCE_TRANSITION) {
            Log.e("GeofenceReceiver", "Nieznana akcja lub brak kontekstu: ${intent?.action}")
            return
        }

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e("GeofenceReceiver", "Błąd: geofencingEvent jest nullem.")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("GeofenceReceiver", "Błąd Geofence: $errorMessage")
            sendNotification(context, "Błąd monitorowania lokalizacji: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        // get the stored preferences
        val appPreferences = EwelinkApiClient.getAppPreferences()
        val deviceId = appPreferences.getSelectedDeviceId()
        val polygonJson = appPreferences.getPolygonCoordinates()

        if (deviceId.isNullOrEmpty() || polygonJson.isNullOrEmpty()) {
            Log.e(
                "GeofenceReceiver",
                "Brak wybranego urządzenia lub zdefiniowanego obszaru. Zatrzymuję operacje."
            )
            sendNotification(
                context,
                "Automatyka wyłączona: brak konfiguracji urządzenia lub obszaru."
            )
            return
        }


        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                sendLogToMainActivity(context, "Zdarzenie GEOFENCE_TRANSITION_ENTER")
                // keep the BroadcastReceiver alive while we do async work
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        sendLogToMainActivity(context, "Sprawdzanie lokalizacji w tle...")
                        sendLogToMainActivity(context, "Dane przekazane do sprawdzania \n deviceId: $deviceId \n polygonCoordinates: $polygonJson")
                        val workRequest = OneTimeWorkRequestBuilder<LocationCheckWorker>()
                            .setInputData(workDataOf("deviceId" to deviceId, "polygonCoordinates" to polygonJson))
                            .build()
                        WorkManager.getInstance(context).enqueue(workRequest)

                    } finally {
                        sendLogToMainActivity(context, "GeofenceReceiver: Koniec działania gefece receiver ")
                        pendingResult.finish()
                    }
                }
            }

            //TODO: Dodaj obsługe zdarzenia EXIT w geofence receiver
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("GeofenceReceiver", "Zdarzenie GEOFENCE_TRANSITION_EXIT")
                sendLogToMainActivity(context, "Zdarzenie GEOFENCE_TRANSITION_EXIT ale robie return")
                return // usuń

//                if (false) { // Upewnij się, że faktycznie jesteś poza wielokątem
//                    Log.d("GeofenceReceiver", "Lokalizacja poza wielokątem. Opuściłeś teren.")
//                    sendNotification(context, "Opuściłeś teren. Brama zostanie wyłączona.")
//                    scope.launch {
//
//                        try {
//                            val apiService = EwelinkApiClient.createApiService()
//                            val requestBody = DeviceControlRequest(
//                                type = 1,
//                                id = deviceId,
//                                params = DeviceControlParams(switch = "on") // lub "off"
//                            )
//                            val response = apiService.setDeviceStatus(requestBody)
//
//                            if (response.error == 0 && response.msg == "ok") {
//                                Log.i(
//                                    TAG,
//                                    "Pomyślnie zmieniono status urządzenia $deviceId na OFF przez REST API."
//                                )
//                                delay(TimeUnit.SECONDS.toMillis(5))
//                                sendNotification(context, "Brama została wyłączona.")
//
//                                // Logika "10 minut nieaktywna, potem sprawdzanie"
//                                // Uruchomienie WorkManagera po 10 minutach
//                                Log.d(
//                                    "GeofenceReceiver",
//                                    "Aplikacja nieaktywna na 10 minut. Zaplanowano sprawdzenie."
//                                )
//                                delay(TimeUnit.MINUTES.toMillis(10)) // 10 minut nieaktywności
//                                // Po 10 minutach, wykonaj sprawdzenie lokalizacji
//
//                            } else {
//                                Log.e(
//                                    TAG,
//                                    "Błąd zmiany statusu urządzenia $deviceId na OFF: ${response.msg ?: "Nieznany błąd"} (Kod: ${response.error})"
//                                )
//                                sendNotification(
//                                    context,
//                                    "Błąd wyłączania bramy: ${response.msg ?: "Nieznany błąd"}"
//                                )
//                            }
//                        } catch (e: Exception) {
//                            Log.e(
//                                TAG,
//                                "Błąd sieci podczas zmiany statusu urządzenia $deviceId na OFF: ${e.message}",
//                                e
//                            )
//                            sendNotification(
//                                context,
//                                "Błąd sieci podczas wyłączania bramy: ${e.message}"
//                            )
//                        }
//
//                    }
//                } else {
//                    Log.d(
//                        "GeofenceReceiver",
//                        "Zdarzenie EXIT, ale nadal wewnątrz wielokąta. Ignoruję."
//                    )
//                }
            }

            else -> {
                Log.d("GeofenceReceiver", "Nieznane zdarzenie Geofence: $geofenceTransition")
            }
        }
    }

    private fun sendNotification(context: Context?, message: String) {
        if (context == null) return

        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("eWeLink Automatyka")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Check for Android version and handle notification permission request
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                with(NotificationManagerCompat.from(context)) {
                    notify(notificationId, builder.build())
                }
            } else {
                Log.w(
                    "GeofenceReceiver",
                    "Brak uprawnień do wysyłania powiadomień na Androidzie 13+."
                )
            }
        } else {
            // Dla Androida < 13, uprawnienia do powiadomień są domyślnie przyznane
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        }
    }

    // Maybe it will be used in future
//    private fun calculateDistanceToPolygon(currentLocation: LatLng, polygon: List<LatLng>): Float {
//        var minDistance = Float.MAX_VALUE
//        for (point in polygon) {
//            val results = FloatArray(1)
//            android.location.Location.distanceBetween(
//                currentLocation.latitude, currentLocation.longitude,
//                point.latitude, point.longitude,
//                results
//            )
//            val distanceMeters = results[0]
//            if (distanceMeters < minDistance) {
//                minDistance = distanceMeters
//            }
//        }
//        Log.d(
//            "GeofenceReceiver",
//            "Odległość do najbliższego punktu wielokąta: ${minDistance / 1000f} km"
//        )
//        return minDistance / 1000f // Zwróć w kilometrach
//    }

//    private fun calculateDynamicInterval(distanceKm: Float): Long {
//        return when {
//            distanceKm < 1 -> TimeUnit.MINUTES.toMillis(1) // Bardzo blisko, sprawdzaj często
//            distanceKm < 5 -> TimeUnit.MINUTES.toMillis(5) // Blisko
//            distanceKm < 20 -> TimeUnit.MINUTES.toMillis(15) // Średnia odległość
//            distanceKm < 50 -> TimeUnit.MINUTES.toMillis(30) // Dalej
//            else -> TimeUnit.HOURS.toMillis(1) // Bardzo daleko, sprawdzaj rzadziej
//        }
//    }

    private fun sendLogToMainActivity(context: Context, message: String) {
        val intent = Intent(ACTION_LOG_UPDATE)
        intent.putExtra(EXTRA_LOG_MESSAGE, message)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

}