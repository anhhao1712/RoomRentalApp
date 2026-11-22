package vn.edu.thuetro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import vn.edu.thuetro.R

class PhotoViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_viewer)
        applySafeArea()

        val images = intent.getStringArrayListExtra("images").orEmpty()
        if (images.isEmpty()) {
            finish()
            return
        }
        val startPos = intent.getIntExtra("position", 0).coerceIn(0, images.lastIndex)
        val title = intent.getStringExtra("title").orEmpty()

        findViewById<View>(R.id.btnCloseViewer).setOnClickListener { finish() }
        findViewById<TextView>(R.id.viewerTitle).text = title.ifBlank { "Ảnh phòng" }

        val counter = findViewById<TextView>(R.id.viewerCounter)
        val dots = findViewById<LinearLayout>(R.id.viewerDots)
        val pager = findViewById<ViewPager2>(R.id.fullScreenPager)

        pager.adapter = RoomPhotoPagerAdapter(images, fullScreen = true) {}
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                counter.text = "${position + 1} / ${images.size}"
                dots.renderPhotoDots(images.size, position)
            }
        })
        pager.setCurrentItem(startPos, false)
        counter.text = "${startPos + 1} / ${images.size}"
        dots.renderPhotoDots(images.size, startPos)
    }
}

class RoomPhotoPagerAdapter(
    private val images: List<String>,
    private val fullScreen: Boolean,
    private val onPhotoClick: (Int) -> Unit
) : RecyclerView.Adapter<RoomPhotoPagerAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (fullScreen) R.layout.item_fullscreen_photo else R.layout.item_room_photo
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return Holder(view as ImageView)
    }

    override fun getItemCount() = images.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val url = images[position]
        if (url.isBlank()) {
            holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        } else {
            holder.imageView.load(url) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
            }
        }
        holder.imageView.setOnClickListener { onPhotoClick(position) }
    }

    class Holder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
}