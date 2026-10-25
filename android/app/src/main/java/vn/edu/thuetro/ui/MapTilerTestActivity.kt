package vn.edu.thuetro.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import vn.edu.thuetro.R

import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.MarkerOptions

class MapTilerTestActivity : AppCompatActivity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        setContentView(R.layout.activity_maptiler_test)

        mapView = findViewById(R.id.mapView)

        mapView.onCreate(savedInstanceState)

        val apiKey = getString(R.string.maptiler_api_key)

        if (apiKey.isBlank()) {
            Log.e("MapTilerTest", "Chua cau hinh API key")
            return
        }

        val styleUrl =
            "https://api.maptiler.com/maps/streets-v4/style.json?key=$apiKey"

        mapView.getMapAsync { map ->

            map.setStyle(
                Style.Builder().fromUri(styleUrl)
            ) {

                val hcm = LatLng(10.8025, 106.7230)

                map.cameraPosition = CameraPosition.Builder()
                    .target(hcm)
                    .zoom(14.0)
                    .build()

                map.addMarker(
                    MarkerOptions()
                        .position(hcm)
                        .title("Phòng trọ thử nghiệm")
                        .snippet("MapLibre + MapTiler")
                )

                Log.d(
                    "MapTilerTest",
                    "Map style loaded"
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }
}