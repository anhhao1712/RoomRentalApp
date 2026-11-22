package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.RentalRequestCreate
import vn.edu.thuetro.data.Room
import vn.edu.thuetro.data.SessionManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RoomDetailActivity : AppCompatActivity() {
    private val id by lazy { intent.getLongExtra("room_id", -1L) }
    private var room: Room? = null
    private var currentImages: List<String> = emptyList()
    private lateinit var imagePager: ViewPager2
    private lateinit var imageCounter: TextView
    private lateinit var dotIndicators: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_detail)
        applySafeArea()
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        imagePager = findViewById(R.id.roomImagePager)
        imageCounter = findViewById(R.id.imageCounter)
        dotIndicators = findViewById(R.id.dotIndicators)
        imagePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val count = currentImages.size
                if (count > 0) {
                    imageCounter.text = "${position + 1} / $count"
                    dotIndicators.renderPhotoDots(count, position)
                }
            }
        })

        load()
    }

    override fun onResume() {
        super.onResume()
        if (room != null) load()
    }

    private fun load() = lifecycleScope.launch {
        try {
            val response = ApiClient.service(this@RoomDetailActivity).room(id)
            val data = response.data
            if (data == null) {
                toast(response.message.ifBlank { "Không tìm thấy phòng." })
                return@launch
            }
            room = data
            bind(data)
        } catch (_: Exception) {
            toast("Không tải được chi tiết phòng.")
        }
    }

    private fun bind(r: Room) {
        val session = SessionManager(this)
        val isOwner = r.landlordId == session.userId
        val canRequestViewing = session.role in listOf("TENANT", "ADMIN") && !isOwner

        currentImages = r.images
        if (currentImages.isEmpty()) {
            imagePager.adapter = RoomPhotoPagerAdapter(listOf(""), fullScreen = false) {}
            imageCounter.visibility = View.GONE
            dotIndicators.visibility = View.GONE
        } else {
            val savedPos = imagePager.currentItem.coerceIn(0, currentImages.lastIndex)
            imagePager.adapter = RoomPhotoPagerAdapter(currentImages, fullScreen = false) { pos ->
                openPhotoViewer(currentImages, pos, r.title)
            }
            imagePager.setCurrentItem(savedPos, false)
            imageCounter.visibility = View.VISIBLE
            imageCounter.text = "${savedPos + 1} / ${currentImages.size}"
            dotIndicators.renderPhotoDots(currentImages.size, savedPos)
        }
        findViewById<TextView>(R.id.status).setRoomStatus(r.status)
        findViewById<TextView>(R.id.title).text = r.title
        findViewById<TextView>(R.id.price).text = "${r.price.money()}/tháng"
        findViewById<TextView>(R.id.address).text = listOfNotNull(r.address, r.ward, r.district, r.city).joinToString(", ")

        findViewById<TextView>(R.id.metricArea).text = "${r.area} m²"
        findViewById<TextView>(R.id.metricTenants).text = "${r.maxTenants} người"
        findViewById<TextView>(R.id.metricDeposit).text = if (r.deposit > 0) r.deposit.compactMoney() else "Không cọc"

        findViewById<TextView>(R.id.description).text = r.description?.ifBlank { null } ?: "Chủ trọ chưa bổ sung mô tả."
        findViewById<TextView>(R.id.amenities).text = "Tiện ích: " + r.amenities.joinToString { it.name }.ifBlank { "Chưa cập nhật" }
        findViewById<TextView>(R.id.fees).text =
            "Điện ${r.electricityPrice.money()}/kWh • Nước ${r.waterPrice.money()}/m³ • Internet ${r.internetFee.money()}/tháng"

        findViewById<ImageView>(R.id.landlordAvatar).loadAvatar(r.landlordAvatarUrl)
        findViewById<TextView>(R.id.owner).text = r.landlordName
        findViewById<TextView>(R.id.landlordRoleBadge).setRoleBadge(r.landlordRole ?: "LANDLORD")
        findViewById<TextView>(R.id.landlordPhone).text = r.landlordPhone?.let { "SĐT: $it" } ?: "Chưa cập nhật số điện thoại"
        findViewById<View>(R.id.landlordCard).setOnClickListener { openUserProfile(r.landlordId) }

        val favoriteButton = findViewById<ImageButton>(R.id.favoriteButton)
        val chatButton = findViewById<Button>(R.id.chatButton)
        val requestButton = findViewById<Button>(R.id.requestButton)
        val editRoomButton = findViewById<Button>(R.id.editRoomButton)
        val roleHint = findViewById<TextView>(R.id.roleHint)

        if (isOwner) {
            favoriteButton.visibility = View.GONE
            chatButton.visibility = View.GONE
            requestButton.visibility = View.GONE
            roleHint.visibility = View.GONE
            editRoomButton.visibility = View.VISIBLE
            editRoomButton.setOnClickListener {
                startActivity(Intent(this, RoomFormActivity::class.java).putExtra("room_id", r.id))
            }
        } else {
            editRoomButton.visibility = View.GONE
            favoriteButton.visibility = View.VISIBLE
            chatButton.visibility = View.VISIBLE
            requestButton.visibility = if (canRequestViewing) View.VISIBLE else View.GONE
            roleHint.visibility = if (canRequestViewing) View.GONE else View.VISIBLE

            favoriteButton.setImageResource(if (r.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
            favoriteButton.setOnClickListener { toggleFavorite(favoriteButton, r) }
            chatButton.setOnClickListener {
                openChatWith(
                    otherUserId = r.landlordId,
                    otherName = r.landlordName,
                    otherAvatar = r.landlordAvatarUrl,
                    otherRole = r.landlordRole ?: "LANDLORD",
                    otherPhone = r.landlordPhone,
                    roomId = r.id,
                    roomTitle = r.title,
                    roomPrice = r.price,
                    roomImage = r.images.firstOrNull()
                )
            }
            requestButton.setOnClickListener { showViewingRequestSheet(r.id) }
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
    }

    private fun toggleFavorite(button: ImageButton, currentRoom: Room) {
        button.isEnabled = false
        lifecycleScope.launch {
            try {
                val result = ApiClient.service(this@RoomDetailActivity).toggleFavorite(currentRoom.id)
                if (result.success) {
                    val newFavorite = result.data ?: !(room?.isFavorite ?: currentRoom.isFavorite)
                    button.setImageResource(if (newFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
                    room = room?.copy(isFavorite = newFavorite)
                    toast(if (newFavorite) "Đã lưu phòng" else "Đã bỏ lưu phòng")
                } else {
                    toast(result.message.ifBlank { "Không cập nhật được yêu thích" })
                }
            } catch (e: Exception) {
                toast(e.apiMessage() ?: "Không cập nhật được yêu thích. Kiểm tra kết nối hoặc đăng nhập.")
            } finally {
                button.isEnabled = true
            }
        }
    }

    private fun showViewingRequestSheet(roomId: Long) {
        val dialog = BottomSheetDialog(this)
        val sheet = layoutInflater.inflate(R.layout.dialog_viewing_request, null)
        dialog.setContentView(sheet)

        val layoutPhone = sheet.findViewById<TextInputLayout>(R.id.layoutPhone)
        val inputPhone = sheet.findViewById<TextInputEditText>(R.id.inputPhone)
        val layoutViewingTime = sheet.findViewById<TextInputLayout>(R.id.layoutViewingTime)
        val inputViewingTime = sheet.findViewById<TextInputEditText>(R.id.inputViewingTime)
        val submitButton = sheet.findViewById<MaterialButton>(R.id.btnSubmitRequest)

        var selectedCal = Calendar.getInstance()
        var selectedHour = defaultViewingHour()
        var selectedMinute = 0

        lifecycleScope.launch {
            try {
                inputPhone.setText(ApiClient.service(this@RoomDetailActivity).me().data?.phone.orEmpty())
            } catch (_: Exception) {
                // Vẫn cho phép người dùng tự nhập số điện thoại
            }
        }

        val openPicker = View.OnClickListener {
            showDateTimePickerSheet(selectedCal, selectedHour, selectedMinute) { cal, hour, minute, formatted ->
                selectedCal = cal
                selectedHour = hour
                selectedMinute = minute
                inputViewingTime.setText(formatted)
                layoutViewingTime.error = null
            }
        }
        inputViewingTime.setOnClickListener(openPicker)
        layoutViewingTime.setOnClickListener(openPicker)

        submitButton.setOnClickListener {
            val rawPhone = inputPhone.text?.toString()?.trim().orEmpty()
            val normalizedPhone = ContactRules.normalizePhone(rawPhone)
            if (normalizedPhone == null) {
                layoutPhone.error = "Nhập số điện thoại hợp lệ (10 số, bắt đầu bằng 0)"
                inputPhone.requestFocus()
                return@setOnClickListener
            }
            layoutPhone.error = null

            val viewingTime = inputViewingTime.text?.toString()?.trim().orEmpty()
            if (viewingTime.isBlank()) {
                layoutViewingTime.error = "Vui lòng chọn ngày, giờ, phút xem phòng"
                return@setOnClickListener
            }
            layoutViewingTime.error = null

            submitButton.isEnabled = false
            submitButton.text = "Đang gửi..."
            lifecycleScope.launch {
                try {
                    val res = ApiClient.service(this@RoomDetailActivity).sendRequest(
                        roomId,
                        RentalRequestCreate(
                            message = null,
                            viewingTime = viewingTime,
                            phone = normalizedPhone
                        )
                    )
                    toast(res.message.ifBlank { if (res.success) "Đã gửi yêu cầu xem phòng" else "Gửi yêu cầu thất bại" })
                    if (res.success) dialog.dismiss()
                } catch (e: Exception) {
                    toast(e.apiMessage() ?: "Không gửi được yêu cầu. Vui lòng thử lại.")
                } finally {
                    submitButton.isEnabled = true
                    submitButton.text = "Xác nhận xem phòng"
                }
            }
        }

        dialog.show()
    }

    private fun defaultViewingHour(): Int {
        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            nowHour < 8 -> 9
            nowHour in 8..19 -> nowHour + 1
            else -> 9
        }
    }

    private fun showDateTimePickerSheet(
        initialCal: Calendar,
        initialHour: Int,
        initialMinute: Int,
        onPicked: (Calendar, Int, Int, String) -> Unit
    ) {
        val pickerDialog = BottomSheetDialog(this)
        val sheet = layoutInflater.inflate(R.layout.dialog_date_time_picker, null)
        pickerDialog.setContentView(sheet)

        val dateLabel = sheet.findViewById<TextView>(R.id.selectedDateLabel)
        val preview = sheet.findViewById<TextView>(R.id.selectedPreview)
        val hourPicker = sheet.findViewById<NumberPicker>(R.id.hourPicker)
        val minutePicker = sheet.findViewById<NumberPicker>(R.id.minutePicker)

        val chosenCal = (initialCal.clone() as Calendar)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dayLabelFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN"))

        hourPicker.minValue = 7
        hourPicker.maxValue = 21
        hourPicker.wrapSelectorWheel = false
        hourPicker.setFormatter { String.format(Locale.US, "%02d giờ", it) }

        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.wrapSelectorWheel = true
        minutePicker.setFormatter { String.format(Locale.US, "%02d phút", it) }

        fun syncMinuteBounds(hour: Int) {
            if (hour >= 21) {
                minutePicker.value = 0
                minutePicker.maxValue = 0
            } else {
                minutePicker.maxValue = 59
            }
        }

        fun updateLabels() {
            val h = hourPicker.value
            val m = if (h >= 21) 0 else minutePicker.value
            dateLabel.text = "Ngày đã chọn: ${dayLabelFormat.format(chosenCal.time)}"
            preview.text = String.format(Locale.US, "%02d:%02d - %s", h, m, dateFormat.format(chosenCal.time))
        }

        hourPicker.value = initialHour.coerceIn(7, 21)
        syncMinuteBounds(hourPicker.value)
        minutePicker.value = if (hourPicker.value >= 21) 0 else initialMinute.coerceIn(0, 59)

        hourPicker.setOnValueChangedListener { _, _, newVal ->
            syncMinuteBounds(newVal)
            updateLabels()
        }
        minutePicker.setOnValueChangedListener { _, _, _ -> updateLabels() }

        fun setOffsetDays(offset: Int) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, offset)
            chosenCal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            updateLabels()
        }

        sheet.findViewById<Chip>(R.id.chipToday).setOnClickListener { setOffsetDays(0) }
        sheet.findViewById<Chip>(R.id.chipTomorrow).setOnClickListener { setOffsetDays(1) }
        sheet.findViewById<Chip>(R.id.chipDayAfter).setOnClickListener { setOffsetDays(2) }
        sheet.findViewById<Chip>(R.id.chipPickDate).setOnClickListener {
            val constraints = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())
                .build()
            val dp = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Chọn ngày xem phòng")
                .setCalendarConstraints(constraints)
                .setSelection(chosenCal.timeInMillis)
                .build()
            dp.addOnPositiveButtonClickListener { utcMillis ->
                val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
                chosenCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH))
                updateLabels()
            }
            dp.show(supportFragmentManager, "viewing_date_picker")
        }

        fun setPresetTime(h: Int, m: Int) {
            hourPicker.value = h.coerceIn(7, 21)
            syncMinuteBounds(hourPicker.value)
            minutePicker.value = if (hourPicker.value >= 21) 0 else m.coerceIn(0, 59)
            updateLabels()
        }
        sheet.findViewById<Chip>(R.id.timePreset09).setOnClickListener { setPresetTime(9, 0) }
        sheet.findViewById<Chip>(R.id.timePreset14).setOnClickListener { setPresetTime(14, 0) }
        sheet.findViewById<Chip>(R.id.timePreset1730).setOnClickListener { setPresetTime(17, 30) }
        sheet.findViewById<Chip>(R.id.timePreset19).setOnClickListener { setPresetTime(19, 0) }

        sheet.findViewById<View>(R.id.btnCancelDateTime).setOnClickListener { pickerDialog.dismiss() }
        sheet.findViewById<View>(R.id.btnConfirmDateTime).setOnClickListener {
            val h = hourPicker.value
            val m = if (h >= 21) 0 else minutePicker.value
            val formatted = String.format(Locale.US, "%02d:%02d - %s", h, m, dateFormat.format(chosenCal.time))
            onPicked(chosenCal, h, m, formatted)
            pickerDialog.dismiss()
        }

        updateLabels()
        pickerDialog.show()
    }
}