package vn.edu.thuetro.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import coil.load
import vn.edu.thuetro.R
import vn.edu.thuetro.data.SessionManager
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats a long currency amount in VND to Vietnamese standard representation.
 */
fun Long.money(): String = NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(this).replace("₫", "đ")

/**
 * Displays a custom toast view to prevent framework crash issues on specific Android emulators/devices.
 *
 * @param message Text message to be displayed.
 */
@Suppress("DEPRECATION")
fun Context.toast(message: String) {
    val density = resources.displayMetrics.density
    val padding = (14 * density).toInt()
    val textView = TextView(this).apply {
        text = message
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(padding, (10 * density).toInt(), padding, (10 * density).toInt())
        background = GradientDrawable().apply {
            setColor(Color.rgb(45, 55, 72))
            cornerRadius = 10 * density
        }
    }
    Toast(this).apply {
        duration = Toast.LENGTH_SHORT
        setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, (72 * density).toInt())
        view = textView
        show()
    }
}

/**
 * Shows a standard confirmation dialog.
 *
 * @param title Dialog title.
 * @param message Dialog body message.
 * @param onYes Callback executed when user clicks 'Đồng ý'.
 */
fun Context.confirm(title: String, message: String, onYes: () -> Unit) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton("Hủy", null)
        .setPositiveButton("Đồng ý") { _, _ -> onYes() }
        .show()
}

/**
 * Ensures interactive content clears system bars, screen cutouts, and the soft keyboard.
 */
fun android.app.Activity.applySafeArea() {
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    val content = findViewById<android.view.ViewGroup>(android.R.id.content)
    val root = content.getChildAt(0) ?: return
    val left = root.paddingLeft
    val top = root.paddingTop
    val right = root.paddingRight
    val bottom = root.paddingBottom
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val safe = insets.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )
        val keyboard = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
        view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + maxOf(safe.bottom, keyboard.bottom))
        // The root owns these insets; children must not apply the same spacing again.
        androidx.core.view.WindowInsetsCompat.CONSUMED
    }
    androidx.core.view.ViewCompat.requestApplyInsets(root)
    root.enableVietnameseIme()
}

/**
 * Gợi ý bàn phím hệ thống ưu tiên tiếng Việt cho các ô nhập văn bản tiếng Việt thông thường.
 */
fun android.view.View.enableVietnameseIme() {
    if (this is android.widget.EditText) {
        val typeClass = inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        val isText = typeClass == android.text.InputType.TYPE_CLASS_TEXT
        val isPassword = variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        val isRestricted = variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_URI
        if (isText && !isPassword && !isRestricted) {
            imeHintLocales = android.os.LocaleList(Locale.forLanguageTag("vi-VN"))
        }
    } else if (this is android.view.ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).enableVietnameseIme()
        }
    }
}

/**
 * Extracts error message returned by server (HTTP 4xx/5xx); null if not HTTP error or message missing.
 */
fun Throwable.apiMessage(): String? = (this as? retrofit2.HttpException)?.let { e ->
    runCatching { org.json.JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * Returns a human-readable localized Vietnamese error message for network & API exceptions.
 * Handles [SocketTimeoutException], [ConnectException], [UnknownHostException], and HTTP errors.
 */
fun Throwable.friendlyErrorMessage(defaultMessage: String = "Không kết nối được server"): String {
    return when (this) {
        is SocketTimeoutException -> "Máy chủ phản hồi quá chậm. Vui lòng thử lại sau."
        is ConnectException, is UnknownHostException -> "Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối mạng và thử lại."
        is retrofit2.HttpException -> apiMessage() ?: "Lỗi từ phía máy chủ (${code()})"
        else -> apiMessage() ?: localizedMessage?.takeIf { it.isNotBlank() } ?: defaultMessage
    }
}

/** Đổi nội dung + màu nhãn trạng thái (nền nhạt, chữ đậm cùng tông). */
fun TextView.setChip(label: String, background: Int, foreground: Int) {
    text = label
    setTextColor(foreground)
    backgroundTintList = ColorStateList.valueOf(background)
}

/** Nhãn trạng thái yêu cầu thuê: chờ = vàng, chấp nhận = xanh, từ chối = đỏ, hủy = xám. */
fun TextView.setRequestStatus(status: String) = when (status) {
    "PENDING" -> setChip("Đang chờ phản hồi", 0xFFFEF3C7.toInt(), 0xFFB45309.toInt())
    "ACCEPTED" -> setChip("Đã chấp nhận", 0xFFDCFCE7.toInt(), 0xFF16A34A.toInt())
    "REJECTED" -> setChip("Đã từ chối", 0xFFFEE2E2.toInt(), 0xFFDC2626.toInt())
    "CANCELLED" -> setChip("Đã hủy", 0xFFE2E8F0.toInt(), 0xFF64748B.toInt())
    else -> setChip(status, 0xFFE2E8F0.toInt(), 0xFF64748B.toInt())
}

/** Nhãn trạng thái phòng: trống = xanh, giữ chỗ = vàng, đã thuê/ẩn = xám. */
fun TextView.setRoomStatus(status: String) = when (status) {
    "AVAILABLE" -> setChip("Còn trống", 0xFFDCFCE7.toInt(), 0xFF16A34A.toInt())
    "RESERVED" -> setChip("Đã giữ chỗ", 0xFFFEF3C7.toInt(), 0xFFB45309.toInt())
    "RENTED" -> setChip("Đã cho thuê", 0xFFE2E8F0.toInt(), 0xFF64748B.toInt())
    "HIDDEN" -> setChip("Đã ẩn", 0xFFE2E8F0.toInt(), 0xFF64748B.toInt())
    else -> setChip(status, 0xFFE2E8F0.toInt(), 0xFF64748B.toInt())
}

/** Nhãn vai trò tài khoản: Quản trị viên = tím, Chủ trọ = xanh dương, Người thuê = xanh lá. */
fun TextView.setRoleBadge(role: String?) = when (role) {
    "ADMIN" -> setChip("Quản trị viên", 0xFFF3E8FF.toInt(), 0xFF7E22CE.toInt())
    "LANDLORD" -> setChip("Chủ trọ", 0xFFDBEAFE.toInt(), 0xFF1D4ED8.toInt())
    else -> setChip("Người thuê", 0xFFDCFCE7.toInt(), 0xFF16A34A.toInt())
}

fun roleLabel(role: String?): String = when (role) {
    "ADMIN" -> "Quản trị viên"
    "LANDLORD" -> "Chủ trọ"
    else -> "Người thuê"
}

/** Định dạng gọn mức giá (vd: 3 triệu, 3,5 triệu). */
fun Long.compactMoney(): String {
    if (this >= 1_000_000L) {
        val millions = this / 1_000_000.0
        val whole = millions.toLong()
        return if (this % 1_000_000L == 0L) "$whole triệu"
        else String.format(Locale("vi", "VN"), "%.1f triệu", millions).replace(".0 ", " ")
    }
    if (this >= 1_000L) return "${this / 1_000L}k"
    return "$this đ"
}

/** Chuyển chuỗi ISO-8601 UTC sang thời gian tương đối tiếng Việt (tương thích minSdk 24). */
fun String?.relativeTime(): String {
    val raw = this?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    val date: Date = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        .firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw)
            }.getOrNull()
        } ?: return ""
    val diffMs = (System.currentTimeMillis() - date.time).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    val days = diffMs / 86_400_000L
    return when {
        mins < 1 -> "Vừa xong"
        mins < 60 -> "$mins phút trước"
        hours < 24 -> "$hours giờ trước"
        days == 1L -> "Hôm qua"
        days < 7 -> "$days ngày trước"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
    }
}

