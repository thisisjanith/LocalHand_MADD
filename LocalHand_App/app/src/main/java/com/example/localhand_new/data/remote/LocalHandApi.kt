package com.example.localhand_new.data.remote

import com.example.localhand_new.data.remote.dto.AuthResponse
import com.example.localhand_new.data.remote.dto.BestListingResponse
import com.example.localhand_new.data.remote.dto.ChatRequest
import com.example.localhand_new.data.remote.dto.ChatResponse
import com.example.localhand_new.data.remote.dto.ListingResponse
import com.example.localhand_new.data.remote.dto.ListingsResponse
import com.example.localhand_new.data.remote.dto.LoginRequest
import com.example.localhand_new.data.remote.dto.PublicProfileResponse
import com.example.localhand_new.data.remote.dto.SignupRequest
import com.example.localhand_new.data.remote.dto.UpdateLocationRequest
import com.example.localhand_new.data.remote.dto.UserResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Mirrors the LocalHand_Backend Express API's routes exactly — see its README. */
interface LocalHandApi {

    @POST("auth/signup")
    suspend fun signup(@Body body: SignupRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("auth/me")
    suspend fun me(): UserResponse

    @GET("listings")
    suspend fun listings(
        @Query("type") type: String? = null,
        @Query("category") category: String? = null,
        @Query("q") q: String? = null,
        @Query("ownerId") ownerId: String? = null,
    ): ListingsResponse

    @GET("listings/mine")
    suspend fun myListings(): ListingsResponse

    @GET("listings/favourites")
    suspend fun favourites(): ListingsResponse

    @GET("listings/best")
    suspend fun best(@Query("category") category: String): BestListingResponse

    @GET("listings/{id}")
    suspend fun listing(@Path("id") id: String): ListingResponse

    // Multer always parses these two routes as multipart on the backend, even
    // with no photos attached, so every field is a @Part, never a JSON @Body.
    // "photo" is repeated once per file — Retrofit sends a List<Part> as one
    // form field per list entry, matching Multer's upload.array("photo", 5).
    @Multipart
    @POST("listings")
    suspend fun createListing(
        @Part("type") type: RequestBody,
        @Part("title") title: RequestBody,
        @Part("category") category: RequestBody,
        @Part("price") price: RequestBody,
        @Part("locality") locality: RequestBody,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?,
        @Part("condition") condition: RequestBody?,
        @Part("description") description: RequestBody,
        @Part photos: List<MultipartBody.Part>,
    ): ListingResponse

    /**
     * [existingImageUrls] is a JSON array (as plain text) of the photo URLs
     * the caller wants to keep from before this edit — anything dropped is
     * deleted server-side. [photos] are new files to append, capped at 5
     * total between the two.
     */
    @Multipart
    @PUT("listings/{id}")
    suspend fun updateListing(
        @Path("id") id: String,
        @Part("type") type: RequestBody,
        @Part("title") title: RequestBody,
        @Part("category") category: RequestBody,
        @Part("price") price: RequestBody,
        @Part("locality") locality: RequestBody,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?,
        @Part("condition") condition: RequestBody?,
        @Part("description") description: RequestBody,
        @Part("existingImageUrls") existingImageUrls: RequestBody,
        @Part photos: List<MultipartBody.Part>,
    ): ListingResponse

    @DELETE("listings/{id}")
    suspend fun deleteListing(@Path("id") id: String)

    @POST("listings/{id}/favourite")
    suspend fun addFavourite(@Path("id") id: String)

    @DELETE("listings/{id}/favourite")
    suspend fun removeFavourite(@Path("id") id: String)

    @PUT("users/me/location")
    suspend fun updateLocation(@Body body: UpdateLocationRequest): UserResponse

    @GET("users/{id}")
    suspend fun userProfile(@Path("id") id: String): PublicProfileResponse

    @POST("assistant/chat")
    suspend fun assistantChat(@Body body: ChatRequest): ChatResponse
}
