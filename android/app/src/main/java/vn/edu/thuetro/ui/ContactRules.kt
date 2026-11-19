package vn.edu.thuetro.ui

object ContactRules {
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isEmail(value: String): Boolean = value.trim().let { it.length <= 254 && emailRegex.matches(it) }

    fun normalizePhone(value: String): String? {
        var digits = value.replace(Regex("[\\s.\\-()]"), "")
        digits = when {
            digits.startsWith("+84") -> "0" + digits.substring(3)
            digits.startsWith("84") && digits.length == 11 -> "0" + digits.substring(2)
            else -> digits
        }
        return digits.takeIf { Regex("^0\\d{9}$").matches(it) }
    }
}
