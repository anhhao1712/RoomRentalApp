package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.view.*
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import coil.load
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.cancellation.CancellationException
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.*

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private var currentRole = "TENANT"
    private var acceptedLandlordTerms = false
    private var loadedUser: User? = null
    private var avatarUrl: String? = null
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { uploadAvatar(it) } }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as MainActivity).title("Tài khoản")
        view.findViewById<View>(R.id.avatarContainer).setOnClickListener { pickAvatar.launch("image/*") }
        view.findViewById<View>(R.id.saveProfile).setOnClickListener { save() }
        view.findViewById<View>(R.id.changeRole).setOnClickListener { changeRole() }
        view.findViewById<View>(R.id.logout).setOnClickListener {
            SessionManager(requireContext()).clear()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }
        load()
    }

    private fun load() = lifecycleScope.launch {
        try {
            val user = ApiClient.service(requireContext()).me().data ?: return@launch
            currentRole = user.role
            acceptedLandlordTerms = user.acceptedLandlordTerms
            loadedUser = user
            avatarUrl = user.avatarUrl
            val root = view ?: return@launch
            renderAvatar()
            root.findViewById<TextView>(R.id.userRole).text = when (user.role) { "ADMIN" -> "Quản trị viên"; "LANDLORD" -> "Chủ trọ"; else -> "Người thuê" }
            root.findViewById<TextView>(R.id.username).text = "@${user.username}"
            set(R.id.fullName, user.fullName); set(R.id.phone, user.phone); set(R.id.email, user.email)
            root.findViewById<View>(R.id.changeRole).apply {
                visibility = if (user.role == "ADMIN") View.GONE else View.VISIBLE
                if (user.role != "ADMIN") (this as com.google.android.material.button.MaterialButton).text = if (user.role == "LANDLORD") "Chuyển sang Người thuê" else "Chuyển sang Chủ trọ"
            }
        } catch (_: Exception) { requireContext().toast("Không tải được hồ sơ.") }
    }

    private fun save() = lifecycleScope.launch {
        try {
            val name = value(R.id.fullName)
            val result = ApiClient.service(requireContext()).updateProfile(UpdateProfileRequest(name, value(R.id.phone).ifBlank { null }, value(R.id.email).ifBlank { null }, avatarUrl))
            if (result.success) SessionManager(requireContext()).fullName = name
            requireContext().toast(result.message)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { requireContext().toast(e.apiMessage() ?: "Không lưu được hồ sơ.") }
    }

    private fun changeRole() {
        if (currentRole == "LANDLORD") {
            requireContext().confirm("Chuyển sang Người thuê", "Bạn vẫn giữ các tin đăng cũ, nhưng không thể quản lý chúng cho tới khi chuyển lại Chủ trọ.") { submitRole("TENANT", false) }
            return
        }
        if (value(R.id.phone).isBlank()) { requireContext().toast("Vui lòng nhập số điện thoại trước khi chuyển sang Chủ trọ."); return }
        lifecycleScope.launch {
            try {
                val saved = ApiClient.service(requireContext()).updateProfile(UpdateProfileRequest(value(R.id.fullName), value(R.id.phone), value(R.id.email).ifBlank { null }, avatarUrl))
                if (saved.success) {
                    if (acceptedLandlordTerms) submitRole("LANDLORD", false) else showTermsDialog()
                }
                else requireContext().toast(saved.message)
            } catch (_: Exception) { requireContext().toast("Không lưu được số điện thoại.") }
        }
    }

    private fun showTermsDialog() {
        val content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 12, 48, 0) }
        content.addView(TextView(requireContext()).apply { text = "1. Cung cấp thông tin phòng chính xác.\n2. Chịu trách nhiệm về tin đăng.\n3. Không đăng tin giả hoặc vi phạm pháp luật.\n4. Đồng ý để ứng dụng xử lý dữ liệu thuê trọ." })
        val check = CheckBox(requireContext()).apply { text = "Tôi đồng ý với điều khoản và dịch vụ" }
        content.addView(check)
        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Điều khoản Chủ trọ").setView(content).setNegativeButton("Hủy", null).setPositiveButton("Đồng ý") { _, _ ->
            if (check.isChecked) submitRole("LANDLORD", true) else requireContext().toast("Bạn cần tích đồng ý điều khoản.")
        }.show()
    }

    private fun submitRole(role: String, acceptedTerms: Boolean) = lifecycleScope.launch {
        try {
            val result = ApiClient.service(requireContext()).changeRole(ChangeRoleRequest(role, acceptedTerms))
            requireContext().toast(result.message)
            if (result.success) { SessionManager(requireContext()).role = role; requireActivity().recreate() }
        } catch (_: Exception) { requireContext().toast("Không thể chuyển vai trò.") }
    }

    private fun renderAvatar() {
        val image = view?.findViewById<ImageView>(R.id.avatar) ?: return
        if (avatarUrl.isNullOrBlank()) image.setImageResource(R.drawable.ic_avatar_placeholder)
        else image.load(avatarUrl) { crossfade(true); placeholder(R.drawable.ic_avatar_placeholder); error(R.drawable.ic_avatar_placeholder) }
    }

    /** Tải ảnh lên (Cloudinary qua server) rồi lưu địa chỉ ảnh vào hồ sơ. Giữ nguyên thông tin đã lưu trên server. */
    private fun uploadAvatar(uri: Uri) = lifecycleScope.launch {
        val ctx = context ?: return@launch
        val base = loadedUser
        if (base == null) { ctx.toast("Hồ sơ chưa tải xong, vui lòng thử lại."); return@launch }
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) { ctx.toast("Không đọc được ảnh đã chọn."); return@launch }
            if (bytes.size > 5 * 1024 * 1024) { ctx.toast("Ảnh quá lớn, vui lòng chọn ảnh dưới 5 MB."); return@launch }
            ctx.toast("Đang tải ảnh lên...")
            val mimeType = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = if (mimeType.contains("png")) "png" else "jpg"
            val part = MultipartBody.Part.createFormData("image", "avatar.$ext", bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            val api = ApiClient.service(ctx)
            val uploaded = api.uploadImage(part, "avatar")
            val url = uploaded.data?.get("url")
            if (!uploaded.success || url == null) { ctx.toast(uploaded.message.ifBlank { "Tải ảnh lên thất bại." }); return@launch }
            val saved = api.updateProfile(UpdateProfileRequest(base.fullName, base.phone, base.email, url))
            if (saved.success) {
                avatarUrl = url
                loadedUser = base.copy(avatarUrl = url)
                renderAvatar()
                ctx.toast("Đã cập nhật ảnh đại diện.")
            } else ctx.toast(saved.message)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { context?.toast(e.friendlyErrorMessage("Không đổi được ảnh đại diện.")) }
    }

    private fun value(id: Int) = view!!.findViewById<TextInputEditText>(id).text?.toString()?.trim().orEmpty()
    private fun set(id: Int, value: String?) = view!!.findViewById<TextInputEditText>(id).setText(value.orEmpty())
}
