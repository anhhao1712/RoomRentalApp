package vn.edu.thuetro.ui

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.*
import vn.edu.thuetro.databinding.ItemRequestBinding
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class RequestsFragment : Fragment(R.layout.fragment_requests) {
    private lateinit var adapter: RequestAdapter
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { (activity as MainActivity).title("Yêu cầu thuê");val role=SessionManager(requireContext()).role;adapter=RequestAdapter(role in listOf("LANDLORD","ADMIN")){request,status->showOwnerNote(request,status)};view.findViewById<RecyclerView>(R.id.recycler).apply{layoutManager=LinearLayoutManager(requireContext());adapter=this@RequestsFragment.adapter};load() }
    override fun onResume(){super.onResume();if(view!=null&&::adapter.isInitialized)load()}
    private fun load()=lifecycleScope.launch{val root=view?:return@launch;try{val items=ApiClient.service(requireContext()).requests().data.orEmpty();adapter.submit(items);root.findViewById<android.widget.TextView>(R.id.emptyText).visibility=if(items.isEmpty())View.VISIBLE else View.GONE}catch(_:Exception){requireContext().toast("Không tải được yêu cầu.")};root.findViewById<View>(R.id.progress).visibility=View.GONE}
    private fun showOwnerNote(request: RentalRequest, status: String) {
        val note = android.widget.EditText(requireContext()).apply { hint = if (status == "ACCEPTED") "Lời nhắn xác nhận (không bắt buộc)" else "Lý do từ chối (không bắt buộc)"; minLines = 2 }
        val title = if (status == "ACCEPTED") "Chấp nhận yêu cầu" else "Từ chối yêu cầu"
        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(title).setView(note).setNegativeButton("Hủy", null).setPositiveButton("Xác nhận") { _, _ -> update(request.id, status, note.text.toString().trim().ifBlank { null }) }.show()
    }
    private fun update(id:Long,status:String,note:String?)=lifecycleScope.launch{try{val result=ApiClient.service(requireContext()).updateRequest(id,RequestStatusUpdate(status,note));requireContext().toast(result.message);load()}catch(_:Exception){requireContext().toast("Không cập nhật được yêu cầu.")}}
}
class RequestAdapter(private val owner:Boolean,private val action:(RentalRequest,String)->Unit):RecyclerView.Adapter<RequestAdapter.Holder>(){
    private var items=emptyList<RentalRequest>();fun submit(data:List<RentalRequest>){items=data;notifyDataSetChanged()};override fun onCreateViewHolder(p:ViewGroup,v:Int)=Holder(ItemRequestBinding.inflate(LayoutInflater.from(p.context),p,false));override fun getItemCount()=items.size;override fun onBindViewHolder(h:Holder,p:Int)=h.bind(items[p])
    inner class Holder(private val b:ItemRequestBinding):RecyclerView.ViewHolder(b.root){fun bind(r:RentalRequest)=with(b){
        roomTitle.text=r.roomTitle
        tenant.text=if(owner)"Người thuê: ${r.tenantName}${r.tenantPhone?.let{" • $it"}?:""}" else "Yêu cầu của bạn"
        message.text=r.message?.ifBlank{"Không có lời nhắn"}?:"Không có lời nhắn"
        viewingTime.text = r.viewingTime?.let { "Thời gian muốn xem: $it" }
        viewingTime.visibility = if (r.viewingTime.isNullOrBlank()) View.GONE else View.VISIBLE
        ownerNote.text = r.ownerNote?.let { "Phản hồi của chủ trọ: $it" }
        ownerNote.visibility = if (r.ownerNote.isNullOrBlank()) View.GONE else View.VISIBLE
        status.text=when(r.status){"PENDING"->"Đang chờ phản hồi";"ACCEPTED"->"Đã chấp nhận";"REJECTED"->"Đã từ chối";"CANCELLED"->"Đã hủy";else->r.status}
        actions.visibility=if(owner&&r.status=="PENDING")View.VISIBLE else View.GONE
        accept.setOnClickListener{action(r,"ACCEPTED")};reject.setOnClickListener{action(r,"REJECTED")}
    }}
}
