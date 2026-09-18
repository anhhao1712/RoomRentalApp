package vn.edu.thuetro.data

data class AreaOption(val city: String, val district: String) {
    val label get() = "$district, $city"
}

object AreaCatalog {
    val options = buildList {
        add(AreaOption("TP. Hồ Chí Minh", "TP. Thủ Đức"))
        listOf("Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6", "Quận 7", "Quận 8", "Quận 10", "Quận 11", "Quận 12", "Bình Thạnh", "Gò Vấp", "Phú Nhuận", "Tân Bình", "Tân Phú", "Bình Tân", "Bình Chánh", "Củ Chi", "Hóc Môn", "Nhà Bè", "Cần Giờ").forEach { add(AreaOption("TP. Hồ Chí Minh", it)) }
        listOf("TP. Dĩ An", "TP. Thuận An", "TP. Thủ Dầu Một").forEach { add(AreaOption("Bình Dương", it)) }
        add(AreaOption("Đồng Nai", "TP. Biên Hòa"))
        add(AreaOption("Long An", "Đức Hòa"))
        add(AreaOption("Long An", "Bến Lức"))
    }
    fun byDistrict(district: String?) = options.firstOrNull { it.district == district } ?: options.first()
}
