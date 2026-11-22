package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.RentalRequest
import vn.edu.thuetro.data.RequestStatusUpdate
import vn.edu.thuetro.data.SessionManager
import vn.edu.thuetro.databinding.ItemRequestBinding

class RequestsFragment : Fragment(R.layout.fragment_requests) {
    private lateinit var adapter: RequestAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? MainActivity)?.title("Yêu cầu thuê")
        val session = SessionManager(requireContext())
        val isOwnerMode = session.role in listOf("LANDLORD", "ADMIN")

        adapter = RequestAdapter(
            myId = session.userId,
            ownerMode = isOwnerMode,
            onAction = { request, status ->
                if (status == "CANCELLED") {
                    requireContext().confirm("Hủy lịch xem phòng", "Bạn có chắc muốn hủy yêu cầu xem phòng này?") {
                        update(request.id, "CANCELLED", null)
                    }
                } else {
                    showOwnerNote(request, status)
                }
            },
            onRoomClick = { roomId ->
                if (roomId > 0L) {
                    startActivity(Intent(requireContext(), RoomDetailActivity::class.java).putExtra("room_id", roomId))
                }
            },
            onProfileClick = { userId -> requireContext().openUserProfile(userId) },
            onChatClick = { r, otherId, otherName, otherAvatar, otherRole, otherPhone ->
                requireContext().openChatWith(
                    otherUserId = otherId,
                    otherName = otherName,
                    otherAvatar = otherAvatar,
                    otherRole = otherRole,
                    otherPhone = otherPhone,
                    roomId = r.roomId,
                    roomTitle = r.roomTitle,
                    roomPrice = r.roomPrice,
                    roomImage = r.roomImage
                )
            }
        )

        view.findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RequestsFragment.adapter
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (view != null && ::adapter.isInitialized) load()
    }

    private fun load() = viewLifecycleOwner.lifecycleScope.launch {
        val root = view ?: return@launch
        try {
            val items = ApiClient.service(requireContext()).requests().data.orEmpty()
            adapter.submit(items)
            root.findViewById<android.widget.TextView>(R.id.emptyText).visibility =
                if (items.isEmpty()) View.VISIBLE else View.GONE
        } catch (_: Exception) {
            if (isAdded) requireContext().toast("Không tải được yêu cầu.")
        } finally {
            view?.findViewById<View>(R.id.progress)?.visibility = View.GONE
        }
    }

    private fun showOwnerNote(request: RentalRequest, status: String) {
        val note = EditText(requireContext()).apply {
            hint = if (status == "ACCEPTED") "Lời nhắn xác nhận (không bắt buộc)" else "Lý do từ chối (không bắt buộc)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            enableVietnameseIme()
        }
        val title = if (status == "ACCEPTED") "Chấp nhận yêu cầu" else "Từ chối yêu cầu"
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(note)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xác nhận") { _, _ ->
                update(request.id, status, note.text.toString().trim().ifBlank { null })
            }
            .show()
    }

    private fun update(id: Long, status: String, note: String?) = viewLifecycleOwner.lifecycleScope.launch {
        try {
            val result = ApiClient.service(requireContext()).updateRequest(id, RequestStatusUpdate(status, note))
            requireContext().toast(result.message)
            load()
        } catch (e: Exception) {
            requireContext().toast(e.apiMessage() ?: "Không cập nhật được yêu cầu.")
        }
    }
}

class RequestAdapter(
    private val myId: Long,
    private val ownerMode: Boolean,
    private val onAction: (RentalRequest, String) -> Unit,
    private val onRoomClick: (Long) -> Unit,
    private val onProfileClick: (Long) -> Unit,
    private val onChatClick: (RentalRequest, Long, String?, String?, String?, String?) -> Unit
) : RecyclerView.Adapter<RequestAdapter.Holder>() {

    private var items = emptyList<RentalRequest>()

    fun submit(data: List<RentalRequest>) {
        items = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int) =
        Holder(ItemRequestBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: Holder, p: Int) = h.bind(items[p])

    inner class Holder(private val b: ItemRequestBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: RentalRequest) = with(b) {
            roomTitle.text = r.roomTitle
            roomTitle.setOnClickListener { onRoomClick(r.roomId) }

            val viewingAsOwner = ownerMode && r.tenantId != myId
            val otherId = if (viewingAsOwner) r.tenantId else r.ownerId
            val otherName = if (viewingAsOwner) r.tenantName else (r.ownerName ?: "Chủ trọ")
            val otherAvatar = if (viewingAsOwner) r.tenantAvatarUrl else r.ownerAvatarUrl
            val otherRole = if (viewingAsOwner) "TENANT" else "LANDLORD"
            val otherPhone = if (viewingAsOwner) r.tenantPhone else r.ownerPhone

            tenant.text = if (viewingAsOwner) {
                "Người thuê: ${r.tenantName}${r.tenantPhone?.let { " • $it" } ?: ""}"
            } else {
                "Chủ trọ: ${otherName}${r.ownerPhone?.let { " • $it" } ?: ""}"
            }
            tenant.setOnClickListener {
                if (otherId > 0L) onProfileClick(otherId)
            }

            if (r.message.isNullOrBlank()) {
                message.visibility = View.GONE
            } else {
                message.text = "Lời nhắn: ${r.message}"
                message.visibility = View.VISIBLE
            }

            if (r.viewingTime.isNullOrBlank()) {
                viewingTime.visibility = View.GONE
            } else {
                viewingTime.text = "Thời gian xem: ${r.viewingTime}"
                viewingTime.visibility = View.VISIBLE
            }

            if (r.ownerNote.isNullOrBlank()) {
                ownerNote.visibility = View.GONE
            } else {
                ownerNote.text = "Phản hồi của chủ trọ: ${r.ownerNote}"
                ownerNote.visibility = View.VISIBLE
            }

            status.setRequestStatus(r.status)

            chatBtn.visibility = if (otherId > 0L && otherId != myId) View.VISIBLE else View.GONE
            chatBtn.setOnClickListener {
                onChatClick(r, otherId, otherName, otherAvatar, otherRole, otherPhone)
            }

            val isPending = r.status == "PENDING"
            actions.visibility = if (isPending && viewingAsOwner) View.VISIBLE else View.GONE
            cancelBtn.visibility = if (isPending && r.tenantId == myId) View.VISIBLE else View.GONE

            accept.setOnClickListener { onAction(r, "ACCEPTED") }
            reject.setOnClickListener { onAction(r, "REJECTED") }
            cancelBtn.setOnClickListener { onAction(r, "CANCELLED") }
        }
    }
}