package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.*
import android.widget.*
import org.json.JSONObject
import retrofit2.HttpException

class AuthActivity : AppCompatActivity() {
    private var registerMode = false
    private lateinit var api: ApiService
    private lateinit var session: SessionManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (session.token != null) { openMain(); return }
        setContentView(R.layout.activity_auth)
        api = ApiClient.service(this)
        val roleSpinner = findViewById<Spinner>(R.id.roleSpinner)
        roleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Người thuê", "Chủ trọ"))
        roleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = renderLandlordTerms()
        }
        findViewById<TextView>(R.id.buttonToggle).setOnClickListener { registerMode = !registerMode; renderMode() }
        findViewById<Button>(R.id.buttonSubmit).setOnClickListener { submit() }
        renderMode()
    }
    private fun renderMode() {
        findViewById<TextView>(R.id.titleAuth).text = if(registerMode) "Tạo tài khoản" else "Đăng nhập"
        findViewById<Button>(R.id.buttonSubmit).text = if(registerMode) "Đăng ký" else "Đăng nhập"
        findViewById<TextView>(R.id.buttonToggle).text = if(registerMode) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký"
        val visibility = if(registerMode) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutFullName).visibility = visibility
        findViewById<View>(R.id.roleSpinner).visibility = visibility
        renderLandlordTerms()
    }
    private fun isLandlord() = findViewById<Spinner>(R.id.roleSpinner).selectedItemPosition == 1
    private fun renderLandlordTerms() {
        findViewById<View>(R.id.landlordTerms).visibility = if (registerMode && isLandlord()) View.VISIBLE else View.GONE
    }
    private fun submit() {
        val username = findViewById<TextInputEditText>(R.id.inputUsername).text?.toString()?.trim().orEmpty()
        val password = findViewById<TextInputEditText>(R.id.inputPassword).text?.toString().orEmpty()
        if(username.isBlank() || password.isBlank()) { toast("Vui lòng nhập tên đăng nhập và mật khẩu."); return }
        if (registerMode && username.length < 4) { toast("Tên đăng nhập cần tối thiểu 4 ký tự."); return }
        if (registerMode && password.length < 3) { toast("Mật khẩu cần tối thiểu 3 ký tự."); return }
        lifecycleScope.launch {
            try {
                val response = if(registerMode) {
                    val fullName = findViewById<TextInputEditText>(R.id.inputFullName).text?.toString()?.trim().orEmpty()
                    if(fullName.isBlank()) { toast("Vui lòng nhập họ tên."); return@launch }
                    val landlord = isLandlord()
                    if (landlord && !findViewById<CheckBox>(R.id.acceptTerms).isChecked) { toast("Bạn cần đồng ý điều khoản để đăng ký Chủ trọ."); return@launch }
                    val role = if(landlord) "LANDLORD" else "TENANT"
                    api.register(RegisterRequest(username, password, fullName, null, null, role, landlord))
                } else api.login(LoginRequest(username, password))
                if(response.success && response.data != null) { session.save(response.data); openMain() } else toast(response.message)
            } catch (e: HttpException) {
                val message = runCatching { JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("message") }.getOrDefault("")
                toast(message.ifBlank { "Không thể tạo tài khoản. Vui lòng kiểm tra lại thông tin." })
            } catch (e: Exception) { toast("Không kết nối được server. Kiểm tra Node.js API đang chạy và địa chỉ API.") }
        }
    }
    private fun openMain() { startActivity(Intent(this, MainActivity::class.java)); finish() }
}
