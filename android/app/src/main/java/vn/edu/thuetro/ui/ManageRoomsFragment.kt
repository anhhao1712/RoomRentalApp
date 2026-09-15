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
import vn.edu.thuetro.data.SessionManager

class ManageRoomsFragment : Fragment(R.layout.fragment_manage) {
    private lateinit var adapter: RoomAdapter
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as MainActivity).title("Quản lý tin đăng")
        val role=SessionManager(requireContext()).role
        if(role !in listOf("LANDLORD","ADMIN")){view.findViewById<android.widget.TextView>(R.id.emptyText).apply{text="Chức năng dành cho chủ trọ";visibility=View.VISIBLE};view.findViewById<View>(R.id.addRoom).visibility=View.GONE;return}
        adapter=RoomAdapter({ room->startActivity(Intent(requireContext(),RoomFormActivity::class.java).putExtra("room_id",room.id))},{}, false)
        view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler).apply{layoutManager=LinearLayoutManager(requireContext());adapter=this@ManageRoomsFragment.adapter}
        view.findViewById<View>(R.id.addRoom).setOnClickListener{startActivity(Intent(requireContext(),RoomFormActivity::class.java))};load()
    }
    override fun onResume(){super.onResume();if(view!=null&&::adapter.isInitialized)load()}
    private fun load()=lifecycleScope.launch{val root=view?:return@launch;try{
        val api = ApiClient.service(requireContext())
        val rooms=api.ownerRooms().data.orEmpty()
        adapter.submit(rooms)
        root.findViewById<android.widget.TextView>(R.id.emptyText).visibility=if(rooms.isEmpty())View.VISIBLE else View.GONE
        val dashboard = api.dashboard().data
        root.findViewById<android.widget.TextView>(R.id.manageSummary).text = dashboard?.let { "${it.totalRooms} tin • ${it.availableRooms} còn trống • ${it.pendingRequests} yêu cầu chờ" } ?: ""
    }catch(_:Exception){requireContext().toast("Không tải được tin đăng.")};root.findViewById<View>(R.id.progress).visibility=View.GONE}
}
