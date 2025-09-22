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
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eureto.opendoor.R
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.network.EwelinkApiClient
import eureto.opendoor.network.model.DeviceControlParams
import eureto.opendoor.network.model.DeviceControlRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await
//TODO: usuń niepotrzebne importy, jeśli są

class GeofenceTransitionsReceiver : BroadcastReceiver() {

    private val notificationId = 1001
    private val notificationChannelId = LocationMonitoringService.NOTIFICATION_CHANNEL_ID
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Main) // Używamy scope dla coroutines
    private val TAG = "GeofenceTransitionsReceiver"

    companion object {
        const val ACTION_LOG_UPDATE = "eureto.opendoor.action.LOG_UPDATE" // Ta sama akcja co w LocationMonitoringService
        const val EXTRA_LOG_MESSAGE = "eureto.opendoor.extra.LOG_MESSAGE" // Ten sam klucz dla wiadomości
    }

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
            Log.e("GeofenceReceiver", "Brak wybranego urządzenia lub zdefiniowanego obszaru. Zatrzymuję operacje.")
            sendNotification(context, "Automatyka wyłączona: brak konfiguracji urządzenia lub obszaru.")
            return
        }

        // Sprawdź, czy lokalizacja rzeczywiście znajduje się w wielokącie (dla wejścia)
        val isInsidePolygon = if (triggeringLocation != null && polygonCoordinates.size >= 3) {
            PolyUtil.containsLocation(
                LatLng(triggeringLocation.latitude, triggeringLocation.longitude),
                polygonCoordinates,
                true // OnSegment = true, aby punkty na krawędziach też były zaliczane
            )
        } else {
            false
        }

        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d("GeofenceReceiver", "Zdarzenie GEOFENCE_TRANSITION_ENTER")
                if (isInsidePolygon) {
                    Log.d("GeofenceReceiver", "Lokalizacja wewnątrz wielokąta. Uruchamiam bramę.")
                    sendNotification(context, "Wjechałeś do terenu! Uruchamiam bramę...")
                    // Włącz bramę
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
                                Log.i(TAG, "Pomyślnie zmieniono status urządzenia $deviceId na ON przez REST API.")
                                delay(TimeUnit.SECONDS.toMillis(5)) // Poczekaj na potwierdzenie
                                sendNotification(context, "Brama została uruchomiona.")
                            } else {
                                Log.e(TAG, "Błąd zmiany statusu urządzenia $deviceId na ON: ${response.msg ?: "Nieznany błąd"} (Kod: ${response.error})")
                                sendNotification(context, "Błąd uruchamiania bramy: ${response.msg ?: "Nieznany błąd"}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Błąd sieci podczas zmiany statusu urządzenia $deviceId na ON: ${e.message}", e)
                            sendNotification(context, "Błąd sieci podczas uruchamiania bramy: ${e.message}")
                        }
                    }
                    // Reset flagi "nieaktywna" i wszelkich WorkManagerów
                } else {
                    Log.d("GeofenceReceiver", "Zdarzenie ENTER, ale poza wielokątem. Prawdopodobnie na krawędzi Geofence okrągłego.")
                    // Tutaj możesz zaimplementować dokładniejsze sprawdzanie lokalizacji
                    // lub poczekać na kolejne zdarzenie DWELL/ENTER, jeśli użytkownik faktycznie wejdzie
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("GeofenceReceiver", "Zdarzenie GEOFENCE_TRANSITION_EXIT")
                if (!isInsidePolygon) { // Upewnij się, że faktycznie jesteś poza wielokątem
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
                                Log.i(TAG, "Pomyślnie zmieniono status urządzenia $deviceId na OFF przez REST API.")
                                delay(TimeUnit.SECONDS.toMillis(5))
                                sendNotification(context, "Brama została wyłączona.")

                                // Logika "10 minut nieaktywna, potem sprawdzanie"
                                // Uruchomienie WorkManagera po 10 minutach
                                Log.d("GeofenceReceiver", "Aplikacja nieaktywna na 10 minut. Zaplanowano sprawdzenie.")
                                delay(TimeUnit.MINUTES.toMillis(10)) // 10 minut nieaktywności
                                // Po 10 minutach, wykonaj sprawdzenie lokalizacji
                                performLocationCheck(context, deviceId, polygonCoordinates, appPreferences)
                            } else {
                                Log.e(TAG, "Błąd zmiany statusu urządzenia $deviceId na OFF: ${response.msg ?: "Nieznany błąd"} (Kod: ${response.error})")
                                sendNotification(context, "Błąd wyłączania bramy: ${response.msg ?: "Nieznany błąd"}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Błąd sieci podczas zmiany statusu urządzenia $deviceId na OFF: ${e.message}", e)
                            sendNotification(context, "Błąd sieci podczas wyłączania bramy: ${e.message}")
                        }

                    }
                } else {
                    Log.d("GeofenceReceiver", "Zdarzenie EXIT, ale nadal wewnątrz wielokąta. Ignoruję.")
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
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                with(NotificationManagerCompat.from(context)) {
                    notify(notificationId, builder.build())
                }
            } else {
                Log.w("GeofenceReceiver", "Brak uprawnień do wysyłania powiadomień na Androidzie 13+.")
            }
        } else {
            // Dla Androida < 13, uprawnienia do powiadomień są domyślnie przyznane
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        }
    }

    // Funkcja do sprawdzania lokalizacji i dostosowywania interwałów
    private fun performLocationCheck(context: Context, deviceId: String, polygonCoordinates: List<LatLng>, appPreferences: AppPreferences) {
        // DODANO: Sprawdzenie uprawnień do lokalizacji
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!(hasFineLocationPermission || hasCoarseLocationPermission)) {
            Log.e("GeofenceReceiver", "Brak uprawnień do lokalizacji podczas performLocationCheck. Zatrzymuję.")
            sendNotification(context, "Błąd: Brak uprawnień do lokalizacji do sprawdzania w tle.")
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        scope.launch {
            try {
                // Użyj rozszerzenia .await() z kotlinx-coroutines-play-services
                val location = fusedLocationClient.lastLocation.await()
                if (location != null) {
                    val currentLocation = LatLng(location.latitude, location.longitude)
                    sendLogToMainActivity(context ,"Aktualna lokalizacja: $currentLocation");
                    val isNowInsidePolygon = PolyUtil.containsLocation(currentLocation, polygonCoordinates, true)

                    if (isNowInsidePolygon) {
                        Log.d("GeofenceReceiver", "Użytkownik wrócił do obszaru. Włączam bramę.")
                        sendLogToMainActivity(context ,"Użytkownik wrócił do obszaru. Włączam bramę.");
                        sendNotification(context, "Wróciłeś do terenu! Uruchamiam bramę...")
                        try {
                            val apiService = EwelinkApiClient.createApiService()
                            val requestBody = DeviceControlRequest(
                                type = 1,
                                id = deviceId,
                                params = DeviceControlParams(switch = "on") // lub "off"
                            )
                            val response = apiService.setDeviceStatus(requestBody)

                            if (response.error == 0 && response.msg == "ok") {
                                Log.i(TAG, "Pomyślnie zmieniono status urządzenia $deviceId na ON przez REST API (z performLocationCheck).")
                                sendLogToMainActivity(context, "Pomyślnie zmieniono status urządzenia $deviceId na ON przez REST API (z performLocationCheck).");
                            } else {
                                Log.e(TAG, "Błąd zmiany statusu urządzenia $deviceId na ON (z performLocationCheck): ${response.msg ?: "Nieznany błąd"} (Kod: ${response.error})")
                                sendNotification(context, "Błąd uruchamiania bramy po sprawdzeniu: ${response.msg ?: "Nieznany błąd"}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Błąd sieci podczas zmiany statusu urządzenia $deviceId na ON (z performLocationCheck): ${e.message}", e)
                            sendNotification(context, "Błąd sieci podczas uruchamiania bramy po sprawdzeniu: ${e.message}")
                        }
                    } else {
                        Log.d("GeofenceReceiver", "Użytkownik nadal poza obszarem.")
                        // Oblicz odległość do najbliższego punktu wielokąta
                        val distance = calculateDistanceToPolygon(currentLocation, polygonCoordinates)
                        val nextCheckIntervalMillis = calculateDynamicInterval(distance)
                        Log.d("GeofenceReceiver", "Następne sprawdzenie za: ${nextCheckIntervalMillis / 1000} sekund.")
                        sendNotification(context, "Jesteś poza terenem. Następne sprawdzenie za ${nextCheckIntervalMillis / 60000} min.")

                        // Zaplanuj kolejne sprawdzenie za pomocą WorkManagera
                        // Tutaj należałoby użyć WorkManager do zaplanowania `LocationCheckWorker`
                        // z odpowiednim opóźnieniem, aby nie blokować BroadcastReceivera
                        // i aby system poprawnie zarządzał zadaniem.
                        // Dla uproszczenia na razie używam delay()
                        delay(nextCheckIntervalMillis)
                        performLocationCheck(context, deviceId, polygonCoordinates, appPreferences) // Rekurencyjne wywołanie (dla demo)
                    }
                } else {
                    Log.e("GeofenceReceiver", "Nie udało się pobrać ostatniej lokalizacji.")
                    // Zaplanuj ponowne sprawdzenie po krótkim czasie
                    delay(TimeUnit.MINUTES.toMillis(5))
                    performLocationCheck(context, deviceId, polygonCoordinates, appPreferences)
                }
            } catch (e: Exception) {
                Log.e("GeofenceReceiver", "Błąd podczas sprawdzania lokalizacji: ${e.message}", e)
                sendNotification(context, "Błąd podczas sprawdzania lokalizacji: ${e.message}")
            }
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
        Log.d("GeofenceReceiver", "Odległość do najbliższego punktu wielokąta: ${minDistance / 1000f} km")
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