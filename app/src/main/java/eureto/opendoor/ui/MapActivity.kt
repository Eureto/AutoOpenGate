package eureto.opendoor.ui

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.network.EwelinkApiClient
import eureto.opendoor.ui.screens.MapScreen
import eureto.opendoor.ui.theme.OpenDoorTheme
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapActivity : AppCompatActivity() {

    private lateinit var appPreferences: AppPreferences
    private var geofenceRadius: Int = 1000
    private var centerLat: Double = 0.0
    private var centerLng: Double = 0.0
    private val polygonPoints = mutableListOf<GeoPoint>()
    private var drawnPolygon: Polygon? = null
    private var drawCircle: Polygon? = null
    private val gson = Gson()
    private lateinit var mMap: MapView
    private lateinit var controller: IMapController
    private lateinit var mMyLocationOverlay: MyLocationNewOverlay

    private var radiusTextState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        appPreferences = EwelinkApiClient.getAppPreferences()
        geofenceRadius = appPreferences.getGeofenceRadius()
        radiusTextState = geofenceRadius.toString()

        mMap = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            getLocalVisibleRect(Rect())
        }

        mMyLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mMap)
        controller = mMap.controller

        mMyLocationOverlay.enableMyLocation()
        mMyLocationOverlay.enableFollowLocation()
        mMyLocationOverlay.isDrawAccuracyEnabled = true
        mMyLocationOverlay.runOnFirstFix {
            runOnUiThread {
                controller.setCenter(mMyLocationOverlay.myLocation)
                controller.animateTo(mMyLocationOverlay.myLocation)
            }
        }

        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                polygonPoints.add(p)
                redrawPolygon()
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        mMap.overlays.add(eventsOverlay)
        controller.setZoom(18.0)
        mMap.overlays.add(mMyLocationOverlay)

        loadPolygon()

        setContent {
            OpenDoorTheme {
                MapScreen(
                    mapView = mMap,
                    radiusText = radiusTextState,
                    onRadiusChange = { newText ->
                        radiusTextState = newText
                        newText.toIntOrNull()?.let { input ->
                            appPreferences.setGeofenceRadius(input)
                            geofenceRadius = input
                            redrawPolygon()
                        }
                    },
                    onRadiusDone = {
                        Toast.makeText(this, "Promień geofence ustawiony na $geofenceRadius metrów.", Toast.LENGTH_SHORT).show()
                    },
                    onClearClick = {
                        clearPolygon()
                    },
                    onSaveClick = {
                        savePolygon()
                    }
                )
            }
        }
    }

    private fun redrawPolygon() {
        drawnPolygon?.let { mMap.overlays.remove(it) }
        drawCircle?.let { mMap.overlays.remove(it) }

        if (polygonPoints.size >= 2) {
            val newPolygon = Polygon(mMap)
            newPolygon.points = polygonPoints
            newPolygon.fillPaint.color = Color.argb(70, 0, 0, 255)
            newPolygon.outlinePaint.color = Color.BLUE
            newPolygon.outlinePaint.strokeWidth = 5f
            newPolygon.setOnClickListener { _, _, _ -> false }
            drawnPolygon = newPolygon
            mMap.overlays.add(drawnPolygon)

            centerLat = polygonPoints.map { it.latitude }.average()
            centerLng = polygonPoints.map { it.longitude }.average()
            val centerGeoPoint = GeoPoint(centerLat, centerLng)
            val circlePoints: MutableList<GeoPoint?> = Polygon.pointsAsCircle(centerGeoPoint, geofenceRadius.toDouble())
            val circle = Polygon(mMap)
            circle.setPoints(circlePoints)
            circle.fillPaint.color = Color.argb(40, 255, 0, 0)
            circle.setOnClickListener { _, _, _ -> false }
            drawCircle = circle
            mMap.overlayManager.add(drawCircle)
        }
        mMap.invalidate()
    }

    private fun savePolygon() {
        if (polygonPoints.size < 3) {
            Toast.makeText(this, "Musisz zaznaczyć co najmniej 3 punkty, aby utworzyć wielokąt.", Toast.LENGTH_LONG).show()
            return
        }

        val polygonLatLngs = polygonPoints.map { LatLng(it.latitude, it.longitude) }
        val json = gson.toJson(polygonLatLngs)
        appPreferences.savePolygonCoordinates(json)

        centerLat = polygonPoints.map { it.latitude }.average()
        centerLng = polygonPoints.map { it.longitude }.average()
        appPreferences.savePolygonCenter(LatLng(centerLat, centerLng))

        Toast.makeText(this, "Obszar domu zapisany pomyślnie!", Toast.LENGTH_SHORT).show()
        Log.d("MapActivity", "Zapisano wielokąt: $json")
        finish()
    }

    private fun loadPolygon() {
        val json = appPreferences.getPolygonCoordinates()
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<LatLng>>() {}.type
            val savedPointsLatLng: List<LatLng> = gson.fromJson(json, type)
            val savedPoints = savedPointsLatLng.map { GeoPoint(it.latitude, it.longitude) }
            polygonPoints.clear()
            polygonPoints.addAll(savedPoints)
            centerLat = polygonPoints.map { it.latitude }.average()
            centerLng = polygonPoints.map { it.longitude }.average()
            redrawPolygon()
        }
    }

    private fun clearPolygon() {
        polygonPoints.clear()
        if (drawnPolygon != null) {
            mMap.overlays.remove(drawnPolygon)
            drawnPolygon = null
        }
        appPreferences.savePolygonCoordinates("")
        if (drawCircle != null) {
            mMap.overlays.remove(drawCircle)
            drawCircle = null
        }
        mMap.invalidate()
        Toast.makeText(this, "Wyczyszczono obszar", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        mMap.onResume()
    }

    override fun onPause() {
        super.onPause()
        mMap.onPause()
    }
}
