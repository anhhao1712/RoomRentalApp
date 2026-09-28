package vn.edu.thuetro.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import vn.edu.thuetro.R
import vn.edu.thuetro.data.*

class RoomFormActivity : AppCompatActivity() {
    private val roomId by lazy { intent.getLongExtra("room_id", -1) }
    private val statusCodes = listOf("AVAILABLE", "RESERVED", "RENTED", "HIDDEN")
    private val statusLabels = listOf("Còn trống", "Đã giữ chỗ", "Đã cho thuê", "Ẩn tin")
    private lateinit var api: ApiService
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { uploadImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_form)
        api = ApiClient.service(this)
        findViewById<Spinner>(R.id.status).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statusLabels)
        findViewById<Spinner>(R.id.areaSpinner).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, AreaCatalog.options.map { it.label })
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.delete).setOnClickListener { confirm("Ẩn tin đăng", "Tin sẽ không còn hiển thị với người thuê.") { delete() } }
        findViewById<Button>(R.id.pickImage).setOnClickListener { pickImage.launch("image/*") }
        loadAmenities()
    }

    private fun uploadImage(uri: Uri) = lifecycleScope.launch {
        try {
            findViewById<Button>(R.id.pickImage).isEnabled = false
            toast("Đang tải ảnh lên...")
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) { toast("Không đọc được ảnh đã chọn."); return@launch }
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val ext = if (mimeType.contains("png")) "png" else "jpg"
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("image", "room.$ext", body)
            val result = api.uploadImage(part)
            val url = result.data?.get("url")
            if (result.success && url != null) { addImageUrl(url); toast("Đã thêm ảnh.") }
            else toast(result.message.ifBlank { "Tải ảnh lên thất bại." })
        } catch (_: Exception) { toast("Tải ảnh lên thất bại. Kiểm tra kết nối tới server.") }
        finally { findViewById<Button>(R.id.pickImage).isEnabled = true }
    }

    private fun addImageUrl(url: String) {
        val field = findViewById<TextInputEditText>(R.id.images)
        val current = field.text?.toString().orEmpty().trim()
        field.setText(if (current.isBlank()) url else "$current\n$url")
        refreshPreview()
    }

    private fun refreshPreview() {
        val holder = findViewById<LinearLayout>(R.id.imagePreview)
        holder.removeAllViews()
        val size = (88 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()
        value(R.id.images).lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { url ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
                scaleType = ImageView.ScaleType.CENTER_CROP
                load(url)
            }
            holder.addView(iv)
        }
    }

    private fun loadAmenities() = lifecycleScope.launch {
        try {
            val amenities = api.amenities().data.orEmpty()
            val holder = findViewById<LinearLayout>(R.id.amenityContainer)
            amenities.forEach { amenity ->
                holder.addView(CheckBox(this@RoomFormActivity).apply { id = View.generateViewId(); tag = amenity.id; text = amenity.name })
            }
            if (roomId > 0) loadRoom()
        } catch (_: Exception) { toast("Không tải được biểu mẫu.") }
    }

    private fun loadRoom() = lifecycleScope.launch {
        try {
            val room = api.room(roomId).data ?: return@launch
            findViewById<TextView>(R.id.formTitle).text = "Chỉnh sửa phòng"
            text(R.id.title, room.title); text(R.id.description, room.description); text(R.id.address, room.address)
            findViewById<Spinner>(R.id.areaSpinner).setSelection(AreaCatalog.options.indexOfFirst { it.district == room.district && it.city == room.city }.coerceAtLeast(0))
            text(R.id.price, room.price.toString()); text(R.id.area, room.area.toString()); text(R.id.maxTenants, room.maxTenants.toString())
            text(R.id.deposit, room.deposit.toString()); text(R.id.electricityPrice, room.electricityPrice.toString())
            text(R.id.waterPrice, room.waterPrice.toString()); text(R.id.internetFee, room.internetFee.toString())
            text(R.id.images, room.images.joinToString("\n")); refreshPreview()
            findViewById<Spinner>(R.id.status).setSelection(statusCodes.indexOf(room.status).coerceAtLeast(0))
            val selected = room.amenities.map { it.id }.toSet()
            val holder = findViewById<LinearLayout>(R.id.amenityContainer)
            for (index in 0 until holder.childCount) {
                (holder.getChildAt(index) as? CheckBox)?.isChecked = (holder.getChildAt(index).tag as? Long) in selected
            }
            findViewById<Button>(R.id.delete).visibility = View.VISIBLE
        } catch (_: Exception) { toast("Không tải được phòng.") }
    }

    private fun save() {
        val title = value(R.id.title); val address = value(R.id.address)
        val price = value(R.id.price).toLongOrNull(); val area = value(R.id.area).toDoubleOrNull()
        if (title.isBlank() || address.isBlank() || price == null || price <= 0 || area == null || area <= 0) {
            toast("Nhập đủ tiêu đề, địa chỉ, giá và diện tích hợp lệ."); return
        }
        val checks = findViewById<LinearLayout>(R.id.amenityContainer)
        val amenityIds = (0 until checks.childCount).mapNotNull { (checks.getChildAt(it) as? CheckBox)?.takeIf { check -> check.isChecked }?.tag as? Long }
        val selectedArea = AreaCatalog.options[findViewById<Spinner>(R.id.areaSpinner).selectedItemPosition]
        val body = RoomUpsertRequest(
            title, value(R.id.description).ifBlank { null }, address, null, selectedArea.district, selectedArea.city,
            price, area, value(R.id.maxTenants).toIntOrNull()?.coerceAtLeast(1) ?: 2,
            value(R.id.deposit).toLongOrNull()?.coerceAtLeast(0) ?: 0,
            value(R.id.electricityPrice).toLongOrNull()?.coerceAtLeast(0) ?: 3500,
            value(R.id.waterPrice).toLongOrNull()?.coerceAtLeast(0) ?: 15000,
            value(R.id.internetFee).toLongOrNull()?.coerceAtLeast(0) ?: 0,
            statusCodes[findViewById<Spinner>(R.id.status).selectedItemPosition],
            value(R.id.images).lines().map { it.trim() }.filter { it.isNotBlank() }, amenityIds
        )
        lifecycleScope.launch {
            try {
                val result = if (roomId > 0) api.updateRoom(roomId, body) else api.createRoom(body)
                toast(result.message); if (result.success) finish()
            } catch (_: Exception) { toast("Không lưu được phòng.") }
        }
    }

    private fun delete() = lifecycleScope.launch {
        try { val result = api.deleteRoom(roomId); toast(result.message); if (result.success) finish() }
        catch (_: Exception) { toast("Không thể ẩn tin đăng.") }
    }

    private fun value(id: Int) = findViewById<TextInputEditText>(id).text?.toString()?.trim().orEmpty()
    private fun text(id: Int, value: String?) { findViewById<TextInputEditText>(id).setText(value.orEmpty()) }
}
