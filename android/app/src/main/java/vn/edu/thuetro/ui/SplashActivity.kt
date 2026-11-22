package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.SessionManager

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        applySafeArea()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        // Luôn xóa phiên cũ khi khởi động app để người dùng luôn đi qua màn hình đăng nhập.
        SessionManager(this).clear()

        findViewById<View>(R.id.splashBrandGroup).apply {
            alpha = 0f
            translationY = 18f * resources.displayMetrics.density
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450L)
                .start()
        }

        lifecycleScope.launch {
            delay(1300L)
            startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
            finish()
        }
    }
}
