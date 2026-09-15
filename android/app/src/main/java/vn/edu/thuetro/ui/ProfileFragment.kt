package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as MainActivity).title("Tài khoản")
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
            val root = view ?: return@launch
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
            val result = ApiClient.service(requireContext()).updateProfile(UpdateProfileRequest(name, value(R.id.phone).ifBlank { null }, value(R.id.email).ifBlank { null }, null))
            if (result.success) SessionManager(requireContext()).fullName = name
            requireContext().toast(result.message)
        } catch (_: Exception) { requireContext().toast("Không lưu được hồ sơ.") }
    }

    private fun changeRole() {
        if (currentRole == "LANDLORD") {
            requireContext().confirm("Chuyển sang Người thuê", "Bạn vẫn giữ các tin đăng cũ, nhưng không thể quản lý chúng cho tới khi chuyển lại Chủ trọ.") { submitRole("TENANT", false) }
            return
        }
        if (value(R.id.phone).isBlank()) { requireContext().toast("Vui lòng nhập số điện thoại trước khi chuyển sang Chủ trọ."); return }
        lifecycleScope.launch {
            try {
                val saved = ApiClient.service(requireContext()).updateProfile(UpdateProfileRequest(value(R.id.fullName), value(R.id.phone), value(R.id.email).ifBlank { null }, null))
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

    private fun value(id: Int) = view!!.findViewById<TextInputEditText>(id).text?.toString()?.trim().orEmpty()
    private fun set(id: Int, value: String?) = view!!.findViewById<TextInputEditText>(id).setText(value.orEmpty())
}