fun ImageView.loadAvatar(url: String?) {
    if (url.isNullOrBlank()) {
        setImageResource(R.drawable.ic_avatar_placeholder)
    } else {
        load(url) {
            crossfade(true)
            placeholder(R.drawable.ic_avatar_placeholder)
            error(R.drawable.ic_avatar_placeholder)
        }
    }
}

fun Context.openUserProfile(userId: Long) {
    if (userId <= 0L) return
    startActivity(Intent(this, UserProfileActivity::class.java).putExtra("user_id", userId))
}

fun Context.openChatWith(
    otherUserId: Long,
    otherName: String? = null,
    otherAvatar: String? = null,
    otherRole: String? = null,
    otherPhone: String? = null,
    roomId: Long? = null,
    roomTitle: String? = null,
    roomPrice: Long? = null,
    roomImage: String? = null
) {
    val myId = SessionManager(this).userId
    if (otherUserId <= 0L || otherUserId == myId) {
        toast("Không thể nhắn tin với chính tài khoản của bạn.")
        return
    }
    val intent = Intent(this, ChatActivity::class.java).apply {
        putExtra("other_user_id", otherUserId)
        otherName?.let { putExtra("other_user_name", it) }
        otherAvatar?.let { putExtra("other_user_avatar", it) }
        otherRole?.let { putExtra("other_user_role", it) }
        otherPhone?.let { putExtra("other_user_phone", it) }
        if (roomId != null && roomId > 0L) putExtra("room_id", roomId)
        roomTitle?.let { putExtra("room_title", it) }
        if (roomPrice != null) putExtra("room_price", roomPrice)
        roomImage?.let { putExtra("room_image", it) }
    }
    startActivity(intent)
}

fun Context.dialPhone(phone: String?) {
    val clean = phone?.trim().orEmpty()
    if (clean.isEmpty()) {
        toast("Người dùng chưa cập nhật số điện thoại.")
        return
    }
    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")))
}

fun Context.openPhotoViewer(images: List<String>, startPosition: Int = 0, title: String? = null) {
    if (images.isEmpty()) return
    val intent = Intent(this, PhotoViewerActivity::class.java).apply {
        putStringArrayListExtra("images", ArrayList(images))
        putExtra("position", startPosition.coerceIn(0, images.lastIndex))
        title?.let { putExtra("title", it) }
    }
    startActivity(intent)
}

/** Cập nhật dải chấm chỉ báo ảnh đang xem trong ViewPager2. */
fun android.widget.LinearLayout.renderPhotoDots(count: Int, activeIndex: Int) {
    if (count <= 1) {
        visibility = android.view.View.GONE
        removeAllViews()
        return
    }
    visibility = android.view.View.VISIBLE
    val density = resources.displayMetrics.density
    val heightPx = (6 * density).toInt()
    val activeWidthPx = (18 * density).toInt()
    val inactiveWidthPx = (6 * density).toInt()
    val marginPx = (3 * density).toInt()

    if (childCount != count) {
        removeAllViews()
        repeat(count) {
            addView(android.view.View(context))
        }
    }
    for (i in 0 until count) {
        val isActive = i == activeIndex
        val dot = getChildAt(i)
        dot.layoutParams = android.widget.LinearLayout.LayoutParams(
            if (isActive) activeWidthPx else inactiveWidthPx,
            heightPx
        ).apply {
            marginStart = marginPx
            marginEnd = marginPx
        }
        dot.setBackgroundResource(if (isActive) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
    }
}
