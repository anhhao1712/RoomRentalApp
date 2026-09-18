package vn.edu.thuetro.data

data class ApiResponse<T>(val success: Boolean, val message: String, val data: T?)
data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val password: String, val fullName: String, val phone: String?, val email: String?, val role: String, val acceptedTerms: Boolean = false)
data class AuthResponse(val token: String, val user: User)
data class User(val id: Long, val username: String, val fullName: String, val phone: String?, val email: String?, val role: String, val acceptedLandlordTerms: Boolean = false, val avatarUrl: String?)
data class UpdateProfileRequest(val fullName: String, val phone: String?, val email: String?, val avatarUrl: String?)
data class Amenity(val id: Long, val name: String, val icon: String)
data class Room(
    val id: Long, val landlordId: Long, val landlordName: String, val landlordPhone: String?,
    val title: String, val description: String?, val address: String, val ward: String?, val district: String?, val city: String,
    val price: Long, val area: Double, val maxTenants: Int, val deposit: Long, val electricityPrice: Long, val waterPrice: Long, val internetFee: Long,
    val status: String, val images: List<String>, val amenities: List<Amenity>, val isFavorite: Boolean, val createdAt: String?
)
data class RoomUpsertRequest(
    val title: String, val description: String?, val address: String, val ward: String?, val district: String?, val city: String,
    val price: Long, val area: Double, val maxTenants: Int, val deposit: Long, val electricityPrice: Long, val waterPrice: Long, val internetFee: Long,
    val status: String, val imageUrls: List<String>, val amenityIds: List<Long>
)
data class RentalRequestCreate(val message: String?, val viewingTime: String?, val phone: String)
data class RentalRequest(val id: Long, val roomId: Long, val roomTitle: String, val tenantId: Long, val tenantName: String, val tenantPhone: String?, val message: String?, val viewingTime: String?, val status: String, val ownerNote: String?, val createdAt: String)
data class RequestStatusUpdate(val status: String, val ownerNote: String?)
data class ChangeRoleRequest(val role: String, val acceptedTerms: Boolean = false)
data class Dashboard(val totalRooms: Int, val availableRooms: Int, val pendingRequests: Int, val totalFavorites: Int)
