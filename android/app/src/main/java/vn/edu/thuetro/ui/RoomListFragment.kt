package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.AreaCatalog

class RoomListFragment : Fragment(R.layout.fragment_room_list) {
    private lateinit var adapter: RoomAdapter
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).title("Tìm phòng")
        adapter = RoomAdapter({ room -> startActivity(Intent(requireContext(), RoomDetailActivity::class.java).putExtra("room_id", room.id)) }, { room -> toggle(room.id) })
        view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.roomRecycler).apply { layoutManager = LinearLayoutManager(requireContext()); adapter = this@RoomListFragment.adapter }
        view.findViewById<EditText>(R.id.inputSearch).setOnEditorActionListener { _, _, _ -> load(); true }
        view.findViewById<ImageButton>(R.id.buttonFilter).setOnClickListener { filterDialog() }
        view.findViewById<ImageButton>(R.id.buttonMap).setOnClickListener { startActivity(Intent(requireContext(), MapActivity::class.java)) }
        load()
    }
    override fun onResume() { super.onResume(); if(view != null) load() }
    private fun load(district: String? = null, min: Long? = null, max: Long? = null, sort: String = "newest") {
        val root = view ?: return
        root.findViewById<ProgressBar>(R.id.progress).visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val q = root.findViewById<EditText>(R.id.inputSearch).text?.toString()
                val result = ApiClient.service(requireContext()).rooms(q, district, min, max, sort)
                val rooms = result.data.orEmpty(); adapter.submit(rooms)
                root.findViewById<TextView>(R.id.roomCount).text = "${rooms.size} phòng đang còn trống"
                root.findViewById<TextView>(R.id.emptyText).visibility = if(rooms.isEmpty()) View.VISIBLE else View.GONE
            } catch (_: Exception) { requireContext().toast("Không tải được danh sách phòng.") }
            root.findViewById<ProgressBar>(R.id.progress).visibility = View.GONE
        }
    }
    private fun toggle(id: Long) = lifecycleScope.launch { try { val r = ApiClient.service(requireContext()).toggleFavorite(id); requireContext().toast(r.message); load() } catch (_:Exception) { requireContext().toast("Bạn cần đăng nhập để lưu phòng.") } }
    private fun filterDialog() {
        val content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(48,20,48,0) }
        val minOptions = listOf("Không giới hạn" to null, "1 triệu" to 1_000_000L, "2 triệu" to 2_000_000L, "3 triệu" to 3_000_000L, "5 triệu" to 5_000_000L, "7 triệu" to 7_000_000L, "10 triệu" to 10_000_000L)
        val maxOptions = listOf("Không giới hạn" to null, "2 triệu" to 2_000_000L, "3 triệu" to 3_000_000L, "5 triệu" to 5_000_000L, "7 triệu" to 7_000_000L, "10 triệu" to 10_000_000L, "15 triệu" to 15_000_000L)
        val areaOptions = listOf("Tất cả khu vực" to null) + AreaCatalog.options.map { it.label to it.district }
        val area = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, areaOptions.map { it.first }) }
        val min = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, minOptions.map { "Giá từ: ${it.first}" }) }
        val max = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, maxOptions.map { "Giá đến: ${it.first}" }) }
        val sorts = Spinner(requireContext()).apply { adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("Mới đăng", "Giá thấp đến cao", "Giá cao đến thấp", "Diện tích lớn")) }
        content.addView(area);content.addView(min);content.addView(max);content.addView(sorts)
        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Lọc phòng").setView(content).setNegativeButton("Hủy",null).setPositiveButton("Áp dụng") { _,_ ->
            val selectedMin = minOptions[min.selectedItemPosition].second
            val selectedMax = maxOptions[max.selectedItemPosition].second
            if (selectedMin != null && selectedMax != null && selectedMin > selectedMax) { requireContext().toast("Giá tối thiểu không thể lớn hơn giá tối đa.") }
            else load(areaOptions[area.selectedItemPosition].second, selectedMin, selectedMax, listOf("newest","price_asc","price_desc","area")[sorts.selectedItemPosition])
        }.show()
    }
}
