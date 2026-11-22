package vn.edu.thuetro.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.edu.thuetro.R
import vn.edu.thuetro.data.ApiClient

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var bellBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySafeArea()

        bottomNav = findViewById(R.id.bottomNav)
        bellBadge = findViewById(R.id.bellBadge)

        findViewById<View>(R.id.bellContainer).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> show(RoomListFragment())
                R.id.nav_favorite -> show(FavoriteFragment())
                R.id.nav_messages -> show(MessagesFragment())
                R.id.nav_profile -> show(ProfileFragment())
            }
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshBadgesNow()
                    delay(10_000L)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBadges()
    }

    fun title(value: String) {
        findViewById<MaterialToolbar>(R.id.toolbar).title = value
    }

    fun refreshBadges() {
        lifecycleScope.launch { refreshBadgesNow() }
    }

    private suspend fun refreshBadgesNow() {
        try {
            val counts = ApiClient.service(this).badges().data ?: return
            val msgBadge = bottomNav.getOrCreateBadge(R.id.nav_messages)
            if (counts.unreadMessages > 0) {
                msgBadge.isVisible = true
                msgBadge.number = counts.unreadMessages
                msgBadge.backgroundColor = 0xFFEF4444.toInt()
                msgBadge.badgeTextColor = 0xFFFFFFFF.toInt()
            } else {
                msgBadge.clearNumber()
                msgBadge.isVisible = false
            }

            if (counts.unreadNotifications > 0) {
                bellBadge.text = if (counts.unreadNotifications > 99) "99+" else counts.unreadNotifications.toString()
                bellBadge.visibility = View.VISIBLE
            } else {
                bellBadge.visibility = View.GONE
            }
        } catch (_: Exception) {
            // Bỏ qua lỗi mạng tạm thời khi cập nhật badge nền
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()
    }
}