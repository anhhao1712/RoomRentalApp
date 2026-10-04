package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import vn.edu.thuetro.R
import vn.edu.thuetro.data.OsmConfig
import java.io.File

class MapPickerActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private var marker: Marker? = null
    private var picked: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmConfig.init(this)
        setContentView(R.layout.activity_map_picker)
        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.minZoomLevel = 5.0
        map.maxZoomLevel = 20.0
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { place(p); return true }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))

        val lat = intent.getDoubleExtra("lat", Double.NaN)
        val lng = intent.getDoubleExtra("lng", Double.NaN)
        if (!lat.isNaN() && !lng.isNaN()) {
            val start = GeoPoint(lat, lng)
            place(start)
            map.controller.setZoom(17.0)
            map.controller.setCenter(start)
        } else {
            map.controller.setZoom(13.0)
            map.controller.setCenter(GeoPoint(10.8231, 106.6297))
        }

        findViewById<Button>(R.id.confirm).setOnClickListener {
            val p = picked
            if (p == null) {
                Toast.makeText(this, "Chạm lên bản đồ để chọn vị trí.", Toast.LENGTH_SHORT).show()
            } else {
                setResult(RESULT_OK, Intent().putExtra("lat", p.latitude).putExtra("lng", p.longitude))
                finish()
            }
        }
        Toast.makeText(this, "Chạm lên bản đồ để đặt vị trí phòng.", Toast.LENGTH_LONG).show()
    }

    private fun place(p: GeoPoint) {
        picked = p
        val m = marker ?: Marker(map).also {
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            it.setOnMarkerClickListener { _, _ -> true }   // chạm vào ghim không hiện hộp trống
            map.overlays.add(it)
            marker = it
        }
        m.position = p
        map.invalidate()
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
}