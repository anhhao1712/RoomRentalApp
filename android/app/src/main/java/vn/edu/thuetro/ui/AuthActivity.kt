package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import vn.edu.thuetro.R
import vn.edu.thuetro.data.*

class AuthActivity : AppCompatActivity() {

    private var registerMode = false
    private lateinit var api: ApiService
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (session.token != null) {
            openMain()
            return
        }
        setContentView(R.layout.activity_auth)
        applySafeArea()
        api = ApiClient.service(this)

        val roleSpinner = findViewById<Spinner>(R.id.roleSpinner)
        roleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Người thuê", "Chủ trọ"))
        roleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = renderLandlordTerms()
        }

        findViewById<TextView>(R.id.buttonToggle).setOnClickListener {
            registerMode = !registerMode
            renderMode()
        }
        findViewById<Button>(R.id.buttonSubmit).setOnClickListener { submit() }
        findViewById<View>(R.id.buttonForgot).setOnClickListener {
            val typed = findViewById<TextInputEditText>(R.id.inputUsername).text?.toString()?.trim().orEmpty()
            startActivity(Intent(this, ForgotPasswordActivity::class.java).putExtra(ForgotPasswordActivity.EXTRA_USERNAME, typed))
        }
        findViewById<View>(R.id.buttonSuggestPassword).setOnClickListener { showPasswordSuggestion() }
        val onPasswordChanged = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshPasswordFeedback()
        }
        findViewById<TextInputEditText>(R.id.inputPassword).addTextChangedListener(onPasswordChanged)
        findViewById<TextInputEditText>(R.id.inputConfirmPassword).addTextChangedListener(onPasswordChanged)
        renderMode()
    }

    private fun renderMode() {
        findViewById<TextView>(R.id.titleAuth).text = if (registerMode) "Tạo tài khoản" else "Đăng nhập"
        findViewById<Button>(R.id.buttonSubmit).text = if (registerMode) "Đăng ký" else "Đăng nhập"
        findViewById<TextView>(R.id.buttonToggle).text = if (registerMode) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký"
        val visibility = if (registerMode) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutFullName).visibility = visibility
        findViewById<View>(R.id.layoutEmail).visibility = visibility
        findViewById<View>(R.id.layoutPhone).visibility = visibility
        findViewById<View>(R.id.roleSpinner).visibility = visibility
        findViewById<View>(R.id.layoutConfirmPassword).visibility = visibility
        findViewById<View>(R.id.buttonSuggestPassword).visibility = visibility
        findViewById<View>(R.id.passwordRules).visibility = visibility
        findViewById<View>(R.id.buttonForgot).visibility = if (registerMode) View.GONE else View.VISIBLE
        refreshPasswordFeedback()
        renderLandlordTerms()
    }

    private fun refreshPasswordFeedback() {
        val strengthView = findViewById<TextView>(R.id.passwordStrength)
        val layoutPassword = findViewById<TextInputLayout>(R.id.layoutPassword)
        val layoutConfirm = findViewById<TextInputLayout>(R.id.layoutConfirmPassword)
        if (!registerMode) {
            strengthView.visibility = View.GONE
            layoutPassword.error = null
            layoutConfirm.error = null
            return
        }
        val password = findViewById<TextInputEditText>(R.id.inputPassword).text?.toString().orEmpty()
        val confirm = findViewById<TextInputEditText>(R.id.inputConfirmPassword).text?.toString().orEmpty()
        val strength = PasswordRules.strength(password)
        strengthView.visibility = if (password.isEmpty()) View.GONE else View.VISIBLE
        strengthView.text = strength.label
        strengthView.setTextColor(strength.color)
        layoutPassword.error = if (password.isEmpty()) null else PasswordRules.validate(password)
        layoutConfirm.error = if (confirm.isNotEmpty() && confirm != password) "Mật khẩu xác nhận không khớp." else null
    }

    private fun showPasswordSuggestion() {
        val suggestion = PasswordRules.suggest()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Mật khẩu gợi ý")
            .setMessage("$suggestion\n\nHãy ghi nhớ hoặc lưu lại mật khẩu này trước khi dùng.")
            .setPositiveButton("Dùng mật khẩu này") { _, _ ->
                findViewById<TextInputEditText>(R.id.inputPassword).setText(suggestion)
                findViewById<TextInputEditText>(R.id.inputConfirmPassword).setText(suggestion)
            }
            .setNeutralButton("Gợi ý khác") { _, _ -> showPasswordSuggestion() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun isLandlord() = findViewById<Spinner>(R.id.roleSpinner).selectedItemPosition == 1

    private fun renderLandlordTerms() {
        findViewById<View>(R.id.landlordTerms).visibility = if (registerMode && isLandlord()) View.VISIBLE else View.GONE
    }

    private fun submit() {
        val username = findViewById<TextInputEditText>(R.id.inputUsername).text?.toString()?.trim().orEmpty()
        val password = findViewById<TextInputEditText>(R.id.inputPassword).text?.toString().orEmpty()

        if (username.isBlank() || password.isBlank()) {
            toast("Vui lòng nhập tên đăng nhập và mật khẩu.")
            return
        }
        if (registerMode && username.length < 4) {
            toast("Tên đăng nhập cần tối thiểu 4 ký tự.")
            return
        }
        if (registerMode) {
            val email = findViewById<TextInputEditText>(R.id.inputEmail).text?.toString()?.trim().orEmpty()
            val phone = findViewById<TextInputEditText>(R.id.inputPhone).text?.toString()?.trim().orEmpty()
            val layoutEmail = findViewById<TextInputLayout>(R.id.layoutEmail)
            val layoutPhone = findViewById<TextInputLayout>(R.id.layoutPhone)
            layoutEmail.error = if (ContactRules.isEmail(email)) null else "Email không hợp lệ."
            layoutPhone.error = if (ContactRules.normalizePhone(phone) != null) null else "Số điện thoại gồm 10 số, bắt đầu bằng 0."
            if (layoutEmail.error != null || layoutPhone.error != null) {
                toast("Vui lòng nhập đúng email và số điện thoại.")
                return
            }
            PasswordRules.validate(password)?.let { toast(it); return }
            val confirm = findViewById<TextInputEditText>(R.id.inputConfirmPassword).text?.toString().orEmpty()
            if (confirm != password) {
                findViewById<TextInputLayout>(R.id.layoutConfirmPassword).error = "Mật khẩu xác nhận không khớp."
                toast("Mật khẩu xác nhận không khớp.")
                return
            }
        }

        val submitButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonSubmit)
        submitButton.isEnabled = false
        val originalText = if (registerMode) "Đăng ký" else "Đăng nhập"
        submitButton.text = "Đang xử lý..."
        lifecycleScope.launch {
            try {
                val response = if (registerMode) {
                    val fullName = findViewById<TextInputEditText>(R.id.inputFullName).text?.toString()?.trim().orEmpty()
                    if (fullName.isBlank()) {
                        toast("Vui lòng nhập họ tên.")
                        return@launch
                    }
                    val landlord = isLandlord()
                    if (landlord && !findViewById<CheckBox>(R.id.acceptTerms).isChecked) {
                        toast("Bạn cần đồng ý điều khoản để đăng ký Chủ trọ.")
                        return@launch
                    }
                    val role = if (landlord) "LANDLORD" else "TENANT"
                    val email = findViewById<TextInputEditText>(R.id.inputEmail).text?.toString()?.trim().orEmpty()
                    val phone = ContactRules.normalizePhone(findViewById<TextInputEditText>(R.id.inputPhone).text?.toString().orEmpty()).orEmpty()
                    api.register(RegisterRequest(username, password, fullName, phone, email, role, landlord))
                } else {
                    api.login(LoginRequest(username, password))
                }

                if (response.success && response.data != null) {
                    session.save(response.data)
                    openMain()
                } else {
                    toast(response.message)
                }
            } catch (e: HttpException) {
                val message = runCatching { JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("message") }.getOrDefault("")
                toast(message.ifBlank { if (registerMode) "Không thể tạo tài khoản. Vui lòng kiểm tra lại thông tin." else "Tên đăng nhập hoặc mật khẩu không chính xác." })
            } catch (e: Exception) {
                toast(e.friendlyErrorMessage("Có lỗi xảy ra. Vui lòng thử lại."))
            } finally {
                if (!isFinishing && !isDestroyed) {
                    submitButton.isEnabled = true
                    submitButton.text = originalText
                }
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}