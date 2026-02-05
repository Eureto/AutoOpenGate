package eureto.opendoor.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.PolyUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eureto.opendoor.R
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.network.EwelinkApiClient
import eureto.opendoor.network.model.DeviceControlParams
import eureto.opendoor.network.model.DeviceControlRequest
import eureto.opendoor.network.EwelinkDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await
import java.lang.Thread.sleep
import kotlin.time.TimeSource
import kotlin.time.measureTime

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

    /**
     * Constants for local broadcasts to MainActivity. It is used to send log messages to MainActivity and display them in the UI.
     */
    companion object {
        const val ACTION_LOG_UPDATE =
            "eureto.opendoor.action.LOG_UPDATE" // Ta sama akcja co w LocationMonitoringService
        const val EXTRA_LOG_MESSAGE =
            "eureto.opendoor.extra.LOG_MESSAGE" // Ten sam klucz dla wiadomości
    }

    //
    override fun onReceive(context: Context?, intent: Intent?) {
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
        val triggeringLocation = geofencingEvent.triggeringLocation

        // Pobierz zapisane dane
        val appPreferences = EwelinkApiClient.getAppPreferences()
        val deviceId = appPreferences.getSelectedDeviceId()
        val polygonJson = appPreferences.getPolygonCoordinates()
        val polygonCoordinates: List<LatLng>? = try {
            if (polygonJson.isNullOrEmpty()) null
            else gson.fromJson(polygonJson, object : TypeToken<List<LatLng>>() {}.type)
        } catch (e: Exception) {
            Log.e("GeofenceReceiver", "Błąd parsowania wielokąta: ${e.message}")
            null
        }

        if (deviceId.isNullOrEmpty() || polygonCoordinates.isNullOrEmpty()) {
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
                Log.d("GeofenceReceiver", "Zdarzenie GEOFENCE_TRANSITION_ENTER")
                performLocationCheck(context, deviceId, polygonCoordinates, appPreferences)
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("GeofenceReceiver", "Zdarzenie GEOFENCE_TRANSITION_EXIT")
                if (false) { // Upewnij się, że faktycznie jesteś poza wielokątem
                    Log.d("GeofenceReceiver", "Lokalizacja poza wielokątem. Opuściłeś teren.")
                    sendNotification(context, "Opuściłeś teren. Brama zostanie wyłączona.")
                    scope.launch {

                        try {
                            val apiService = EwelinkApiClient.createApiService()
                            val requestBody = DeviceControlRequest(
                                type = 1,
                                id = deviceId,
                                params = DeviceControlParams(switch = "on") // lub "off"
                            )
                            val response = apiService.setDeviceStatus(requestBody)

                            if (response.error == 0 && response.msg == "ok") {
                                Log.i(
                                    TAG,
                                    "Pomyślnie zmieniono status urządzenia $deviceId na OFF przez REST API."
                                )
                                delay(TimeUnit.SECONDS.toMillis(5))
                                sendNotification(context, "Brama została wyłączona.")

                                // Logika "10 minut nieaktywna, potem sprawdzanie"
                                // Uruchomienie WorkManagera po 10 minutach
                                Log.d(
                                    "GeofenceReceiver",
                                    "Aplikacja nieaktywna na 10 minut. Zaplanowano sprawdzenie."
                                )
                                delay(TimeUnit.MINUTES.toMillis(10)) // 10 minut nieaktywności
                                // Po 10 minutach, wykonaj sprawdzenie lokalizacji
                                performLocationCheck(
                                    context,
                                    deviceId,
                                    polygonCoordinates,
                                    appPreferences
                                )
                            } else {
                                Log.e(
                                    TAG,
                                    "Błąd zmiany statusu urządzenia $deviceId na OFF: ${response.msg ?: "Nieznany błąd"} (Kod: ${response.error})"
                                )
                                sendNotification(
                                    context,
                                    "Błąd wyłączania bramy: ${response.msg ?: "Nieznany błąd"}"
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Błąd sieci podczas zmiany statusu urządzenia $deviceId na OFF: ${e.message}",
                                e
                            )
                            sendNotification(
                                context,
                                "Błąd sieci podczas wyłączania bramy: ${e.message}"
                            )
                        }

                    }
                } else {
                    Log.d(
                        "GeofenceReceiver",
                        "Zdarzenie EXIT, ale nadal wewnątrz wielokąta. Ignoruję."
                    )
                }
            }

            else -> {
                Log.d("GeofenceReceiver", "Nieznane zdarzenie Geofence: $geofenceTransition")
            }
        }
    }

    private fun sendNotification(context: Context?, message: String) {
        if (context == null) return

        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Zmień na ikonę aplikacji
            .setContentTitle("eWeLink Automatyka")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // DODANO: Sprawdzenie uprawnień do wysyłania powiadomień
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

    // This functions handles everything after user enters geofence area
    // It check location of user for 10 minutes and if user enters selected are it opens the gate
    // If user doesn't enter selected area it does nothing
    private fun performLocationCheck(
        context: Context,
        deviceId: String,
        polygonCoordinates: List<LatLng>,
        appPreferences: AppPreferences
    ) {
        // Checking permissions
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!(hasFineLocationPermission || hasCoarseLocationPermission)) {
            Log.e(
                "GeofenceReceiver",
                "Brak uprawnień do lokalizacji podczas performLocationCheck. Zatrzymuję."
            )
            sendNotification(context, "Błąd: Brak uprawnień do lokalizacji do sprawdzania w tle.")
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val timeSource = TimeSource.Monotonic
        val markStart = timeSource.markNow()
        var timeElapsedInMinutes: Long = 0
        var gateOpened: Boolean = false;

        while(timeElapsedInMinutes < 10 && !gateOpened) {
            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        //getting current location of user
                        val currentLocation = LatLng(location.latitude, location.longitude)
                        sendLogToMainActivity(context, "Aktualna lokalizacja: $currentLocation");

                        val isNowInsidePolygon =
                            PolyUtil.containsLocation(currentLocation, polygonCoordinates, true)

                        if (isNowInsidePolygon) {
                            Log.d(
                                "GeofenceReceiver",
                                "Użytkownik wrócił do obszaru. Włączam bramę."
                            )
                            sendLogToMainActivity(
                                context,
                                "Użytkownik wrócił do obszaru. Włączam bramę."
                            );
                            sendNotification(context, "Wróciłeś do domu! Uruchamiam bramę...")
                            EwelinkDevices.toggleDevice(deviceId, "on")
                            gateOpened = true;
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GeofenceReceiver", "Błąd podczas sprawdzania lokalizacji: ${e.message}", e)
                sendNotification(context, "Błąd podczas sprawdzania lokalizacji")
            }

            timeElapsedInMinutes = markStart.elapsedNow().inWholeMinutes
            Log.d(
                "GeofenceReceiver",
                "Aktualnie sprawdzanie traw już $timeElapsedInMinutes minut"
            )
        }
    }

    // Obliczanie odległości do najbliższego punktu wielokąta
    private fun calculateDistanceToPolygon(currentLocation: LatLng, polygon: List<LatLng>): Float {
        var minDistance = Float.MAX_VALUE
        for (point in polygon) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                point.latitude, point.longitude,
                results
            )
            val distanceMeters = results[0]
            if (distanceMeters < minDistance) {
                minDistance = distanceMeters
            }
        }
        Log.d(
            "GeofenceReceiver",
            "Odległość do najbliższego punktu wielokąta: ${minDistance / 1000f} km"
        )
        return minDistance / 1000f // Zwróć w kilometrach
    }

    // Dynamiczne obliczanie interwału sprawdzania (w milisekundach)
    private fun calculateDynamicInterval(distanceKm: Float): Long {
        return when {
            distanceKm < 1 -> TimeUnit.MINUTES.toMillis(1) // Bardzo blisko, sprawdzaj często
            distanceKm < 5 -> TimeUnit.MINUTES.toMillis(5) // Blisko
            distanceKm < 20 -> TimeUnit.MINUTES.toMillis(15) // Średnia odległość
            distanceKm < 50 -> TimeUnit.MINUTES.toMillis(30) // Dalej
            else -> TimeUnit.HOURS.toMillis(1) // Bardzo daleko, sprawdzaj rzadziej
        }
    }

    private fun sendLogToMainActivity(context: Context, message: String) {
        val intent = Intent(ACTION_LOG_UPDATE)
        intent.putExtra(EXTRA_LOG_MESSAGE, message)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

}