package eureto.opendoor.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eureto.opendoor.R
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.network.EwelinkApiClient
import eureto.opendoor.network.EwelinkDevices
import eureto.opendoor.network.EwelinkWebSocketClient
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit


/**
 * This service monitors the user's location and manages geofences based on a defined polygon.
 * It connects to the eWeLink WebSocket to control a device when entering or exiting the geofenced area.
 * The service runs in the foreground to ensure it remains active
 */
class LocationMonitoringService : Service() {

    private lateinit var geofencingClient: GeofencingClient
    private lateinit var appPreferences: AppPreferences
    private lateinit var ewelinkWebSocketClient: EwelinkWebSocketClient
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private var deviceIdToControl: String? = null
    private var polygonCoordinates: List<LatLng>? = null
    private var isInsideGeofence = false // Stan, czy użytkownik jest w obszarze

    // Static values for intents and notifications
    companion object {
        const val ACTION_LOG_UPDATE = "eureto.opendoor.action.LOG_UPDATE"
        const val EXTRA_LOG_MESSAGE = "eureto.opendoor.extra.LOG_MESSAGE"
        const val NOTIFICATION_CHANNEL_ID = "eureto_opendoor_location_channel"
        const val NOTIFICATION_ID = 123
        const val GEOFENCE_REQUEST_ID = "home_area_geofence"
        const val GEOFENCE_RADIUS_METERS = 1000f // TODO: Maybe add option to set radius in app
        const val ACTION_GEOFENCE_TRANSITION = "eureto.opendoor.ACTION_GEOFENCE_TRANSITION"
        const val ACTION_OPEN_GATE = "eureto.opendoor.ACTION_OPEN_GATE"
        const val ACTION_STOP_SERVICE = "eureto.opendoor.ACTION_STOP_SERVICE"
    }


