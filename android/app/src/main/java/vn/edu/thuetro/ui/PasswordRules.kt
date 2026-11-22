package vn.edu.thuetro.ui

import java.security.SecureRandom

object PasswordRules {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    fun validate(password: String): String? = when {
        password.length < MIN_LENGTH -> "Mật khẩu cần tối thiểu $MIN_LENGTH ký tự."
        password.length > MAX_LENGTH -> "Mật khẩu tối đa $MAX_LENGTH ký tự."
        password.any { it.isWhitespace() } -> "Mật khẩu không được chứa khoảng trắng."
        password.none { it in 'a'..'z' } -> "Cần có ít nhất 1 chữ thường."
        password.none { it in 'A'..'Z' } -> "Cần có ít nhất 1 chữ hoa."
        password.none { it in '0'..'9' } -> "Cần có ít nhất 1 chữ số."
        password.none { !it.isLetterOrDigit() && !it.isWhitespace() } -> "Cần có ít nhất 1 ký tự đặc biệt (ví dụ @ # $ % ! ?)."
        else -> null
    }

    enum class Strength(val label: String, val color: Int) {
        EMPTY("", 0xFF64748B.toInt()),
        WEAK("Độ mạnh: Yếu", 0xFFDC2626.toInt()),
        MEDIUM("Độ mạnh: Trung bình", 0xFFD97706.toInt()),
        STRONG("Độ mạnh: Mạnh", 0xFF16A34A.toInt())
    }

    fun strength(password: String): Strength = when {
        password.isEmpty() -> Strength.EMPTY
        validate(password) != null -> Strength.WEAK
        password.length >= 12 -> Strength.STRONG
        else -> Strength.MEDIUM
    }

    // Bỏ các ký tự dễ nhầm: I l 1 O 0
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "@#$%&*!?"
    private val random = SecureRandom()

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