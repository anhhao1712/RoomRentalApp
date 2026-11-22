package vn.edu.thuetro.data

data class ApiResponse<T>(val success: Boolean, val message: String, val data: T?)
data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val password: String, val fullName: String, val phone: String?, val email: String?, val role: String, val acceptedTerms: Boolean = false)
data class ForgotPasswordRequest(val username: String)
data class ResetPasswordRequest(val username: String, val code: String, val newPassword: String)
data class AuthResponse(val token: String, val user: User)
data class User(val id: Long, val username: String, val fullName: String, val phone: String?, val email: String?, val role: String, val acceptedLandlordTerms: Boolean = false, val avatarUrl: String?)
data class UpdateProfileRequest(val fullName: String, val phone: String?, val email: String?, val avatarUrl: String?)
data class Amenity(val id: Long, val name: String, val icon: String)
data class Room(
    val id: Long, val landlordId: Long, val landlordName: String, val landlordPhone: String?,
    val title: String, val description: String?, val address: String, val ward: String?, val district: String?, val city: String,
    val price: Long, val area: Double, val maxTenants: Int, val deposit: Long, val electricityPrice: Long, val waterPrice: Long, val internetFee: Long,
    val status: String, val images: List<String>, val amenities: List<Amenity>, val isFavorite: Boolean, val createdAt: String?,
    val latitude: Double? = null, val longitude: Double? = null,
    val landlordAvatarUrl: String? = null, val landlordUsername: String? = null, val landlordRole: String? = null
)
data class RoomUpsertRequest(
    val title: String, val description: String?, val address: String, val ward: String?, val district: String?, val city: String,
    val price: Long, val area: Double, val maxTenants: Int, val deposit: Long, val electricityPrice: Long, val waterPrice: Long, val internetFee: Long,
    val status: String, val imageUrls: List<String>, val amenityIds: List<Long>,
    val latitude: Double? = null, val longitude: Double? = null
)
data class RentalRequestCreate(val message: String?, val viewingTime: String?, val phone: String)
data class RentalRequest(
    val id: Long, val roomId: Long, val roomTitle: String, val tenantId: Long, val tenantName: String, val tenantPhone: String?,
    val message: String?, val viewingTime: String?, val status: String, val ownerNote: String?, val createdAt: String,
    val ownerId: Long = 0L, val ownerName: String? = null, val ownerPhone: String? = null,
    val ownerAvatarUrl: String? = null, val tenantAvatarUrl: String? = null,
    val roomImage: String? = null, val roomPrice: Long? = null
)
data class RequestStatusUpdate(val status: String, val ownerNote: String?)
data class ChangeRoleRequest(val role: String, val acceptedTerms: Boolean = false)
data class Dashboard(
    val totalRooms: Int, val availableRooms: Int, val pendingRequests: Int, val totalFavorites: Int,
    val totalRequests: Int = 0, val acceptedRequests: Int = 0
)

data class PublicUser(
    val id: Long, val username: String, val fullName: String, val phone: String?, val role: String,
    val avatarUrl: String?, val createdAt: String? = null, val roomCount: Int = 0, val availableRoomCount: Int = 0
)
data class UserProfileStats(val totalRooms: Int = 0, val availableRooms: Int = 0, val totalPhotos: Int = 0)
data class UserPhoto(val url: String, val roomId: Long, val roomTitle: String, val createdAt: String? = null)
data class UserProfileResponse(
    val user: PublicUser, val stats: UserProfileStats,
    val rooms: List<Room> = emptyList(), val photos: List<UserPhoto> = emptyList()
)
data class OpenConversationRequest(val otherUserId: Long, val roomId: Long? = null)
data class Conversation(
    val id: String, val otherUser: PublicUser, val lastMessage: String = "",
    val lastMessageAt: String? = null, val lastSenderId: Long? = null,
    val lastRoomId: Long? = null, val lastRoomTitle: String? = null, val unreadCount: Int = 0,
    val isPinned: Boolean = false, val blockedByMe: Boolean = false, val blockedByOther: Boolean = false
)
data class PinConversationRequest(val pinned: Boolean)
data class BlockConversationRequest(val blocked: Boolean)
data class ChatMessage(
    val id: Long, val senderId: Long, val text: String,
    val roomId: Long? = null, val roomTitle: String? = null, val roomPrice: Long? = null,
    val roomImage: String? = null, val createdAt: String? = null
)
data class MessagesResponse(val conversation: Conversation, val messages: List<ChatMessage> = emptyList())
data class SendMessageRequest(val text: String, val roomId: Long? = null)
data class AppNotification(
    val id: Long, val userId: Long, val type: String, val title: String, val body: String,
    val roomId: Long? = null, val requestId: Long? = null,
    val actorId: Long? = null, val actorName: String? = null, val actorAvatarUrl: String? = null,
    val isRead: Boolean = false, val createdAt: String? = null
)
data class BadgeCounts(val unreadMessages: Int = 0, val unreadNotifications: Int = 0, val pendingRequests: Int = 0)
