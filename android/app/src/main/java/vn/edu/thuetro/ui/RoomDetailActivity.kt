package vn.edu.thuetro.ui

import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);setContentView(R.layout.activity_room_detail);load() }
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
        findViewById<Button>(R.id.favoriteButton).text=if(r.isFavorite)"Bỏ lưu phòng" else "Lưu phòng"
        findViewById<Button>(R.id.favoriteButton).setOnClickListener{lifecycleScope.launch{try{toast(ApiClient.service(this@RoomDetailActivity).toggleFavorite(r.id).message);load()}catch(_:Exception){toast("Bạn cần đăng nhập.")}}}
        findViewById<Button>(R.id.requestButton).setOnClickListener{requestDialog(r.id)}
    }
    private fun requestDialog(roomId:Long){
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 12, 48, 0) }
        val phone = EditText(this).apply { hint = "Số điện thoại liên hệ *"; inputType = android.text.InputType.TYPE_CLASS_PHONE }
        val viewingTime = EditText(this).apply { hint = "Thời gian muốn xem (vd: 18:00 30/10)" }
        val input = EditText(this).apply { hint = "Lời nhắn cho chủ trọ (không bắt buộc)"; minLines = 3 }
        content.addView(phone); content.addView(viewingTime); content.addView(input)
        lifecycleScope.launch { try { phone.setText(ApiClient.service(this@RoomDetailActivity).me().data?.phone.orEmpty()) } catch (_: Exception) {} }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Gửi yêu cầu xem phòng").setMessage("Chủ trọ sẽ nhận được yêu cầu và phản hồi trong mục Yêu cầu.").setView(content).setNegativeButton("Hủy",null).setPositiveButton("Gửi"){_,_->
            lifecycleScope.launch{try{
                val contactPhone = phone.text.toString().trim()
                if (contactPhone.isBlank()) { toast("Vui lòng nhập số điện thoại liên hệ."); return@launch }
                val res=ApiClient.service(this@RoomDetailActivity).sendRequest(roomId,RentalRequestCreate(input.text.toString().trim().ifBlank { null }, viewingTime.text.toString().trim().ifBlank { null }, contactPhone))
                toast(res.message)
            }catch(_:Exception){toast("Không gửi được yêu cầu. Bạn cần đăng nhập tài khoản người thuê.")}}
        }.show()
    }
}
