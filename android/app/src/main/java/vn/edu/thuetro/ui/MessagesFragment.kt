package vn.edu.thuetro.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.BlockConversationRequest
import vn.edu.thuetro.data.Conversation
import vn.edu.thuetro.data.PinConversationRequest
import vn.edu.thuetro.data.SessionManager
import vn.edu.thuetro.databinding.ItemConversationBinding

class MessagesFragment : Fragment(R.layout.fragment_messages) {

    private lateinit var adapter: ConversationAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.title("Tin nhắn")

        val myId = SessionManager(requireContext()).userId
        adapter = ConversationAdapter(
            myId = myId,
            onClick = { conv ->
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("conversation_id", conv.id)
                    putExtra("other_user_id", conv.otherUser.id)
                    putExtra("other_user_name", conv.otherUser.fullName)
                    putExtra("other_user_avatar", conv.otherUser.avatarUrl)
                    putExtra("other_user_role", conv.otherUser.role)
                    putExtra("other_user_phone", conv.otherUser.phone)
                }
                startActivity(intent)
            },
            onMoreClick = { anchor, conv -> showConversationMenu(anchor, conv) }
        )

        view.findViewById<RecyclerView>(R.id.recycler).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MessagesFragment.adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    load(showLoading = false)
                    delay(6_000L)
                }
            }
        }
        load(showLoading = true)
    }

    override fun onResume() {
        super.onResume()
        if (view != null && ::adapter.isInitialized) load(showLoading = false)
    }

    private fun showConversationMenu(anchor: View, conv: Conversation) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, if (conv.isPinned) "Bỏ ghim cuộc trò chuyện" else "Ghim cuộc trò chuyện")
        popup.menu.add(0, 2, 1, "Xóa cuộc trò chuyện")
        popup.menu.add(0, 3, 2, if (conv.blockedByMe) "Bỏ chặn người dùng" else "Chặn người dùng")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> pinConversation(conv)
                2 -> deleteConversation(conv)
                3 -> blockConversation(conv)
            }
            true
        }
        popup.show()
    }

    private fun pinConversation(conv: Conversation) = viewLifecycleOwner.lifecycleScope.launch {
        try {
            val res = ApiClient.service(requireContext())
                .pinConversation(conv.id, PinConversationRequest(!conv.isPinned))
            requireContext().toast(res.message)
            if (res.success) load(showLoading = false)
        } catch (e: Exception) {
            requireContext().toast(e.apiMessage() ?: "Không cập nhật được trạng thái ghim.")
        }
    }

    private fun deleteConversation(conv: Conversation) {
        requireContext().confirm(
            "Xóa cuộc trò chuyện",
            "Lịch sử trò chuyện với ${conv.otherUser.fullName} trên tài khoản của bạn sẽ bị xóa."
        ) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val res = ApiClient.service(requireContext()).deleteConversation(conv.id)
                    requireContext().toast(res.message.ifBlank { "Đã xóa cuộc trò chuyện." })
                    if (res.success) load(showLoading = false)
                } catch (e: Exception) {
                    requireContext().toast(e.apiMessage() ?: "Không xóa được cuộc trò chuyện.")
                }
            }
        }
    }

    private fun blockConversation(conv: Conversation) {
        val execute = {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val res = ApiClient.service(requireContext())
                        .blockConversation(conv.id, BlockConversationRequest(!conv.blockedByMe))
                    requireContext().toast(res.message)
                    if (res.success) load(showLoading = false)
                } catch (e: Exception) {
                    requireContext().toast(e.apiMessage() ?: "Không cập nhật được trạng thái chặn.")
                }
            }
        }
        if (conv.blockedByMe) {
            execute()
        } else {
            requireContext().confirm(
                "Chặn ${conv.otherUser.fullName}",
                "Bạn và người này sẽ không thể nhắn tin cho nhau cho tới khi bạn bỏ chặn."
            ) { execute() }
        }
    }

    private fun load(showLoading: Boolean) {
        val root = view ?: return
        if (showLoading) root.findViewById<View>(R.id.progress).visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = ApiClient.service(requireContext()).conversations().data.orEmpty()
                adapter.submit(items)
                root.findViewById<TextView>(R.id.emptyText).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                (activity as? MainActivity)?.refreshBadges()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (showLoading && isAdded) requireContext().toast("Không tải được danh sách tin nhắn.")
            } finally {
                view?.findViewById<View>(R.id.progress)?.visibility = View.GONE
            }
        }
    }
}

private class ConversationAdapter(
    private val myId: Long,
    private val onClick: (Conversation) -> Unit,
    private val onMoreClick: (View, Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.Holder>() {

    private var items = emptyList<Conversation>()

    fun submit(data: List<Conversation>) {
        items = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val b: ItemConversationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(conv: Conversation) = with(b) {
            avatar.loadAvatar(conv.otherUser.avatarUrl)
            pinIcon.visibility = if (conv.isPinned) View.VISIBLE else View.GONE
            blockedBadge.visibility = if (conv.blockedByMe) View.VISIBLE else View.GONE
            otherName.text = conv.otherUser.fullName
            otherRole.setRoleBadge(conv.otherUser.role)
            timeText.text = conv.lastMessageAt.relativeTime()

            val preview = when {
                conv.lastMessage.isNotBlank() ->
                    if (conv.lastSenderId == myId) "Bạn: ${conv.lastMessage}" else conv.lastMessage
                !conv.lastRoomTitle.isNullOrBlank() -> "Quan tâm: ${conv.lastRoomTitle}"
                else -> "Bắt đầu cuộc trò chuyện"
            }
            lastMessage.text = preview

            val unread = conv.unreadCount > 0
            lastMessage.setTypeface(null, if (unread) Typeface.BOLD else Typeface.NORMAL)
            lastMessage.setTextColor(if (unread) 0xFF0F172A.toInt() else 0xFF64748B.toInt())

            if (unread) {
                unreadBadge.text = if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString()
                unreadBadge.visibility = View.VISIBLE
            } else {
                unreadBadge.visibility = View.GONE
            }
            root.setOnClickListener { onClick(conv) }
            root.setOnLongClickListener { onMoreClick(btnConvMore, conv); true }
            btnConvMore.setOnClickListener { onMoreClick(btnConvMore, conv) }
        }
    }
}