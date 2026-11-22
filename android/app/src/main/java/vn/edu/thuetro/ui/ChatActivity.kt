package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.BlockConversationRequest
import vn.edu.thuetro.data.ChatMessage
import vn.edu.thuetro.data.Conversation
import vn.edu.thuetro.data.OpenConversationRequest
import vn.edu.thuetro.data.PinConversationRequest
import vn.edu.thuetro.data.PublicUser
import vn.edu.thuetro.data.SendMessageRequest
import vn.edu.thuetro.data.SessionManager
import vn.edu.thuetro.databinding.ItemMessageReceivedBinding
import vn.edu.thuetro.databinding.ItemMessageSentBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ChatActivity : AppCompatActivity() {

    private var conversationId: String? = null
    private var otherUserId: Long = -1L
    private var otherUserName: String = "Trò chuyện"
    private var otherUserRole: String? = null
    private var otherUserPhone: String? = null
    private var pendingRoomId: Long? = null

    private var isPinned: Boolean = false
    private var blockedByMe: Boolean = false
    private var blockedByOther: Boolean = false

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var inputMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var emptyChatText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        applySafeArea()

        conversationId = intent.getStringExtra("conversation_id")
        otherUserId = intent.getLongExtra("other_user_id", -1L)
        otherUserName = intent.getStringExtra("other_user_name") ?: "Trò chuyện"
        otherUserRole = intent.getStringExtra("other_user_role")
        otherUserPhone = intent.getStringExtra("other_user_phone")
        val initRoomId = intent.getLongExtra("room_id", -1L)
        if (initRoomId > 0L) pendingRoomId = initRoomId

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        bindHeader(
            name = otherUserName,
            avatar = intent.getStringExtra("other_user_avatar"),
            role = otherUserRole,
            phone = otherUserPhone
        )
        setupRoomContextBanner(
            roomId = initRoomId,
            title = intent.getStringExtra("room_title"),
            price = if (intent.hasExtra("room_price")) intent.getLongExtra("room_price", 0L) else null,
            image = intent.getStringExtra("room_image")
        )

        recycler = findViewById(R.id.messageRecycler)
        inputMessage = findViewById(R.id.inputMessage)
        btnSend = findViewById(R.id.btnSend)
        emptyChatText = findViewById(R.id.emptyChatText)

        val myId = SessionManager(this).userId
        adapter = ChatMessageAdapter(myId) { roomId ->
            startActivity(Intent(this, RoomDetailActivity::class.java).putExtra("room_id", roomId))
        }
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        btnSend.setOnClickListener { sendCurrentMessage() }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMoreMenu(it) }
        findViewById<View>(R.id.btnUnblock).setOnClickListener { toggleBlock(targetBlocked = false) }

        initAndPoll()
    }

    private fun bindHeader(name: String, avatar: String?, role: String?, phone: String?) {
        otherUserName = name
        otherUserRole = role
        otherUserPhone = phone
        findViewById<ImageView>(R.id.headerAvatar).loadAvatar(avatar)
        findViewById<TextView>(R.id.headerName).text = name
        renderRoleSubtitle()
        findViewById<View>(R.id.userHeader).setOnClickListener {
            if (otherUserId > 0L) openUserProfile(otherUserId)
        }
        val btnCall = findViewById<View>(R.id.btnCall)
        if (phone.isNullOrBlank()) {
            btnCall.visibility = View.GONE
        } else {
            btnCall.visibility = View.VISIBLE
            btnCall.setOnClickListener { dialPhone(phone) }
        }
    }

    private fun renderRoleSubtitle() {
        val base = roleLabel(otherUserRole)
        findViewById<TextView>(R.id.headerRole).text = if (isPinned) "$base • Đã ghim" else base
    }

    private fun applyConversationState(conv: Conversation) {
        isPinned = conv.isPinned
        blockedByMe = conv.blockedByMe
        blockedByOther = conv.blockedByOther
        bindHeaderFromUser(conv.otherUser)
        renderBlockState()
    }

    private fun bindHeaderFromUser(user: PublicUser) {
        otherUserId = user.id
        bindHeader(user.fullName, user.avatarUrl, user.role, user.phone)
    }

    private fun renderBlockState() {
        val blockedBanner = findViewById<View>(R.id.blockedBanner)
        val blockedText = findViewById<TextView>(R.id.blockedText)
        val btnUnblock = findViewById<View>(R.id.btnUnblock)
        val composerBar = findViewById<View>(R.id.composerBar)
        val roomBanner = findViewById<View>(R.id.roomContextBanner)

        when {
            blockedByMe -> {
                blockedBanner.visibility = View.VISIBLE
                blockedText.text = "Bạn đã chặn người dùng này."
                btnUnblock.visibility = View.VISIBLE
                composerBar.visibility = View.GONE
                roomBanner.visibility = View.GONE
            }
            blockedByOther -> {
                blockedBanner.visibility = View.VISIBLE
                blockedText.text = "Bạn không thể nhắn tin vì người dùng này đã chặn bạn."
                btnUnblock.visibility = View.GONE
                composerBar.visibility = View.GONE
                roomBanner.visibility = View.GONE
            }
            else -> {
                blockedBanner.visibility = View.GONE
                composerBar.visibility = View.VISIBLE
                if (pendingRoomId != null && !intent.getStringExtra("room_title").isNullOrBlank()) {
                    roomBanner.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showMoreMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Xem trang cá nhân")
        popup.menu.add(0, 2, 1, if (isPinned) "Bỏ ghim cuộc trò chuyện" else "Ghim cuộc trò chuyện")
        popup.menu.add(0, 3, 2, "Xóa cuộc trò chuyện")
        popup.menu.add(0, 4, 3, if (blockedByMe) "Bỏ chặn người dùng" else "Chặn người dùng")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> if (otherUserId > 0L) openUserProfile(otherUserId)
                2 -> togglePin()
                3 -> confirmDeleteConversation()
                4 -> {
                    if (blockedByMe) {
                        toggleBlock(false)
                    } else {
                        confirm("Chặn $otherUserName", "Bạn và người này sẽ không thể nhắn tin cho nhau cho tới khi bạn bỏ chặn.") {
                            toggleBlock(true)
                        }
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun togglePin() {
        val convId = conversationId ?: return
        lifecycleScope.launch {
            try {
                val target = !isPinned
                val res = ApiClient.service(this@ChatActivity).pinConversation(convId, PinConversationRequest(target))
                if (res.success) {
                    isPinned = res.data?.get("isPinned") ?: target
                    renderRoleSubtitle()
                    toast(res.message)
                }
            } catch (e: Exception) {
                toast(e.apiMessage() ?: "Không cập nhật được trạng thái ghim.")
            }
        }
    }

    private fun confirmDeleteConversation() {
        val convId = conversationId ?: return
        confirm("Xóa cuộc trò chuyện", "Lịch sử trò chuyện trên tài khoản của bạn sẽ bị xóa.") {
            lifecycleScope.launch {
                try {
                    val res = ApiClient.service(this@ChatActivity).deleteConversation(convId)
                    toast(res.message.ifBlank { "Đã xóa cuộc trò chuyện." })
                    if (res.success) finish()
                } catch (e: Exception) {
                    toast(e.apiMessage() ?: "Không xóa được cuộc trò chuyện.")
                }
            }
        }
    }

    private fun toggleBlock(targetBlocked: Boolean) {
        val convId = conversationId ?: return
        lifecycleScope.launch {
            try {
                val res = ApiClient.service(this@ChatActivity).blockConversation(convId, BlockConversationRequest(targetBlocked))
                if (res.success) {
                    blockedByMe = res.data?.get("blockedByMe") ?: targetBlocked
                    renderBlockState()
                    toast(res.message)
                }
            } catch (e: Exception) {
                toast(e.apiMessage() ?: "Không cập nhật được trạng thái chặn.")
            }
        }
    }

    private fun setupRoomContextBanner(roomId: Long, title: String?, price: Long?, image: String?) {
        val banner = findViewById<View>(R.id.roomContextBanner)
        if (roomId <= 0L || title.isNullOrBlank()) {
            banner.visibility = View.GONE
            return
        }
        banner.visibility = View.VISIBLE
        findViewById<TextView>(R.id.contextRoomTitle).text = title
        findViewById<TextView>(R.id.contextRoomPrice).text = price?.let { "${it.money()}/tháng" }.orEmpty()
        findViewById<ImageView>(R.id.contextRoomImage).load(image) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
        }
        banner.setOnClickListener {
            startActivity(Intent(this, RoomDetailActivity::class.java).putExtra("room_id", roomId))
        }
        findViewById<View>(R.id.btnDismissContext).setOnClickListener {
            pendingRoomId = null
            banner.visibility = View.GONE
        }
    }

    private fun initAndPoll() {
        val progress = findViewById<View>(R.id.progress)
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                if (conversationId.isNullOrBlank() && otherUserId > 0L) {
                    val conv = ApiClient.service(this@ChatActivity)
                        .openConversation(OpenConversationRequest(otherUserId, pendingRoomId))
                        .data
                    if (conv == null) {
                        toast("Không mở được cuộc trò chuyện.")
                        finish()
                        return@launch
                    }
                    conversationId = conv.id
                    applyConversationState(conv)
                }
                loadMessages(scrollToBottom = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.apiMessage() ?: "Không tải được cuộc trò chuyện.")
            } finally {
                progress.visibility = View.GONE
            }

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(3_000L)
                    if (!conversationId.isNullOrBlank()) {
                        runCatching { loadMessages(scrollToBottom = false) }
                    }
                }
            }
        }
    }

    private suspend fun loadMessages(scrollToBottom: Boolean) {
        val convId = conversationId ?: return
        val res = ApiClient.service(this).messages(convId).data ?: return
        applyConversationState(res.conversation)
        val changed = adapter.submitIfChanged(res.messages)
        emptyChatText.visibility = if (res.messages.isEmpty()) View.VISIBLE else View.GONE
        if ((scrollToBottom || changed) && res.messages.isNotEmpty()) {
            recycler.scrollToPosition(res.messages.size - 1)
        }
    }

    private fun sendCurrentMessage() {
        if (blockedByMe || blockedByOther) return
        val text = inputMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        btnSend.isEnabled = false
        lifecycleScope.launch {
            try {
                val api = ApiClient.service(this@ChatActivity)
                if (conversationId.isNullOrBlank() && otherUserId > 0L) {
                    val conv = api.openConversation(OpenConversationRequest(otherUserId, pendingRoomId)).data
                    conversationId = conv?.id
                    if (conv != null) applyConversationState(conv)
                }
                val convId = conversationId ?: return@launch
                val attachedRoom = pendingRoomId
                val res = api.sendMessage(convId, SendMessageRequest(text, attachedRoom))
                if (res.success && res.data != null) {
                    inputMessage.setText("")
                    if (attachedRoom != null) {
                        pendingRoomId = null
                        findViewById<View>(R.id.roomContextBanner).visibility = View.GONE
                    }
                    adapter.append(res.data)
                    emptyChatText.visibility = View.GONE
                    recycler.scrollToPosition(adapter.itemCount - 1)
                } else {
                    toast(res.message.ifBlank { "Không gửi được tin nhắn." })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.apiMessage() ?: "Không gửi được tin nhắn.")
            } finally {
                btnSend.isEnabled = true
            }
        }
    }
}

