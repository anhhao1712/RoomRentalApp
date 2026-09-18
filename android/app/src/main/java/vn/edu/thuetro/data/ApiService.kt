package vn.edu.thuetro.data

import retrofit2.http.*

interface ApiService {
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): ApiResponse<AuthResponse>
    @POST("api/auth/register") suspend fun register(@Body body: RegisterRequest): ApiResponse<AuthResponse>
    @GET("api/rooms") suspend fun rooms(@Query("q") q: String? = null, @Query("district") district: String? = null, @Query("minPrice") minPrice: Long? = null, @Query("maxPrice") maxPrice: Long? = null, @Query("sort") sort: String? = null): ApiResponse<List<Room>>
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
}
