package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.RangeSlider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.Amenity
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.AreaCatalog
import vn.edu.thuetro.data.SessionManager
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

class RoomListFragment : Fragment(R.layout.fragment_room_list) {
    private lateinit var roomAdapter: RoomAdapter
    private lateinit var userAdapter: UserSearchAdapter

    private var activeDistrict: String? = null
    private var activeMinPrice: Long? = null
    private var activeMaxPrice: Long? = null
    private var activeSort: String = "newest"
    private var activeAmenityIds: Set<Long> = emptySet()
    private var availableAmenities: List<Amenity> = listOf(
        Amenity(1L, "Wi-Fi", "wifi"),
        Amenity(2L, "Máy lạnh", "ac"),
        Amenity(3L, "Gác lửng", "stairs"),
        Amenity(4L, "Bãi xe", "parking"),
        Amenity(5L, "Camera an ninh", "camera"),
        Amenity(6L, "Máy giặt", "washer")
    )
    private var searchJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? MainActivity)?.title("Tìm phòng")
        view.enableVietnameseIme()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { ApiClient.service(requireContext()).amenities().data }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { availableAmenities = it }
        }

        val myId = SessionManager(requireContext()).userId
        userAdapter = UserSearchAdapter(
            currentUserId = myId,
            onUserClick = { user -> requireContext().openUserProfile(user.id) },
            onChatClick = { user ->
                requireContext().openChatWith(
                    otherUserId = user.id,
                    otherName = user.fullName,
                    otherAvatar = user.avatarUrl,
                    otherRole = user.role,
                    otherPhone = user.phone
                )
            }
        )
        roomAdapter = RoomAdapter(
            onClick = { room -> startActivity(Intent(requireContext(), RoomDetailActivity::class.java).putExtra("room_id", room.id)) },
            onFavorite = { room -> toggle(room.id) }
        )

        view.findViewById<RecyclerView>(R.id.roomRecycler).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ConcatAdapter(userAdapter, roomAdapter)
        }

        val inputSearch = view.findViewById<EditText>(R.id.inputSearch)
        val clearBtn = view.findViewById<ImageButton>(R.id.buttonClearSearch)

        inputSearch.setOnEditorActionListener { _, _, _ ->
            searchJob?.cancel()
            load()
            true
        }
        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                clearBtn.visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE
                if (s != null && BaseInputConnection.getComposingSpanStart(s) != -1) return
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(350L)
                    load()
                }
            }
        })
        clearBtn.setOnClickListener {
            inputSearch.setText("")
            searchJob?.cancel()
            load()
        }

        view.findViewById<ImageButton>(R.id.buttonFilter).setOnClickListener { showFilterSheet() }
        view.findViewById<ImageButton>(R.id.buttonMap).setOnClickListener { startActivity(Intent(requireContext(), MapActivity::class.java)) }
        view.findViewById<View>(R.id.buttonClearFilter).setOnClickListener {
            activeDistrict = null
            activeMinPrice = null
            activeMaxPrice = null
            activeSort = "newest"
            activeAmenityIds = emptySet()
            renderActiveFilters()
            load()
        }
        renderActiveFilters()
        load()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) load()
    }

    private fun renderActiveFilters() {
        val root = view ?: return
        val hasFilter = activeDistrict != null ||
            activeMinPrice != null ||
            activeMaxPrice != null ||
            activeSort != "newest" ||
            activeAmenityIds.isNotEmpty()
        root.findViewById<View>(R.id.filterDot).visibility = if (hasFilter) View.VISIBLE else View.GONE
        val row = root.findViewById<View>(R.id.activeFilterRow)
        if (!hasFilter) {
            row.visibility = View.GONE
            return
        }
        val parts = mutableListOf<String>()
        if (activeMinPrice != null || activeMaxPrice != null) {
            val minStr = activeMinPrice?.compactMoney() ?: "0"
            val maxStr = activeMaxPrice?.compactMoney() ?: "trở lên"
            parts.add("Giá: $minStr – $maxStr")
        }
        activeDistrict?.let { parts.add(it) }
        if (activeAmenityIds.isNotEmpty()) {
            val names = availableAmenities.filter { it.id in activeAmenityIds }.map { it.name }
            parts.add(if (names.isNotEmpty()) names.joinToString(", ") else "${activeAmenityIds.size} tiện ích")
        }
        when (activeSort) {
            "price_asc" -> parts.add("Giá tăng dần")
            "price_desc" -> parts.add("Giá giảm dần")
            "area" -> parts.add("Diện tích lớn")
        }
        root.findViewById<TextView>(R.id.activeFilterText).text = parts.joinToString(" • ")
        row.visibility = View.VISIBLE
    }

    private fun load() {
        val root = view ?: return
        root.findViewById<ProgressBar>(R.id.progress).visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val q = root.findViewById<EditText>(R.id.inputSearch).text?.toString()?.trim().orEmpty()
                val api = ApiClient.service(requireContext())
                val (users, rooms) = coroutineScope {
                    val usersJob = async {
                        if (q.isNotBlank()) runCatching { api.searchUsers(q).data.orEmpty() }.getOrDefault(emptyList())
                        else emptyList()
                    }
                    val roomsJob = async {
                        api.rooms(
                            q = q.ifBlank { null },
                            district = activeDistrict,
                            minPrice = activeMinPrice,
                            maxPrice = activeMaxPrice,
                            sort = activeSort,
                            amenities = activeAmenityIds.sorted().joinToString(",").ifBlank { null }
                        ).data.orEmpty()
                    }
                    usersJob.await() to roomsJob.await()
                }
                userAdapter.submit(users)
                roomAdapter.submit(rooms)
                root.findViewById<TextView>(R.id.roomCount).text = buildString {
                    append("${rooms.size} phòng đang còn trống")
                    if (users.isNotEmpty()) append(" • ${users.size} chủ trọ phù hợp")
                }
                root.findViewById<TextView>(R.id.emptyText).visibility =
                    if (rooms.isEmpty() && users.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (isAdded) requireContext().toast("Không tải được danh sách phòng.")
            } finally {
                view?.findViewById<ProgressBar>(R.id.progress)?.visibility = View.GONE
            }
        }
    }

    private fun toggle(id: Long) = viewLifecycleOwner.lifecycleScope.launch {
        try {
            val r = ApiClient.service(requireContext()).toggleFavorite(id)
            requireContext().toast(r.message)
            load()
        } catch (_: Exception) {
            requireContext().toast("Bạn cần đăng nhập để lưu phòng.")
        }
    }

    private fun showFilterSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = layoutInflater.inflate(R.layout.dialog_room_filter, null)
        dialog.setContentView(sheet)

        val slider = sheet.findViewById<RangeSlider>(R.id.priceRangeSlider)
        val minEdit = sheet.findViewById<EditText>(R.id.inputMinPrice)
        val maxEdit = sheet.findViewById<EditText>(R.id.inputMaxPrice)
        val areaDropdown = sheet.findViewById<AutoCompleteTextView>(R.id.inputArea)
        val amenityGroup = sheet.findViewById<ChipGroup>(R.id.amenityChipGroup)
        val sortGroup = sheet.findViewById<ChipGroup>(R.id.sortChipGroup)

        availableAmenities.forEach { amenity ->
            val chip = Chip(requireContext()).apply {
                text = amenity.name
                tag = amenity.id
                isCheckable = true
                isCheckedIconVisible = true
                isChecked = amenity.id in activeAmenityIds
            }
            amenityGroup.addView(chip)
        }

        val areaOptions = listOf("Tất cả khu vực" to null) + AreaCatalog.options.map { it.label to it.district }
        var selectedDistrict = activeDistrict
        areaDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, areaOptions.map { it.first }))
        val initialAreaLabel = areaOptions.firstOrNull { it.second == activeDistrict }?.first ?: areaOptions.first().first
        areaDropdown.setText(initialAreaLabel, false)
        areaDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedDistrict = areaOptions[position].second
        }

        when (activeSort) {
            "price_asc" -> sortGroup.check(R.id.chipSortPriceAsc)
            "price_desc" -> sortGroup.check(R.id.chipSortPriceDesc)
            "area" -> sortGroup.check(R.id.chipSortArea)
            else -> sortGroup.check(R.id.chipSortNewest)
        }

        var isSyncing = false

        fun vndToSliderStep(vnd: Long?): Float? {
            if (vnd == null) return null
            val steps = (vnd / 500_000.0).roundToInt() * 0.5f
            return steps.coerceIn(0f, 100f)
        }

        fun syncSliderFromInputs() {
            if (isSyncing) return
            isSyncing = true
            val minVnd = parseVndDigits(minEdit.text)
            val maxVnd = parseVndDigits(maxEdit.text)
            val low = (vndToSliderStep(minVnd) ?: 0f).coerceIn(0f, 100f)
            val high = (vndToSliderStep(maxVnd) ?: 100f).coerceIn(low, 100f)
            slider.values = listOf(low, high)
            isSyncing = false
        }

        fun applyPriceValues(minVnd: Long?, maxVnd: Long?) {
            isSyncing = true
            minEdit.setText(minVnd?.let(::formatVndDigits).orEmpty())
            maxEdit.setText(maxVnd?.let(::formatVndDigits).orEmpty())
            val low = (vndToSliderStep(minVnd) ?: 0f).coerceIn(0f, 100f)
            val high = (vndToSliderStep(maxVnd) ?: 100f).coerceIn(low, 100f)
            slider.values = listOf(low, high)
            isSyncing = false
        }

        applyPriceValues(activeMinPrice, activeMaxPrice)

        slider.addOnChangeListener { rangeSlider, _, fromUser ->
            if (!fromUser || isSyncing) return@addOnChangeListener
            isSyncing = true
            val low = rangeSlider.values[0]
            val high = rangeSlider.values[1]
            minEdit.setText(if (low <= 0f) "" else formatVndDigits((low * 1_000_000.0).toLong()))
            maxEdit.setText(if (high >= 100f) "" else formatVndDigits((high * 1_000_000.0).toLong()))
            isSyncing = false
        }

        val inputWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = syncSliderFromInputs()
        }
        minEdit.addTextChangedListener(inputWatcher)
        maxEdit.addTextChangedListener(inputWatcher)

        val formatOnBlur = View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && v is EditText && !isSyncing) {
                isSyncing = true
                val parsed = parseVndDigits(v.text)
                v.setText(parsed?.let(::formatVndDigits).orEmpty())
                isSyncing = false
            }
        }
        minEdit.onFocusChangeListener = formatOnBlur
        maxEdit.onFocusChangeListener = formatOnBlur

        sheet.findViewById<Chip>(R.id.presetUnder3).setOnClickListener { applyPriceValues(null, 3_000_000L) }
        sheet.findViewById<Chip>(R.id.preset3to5).setOnClickListener { applyPriceValues(3_000_000L, 5_000_000L) }
        sheet.findViewById<Chip>(R.id.preset5to10).setOnClickListener { applyPriceValues(5_000_000L, 10_000_000L) }
        sheet.findViewById<Chip>(R.id.presetOver10).setOnClickListener { applyPriceValues(10_000_000L, null) }

        sheet.findViewById<View>(R.id.btnResetFilter).setOnClickListener {
            selectedDistrict = null
            areaDropdown.setText(areaOptions.first().first, false)
            for (i in 0 until amenityGroup.childCount) {
                (amenityGroup.getChildAt(i) as? Chip)?.isChecked = false
            }
            sortGroup.check(R.id.chipSortNewest)
            applyPriceValues(null, null)
        }

        sheet.findViewById<MaterialButton>(R.id.btnApplyFilter).setOnClickListener {
            val minVnd = parseVndDigits(minEdit.text)
            val maxVnd = parseVndDigits(maxEdit.text)
            if (minVnd != null && maxVnd != null && minVnd > maxVnd) {
                requireContext().toast("Giá tối thiểu không thể lớn hơn giá tối đa.")
                return@setOnClickListener
            }
            activeDistrict = selectedDistrict
            activeMinPrice = minVnd
            activeMaxPrice = maxVnd
            activeAmenityIds = (0 until amenityGroup.childCount)
                .mapNotNull { i -> (amenityGroup.getChildAt(i) as? Chip)?.takeIf { it.isChecked }?.tag as? Long }
                .toSet()
            activeSort = when (sortGroup.checkedChipId) {
                R.id.chipSortPriceAsc -> "price_asc"
                R.id.chipSortPriceDesc -> "price_desc"
                R.id.chipSortArea -> "area"
                else -> "newest"
            }
            dialog.dismiss()
            renderActiveFilters()
            load()
        }

        dialog.show()
    }

    private fun parseVndDigits(raw: CharSequence?): Long? {
        val digits = raw?.toString()?.replace(Regex("\\D"), "").orEmpty()
        return digits.toLongOrNull()
    }

    private fun formatVndDigits(amount: Long): String =
        NumberFormat.getIntegerInstance(Locale("vi", "VN")).format(amount)
}