private class ChatMessageAdapter(
    private val myId: Long,
    private val onRoomClick: (Long) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = mutableListOf<ChatMessage>()

    fun submitIfChanged(data: List<ChatMessage>): Boolean {
        if (items == data) return false
        items = data.toMutableList()
        notifyDataSetChanged()
        return true
    }

    fun append(msg: ChatMessage) {
        if (items.any { it.id == msg.id }) return
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].senderId == myId) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            SentHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
        } else {
            ReceivedHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        if (holder is SentHolder) holder.bind(msg) else if (holder is ReceivedHolder) holder.bind(msg)
    }

    private fun bindRoomCard(card: View, titleView: TextView, priceView: TextView, msg: ChatMessage) {
        val rid = msg.roomId
        if (rid != null && !msg.roomTitle.isNullOrBlank()) {
            card.visibility = View.VISIBLE
            titleView.text = msg.roomTitle
            priceView.text = msg.roomPrice?.let { "${it.money()}/tháng" } ?: "Xem phòng ›"
            card.setOnClickListener { onRoomClick(rid) }
        } else {
            card.visibility = View.GONE
        }
    }

    private fun formatMsgTime(iso: String?): String {
        val raw = iso?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val parsed = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
            .firstNotNullOfOrNull { p ->
                runCatching {
                    SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)
                }.getOrNull()
            } ?: return ""
        return SimpleDateFormat("HH:mm • dd/MM", Locale.getDefault()).format(parsed)
    }

    inner class SentHolder(private val b: ItemMessageSentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage) = with(b) {
            msgText.text = msg.text
            msgTime.text = formatMsgTime(msg.createdAt)
            bindRoomCard(msgRoomCard, msgRoomTitle, msgRoomPrice, msg)
        }
    }

    inner class ReceivedHolder(private val b: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(msg: ChatMessage) = with(b) {
            msgText.text = msg.text
            msgTime.text = formatMsgTime(msg.createdAt)
            bindRoomCard(msgRoomCard, msgRoomTitle, msgRoomPrice, msg)
        }
    }
}