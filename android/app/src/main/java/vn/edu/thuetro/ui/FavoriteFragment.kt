package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient

class FavoriteFragment : Fragment(R.layout.fragment_simple_list) {
    private lateinit var adapter: RoomAdapter
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as MainActivity).title("Phòng đã lưu")
        adapter = RoomAdapter({ startActivity(Intent(requireContext(), RoomDetailActivity::class.java).putExtra("room_id", it.id)) }, { toggle(it.id) })
        view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler).apply { layoutManager = LinearLayoutManager(requireContext()); adapter = this@FavoriteFragment.adapter }; load()
    }
    override fun onResume() { super.onResume(); if(view!=null)load() }
    private fun load() = lifecycleScope.launch {
        val root = view ?: return@launch
        root.findViewById<View>(R.id.progress).visibility = View.VISIBLE
        try {
            val data = ApiClient.service(requireContext()).favorites().data.orEmpty()
            adapter.submit(data)
            root.findViewById<android.widget.TextView>(R.id.emptyText).apply { text = "Bạn chưa lưu phòng nào"; visibility = if (data.isEmpty()) View.VISIBLE else View.GONE }
        } catch (_: Exception) {
            root.findViewById<android.widget.TextView>(R.id.emptyText).apply { text = "Không tải được phòng đã lưu"; visibility = View.VISIBLE }
            requireContext().toast("Bạn cần đăng nhập để xem mục này.")
        } finally { root.findViewById<View>(R.id.progress).visibility = View.GONE }
    }
    private fun toggle(id:Long)=lifecycleScope.launch{try{requireContext().toast(ApiClient.service(requireContext()).toggleFavorite(id).message);load()}catch(_:Exception){}}
}
