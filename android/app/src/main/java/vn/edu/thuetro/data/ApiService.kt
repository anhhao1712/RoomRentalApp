package vn.edu.thuetro.data

import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {
    @Multipart @POST("api/uploads/image") suspend fun uploadImage(@Part image: MultipartBody.Part, @Query("type") type: String? = null): ApiResponse<Map<String, String>>
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): ApiResponse<AuthResponse>
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequest): ApiResponse<AuthResponse>
    @POST("api/auth/forgot-password") suspend fun forgotPassword(@Body body: ForgotPasswordRequest): ApiResponse<Any>
    @POST("api/auth/reset-password") suspend fun resetPassword(@Body body: ResetPasswordRequest): ApiResponse<Any>
    @GET("api/rooms") suspend fun rooms(@Query("q") q: String? = null, @Query("district") district: String? = null, @Query("minPrice") minPrice: Long? = null, @Query("maxPrice") maxPrice: Long? = null, @Query("sort") sort: String? = null, @Query("amenities") amenities: String? = null): ApiResponse<List<Room>>
    @GET("api/rooms/{id}") suspend fun room(@Path("id") id: Long): ApiResponse<Room>
    @GET("api/amenities") suspend fun amenities(): ApiResponse<List<Amenity>>
    @GET("api/me") suspend fun me(): ApiResponse<User>
    @PUT("api/me") suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiResponse<Any>
    @PATCH("api/me/role") suspend fun changeRole(@Body body: ChangeRoleRequest): ApiResponse<Any>
    @GET("api/favorites") suspend fun favorites(): ApiResponse<List<Room>>
    @POST("api/rooms/{id}/favorite") suspend fun toggleFavorite(@Path("id") id: Long): ApiResponse<Boolean>
    @POST("api/rooms/{id}/requests") suspend fun sendRequest(@Path("id") id: Long, @Body body: RentalRequestCreate): ApiResponse<Any>
    @GET("api/requests") suspend fun requests(): ApiResponse<List<RentalRequest>>
    @PATCH("api/requests/{id}") suspend fun updateRequest(@Path("id") id: Long, @Body body: RequestStatusUpdate): ApiResponse<Any>
    @GET("api/owner/rooms") suspend fun ownerRooms(): ApiResponse<List<Room>>
    @POST("api/rooms") suspend fun createRoom(@Body body: RoomUpsertRequest): ApiResponse<Map<String, Long>>
    @PUT("api/rooms/{id}") suspend fun updateRoom(@Path("id") id: Long, @Body body: RoomUpsertRequest): ApiResponse<Any>
    @PATCH("api/rooms/{id}/status") suspend fun updateStatus(@Path("id") id: Long, @Body body: Map<String, String>): ApiResponse<Any>
    @DELETE("api/rooms/{id}") suspend fun deleteRoom(@Path("id") id: Long): ApiResponse<Any>
    @GET("api/dashboard") suspend fun dashboard(): ApiResponse<Dashboard>

    @GET("api/users/search") suspend fun searchUsers(@Query("q") q: String): ApiResponse<List<PublicUser>>
    @GET("api/users/{id}/profile") suspend fun userProfile(@Path("id") id: Long): ApiResponse<UserProfileResponse>

    @GET("api/conversations") suspend fun conversations(): ApiResponse<List<Conversation>>
    @POST("api/conversations/open") suspend fun openConversation(@Body body: OpenConversationRequest): ApiResponse<Conversation>
    @GET("api/conversations/{id}/messages") suspend fun messages(@Path("id") id: String): ApiResponse<MessagesResponse>
    @POST("api/conversations/{id}/messages") suspend fun sendMessage(@Path("id") id: String, @Body body: SendMessageRequest): ApiResponse<ChatMessage>
    @PATCH("api/conversations/{id}/read") suspend fun markConversationRead(@Path("id") id: String): ApiResponse<Any>
    @PATCH("api/conversations/{id}/pin") suspend fun pinConversation(@Path("id") id: String, @Body body: PinConversationRequest): ApiResponse<Map<String, Boolean>>
    @DELETE("api/conversations/{id}") suspend fun deleteConversation(@Path("id") id: String): ApiResponse<Any>
    @PATCH("api/conversations/{id}/block") suspend fun blockConversation(@Path("id") id: String, @Body body: BlockConversationRequest): ApiResponse<Map<String, Boolean>>

    @GET("api/notifications") suspend fun notifications(): ApiResponse<List<AppNotification>>
    @PATCH("api/notifications/{id}/read") suspend fun markNotificationRead(@Path("id") id: Long): ApiResponse<Any>
    @PATCH("api/notifications/read-all") suspend fun markAllNotificationsRead(): ApiResponse<Any>
    @GET("api/badges") suspend fun badges(): ApiResponse<BadgeCounts>
}
