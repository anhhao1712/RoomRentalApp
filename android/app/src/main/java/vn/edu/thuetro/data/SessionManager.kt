package vn.edu.thuetro.data

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("thue_tro_session", Context.MODE_PRIVATE)
    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()
    var role: String?
        get() = prefs.getString("role", null)
        set(value) = prefs.edit().putString("role", value).apply()
    var fullName: String?
        get() = prefs.getString("full_name", null)
        set(value) = prefs.edit().putString("full_name", value).apply()
    var userId: Long
        get() = prefs.getLong("user_id", -1L)
        set(value) = prefs.edit().putLong("user_id", value).apply()
    fun save(auth: AuthResponse) { token = auth.token; role = auth.user.role; fullName = auth.user.fullName; userId = auth.user.id }
    fun clear() = prefs.edit().clear().apply()
}
