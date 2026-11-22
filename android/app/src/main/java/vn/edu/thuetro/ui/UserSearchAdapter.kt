package vn.edu.thuetro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import vn.edu.thuetro.data.PublicUser
import vn.edu.thuetro.databinding.ItemUserCardBinding

class UserSearchAdapter(
    private val currentUserId: Long,
    private val onUserClick: (PublicUser) -> Unit,
    private val onChatClick: (PublicUser) -> Unit
) : RecyclerView.Adapter<UserSearchAdapter.Holder>() {

    private var items = emptyList<PublicUser>()

    fun submit(data: List<PublicUser>) {
        items = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemUserCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position], position)

    inner class Holder(private val binding: ItemUserCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: PublicUser, position: Int) = with(binding) {
            sectionHeader.visibility = if (position == 0) View.VISIBLE else View.GONE
            userAvatar.loadAvatar(user.avatarUrl)
            userName.text = user.fullName
            userRole.setRoleBadge(user.role)
            userSub.text = buildString {
                append("@${user.username}")
                if (!user.phone.isNullOrBlank()) append(" • ${user.phone}")
            }
            userStats.text = when {
                user.availableRoomCount > 0 -> "${user.availableRoomCount} phòng đang còn trống"
                user.roomCount > 0 -> "${user.roomCount} tin phòng đã đăng"
                else -> "Xem trang cá nhân"
            }
            btnChatUser.visibility = if (user.id != currentUserId) View.VISIBLE else View.GONE
            root.setOnClickListener { onUserClick(user) }
            btnChatUser.setOnClickListener { onChatClick(user) }
        }
    }
}