package eureto.opendoor.ui

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.databinding.ActivityMapBinding
import eureto.opendoor.network.EwelinkApiClient
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

class MapActivity : AppCompatActivity(){

    private lateinit var binding: ActivityMapBinding
    private lateinit var appPreferences: AppPreferences
    private var geofenceRadius: Int = 1000
    private var isGeofenceEnabled: Boolean = true
    private var centerLat: Double = 0.0
    private var centerLng: Double = 0.0
    private val polygonPoints = mutableListOf<GeoPoint>()
    private var drawnPolygon: Polygon? = null
    private var drawCircle: Polygon? = null
    private val gson = Gson()
    lateinit var mMap: MapView
    lateinit var controller: IMapController
    lateinit var mMyLocationOverlay: MyLocationNewOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // przypisanie zapisanych wartości
        appPreferences = EwelinkApiClient.getAppPreferences()
        geofenceRadius = appPreferences.getGeofenceRadius()
        isGeofenceEnabled = appPreferences.getIsGeofenceEnabled()

        binding.inputGeofenceRadius.setHint(geofenceRadius.toString() + " metrów")
        if(isGeofenceEnabled) {
            binding.btnTurnOnOffGeofence.text = "Wyłącz geofence"
        }else{
            binding.btnTurnOnOffGeofence.text = "Włącz geofence"
        }

        mMap = binding.osmmap
        mMap.setTileSource(TileSourceFactory.MAPNIK)
        mMap.mapCenter
        mMap.setMultiTouchControls(true)
        mMap.getLocalVisibleRect(Rect())

        mMyLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mMap)
        controller = mMap.controller

        mMyLocationOverlay.enableMyLocation()
        mMyLocationOverlay.enableFollowLocation()
        mMyLocationOverlay.isDrawAccuracyEnabled = true
        mMyLocationOverlay.runOnFirstFix {
            runOnUiThread {
                controller.setCenter(mMyLocationOverlay.myLocation);
                controller.animateTo(mMyLocationOverlay.myLocation)
            }
        }
        // val mapPoint = GeoPoint(latitude, longitude)
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


        //BUTTON BINDINGS
        binding.btnSaveArea.setOnClickListener {
            savePolygon()
        }

        binding.btnClearArea.setOnClickListener {
            clearPolygon()
        }
        binding.inputGeofenceRadius.setOnEditorActionListener { textView, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val input = textView.text.toString().toIntOrNull()
                if(input != null) {
                    appPreferences.setGeofenceRadius(input)
                    geofenceRadius = input
                    Toast.makeText(this, "Promień geofence ustawiony na $input metrów.", Toast.LENGTH_SHORT).show()
                    redrawPolygon()
                }
            }
            false
        }
        binding.btnTurnOnOffGeofence.setOnClickListener {
            val btnState = binding.btnTurnOnOffGeofence.text.toString()
            if(btnState == "Włącz geofence") {
                isGeofenceEnabled = true
                appPreferences.setIsGeofenceEnabled(true)
                binding.btnTurnOnOffGeofence.text = "Wyłącz geofence"
                redrawPolygon()
            }else{
                appPreferences.setIsGeofenceEnabled(false)
                isGeofenceEnabled = false
                binding.btnTurnOnOffGeofence.text = "Włącz geofence"
                redrawPolygon()
            }

        }
    }


    private fun redrawPolygon() {
        // Remove old polygon if it exists
        drawnPolygon?.let {
            mMap.overlays.remove(it)
        }
        drawCircle?.let{
            mMap.overlays.remove(it)
        }

        if (polygonPoints.size >= 2) {
            //Create the osmdroid Polygon
            val newPolygon = org.osmdroid.views.overlay.Polygon(mMap)
            newPolygon.points = polygonPoints

            newPolygon.fillPaint.color = Color.argb(70, 0, 0, 255)
            newPolygon.outlinePaint.color = Color.BLUE
            newPolygon.outlinePaint.strokeWidth = 5f
            newPolygon.setOnClickListener { polygon, mapView, eventPos -> false } // Disable message icon when clicked inside polygon.
            drawnPolygon = newPolygon
            mMap.overlays.add(drawnPolygon)

            // create geofence circle
            if(isGeofenceEnabled) {
                centerLat = polygonPoints.map { it.latitude }.average()
                centerLng = polygonPoints.map { it.longitude }.average()
                val centerGeoPoint = GeoPoint(centerLat, centerLng)
                val circlePoints: MutableList<GeoPoint?> =
                    Polygon.pointsAsCircle(centerGeoPoint, geofenceRadius.toDouble())
                var circle = Polygon(mMap)
                circle.setPoints(circlePoints)
                circle.getFillPaint().setColor(Color.argb(40, 255, 0, 0))
                circle.setOnClickListener { circlePolygon, mapView, eventPos -> false }
                drawCircle = circle
                mMap.getOverlayManager().add(drawCircle)
            }
        }
        mMap.invalidate() // Refresh map
    }

    private fun savePolygon() {
        if (polygonPoints.size < 3) {
            Toast.makeText(this, "Musisz zaznaczyć co najmniej 3 punkty, aby utworzyć wielokąt.", Toast.LENGTH_LONG).show()
            return
        }

        //Convert GeoPoints to LatLng format
        val polygonPoints = polygonPoints.map { LatLng(it.latitude, it.longitude) }
        val json = gson.toJson(polygonPoints)
        appPreferences.savePolygonCoordinates(json)

        // Save center point of the polygon for geofencing
        centerLat = polygonPoints.map { it.latitude }.average()
        centerLng = polygonPoints.map { it.longitude }.average()
        appPreferences.savePolygonCenter(LatLng(centerLng, centerLat))

        Toast.makeText(this, "Obszar domu zapisany pomyślnie!", Toast.LENGTH_SHORT).show()
        Log.d("MapActivity", "Zapisano wielokąt: $json")
        finish() // go back to mainActivity
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
        if(drawCircle != null) {
            mMap.overlays.remove(drawCircle)
            drawCircle = null
        }
        mMap.invalidate()
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