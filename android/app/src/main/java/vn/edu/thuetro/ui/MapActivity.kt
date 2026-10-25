
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
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.Room

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var progress: ProgressBar
    private lateinit var roomCount: TextView

    private var mapLibreMap: MapLibreMap? = null
    private val roomMarkers = mutableListOf<Marker>()
    private val markerRoomIds = mutableMapOf<Long, Long>()

    private var pendingRooms: List<Room>? = null

    private val defaultPosition = LatLng(
        10.8231,
        106.6297
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.mapView)
        progress = findViewById(R.id.progress)
        roomCount = findViewById(R.id.txtRoomCount)

        mapView.onCreate(savedInstanceState)

        findViewById<ImageButton>(R.id.btnBack)
            .setOnClickListener {
                finish()
            }

        findViewById<ImageButton>(R.id.btnRefresh)
            .setOnClickListener {
                loadRooms()
            }

        findViewById<View>(R.id.btnZoomIn)
            .setOnClickListener {
                mapLibreMap?.let { map ->
                    map.animateCamera(
                        CameraUpdateFactory.zoomIn()
                    )
                }
            }

        findViewById<View>(R.id.btnZoomOut)
            .setOnClickListener {
                mapLibreMap?.let { map ->
                    map.animateCamera(
                        CameraUpdateFactory.zoomOut()
                    )
                }
            }

        setupMap()
        loadRooms()
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

        mapView.getMapAsync { map ->

            mapLibreMap = map

            map.setMinZoomPreference(5.0)
            map.setMaxZoomPreference(20.0)

            map.cameraPosition = CameraPosition.Builder()
                .target(defaultPosition)
                .zoom(13.0)
                .build()

            map.setOnMarkerClickListener { marker ->

                val roomId = markerRoomIds[marker.id]

                if (roomId != null) {
                    marker.showInfoWindow(map, mapView)
                }

                true
            }

            map.setOnInfoWindowClickListener { marker ->

                val roomId = markerRoomIds[marker.id]
                if (roomId != null) {
                    openDetail(roomId)
                }

                true
            }

            map.setStyle(
                Style.Builder().fromUri(styleUrl)
            ) {
                pendingRooms?.let { rooms ->
                    showMarkers(rooms)
                    pendingRooms = null
                }
            }
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
                    .filter { room ->
                        val lat = room.latitude
                        val lng = room.longitude

                        lat != null &&
                                lng != null &&
                                lat.isFinite() &&
                                lng.isFinite() &&
                                lat in -90.0..90.0 &&
                                lng in -180.0..180.0
                    }

                val map = mapLibreMap

                if (map != null && map.style?.isFullyLoaded == true) {
                    showMarkers(rooms)
                } else {
                    pendingRooms = rooms
                }

                roomCount.text =
                    "${rooms.size} phòng đang hiển thị"

            } catch (e: Exception) {

                roomCount.text =
                    "Không tải được danh sách phòng"

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
        val map = mapLibreMap ?: return

        roomMarkers.forEach { marker ->
            map.removeMarker(marker)
        }

        roomMarkers.clear()
        markerRoomIds.clear()

        val points = mutableListOf<LatLng>()

        rooms.forEach { room ->

            val lat = room.latitude ?: return@forEach
            val lng = room.longitude ?: return@forEach

            val point = LatLng(lat, lng)
            points.add(point)

            val marker = map.addMarker(
                MarkerOptions()
                    .position(point)
                    .title(room.title)
                    .snippet(
                        "%,d đ/tháng".format(room.price)
                    )
            )

            roomMarkers.add(marker)
            markerRoomIds[marker.id] = room.id
        }

        when {
            points.size == 1 -> {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        points.first(),
                        16.0
                    )
                )
            }

            points.size > 1 -> {
                val builder = LatLngBounds.Builder()

                points.forEach { builder.include(it) }

                val bounds = builder.build()

                mapView.post {
                    if (mapView.width > 0 &&
                        mapView.height > 0
                    ) {
                        map.animateCamera(
                            CameraUpdateFactory
                                .newLatLngBounds(bounds, 100)
                        )
                    }
                }
            }
        }
    }

    private fun openDetail(roomId: Long) {
        val intent = Intent(
            this,
            RoomDetailActivity::class.java
        )

        intent.putExtra("room_id", roomId)
        startActivity(intent)
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
