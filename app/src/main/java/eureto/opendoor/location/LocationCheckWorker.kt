package eureto.opendoor.location

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.compose.ui.input.key.type
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.maps.android.PolyUtil
import eureto.opendoor.R
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.location.GeofenceTransitionsReceiver.Companion.ACTION_LOG_UPDATE
import eureto.opendoor.location.GeofenceTransitionsReceiver.Companion.EXTRA_LOG_MESSAGE
import eureto.opendoor.network.EwelinkDevices
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.time.TimeSource


class LocationCheckWorker(appContext: Context, workerParams: WorkerParameters): CoroutineWorker(appContext, workerParams) {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val notificationChannelId = LocationMonitoringService.NOTIFICATION_CHANNEL_ID // same as in LocationMonitoringService
    private val notificationId = 1001

    // TODO: move this funciton to locationMonitoringService as it should be killable by user
    // but doWork is managed by system.
    override suspend fun doWork(): Result{
        val appPreferences = eureto.opendoor.network.EwelinkApiClient.getAppPreferences()

        if(appPreferences.getIsLocationCheckWorkerRunning()) return Result.failure()
        appPreferences.setIsLocationCheckWorkerRunning(true)

        val isGeofenceEnabled = appPreferences.getIsGeofenceEnabled()
        val context = applicationContext
        val deviceId = inputData.getString("deviceId")
        val polygonCoordinatesJSON = inputData.getString("polygonCoordinates")
        val type = object : TypeToken<List<LatLng>>() {}.type
        val polygonCoordinates: List<LatLng> = Gson().fromJson(polygonCoordinatesJSON, type)

        if(deviceId == null || polygonCoordinates == null){
            sendNotification(context, "Błąd: Brak wymaganych danych do sprawdzania lokalizacji.")
            sendLogToMainActivity(context, "GeofenceReceiver: Brak wymaganych danych do sprawdzania lokalizacji")
            appPreferences.setIsLocationCheckWorkerRunning(false)
            return Result.failure()
        }

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
            sendNotification(context, "Błąd: Brak uprawnień do lokalizacji do sprawdzania w tle.")
            sendLogToMainActivity(context, "GeofenceReceiver: Brak uprawnień do lokalizacji do sprawdzania w tle. kod:asdf1")
            appPreferences.setIsLocationCheckWorkerRunning(false)
            return Result.failure()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val timeSource = TimeSource.Monotonic
        val markStart = timeSource.markNow()
        var timeElapsedInMinutes: Long = 0
        var gateOpened: Boolean = false

        while (timeElapsedInMinutes < 10 && !gateOpened && isGeofenceEnabled) {
            try {
                val cts = CancellationTokenSource()
                val location: Location? = try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cts.token
                    ).await()
                } catch (e: Exception) {
                    sendLogToMainActivity(context, "GeofenceReceiver - Błąd pobierania lokalizacji: ${e.message}")
                    null
                }

                // Getting current location of user
                if (location != null) {
                    val currentLocation = LatLng(location.latitude, location.longitude)
                    sendLogToMainActivity(context, "Aktualna lokalizacja: $currentLocation")

                    val isNowInsidePolygon =
                        PolyUtil.containsLocation(currentLocation, polygonCoordinates, true)

                    if (isNowInsidePolygon) {
                        sendLogToMainActivity(
                            context,"GeofenceReceiver: Użytkownik wrócił do obszaru. Włączam bramę."
                        )
                        sendNotification(context, "Wróciłeś do domu! Uruchamiam bramę...")
                        EwelinkDevices.toggleDevice(deviceId, "on")
                        gateOpened = true
                    }
                }

            } catch (e: Exception) {
                sendNotification(context, "Błąd podczas sprawdzania lokalizacji")
                sendLogToMainActivity(context, "GeofenceReceiver: Błąd podczas sprawdzania lokalizacji: ${e.message}")
            }

            // small delay to save battery
            if (!gateOpened) delay(TimeUnit.SECONDS.toMillis(1))

            timeElapsedInMinutes = markStart.elapsedNow().inWholeMinutes
            sendLogToMainActivity(context, "Aktualnie sprawdzanie lokalizacji trwa $timeElapsedInMinutes minute/y")
        }
        appPreferences.setIsLocationCheckWorkerRunning(false)
        return Result.success()

    }

    // TODO: make one place for sendign notification
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
                sendNotification(context, "Brak uprawnień do wysyłania powiadomień")
                sendLogToMainActivity(context, "Brak uprawnień do wysyłania powiadomień KOD:asdsafyd87vcx")
            }
        } else {
            // Dla Androida < 13, uprawnienia do powiadomień są domyślnie przyznane
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        }
    }
    private fun sendLogToMainActivity(context: Context, message: String) {
        val intent = Intent(ACTION_LOG_UPDATE)
        intent.putExtra(EXTRA_LOG_MESSAGE, message)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}