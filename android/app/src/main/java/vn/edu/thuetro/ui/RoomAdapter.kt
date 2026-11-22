package vn.edu.thuetro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import vn.edu.thuetro.R
import vn.edu.thuetro.data.Room
import vn.edu.thuetro.databinding.ItemRoomBinding

class RoomAdapter(
    private val onClick: (Room) -> Unit,
    private val onFavorite: (Room) -> Unit,
    private val showFavorite: Boolean = true,
    private val showStatus: Boolean = false
) : RecyclerView.Adapter<RoomAdapter.Holder>() {
    private var items = emptyList<Room>()
    fun submit(data: List<Room>) { items = data; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    inner class Holder(private val binding: ItemRoomBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(room: Room) = with(binding) {
            roomImage.load(room.images.firstOrNull()) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_gallery)
            }
            roomStatus.visibility = if (showStatus) View.VISIBLE else View.GONE
            if (showStatus) roomStatus.setRoomStatus(room.status)
            roomTitle.text = room.title
            roomPrice.text = "${room.price.money()}/tháng"
            roomMeta.text = "${room.area} m² • Tối đa ${room.maxTenants} người"
            roomAddress.text = listOfNotNull(room.address, room.district, room.city).joinToString(", ")
            favoriteButton.visibility = if (showFavorite) View.VISIBLE else View.GONE
            favoriteButton.setImageResource(if (room.isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
            root.setOnClickListener { onClick(room) }
            favoriteButton.setOnClickListener { if (showFavorite) onFavorite(room) }
        }
    }
}