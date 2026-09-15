package vn.edu.thuetro.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.NumberFormat
import java.util.Locale

fun Long.money(): String = NumberFormat.getCurrencyInstance(Locale("vi", "VN")).format(this).replace("₫", "đ")

/**
 * Nox's bundled Android image can crash while inflating the system Toast layout.
 * A small programmatic view avoids that framework layout entirely.
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
fun Context.confirm(title: String, message: String, onYes: () -> Unit) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Hủy", null).setPositiveButton("Đồng ý") { _, _ -> onYes() }.show() }
