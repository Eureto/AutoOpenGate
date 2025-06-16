package eureto.opendoor.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eureto.opendoor.R // Upewnij się, że masz R.id.map
import eureto.opendoor.data.AppPreferences // Zmień nazwę pakietu
import eureto.opendoor.databinding.ActivityMapBinding // Zmień nazwę pakietu
import eureto.opendoor.network.EwelinkApiClient // Zmień nazwę pakietu

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapBinding
    private lateinit var mMap: GoogleMap
    private lateinit var appPreferences: AppPreferences
    private val polygonPoints = mutableListOf<LatLng>()
    private var drawnPolygon: Polygon? = null
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = EwelinkApiClient.getAppPreferences()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnSaveArea.setOnClickListener {
            savePolygon()
        }

        binding.btnClearArea.setOnClickListener {
            clearPolygon()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Ustaw domyślną lokalizację (np. centrum Warszawy)
        val warsaw = LatLng(52.2297, 21.0122)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(warsaw, 10f))

        // Umożliwienie rysowania wielokąta poprzez dotykanie mapy
        mMap.setOnMapClickListener { latLng ->
            polygonPoints.add(latLng)
            redrawPolygon()
        }

        // Załaduj zapisany wielokąt, jeśli istnieje
        loadPolygon()
    }

    private fun redrawPolygon() {
        drawnPolygon?.remove() // Usuń stary wielokąt
        if (polygonPoints.size > 1) {
            val polygonOptions = PolygonOptions()
                .addAll(polygonPoints)
                .strokeColor(Color.BLUE)
                .fillColor(Color.argb(70, 0, 0, 255)) // Półprzezroczysty niebieski
                .strokeWidth(5f)
            drawnPolygon = mMap.addPolygon(polygonOptions)
        }
    }

    private fun savePolygon() {
        if (polygonPoints.size < 3) {
            Toast.makeText(this, "Musisz zaznaczyć co najmniej 3 punkty, aby utworzyć wielokąt.", Toast.LENGTH_LONG).show()
            return
        }
        val json = gson.toJson(polygonPoints)
        appPreferences.savePolygonCoordinates(json)
        Toast.makeText(this, "Obszar domu zapisany pomyślnie!", Toast.LENGTH_SHORT).show()
        Log.d("MapActivity", "Zapisano wielokąt: $json")
        finish() // Wróć do MainActivity
    }

    private fun loadPolygon() {
        val json = appPreferences.getPolygonCoordinates()
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<LatLng>>() {}.type
            val savedPoints: List<LatLng> = gson.fromJson(json, type)
            polygonPoints.clear()
            polygonPoints.addAll(savedPoints)
            redrawPolygon()
            // Przesuń kamerę do środka wielokąta
            if (polygonPoints.isNotEmpty()) {
                val bounds = com.google.android.gms.maps.model.LatLngBounds.Builder()
                for (point in polygonPoints) {
                    bounds.include(point)
                }
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
            }
            Log.d("MapActivity", "Załadowano wielokąt: $json")
        }
    }

    private fun clearPolygon() {
        polygonPoints.clear()
        drawnPolygon?.remove()
        drawnPolygon = null
        appPreferences.savePolygonCoordinates("") // Wyczyść z preferencji
        Toast.makeText(this, "Obszar wyczyszczony.", Toast.LENGTH_SHORT).show()
    }
}