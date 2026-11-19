package vn.edu.thuetro.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient
import vn.edu.thuetro.data.ApiService
import vn.edu.thuetro.data.ForgotPasswordRequest
import vn.edu.thuetro.data.ResetPasswordRequest

class ForgotPasswordActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USERNAME = "username"
        private const val RESEND_SECONDS = 60L
    }

    private lateinit var api: ApiService
    private lateinit var sendButton: MaterialButton
    private var countdown: CountDownTimer? = null
    private var codeRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        applySafeArea()
        api = ApiClient.service(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        sendButton = findViewById(R.id.buttonSendCode)
        findViewById<TextInputEditText>(R.id.inputUsername).setText(intent.getStringExtra(EXTRA_USERNAME).orEmpty())

        sendButton.setOnClickListener { sendCode() }
        findViewById<View>(R.id.buttonReset).setOnClickListener { resetPassword() }
        findViewById<View>(R.id.buttonSuggestPassword).setOnClickListener { showPasswordSuggestion() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshPasswordFeedback()
        }
        findViewById<TextInputEditText>(R.id.inputNewPassword).addTextChangedListener(watcher)
        findViewById<TextInputEditText>(R.id.inputConfirmPassword).addTextChangedListener(watcher)
    }

    override fun onDestroy() {
        countdown?.cancel()
        super.onDestroy()
    }

    private fun username() = findViewById<TextInputEditText>(R.id.inputUsername).text?.toString()?.trim().orEmpty()

    private fun sendCode() {
        val username = username()
        if (username.isBlank()) {
            toast("Vui lòng nhập tên đăng nhập.")
            return
        }
        sendButton.isEnabled = false
        sendButton.text = "Đang gửi..."
        lifecycleScope.launch {
            try {
                val response = api.forgotPassword(ForgotPasswordRequest(username))
                toast(response.message)
                if (response.success) {
                    codeRequested = true
                    findViewById<View>(R.id.resetSection).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.stepHint).text = response.message
                    startResendCountdown()
                    return@launch
                }
            } catch (e: Exception) {
                toast(e.friendlyErrorMessage("Không gửi được mã. Vui lòng thử lại."))
            }
            sendButton.isEnabled = true
            sendButton.text = if (codeRequested) "Gửi lại mã" else "Gửi mã xác nhận"
        }
    }

    private fun startResendCountdown() {
        countdown?.cancel()
        sendButton.isEnabled = false
        countdown = object : CountDownTimer(RESEND_SECONDS * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                sendButton.text = "Gửi lại mã sau ${millisUntilFinished / 1000 + 1}s"
            }

            override fun onFinish() {
                sendButton.isEnabled = true
                sendButton.text = "Gửi lại mã"
            }
        }.start()
    }

    private fun refreshPasswordFeedback() {
        val password = findViewById<TextInputEditText>(R.id.inputNewPassword).text?.toString().orEmpty()
        val confirm = findViewById<TextInputEditText>(R.id.inputConfirmPassword).text?.toString().orEmpty()
        val strengthView = findViewById<TextView>(R.id.passwordStrength)
        val strength = PasswordRules.strength(password)
        strengthView.visibility = if (password.isEmpty()) View.GONE else View.VISIBLE
        strengthView.text = strength.label
        strengthView.setTextColor(strength.color)
        findViewById<TextInputLayout>(R.id.layoutNewPassword).error = if (password.isEmpty()) null else PasswordRules.validate(password)
        findViewById<TextInputLayout>(R.id.layoutConfirmPassword).error =
            if (confirm.isNotEmpty() && confirm != password) "Mật khẩu xác nhận không khớp." else null
    }

    private fun showPasswordSuggestion() {
        val suggestion = PasswordRules.suggest()
        AlertDialog.Builder(this)
            .setTitle("Mật khẩu gợi ý")
            .setMessage("$suggestion\n\nHãy ghi nhớ hoặc lưu lại mật khẩu này trước khi dùng.")
            .setPositiveButton("Dùng mật khẩu này") { _, _ ->
                findViewById<TextInputEditText>(R.id.inputNewPassword).setText(suggestion)
                findViewById<TextInputEditText>(R.id.inputConfirmPassword).setText(suggestion)
            }
            .setNeutralButton("Gợi ý khác") { _, _ -> showPasswordSuggestion() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun resetPassword() {
        val username = username()
        val code = findViewById<TextInputEditText>(R.id.inputCode).text?.toString()?.trim().orEmpty()
        val password = findViewById<TextInputEditText>(R.id.inputNewPassword).text?.toString().orEmpty()
        val confirm = findViewById<TextInputEditText>(R.id.inputConfirmPassword).text?.toString().orEmpty()
        if (code.length != 6) {
            toast("Vui lòng nhập mã xác nhận gồm 6 số.")
            return
        }
        PasswordRules.validate(password)?.let { toast(it); return }
        if (confirm != password) {
            toast("Mật khẩu xác nhận không khớp.")
            return
        }
        val button = findViewById<MaterialButton>(R.id.buttonReset)
        button.isEnabled = false
        button.text = "Đang xử lý..."
        lifecycleScope.launch {
            try {
                val response = api.resetPassword(ResetPasswordRequest(username, code, password))
                toast(response.message)
                if (response.success) {
                    finish() // quay về màn hình đăng nhập
                    return@launch
                }
            } catch (e: Exception) {
                toast(e.friendlyErrorMessage("Không đặt lại được mật khẩu. Vui lòng thử lại."))
            }
            button.isEnabled = true
            button.text = "Đặt lại mật khẩu"
        }
    }
}
