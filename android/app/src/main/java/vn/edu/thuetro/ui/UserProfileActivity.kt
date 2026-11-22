package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.PublicUser
import vn.edu.thuetro.data.Room
import vn.edu.thuetro.data.SessionManager
import vn.edu.thuetro.data.UserPhoto
import vn.edu.thuetro.data.UserProfileResponse
import vn.edu.thuetro.databinding.ItemPhotoGridBinding
import vn.edu.thuetro.databinding.ItemProfilePostBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class UserProfileActivity : AppCompatActivity() {

    private val userId by lazy { intent.getLongExtra("user_id", -1L) }
    private var profile: UserProfileResponse? = null
    private lateinit var recycler: RecyclerView
    private lateinit var emptyTabText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)
        applySafeArea()

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        recycler = findViewById(R.id.recycler)
        emptyTabText = findViewById(R.id.emptyTabText)

        findViewById<TabLayout>(R.id.tabLayout).addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) = renderTab(tab?.position ?: 0)
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = renderTab(tab?.position ?: 0)
        })

        load()
    }

    private fun load() = lifecycleScope.launch {
        val progress = findViewById<View>(R.id.progress)
        progress.visibility = View.VISIBLE
        try {
            val res = ApiClient.service(this@UserProfileActivity).userProfile(userId)
            val data = res.data
            if (data == null) {
                toast(res.message.ifBlank { "Không tìm thấy người dùng." })
                finish()
                return@launch
            }
            profile = data
            bindProfile(data)
            findViewById<View>(R.id.contentScroll).visibility = View.VISIBLE
            renderTab(findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition)
        } catch (_: Exception) {
            toast("Không tải được trang cá nhân.")
        } finally {
            progress.visibility = View.GONE
        }
    }

    private fun bindProfile(data: UserProfileResponse) {
        val user = data.user
        val myId = SessionManager(this).userId
        findViewById<MaterialToolbar>(R.id.toolbar).title = user.fullName
        findViewById<ImageView>(R.id.avatar).loadAvatar(user.avatarUrl)
        findViewById<TextView>(R.id.fullName).text = user.fullName
        findViewById<TextView>(R.id.roleBadge).setRoleBadge(user.role)
        findViewById<TextView>(R.id.userMeta).text = buildString {
            append("@${user.username}")
            if (!user.phone.isNullOrBlank()) append(" • ${user.phone}")
        }

        val joinText = formatJoinMonth(user.createdAt)
        val joinView = findViewById<TextView>(R.id.joinDate)
        if (joinText.isNotEmpty()) {
            joinView.text = "Tham gia từ $joinText"
            joinView.visibility = View.VISIBLE
        } else {
            joinView.visibility = View.GONE
        }

        findViewById<TextView>(R.id.statRooms).text = data.stats.totalRooms.toString()
        findViewById<TextView>(R.id.statAvailable).text = data.stats.availableRooms.toString()
        findViewById<TextView>(R.id.statPhotos).text = data.stats.totalPhotos.toString()

        val actionsRow = findViewById<View>(R.id.actionButtonsRow)
        if (user.id == myId) {
            actionsRow.visibility = View.GONE
        } else {
            actionsRow.visibility = View.VISIBLE
            findViewById<View>(R.id.btnChat).setOnClickListener {
                openChatWith(
                    otherUserId = user.id,
                    otherName = user.fullName,
                    otherAvatar = user.avatarUrl,
                    otherRole = user.role,
                    otherPhone = user.phone
                )
            }
            val btnCall = findViewById<View>(R.id.btnCall)
            btnCall.visibility = if (user.phone.isNullOrBlank()) View.GONE else View.VISIBLE
            btnCall.setOnClickListener { dialPhone(user.phone) }
        }
    }

    private fun renderTab(tabPosition: Int) {
        val data = profile ?: return
        if (tabPosition == 0) {
            recycler.layoutManager = LinearLayoutManager(this)
            recycler.adapter = ProfilePostAdapter(data.user, data.rooms) { room ->
                startActivity(Intent(this, RoomDetailActivity::class.java).putExtra("room_id", room.id))
            }
            emptyTabText.text = "Chưa có bài đăng phòng nào."
            emptyTabText.visibility = if (data.rooms.isEmpty()) View.VISIBLE else View.GONE
        } else {
            recycler.layoutManager = GridLayoutManager(this, 3)
            recycler.adapter = PhotoGridAdapter(data.photos) { photo -> showPhotoPreview(photo) }
            emptyTabText.text = "Chưa có hình ảnh nào được đăng."
            emptyTabText.visibility = if (data.photos.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showPhotoPreview(photo: UserPhoto) {
        val dp = resources.displayMetrics.density
        val image = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (300 * dp).toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(photo.url) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(photo.roomTitle)
            .setView(image)
            .setNegativeButton("Đóng", null)
            .setPositiveButton("Xem phòng") { _, _ ->
                startActivity(Intent(this, RoomDetailActivity::class.java).putExtra("room_id", photo.roomId))
            }
            .show()
    }

    private fun formatJoinMonth(iso: String?): String {
        val raw = iso?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val parsed = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
            .firstNotNullOfOrNull { pattern ->
                runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)
                }.getOrNull()
            } ?: return ""
        return SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(parsed)
    }
}

private class ProfilePostAdapter(
    private val user: PublicUser,
    private val rooms: List<Room>,
    private val onClick: (Room) -> Unit
) : RecyclerView.Adapter<ProfilePostAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemProfilePostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = rooms.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rooms[position])

    inner class Holder(private val b: ItemProfilePostBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(room: Room) = with(b) {
            postAuthorAvatar.loadAvatar(user.avatarUrl)
            postAuthorName.text = user.fullName
            postTime.text = room.createdAt.relativeTime().ifBlank { "Tin đăng phòng" }
            postStatus.setRoomStatus(room.status)
            postTitle.text = room.title
            postPrice.text = "${room.price.money()}/tháng • ${room.area} m²"
            val firstImage = room.images.firstOrNull()
            if (firstImage.isNullOrBlank()) {
                postImage.visibility = View.GONE
            } else {
                postImage.visibility = View.VISIBLE
                postImage.load(firstImage) {
                    crossfade(true)
                    placeholder(android.R.drawable.ic_menu_gallery)
                }
            }
            postAddress.text = listOfNotNull(room.address, room.ward, room.district, room.city).joinToString(", ")
            root.setOnClickListener { onClick(room) }
        }
    }
}

private class PhotoGridAdapter(
    private val photos: List<UserPhoto>,
    private val onClick: (UserPhoto) -> Unit
) : RecyclerView.Adapter<PhotoGridAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPhotoGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = photos.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(photos[position])

    inner class Holder(private val b: ItemPhotoGridBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(photo: UserPhoto) = with(b) {
            photoThumb.load(photo.url) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
            }
            root.setOnClickListener { onClick(photo) }
        }
    }
}