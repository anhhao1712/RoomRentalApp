package vn.edu.thuetro.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import vn.edu.thuetro.R
import vn.edu.thuetro.data.SessionManager

class RequestsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_requests)
        applySafeArea()

        val role = SessionManager(this).role
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = if (role in listOf("LANDLORD", "ADMIN")) "Yêu cầu xem phòng" else "Lịch xem phòng của tôi"
            setNavigationOnClickListener { finish() }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, RequestsFragment())
                .commit()
        }
    }
}