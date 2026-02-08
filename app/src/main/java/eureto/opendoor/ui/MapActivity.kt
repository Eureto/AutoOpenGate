package eureto.opendoor.ui

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.config.Configuration
import org.osmdroid.views.overlay.Polygon
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.databinding.ActivityMapBinding
import eureto.opendoor.network.EwelinkApiClient
import org.osmdroid.api.IMapController
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.events.MapEventsReceiver

class MapActivity : AppCompatActivity(){

    private lateinit var binding: ActivityMapBinding
    private lateinit var appPreferences: AppPreferences
    private val polygonPoints = mutableListOf<GeoPoint>()
    private var drawnPolygon: Polygon? = null
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

        appPreferences = EwelinkApiClient.getAppPreferences()

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
    }


    private fun redrawPolygon() {
        // Remove old polygon if it exists
        drawnPolygon?.let {
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
        // Also save center point of the polygon for geofencing
        val centerLat = polygonPoints.map { it.latitude }.average()
        val centerLng = polygonPoints.map { it.longitude }.average()
        appPreferences.savePolygonCenter(LatLng(centerLng, centerLat))

        Toast.makeText(this, "Obszar domu zapisany pomyślnie!", Toast.LENGTH_SHORT).show()
        Log.d("MapActivity", "Zapisano wielokąt: $json")
        finish() // go back to mainActivity
    }

    private fun loadPolygon() {
        val json = appPreferences.getPolygonCoordinates()
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<GeoPoint>>() {}.type
            val savedPoints: List<GeoPoint> = gson.fromJson(json, type)
            polygonPoints.clear()
            polygonPoints.addAll(savedPoints)
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