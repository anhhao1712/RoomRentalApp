
package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import vn.edu.thuetro.R
import java.util.Locale

class MapPickerActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var instruction: TextView

    private var mapLibreMap: MapLibreMap? = null
    private var marker: Marker? = null
    private var picked: LatLng? = null

    private val defaultPosition = LatLng(
        10.8231,
        106.6297
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        setContentView(R.layout.activity_map_picker)

        mapView = findViewById(R.id.mapView)
        instruction = findViewById(R.id.txtInstruction)

        mapView.onCreate(savedInstanceState)

        findViewById<View>(R.id.btnZoomIn)
            .setOnClickListener {
                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.zoomIn()
                )
            }

        findViewById<View>(R.id.btnZoomOut)
            .setOnClickListener {
                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.zoomOut()
                )
            }

        findViewById<Button>(R.id.confirm)
            .setOnClickListener {
                confirmPosition()
            }

        setupMap()
    }

    private fun setupMap() {

        val apiKey = getString(R.string.maptiler_api_key)

        if (apiKey.isBlank()) {
            Toast.makeText(
                this,
                "Chưa cấu hình MapTiler API key",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val styleUrl =
            "https://api.maptiler.com/maps/streets-v4/style.json?key=$apiKey"

        val lat = intent.getDoubleExtra(
            "lat",
            Double.NaN
        )

        val lng = intent.getDoubleExtra(
            "lng",
            Double.NaN
        )

        val hasInitialPosition =
            lat.isFinite() &&
                    lng.isFinite() &&
                    lat in -90.0..90.0 &&
                    lng in -180.0..180.0

        val startPosition = if (hasInitialPosition) {
            LatLng(lat, lng)
        } else {
            defaultPosition
        }

        mapView.getMapAsync { map ->

            mapLibreMap = map

            map.setMinZoomPreference(5.0)
            map.setMaxZoomPreference(20.0)

            map.cameraPosition = CameraPosition.Builder()
                .target(startPosition)
                .zoom(if (hasInitialPosition) 17.0 else 13.0)
                .build()

            map.setStyle(
                Style.Builder().fromUri(styleUrl)
            ) {

                if (hasInitialPosition) {
                    place(startPosition)
                }

                map.addOnMapClickListener { position ->
                    place(position)
                    true
                }



            }
        }
    }


    private fun place(position: LatLng) {
        val map = mapLibreMap ?: return

        picked = position

        marker?.let { oldMarker ->
            map.removeMarker(oldMarker)
        }

        marker = map.addMarker(
            MarkerOptions()
                .position(position)
                .title("Vị trí phòng trọ")
        )

        updateInstruction(position)
    }


    private fun updateInstruction(position: LatLng) {
        instruction.text = String.format(
            Locale.US,
            "Đã chọn: %.6f, %.6f",
            position.latitude,
            position.longitude
        )
    }

    private fun confirmPosition() {

        val position = picked

        if (position == null) {
            Toast.makeText(
                this,
                "Chạm lên bản đồ để chọn vị trí.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val result = Intent().apply {
            putExtra("lat", position.latitude)
            putExtra("lng", position.longitude)
        }

        setResult(RESULT_OK, result)
        finish()
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

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }
}
