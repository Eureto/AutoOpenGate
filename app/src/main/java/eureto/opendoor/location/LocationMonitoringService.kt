package eureto.opendoor.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.maps.android.PolyUtil
import eureto.opendoor.R
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.network.EwelinkApiClient
import eureto.opendoor.network.EwelinkDevices
import eureto.opendoor.logging.MyLog
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.time.TimeSource


/**
 * This service monitors the user's location and manages geofences based on a defined polygon.
 * It connects to the eWeLink WebSocket to control a device when entering or exiting the geofenced area.
 * The service runs in the foreground to ensure it remains active
 */
class LocationMonitoringService : Service() {

    private lateinit var geofencingClient: GeofencingClient
    private lateinit var appPreferences: AppPreferences
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var deviceIdToControl: String? = null
    private var polygonJson: String? = null
    private var polygonCoordinates: List<LatLng>? = null
    private var polygonCenterJSON: String? = null
    private var polygonCenter: LatLng? = null
    private var geofenceRadius: Int? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var locationCheckJob: Job? = null
    private var isBackgroundLocationLoopRunning = false

    // Static values for intents and notifications
    companion object {
        const val ACTION_LOG_UPDATE = "eureto.opendoor.action.LOG_UPDATE"
        const val EXTRA_LOG_MESSAGE = "eureto.opendoor.extra.LOG_MESSAGE"
        const val NOTIFICATION_CHANNEL_ID = "eureto_opendoor_location_channel"
        const val NOTIFICATION_ID = 123
        const val GEOFENCE_REQUEST_ID = "home_area_geofence"
        const val ACTION_GEOFENCE_TRANSITION = "eureto.opendoor.ACTION_GEOFENCE_TRANSITION"
        const val ACTION_OPEN_GATE = "eureto.opendoor.ACTION_OPEN_GATE"
        const val ACTION_STOP_SERVICE = "eureto.opendoor.ACTION_STOP_SERVICE"
        const val ACTION_START_LOCATION = "eureto.opendoor.ACTION_START_LOCATION"
    }


