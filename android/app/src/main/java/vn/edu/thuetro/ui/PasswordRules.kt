package vn.edu.thuetro.ui

import java.security.SecureRandom

/**
 * Quy tắc mật khẩu khi đăng ký / đặt lại mật khẩu (khớp với server/src/password-rules.js).
 * Không dùng khi đăng nhập để tài khoản cũ vẫn vào được.
 */
object PasswordRules {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    /** Trả về thông báo lỗi tiếng Việt, hoặc null nếu mật khẩu hợp lệ. */
    fun validate(password: String): String? = when {
        password.length < MIN_LENGTH -> "Mật khẩu cần tối thiểu $MIN_LENGTH ký tự."
        password.length > MAX_LENGTH -> "Mật khẩu tối đa $MAX_LENGTH ký tự."
        password.any { it.isWhitespace() } -> "Mật khẩu không được chứa khoảng trắng."
        password.none { it in 'a'..'z' } -> "Cần có ít nhất 1 chữ thường."
        password.none { it in 'A'..'Z' } -> "Cần có ít nhất 1 chữ hoa."
        password.none { it in '0'..'9' } -> "Cần có ít nhất 1 chữ số."
        else -> null
    }

    enum class Strength(val label: String, val color: Int) {
        EMPTY("", 0xFF64748B.toInt()),
        WEAK("Độ mạnh: Yếu", 0xFFDC2626.toInt()),
        MEDIUM("Độ mạnh: Trung bình", 0xFFD97706.toInt()),
        STRONG("Độ mạnh: Mạnh", 0xFF16A34A.toInt())
    }

    /** Chấm điểm đơn giản: độ dài + số loại ký tự. Chỉ để hiển thị gợi ý cho người dùng. */
    fun strength(password: String): Strength {
        if (password.isEmpty()) return Strength.EMPTY
        var score = 0
        if (password.length >= MIN_LENGTH) score++
        if (password.length >= 12) score++
        if (password.any { it in 'a'..'z' } && password.any { it in 'A'..'Z' }) score++
        if (password.any { it in '0'..'9' }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return when {
            validate(password) != null || score <= 2 -> Strength.WEAK
            score == 3 -> Strength.MEDIUM
            else -> Strength.STRONG
        }
    }

    // Bỏ các ký tự dễ nhầm: I l 1 O 0
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "@#$%&*!?"
    private val random = SecureRandom()

    /** Sinh mật khẩu ngẫu nhiên (mặc định 12 ký tự) luôn thỏa [validate]. */
    fun suggest(length: Int = 12): String {
        val size = length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val all = UPPER + LOWER + DIGITS + SYMBOLS
        val chars = mutableListOf(pick(UPPER), pick(LOWER), pick(DIGITS), pick(SYMBOLS))
        while (chars.size < size) chars += pick(all)
        chars.shuffle(random)
        return chars.joinToString("")
    }

    private fun pick(source: String) = source[random.nextInt(source.length)]
}