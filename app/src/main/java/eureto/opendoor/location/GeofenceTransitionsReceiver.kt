package eureto.opendoor.location
import android.app.PendingIntent
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
import eureto.opendoor.location.LocationMonitoringService.Companion.ACTION_OPEN_GATE
import eureto.opendoor.network.EwelinkApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.jvm.java
import android.R.attr.action
import eureto.opendoor.location.LocationMonitoringService.Companion.ACTION_START_LOCATION
import eureto.opendoor.logging.MyLog

/**
 * BroadcastReceiver to handle geofence transition events.
 * It processes ENTER and EXIT events, checks if the user is within a defined polygon,
 * and controls a device via the eWeLink API accordingly.
 * It also manages notifications and logs events to MainActivity.
 */

class GeofenceTransitionsReceiver : BroadcastReceiver() {

    private val notificationId = 1001
    private val notificationChannelId = LocationMonitoringService.NOTIFICATION_CHANNEL_ID

    override fun onReceive(context: Context?, intent: Intent?) {
        MyLog.addLogMessageIntoFile(context ?: return, "Otrzymano złgłoszenie Geofence")

        if (context == null || intent?.action != LocationMonitoringService.ACTION_GEOFENCE_TRANSITION) {
            MyLog.addLogMessageIntoFile(context, "Nieznana akcja lub brak kontekstu: ${intent?.action}")
            return
        }

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            MyLog.addLogMessageIntoFile(context, "Błąd: geofencingEvent jest nullem.")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            sendNotification(context, "Błąd monitorowania lokalizacji: $errorMessage")
            MyLog.addLogMessageIntoFile(context, "Błąd Geofence: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        // get the stored preferences
        val appPreferences = EwelinkApiClient.getAppPreferences()
        val deviceId = appPreferences.getSelectedDeviceId()
        val polygonJson = appPreferences.getPolygonCoordinates()

        if (deviceId.isNullOrEmpty() || polygonJson.isNullOrEmpty()) {
            sendNotification(
                context,
                "Automatyka wyłączona: brak konfiguracji urządzenia lub obszaru."
            )
            return
        }


        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                MyLog.addLogMessageIntoFile(context, "Zdarzenie GEOFENCE_TRANSITION_DWELL")
                MyLog.addLogMessageIntoFile(context, "Sprawdzanie lokalizacji w tle...")
                val locationIntent = Intent(context, LocationMonitoringService::class.java).apply {
                    action = ACTION_START_LOCATION
                }
                ContextCompat.startForegroundService(context, locationIntent)
            }

            //TODO: Dodaj obsługe zdarzenia EXIT w geofence receiver
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("GeofenceReceiver", "Zdarzenie GEOFENCE_TRANSITION_EXIT")
                MyLog.addLogMessageIntoFile(context, "Zdarzenie GEOFENCE_TRANSITION_EXIT ale robie return")
                return
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


}