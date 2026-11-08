package vn.edu.thuetro.ui

import android.os.Bundle
import android.content.Intent
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.*

class RoomDetailActivity : AppCompatActivity() {
    private val id by lazy { intent.getLongExtra("room_id", -1) }
    private var room: Room? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_detail)
        applySafeArea()
        findViewById<View>(R.id.btnDetailBack).setOnClickListener { finish() }
        load()
    }
    private fun load()=lifecycleScope.launch{try{val response=ApiClient.service(this@RoomDetailActivity).room(id);room=response.data;if(room==null){toast(response.message);return@launch};bind(room!!)}catch(_:Exception){toast("Không tải được chi tiết phòng.")}}
    private fun bind(r:Room){
        val session = SessionManager(this)
        val isTenant = session.role == "TENANT"
        findViewById<ImageView>(R.id.roomImage).load(r.images.firstOrNull()){placeholder(android.R.drawable.ic_menu_gallery)}
        findViewById<TextView>(R.id.status).text=when(r.status) { "AVAILABLE" -> "Còn trống"; "RESERVED" -> "Đã giữ chỗ"; "RENTED" -> "Đã cho thuê"; "HIDDEN" -> "Đã ẩn"; else -> r.status }
        findViewById<TextView>(R.id.title).text=r.title;findViewById<TextView>(R.id.price).text="${r.price.money()}/tháng"
        findViewById<TextView>(R.id.address).text=listOfNotNull(r.address,r.ward,r.district,r.city).joinToString(", ")
        findViewById<TextView>(R.id.meta).text="${r.area} m² • Tối đa ${r.maxTenants} người • Cọc ${r.deposit.money()}"
        findViewById<TextView>(R.id.description).text=r.description ?: "Chủ trọ chưa bổ sung mô tả."
        findViewById<TextView>(R.id.amenities).text="Tiện ích: "+r.amenities.joinToString { it.name }.ifBlank{"Chưa cập nhật"}
        findViewById<TextView>(R.id.fees).text="Điện ${r.electricityPrice.money()}/kWh • Nước ${r.waterPrice.money()}/m³ • Internet ${r.internetFee.money()}/tháng"
        findViewById<TextView>(R.id.owner).text="Chủ trọ: ${r.landlordName}${r.landlordPhone?.let{" • $it"}?:""}"
        findViewById<Button>(R.id.favoriteButton).visibility = if (isTenant) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.requestButton).visibility = if (isTenant) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.roleHint).visibility = if (isTenant) View.GONE else View.VISIBLE
        val favoriteButton = findViewById<Button>(R.id.favoriteButton)
        favoriteButton.text = if (r.isFavorite) "Bỏ lưu phòng" else "Lưu phòng"
        favoriteButton.setOnClickListener {
            favoriteButton.isEnabled = false
            lifecycleScope.launch {
                try {
                    val result = ApiClient.service(this@RoomDetailActivity).toggleFavorite(r.id)
                    if (result.success) {
                        // API trả về Boolean thể hiện trạng thái yêu thích sau khi toggle.
                        val newFavorite = result.data ?: !(room?.isFavorite ?: r.isFavorite)
                        favoriteButton.text = if (newFavorite) "Bỏ lưu phòng" else "Lưu phòng"
                        room = room?.copy(isFavorite = newFavorite)
                        toast(if (newFavorite) "Đã lưu phòng" else "Đã bỏ lưu phòng")
                    } else {
                        toast(result.message.ifBlank { "Không cập nhật được yêu thích" })
                    }
                } catch (e: Exception) {
                    toast("Không cập nhật được yêu thích. Kiểm tra kết nối hoặc đăng nhập.")
                } finally {
                    favoriteButton.isEnabled = true
                }
            }
        }
        val viewLocation = findViewById<Button>(R.id.btnViewLocation)
        val validCoordinates = r.latitude?.let { lat ->
            r.longitude?.let { lng ->
                lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
            }
        } == true
        viewLocation.isEnabled = validCoordinates
        viewLocation.text = if (validCoordinates) "Xem vị trí trên bản đồ" else "Phòng chưa có vị trí bản đồ"
        viewLocation.setOnClickListener {
            if (validCoordinates) {
                startActivity(Intent(this, MapActivity::class.java).putExtra("room_id", r.id))
            }
        }
        findViewById<Button>(R.id.requestButton).setOnClickListener { requestDialog(r.id) }
    }
    private fun requestDialog(roomId: Long) {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        val phone = EditText(this).apply {
            hint = "Số điện thoại liên hệ *"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val viewingTime = EditText(this).apply { hint = "Thời gian muốn xem (ví dụ: 18:00 30/10)" }
        val input = EditText(this).apply { hint = "Lời nhắn cho chủ trọ (không bắt buộc)"; minLines = 2 }
        content.addView(phone)
        content.addView(viewingTime)
        content.addView(input)
        lifecycleScope.launch {
            try {
                phone.setText(ApiClient.service(this@RoomDetailActivity).me().data?.phone.orEmpty())
            } catch (_: Exception) { /* Vẫn cho phép nhập thủ công */ }
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Gửi yêu cầu xem phòng")
            .setMessage("Chủ trọ sẽ nhận yêu cầu và phản hồi trong mục Yêu cầu.")
            .setView(content)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Gửi", null)
            .create()
        dialog.setOnShowListener {
            val sendButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            sendButton.setOnClickListener {
                val contactPhone = phone.text.toString().trim()
                if (contactPhone.isBlank()) {
                    phone.error = "Vui lòng nhập số điện thoại liên hệ"
                    phone.requestFocus()
                    return@setOnClickListener
                }
                sendButton.isEnabled = false
                lifecycleScope.launch {
                    try {
                        val res = ApiClient.service(this@RoomDetailActivity).sendRequest(
                            roomId,
                            RentalRequestCreate(
                                input.text.toString().trim().ifBlank { null },
                                viewingTime.text.toString().trim().ifBlank { null },
                                contactPhone
                            )
                        )
                        toast(res.message.ifBlank { if (res.success) "Đã gửi yêu cầu" else "Gửi yêu cầu thất bại" })
                        if (res.success) dialog.dismiss()
                    } catch (e: Exception) {
                        toast("Không gửi được yêu cầu. Vui lòng kiểm tra kết nối hoặc quyền tài khoản.")
                    } finally {
                        sendButton.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }
}
