package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.OsmConfig
import vn.edu.thuetro.data.Room

class MapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var progress: ProgressBar
    private lateinit var roomCount: TextView

    private val roomMarkers = mutableListOf<Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmConfig.init(this)
        setContentView(R.layout.activity_map)
        map = findViewById(R.id.mapView)
        progress = findViewById(R.id.progress)
        roomCount = findViewById(R.id.txtRoomCount)
        setupMap()
        findViewById<ImageButton>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }
        findViewById<ImageButton>(R.id.btnRefresh)
            .setOnClickListener {
                loadRooms()
            }
        loadRooms()
    }

    private fun setupMap() {
        map.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 5.0
            maxZoomLevel = 20.0
            controller.setZoom(13.0)
            controller.setCenter(
                GeoPoint(
                    10.8231,
                    106.6297
                )
            )
        }
    }

    private fun loadRooms() {
        progress.visibility = View.VISIBLE
        roomCount.text = "Đang tải phòng..."
        lifecycleScope.launch {
            try {
                val rooms = ApiClient
                    .service(this@MapActivity)
                    .rooms()
                    .data
                    .orEmpty()
                    .filter {
                        it.latitude != null &&
                                it.longitude != null
                    }
                showMarkers(rooms)
                roomCount.text =
                    "${rooms.size} phòng đang hiển thị"
            } catch (e: Exception) {
                roomCount.text = "Không tải được danh sách phòng"

                Toast.makeText(
                    this@MapActivity,
                    "Không tải được phòng: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun showMarkers(rooms: List<Room>) {
        roomMarkers.forEach {
            map.overlays.remove(it)
        }
        roomMarkers.clear()
        val points = mutableListOf<GeoPoint>()

        rooms.forEach { room ->
            val lat = room.latitude ?: return@forEach
            val lng = room.longitude ?: return@forEach
            val point = GeoPoint(lat, lng)
            points.add(point)
            val marker = Marker(map).apply {
                position = point
                title = room.title
                snippet =
                    "%,d đ/tháng".format(room.price)
                setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
                )

                setOnMarkerClickListener { m, _ ->
                    if (m.isInfoWindowShown) {
                        openDetail(room.id)
                    } else {
                        roomMarkers.forEach {
                            if (it != m) {
                                it.closeInfoWindow()
                            }
                        }
                        m.showInfoWindow()
                    }
                    true
                }
            }
            roomMarkers.add(marker)
            map.overlays.add(marker)
        }

        when {
            points.size == 1 -> {
                map.controller.setZoom(16.0)
                map.controller.animateTo(
                    points.first()
                )
            }
            points.size > 1 -> {
                map.post {
                    map.zoomToBoundingBox(
                        BoundingBox.fromGeoPoints(points),
                        true,
                        100
                    )
                }
            }
        }
        map.invalidate()
    }

    private fun openDetail(roomId: Long) {
        val intent =
            Intent(
                this,
                RoomDetailActivity::class.java
            )
        intent.putExtra(
            "room_id",
            roomId
        )

        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
    }
}