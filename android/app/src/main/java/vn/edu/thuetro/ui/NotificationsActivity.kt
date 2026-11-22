package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.AppNotification
import vn.edu.thuetro.databinding.ItemNotificationBinding

class NotificationsActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        applySafeArea()

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        adapter = NotificationAdapter { notif -> onNotificationClicked(notif) }
        findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
            addItemDecoration(DividerItemDecoration(this@NotificationsActivity, DividerItemDecoration.VERTICAL))
            adapter = this@NotificationsActivity.adapter
        }

        findViewById<View>(R.id.btnReadAll).setOnClickListener { markAllRead() }
        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() = lifecycleScope.launch {
        val progress = findViewById<View>(R.id.progress)
        try {
            val items = ApiClient.service(this@NotificationsActivity).notifications().data.orEmpty()
            adapter.submit(items)
            findViewById<View>(R.id.emptyText).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        } catch (_: Exception) {
            toast("Không tải được thông báo.")
        } finally {
            progress.visibility = View.GONE
        }
    }

    private fun markAllRead() = lifecycleScope.launch {
        try {
            val res = ApiClient.service(this@NotificationsActivity).markAllNotificationsRead()
            if (res.success) {
                adapter.markAllReadLocally()
                toast("Đã đánh dấu tất cả là đã đọc.")
            }
        } catch (_: Exception) {
            toast("Không cập nhật được trạng thái thông báo.")
        }
    }

    private fun onNotificationClicked(notif: AppNotification) {
        if (!notif.isRead) {
            adapter.markReadLocally(notif.id)
            lifecycleScope.launch {
                runCatching { ApiClient.service(this@NotificationsActivity).markNotificationRead(notif.id) }
            }
        }
        when {
            notif.requestId != null || notif.type.startsWith("REQUEST_") ->
                startActivity(Intent(this, RequestsActivity::class.java))
            notif.roomId != null ->
                startActivity(Intent(this, RoomDetailActivity::class.java).putExtra("room_id", notif.roomId))
        }
    }
}

private class NotificationAdapter(
    private val onClick: (AppNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.Holder>() {

    private var items = mutableListOf<AppNotification>()

    fun submit(data: List<AppNotification>) {
        items = data.toMutableList()
        notifyDataSetChanged()
    }

    fun markReadLocally(id: Long) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(isRead = true)
            notifyItemChanged(idx)
        }
    }

    fun markAllReadLocally() {
        items = items.map { it.copy(isRead = true) }.toMutableList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: AppNotification) = with(b) {
            actorAvatar.loadAvatar(item.actorAvatarUrl)
            typeIcon.setImageResource(
                when (item.type) {
                    "REQUEST_ACCEPTED" -> R.drawable.ic_check_circle
                    "REQUEST_REJECTED", "REQUEST_CANCELLED" -> R.drawable.ic_cancel_circle
                    else -> R.drawable.ic_calendar_clock
                }
            )
            notifTitle.text = item.title
            notifBody.text = item.body
            notifTime.text = item.createdAt.relativeTime()

            val unread = !item.isRead
            notifRoot.setBackgroundColor(if (unread) 0xFFEFF6FF.toInt() else 0xFFFFFFFF.toInt())
            unreadDot.visibility = if (unread) View.VISIBLE else View.GONE
            notifTime.setTextColor(if (unread) 0xFF2563EB.toInt() else 0xFF64748B.toInt())

            root.setOnClickListener { onClick(item) }
        }
    }
}