    override fun onCreate() {
        super.onCreate()
        geofencingClient = LocationServices.getGeofencingClient(this)
        appPreferences = EwelinkApiClient.getAppPreferences()
        ewelinkWebSocketClient = EwelinkApiClient.createWebSocketClient()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    /**
     * Called by the system every time a client explicitly starts the service by calling Context.startService,
     * providing the arguments it supplied and a unique integer token representing the start request.
     *
     * Do not call this method directly.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "onStartCommand")

        deviceIdToControl = intent?.getStringExtra("deviceId")
        val polygonJson = intent?.getStringExtra("polygonJson")

        // Check if deviceId and polygon are provided
        if (deviceIdToControl.isNullOrEmpty() || polygonJson.isNullOrEmpty()) {
            Log.e("LocationService", "Brak deviceId lub polygonJson. Zatrzymuję usługę.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Parse polygon coordinates from JSON
        try {
            val type = object : TypeToken<List<LatLng>>() {}.type
            polygonCoordinates = gson.fromJson(polygonJson, type)
            if (polygonCoordinates.isNullOrEmpty() || polygonCoordinates!!.size < 3) {
                Log.e("LocationService", "Nieprawidłowe współrzędne wielokąta. Zatrzymuję usługę.")
                stopSelf()
                return START_NOT_STICKY
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Błąd parsowania współrzędnych wielokąta: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }


        startForeground(NOTIFICATION_ID, createNotification("Monitorowanie lokalizacji aktywne.").build())


        // TODO: Do i need this websocket?
        // Connect to WebSocket if not already connected
        if (ewelinkWebSocketClient.webSocket == null) {
            ewelinkWebSocketClient.connect()
        }

        addGeofences()

        // TODO: Check if it is needed in documentation
        // Start ping coroutine to keep WebSocket alive
        serviceScope.launch {
            while (isActive) {
                delay(TimeUnit.SECONDS.toMillis(30)) // Send ping every 30 seconds
                ewelinkWebSocketClient.sendPing()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "onDestroy executed")
        removeGeofences()
        ewelinkWebSocketClient.disconnect()
        serviceScope.cancel()
        stopForeground(true)
    }

    // Create a notification channel for foreground service
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Monitorowanie lokalizacji",
                NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.description = "Kanał dla powiadomień o monitorowaniu lokalizacji eWeLink."
            serviceChannel.enableLights(false)
            serviceChannel.enableVibration(false)
            serviceChannel.setSound(null, null)
            serviceChannel.lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE

            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    // Creates notification with given message and intent to open MainActivity
    private fun createNotification(message: String): NotificationCompat.Builder {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent for the "Open Gate" button
        val openGateIntent = Intent(this, LocationMonitoringService::class.java).apply {
            action = ACTION_OPEN_GATE
        }
        val openGatePendingIntent: PendingIntent = PendingIntent.getService(
            this,
            1, // Unique request code
            openGateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for the "Stop Service" button
        val stopServiceIntent = Intent(this, LocationMonitoringService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopServicePendingIntent: PendingIntent = PendingIntent.getService(
            this,
            2, // Unique request code
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("eWeLink Automatyka")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Make notification non-swipable - THIS DOES NOT WORK on android 15, on android 11 works well
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_launcher_foreground, "Otwórz bramę", openGatePendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Zatrzymaj", stopServicePendingIntent)
    }


    private fun addGeofences() {
        sendLogToMainActivity("Wykonuję funkcję addGeofences()")
        // Permission Check
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!(hasFineLocationPermission || hasCoarseLocationPermission)) {
            Log.e("LocationService", "Brak uprawnień do lokalizacji podczas dodawania Geofence. Nie dodaję.")
            updateNotification("Błąd: Brak uprawnień do lokalizacji przy tworzeniu geofence.")
            return
        }

        // Check Google Play Services availability
        val gpa = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val playServicesStatus = gpa.isGooglePlayServicesAvailable(this)
        if (playServicesStatus != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            val msg = "Google Play Services niedostępne (kod $playServicesStatus). Nie można dodać geofence."
            Log.e("LocationService", msg)
            updateNotification("Błąd: $msg")
            return
        }

        val polygon = polygonCoordinates
        if (polygon.isNullOrEmpty() || polygon.size < 3) {
            Log.e("LocationService", "Brak zdefiniowanego wielokąta do geofencingu.")
            return
        }

        // Calculate the centroid of the polygon and then use big radius for geofence
        val centroid = calculatePolygonCentroid(polygon)

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_REQUEST_ID)
            .setCircularRegion(centroid.latitude, centroid.longitude, GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .addGeofence(geofence)
            .setInitialTrigger(0) // Sprawdź przy dodawaniu, 0 oznacza że nie będzie triggera jeśli użytkownik jest w strefie w czasie tworzenia geofencingu
            .build()

        val geofencePendingIntent: PendingIntent by lazy {
            val intent = Intent(this, GeofenceTransitionsReceiver::class.java).apply {
                action = ACTION_GEOFENCE_TRANSITION
            }
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        }

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("LocationService", "Geofence dodany pomyślnie.")
                updateNotification("Monitorowanie aktywne: Obszar dodany.")
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Błąd dodawania Geofence: ${e.message}", e)
                if (e is com.google.android.gms.common.api.ApiException) {
                    when (e.statusCode) {
                        com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> {
                            updateNotification("Błąd geofencingu: usługa geofence niedostępna (kod 1000). Upewnij się, że precyzyjna lokalizacja jest włączona")
                        }
                        com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> {
                            updateNotification("Błąd geofencingu: za dużo geofence'ów (kod 1001). Usuń niepotrzebne geofency lub zresetuj aplikację.")
                        }
                        com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> {
                            updateNotification("Błąd geofencingu: za dużo PendingIntent (kod 1002).")
                        }
                        else -> {
                            updateNotification("Błąd dodawania Geofence: ${e.statusCode} - ${e.message}")
                        }
                    }
                } else {
                    updateNotification("Błąd dodawania Geofence: ${e?.message}")
                }
            }
    }

    private fun removeGeofences() {
        sendLogToMainActivity("Usuwanie Geofences w removeGeofences()")
        geofencingClient.removeGeofences(listOf(GEOFENCE_REQUEST_ID))
            .addOnSuccessListener {

                sendLogToMainActivity("LocationService: Geofence usunięty pomyślnie.")
            }
            .addOnFailureListener { e ->
                sendLogToMainActivity("LocationService: Błąd usuwania Geofence: ${e.message}")
            }
    }

    // Calculate the centroid of a polygon given its vertices
    private fun calculatePolygonCentroid(polygon: List<LatLng>): LatLng {
        var latitude = 0.0
        var longitude = 0.0
        for (point in polygon) {
            latitude += point.latitude
            longitude += point.longitude
        }
        return LatLng(latitude / polygon.size, longitude / polygon.size)
    }

    private fun updateNotification(message: String) {
        sendLogToMainActivity("updateNotification: $message")
        val notification = createNotification(message).build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendLogToMainActivity(message: String) {
        val intent = Intent(ACTION_LOG_UPDATE)
        intent.putExtra(EXTRA_LOG_MESSAGE, message)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}