package vn.edu.thuetro.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import vn.edu.thuetro.R
import vn.edu.thuetro.data.SessionManager
import android.content.Intent

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        val role = SessionManager(this).role
        // Người thuê không cần thấy mục quản lý tin đăng; chủ trọ và quản trị viên vẫn có đủ chức năng.
        nav.menu.findItem(R.id.nav_manage).isVisible = role in listOf("LANDLORD", "ADMIN")
        nav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> show(RoomListFragment())
                R.id.nav_favorite -> show(FavoriteFragment())
                R.id.nav_requests -> show(RequestsFragment())
                R.id.nav_manage -> show(ManageRoomsFragment())
                R.id.nav_profile -> show(ProfileFragment())
            }; true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_home
//            startActivity(
//                Intent(this, MapTilerTestActivity::class.java)
//            )
        }
    }
    fun title(value: String) { findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title = value }
    private fun show(fragment: Fragment) { supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit() }
}
