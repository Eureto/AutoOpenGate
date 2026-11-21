package eureto.opendoor.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.maps.android.PolyUtil
import eureto.opendoor.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Gdzie aktualnie znajduje się użytkownik względem skonfigurowanej strefy. */
enum class ZoneState {
    /** Poza okręgiem (promień geofence). */
    OUTSIDE,

    /** W okręgu, ale jeszcze nie w wielokącie domu. */
    IN_CIRCLE,

    /** W wielokącie (obszar bramy / dom). */
    IN_POLYGON
}

/**
 * Reaktywny stan strefy użytkownika, aktualizowany z każdego miejsca, które zna pozycję
 * (serwis lokalizacji, geofence receiver oraz lokalizacja na ekranie, gdy aplikacja jest widoczna).
 */
object ZoneStateManager {

    private val _zone = MutableStateFlow<ZoneState?>(null)

    /** null = jeszcze nie znamy pozycji. */
    val zone: StateFlow<ZoneState?> = _zone.asStateFlow()

    private val gson = Gson()

    // Cache sparsowanego wielokąta, żeby nie parsować JSON-a przy każdej aktualizacji pozycji
    private var cachedPolygonJson: String? = null
    private var cachedPolygon: List<LatLng>? = null

    private var foregroundClient: FusedLocationProviderClient? = null
    private var foregroundCallback: LocationCallback? = null

    /** Klasyfikuje pozycję i publikuje wynik. */
    fun update(location: LatLng, polygon: List<LatLng>, center: LatLng, radiusMeters: Int) {
        if (polygon.size < 3) return

        val newState = when {
            PolyUtil.containsLocation(location, polygon, true) -> ZoneState.IN_POLYGON
            distanceMeters(center, location) <= radiusMeters -> ZoneState.IN_CIRCLE
            else -> ZoneState.OUTSIDE
        }
        _zone.value = newState
    }

    /** Wariant korzystający z zapisanej konfiguracji (wielokąt + promień). */
    fun updateFromPrefs(location: LatLng, appPreferences: AppPreferences) {
        val polygon = loadPolygon(appPreferences) ?: return
        val center = LatLng(
            polygon.map { it.latitude }.average(),
            polygon.map { it.longitude }.average()
        )
        update(location, polygon, center, appPreferences.getGeofenceRadius())
    }

    /**
     * Grubsza aktualizacja z geofence receivera (zna tylko okrąg). Nie cofa stanu IN_POLYGON
     * przy ENTER, bo wielokąt jest już "głębiej" w okręgu.
     */
    fun onCircleTransition(entered: Boolean) {
        if (entered) {
            if (_zone.value != ZoneState.IN_POLYGON) _zone.value = ZoneState.IN_CIRCLE
        } else {
            _zone.value = ZoneState.OUTSIDE
        }
    }

    fun reset() {
        _zone.value = null
    }

    /**
     * Uruchamia częste odświeżanie pozycji na czas, gdy aplikacja jest widoczna,
     * dzięki czemu wiersz "Strefa" zmienia się na żywo.
     */
    fun startForegroundUpdates(context: Context, appPreferences: AppPreferences) {
        if (foregroundCallback != null) return

        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return

        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                updateFromPrefs(LatLng(location.latitude, location.longitude), appPreferences)
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            // Szybkie pierwsze wypełnienie ostatnią znaną pozycją
            client.lastLocation.addOnSuccessListener { last ->
                if (last != null && _zone.value == null) {
                    updateFromPrefs(LatLng(last.latitude, last.longitude), appPreferences)
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            foregroundClient = client
            foregroundCallback = callback
        } catch (_: SecurityException) {
            // Brak uprawnień — stan pozostaje bez zmian
        }
    }

    fun stopForegroundUpdates() {
        val client = foregroundClient
        val callback = foregroundCallback
        if (client != null && callback != null) {
            client.removeLocationUpdates(callback)
        }
        foregroundClient = null
        foregroundCallback = null
    }

    private fun loadPolygon(appPreferences: AppPreferences): List<LatLng>? {
        val json = appPreferences.getPolygonCoordinates()
        if (json.isNullOrEmpty()) return null
        if (json == cachedPolygonJson) return cachedPolygon

        val parsed: List<LatLng>? = try {
            gson.fromJson(json, object : TypeToken<List<LatLng>>() {}.type)
        } catch (_: Exception) {
            null
        }
        cachedPolygonJson = json
        cachedPolygon = parsed
        return parsed
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Float {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0]
    }
}