    override fun onCreate() {
        super.onCreate()
        geofencingClient = LocationServices.getGeofencingClient(this)
        appPreferences = EwelinkApiClient.getAppPreferences()
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

        deviceIdToControl = appPreferences.getSelectedDeviceId()
        polygonJson = appPreferences.getPolygonCoordinates()
        polygonCenterJSON = appPreferences.getPolygonCenter()
        geofenceRadius = appPreferences.getGeofenceRadius()


        if (deviceIdToControl.isNullOrEmpty() || polygonJson.isNullOrEmpty() || polygonCenterJSON == null || geofenceRadius == null) {
            Toast.makeText(this, "Brak zapisanych ustawień lokalizacji. Zatrzymuje usługę", Toast.LENGTH_LONG).show()
            Log.e("LocationService", "Brak zapisanych ustawień lokalizacji. Zatrzymuję usługę.")
            stopSelf()
            return START_NOT_STICKY
        }


        if (polygonCenterJSON.isNullOrEmpty()) {
            MyLog.addLogMessageIntoFile(this, "Brak polygonCenter, return")
            return START_NOT_STICKY
        }

        // Retrive from JSON center points of polygon
        val polygonCenterJSON_IMMUTABLE = polygonCenterJSON
        polygonCenter = try {
            val parts = polygonCenterJSON_IMMUTABLE!!.split(",")
            val lng = parts[0].toDouble()
            val lat = parts[1].toDouble()
            LatLng(lat, lng)
        } catch (e: Exception) {
            Log.e("LocationService", "Błąd parsowania środka wielokąta: $polygonCenter")
            return START_NOT_STICKY
        }

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

        when (intent?.action) {
            ACTION_OPEN_GATE -> {
                MyLog.addLogMessageIntoFile(this,"Otwarto brame ręcznie z poziomu powiadomienia")
                val id = deviceIdToControl ?: appPreferences.getSelectedDeviceId()
                if (!id.isNullOrEmpty()) {
                    EwelinkDevices.toggleDevice(id, "on")
                }
                return START_STICKY;
            }

            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START_LOCATION -> {
                if(locationCheckJob?.isActive == false || locationCheckJob == null) {
                    locationCheckJob = scope.launch {
                        checkLocationInGeofence()
                    }
                }else {
                    MyLog.addLogMessageIntoFile(this,"Próba ponownego uruchominenia sprawdzania lokalizacji")
                }
                return START_STICKY
            }
        }


        startForeground(
            NOTIFICATION_ID,
            createMainNotification("Monitorowanie lokalizacji aktywne.").build()
        )

        addGeofences()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "onDestroy executed")
        removeGeofences()
        serviceScope.cancel()
        stopForeground(true)
    }

    ///////////////////////////////////
    //      MANUAL CHECKING          //
    ///////////////////////////////////

    // TODO: make checkLocation function work with and without geofence
    private suspend fun checkLocationInGeofence() {
        MyLog.addLogMessageIntoFile(this, "inside checkLocation()")

        val context = applicationContext
        val deviceId = deviceIdToControl

        if (deviceId == null || polygonCoordinates == null) {
            updateMainNotification("Błąd: Brak wymaganych danych do sprawdzania lokalizacji.")
            MyLog.addLogMessageIntoFile(this, " Brak wymaganych danych do sprawdzania lokalizacji")
            return
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
            updateMainNotification("Błąd: Brak uprawnień do lokalizacji do sprawdzania w tle.")
            MyLog.addLogMessageIntoFile(this,"Brak uprawnień do lokalizacji do sprawdzania w tle. kod:asdf1")
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val timeSource = TimeSource.Monotonic
        val markStart = timeSource.markNow()
        var timeElapsedInMinutes: Long = 0
        var gateOpened: Boolean = false
        var dynamicDelay: Long = 1
        var location: Location? = null

        while (timeElapsedInMinutes < 60 && !gateOpened) {
            try {

                // TODO: replce it with constant location check rather than requesting it every second
                // Getting current location of user
                val cts = CancellationTokenSource()
                location = try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cts.token
                    ).await()
                } catch (e: Exception) {
                    MyLog.addLogMessageIntoFile(this,"Błąd pobierania lokalizacji: ${e.message}")
                    null
                }

                if (location != null) {
                    val currentLocation = LatLng(location.latitude, location.longitude)
                    MyLog.addLogMessageIntoFile(this,"Aktualna lokalizacja: $currentLocation")

                    // Check if user is in the geofence circle
                    var result = FloatArray(1)
                    Location.distanceBetween(
                        polygonCenter!!.latitude,
                        polygonCenter!!.longitude,
                        currentLocation.latitude,
                        currentLocation.longitude,
                        result)
                    val distanceFromCenterToUser = result[0]
                    if(geofenceRadius == null) {createInformationNotification("Błąd konfiguracji","Brak promienia geofence, aplikacja kończy działanie"); return}
                    if( distanceFromCenterToUser > geofenceRadius!!*1.5){
                        MyLog.addLogMessageIntoFile(this," Użytkownik nie jest w obszarze okręgu")
                        // brake
                    }

                    // Check if user entered area where gate should be opened
                    val isNowInsidePolygon =
                        PolyUtil.containsLocation(currentLocation, polygonCoordinates, true)

                    if (isNowInsidePolygon) {
                        MyLog.addLogMessageIntoFile(this,
                            "Użytkownik wrócił do obszaru. Włączam bramę."
                        )

                        // Create new notification to tell user that gate was opened
                        val timestamp = SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(Date())
                        createInformationNotification("Stan Bramy", "Brama została otworzona o godzienie: $timestamp")

                        EwelinkDevices.toggleDevice(deviceId, "on")
                        gateOpened = true
                    }
                }

            } catch (e: Exception) {
                updateMainNotification("Błąd podczas sprawdzania lokalizacji")
                MyLog.addLogMessageIntoFile(this,"Błąd podczas sprawdzania lokalizacji: ${e.message}")
            }

            delay(TimeUnit.SECONDS.toMillis(1))

            timeElapsedInMinutes = markStart.elapsedNow().inWholeMinutes
            if(!gateOpened) MyLog.addLogMessageIntoFile(this,"Aktualnie sprawdzanie lokalizacji trwa $timeElapsedInMinutes minute/y")
        }
        return

    }

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
        return minDistance / 1000f // return kilometers
    }

    private fun calculateDynamicInterval(distanceKm: Float): Long {
        return when {
            distanceKm < 1 -> TimeUnit.MINUTES.toMillis(10)
            distanceKm >= 1 -> { val timeout = distanceKm / 2 // div by two because if avg speed is 60 km/h then every kilometer is passed by one minute so i will check two times more to be precise
                TimeUnit.MINUTES.toMillis(timeout.toLong())}
            else -> TimeUnit.HOURS.toMillis(1) // Bardzo daleko, sprawdzaj rzadziej
        }
    }

    ///////////////////////////////////
    //          GEOFENCING           //
    ///////////////////////////////////
    private fun addGeofences() {
        MyLog.addLogMessageIntoFile(this,"Wykonuję dodawanie geofence")
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
            Log.e(
                "LocationService",
                "Brak uprawnień do lokalizacji podczas dodawania Geofence. Nie dodaję."
            )
            updateMainNotification("Błąd: Brak uprawnień do lokalizacji przy tworzeniu geofence.")
            return
        }

        // Check Google Play Services availability
        val gpa = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val playServicesStatus = gpa.isGooglePlayServicesAvailable(this)
        if (playServicesStatus != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            val msg =
                "Google Play Services niedostępne (kod $playServicesStatus). Nie można dodać geofence."
            Log.e("LocationService", msg)
            updateMainNotification("Błąd: $msg")
            return
        }

        val centroid = polygonCenter ?: return
        MyLog.addLogMessageIntoFile(this,"Centroid: $centroid")

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_REQUEST_ID)
            .setCircularRegion(centroid.latitude, centroid.longitude, geofenceRadius!!.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(60000) //62000
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .addGeofence(geofence)
            .setInitialTrigger(0)
            .build()

        val geofencePendingIntent: PendingIntent by lazy {
            val intent = Intent(this, GeofenceTransitionsReceiver::class.java).apply {
                action = ACTION_GEOFENCE_TRANSITION
            }
            PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("LocationService", "Geofence dodany pomyślnie.")
                MyLog.addLogMessageIntoFile(this,"Geofence dodany pomyślnie.")
                updateMainNotification("Monitorowanie aktywne: Obszar dodany.")

                //start monitoring locaiton at the background so phone can update its position
                checkLocationAtBackground(this)
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Błąd dodawania Geofence: ${e.message}", e)
                if (e is com.google.android.gms.common.api.ApiException) {
                    when (e.statusCode) {
                        com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> {
                            updateMainNotification("Błąd geofencingu: usługa geofence niedostępna (kod 1000). Upewnij się, że precyzyjna lokalizacja jest włączona")
                        }

                        com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> {
                            updateMainNotification("Błąd geofencingu: za dużo geofence'ów (kod 1001). Usuń niepotrzebne geofency lub zresetuj aplikację.")
                        }

                        com.google.android.gms.location.GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> {
                            updateMainNotification("Błąd geofencingu: za dużo PendingIntent (kod 1002).")
                        }

                        else -> {
                            updateMainNotification("Błąd dodawania Geofence: ${e.statusCode} - ${e.message}")
                        }
                    }
                } else {
                    updateMainNotification("Błąd dodawania Geofence: ${e?.message}")
                }
            }
    }

    // Why this function even exists?
    // Because according to my testing GEOFENCE does not work reliably even in big city.
    // It is better to update user location using GPS at the background and let the geofence receiver do the work (triggering enter and exit)
    // and it does not use too much battery.
    fun checkLocationAtBackground(context: Context) {
        // manually run location moniotoring with interval to let the gofence work as expected
        if (isBackgroundLocationLoopRunning) {
            MyLog.addLogMessageIntoFile(this,"Pętla już działa, nie uruchamiam ponownie.")
            return
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
            MyLog.addLogMessageIntoFile(this,"Brak uprawnień do lokalizacji do sprawdzania w tle. kod:asdf1")
            return
        }
        MyLog.addLogMessageIntoFile(this,"Zaczynam pobieranie lokalizacji w tle")
        var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        scope.launch {
            isBackgroundLocationLoopRunning = true
            MyLog.addLogMessageIntoFile(context,"Pętla tła URUCHOMIONA")
            try {
                while (isActive) {
                    val cts = CancellationTokenSource()
                    try {
                        val location =
                        fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cts.token
                        ).await()
                        if(location != null) {
                            MyLog.addLogMessageIntoFile(context,"Zaktualizowano lokalizacje w tle")

                                val currentLocation = LatLng(location.latitude, location.longitude)
                                var distance = calculateDistanceToPolygon(currentLocation, polygonCoordinates!!)
                                // TODO: Check if user is in the geofence circle
                                // if it is then quit this function of just set the delay to idk. 1hour or so
                                // or implement the ability to trigger this function while GeofenceTransition.EXIT
                                val dynamicDelay = calculateDynamicInterval(distance) / 1000 //div by 1000 to get seconds
                                MyLog.addLogMessageIntoFile(context,"Jesteś $distance km od obszaru | delay test: $dynamicDelay s")

                        }
                    } catch (e: Exception) {
                        MyLog.addLogMessageIntoFile(context,"Błąd aktualizacji lokalizacji w tle: ${e.message}")
                    } finally {
                        cts.cancel()
                    }
                    delay(TimeUnit.MINUTES.toMillis(1))
                }
            }finally {
                isBackgroundLocationLoopRunning = false
                MyLog.addLogMessageIntoFile(context,"Pętla tła ZATRZYMANA")
            }
        }
    }


    private fun removeGeofences() {
        MyLog.addLogMessageIntoFile(this,"Usuwanie Geofence")
        geofencingClient.removeGeofences(listOf(GEOFENCE_REQUEST_ID))
            .addOnSuccessListener {
                MyLog.addLogMessageIntoFile(this,"Geofence usunięty pomyślnie.")
            }
            .addOnFailureListener { e ->
                MyLog.addLogMessageIntoFile(this,"Błąd usuwania Geofence: ${e.message}")
            }
    }


    ///////////////////////////////////
    //          NOTIFICATIONS        //
    ///////////////////////////////////
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Monitorowanie lokalizacji",
                NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.description =
                "Kanał dla powiadomień o monitorowaniu lokalizacji eWeLink."
            serviceChannel.enableLights(false)
            serviceChannel.enableVibration(false)
            serviceChannel.setSound(null, null)
            serviceChannel.lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE

            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createMainNotification(message: String): NotificationCompat.Builder {
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
            .setOngoing(true) // Make notification non-swipable - does not work on android 15, on android 11 works well
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_launcher_foreground, "Otwórz bramę", openGatePendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Zatrzymaj", stopServicePendingIntent)
    }

    private fun updateMainNotification(message: String) {
        MyLog.addLogMessageIntoFile(this,"updateNotification message: $message")
        val notification = createMainNotification(message).build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    private fun createInformationNotification(title: String, message: String){
        // TODO: Create global funciton that will check for permissions

        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            android.Manifest.permission.ACCESS_NOTIFICATION_POLICY
        ) == PackageManager.PERMISSION_GRANTED
        if(!hasFineLocationPermission) {
            MyLog.addLogMessageIntoFile(this, "Brak uprawnień do tworzenia powiadomień")
            return
        }

        // Create new notification
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(1000))

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }
}
