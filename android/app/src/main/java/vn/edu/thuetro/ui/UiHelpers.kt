package vn.edu.thuetro.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.NumberFormat
import java.util.Locale

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
