package com.example.localhand_new.data.remote.dto

import com.example.localhand_new.data.model.Category
import com.example.localhand_new.data.model.Condition
import com.example.localhand_new.data.model.Listing
import com.example.localhand_new.data.model.ListingType
import com.example.localhand_new.data.model.PublicProfile
import com.example.localhand_new.data.model.UserProfile
import com.example.localhand_new.data.model.defaultImageFor
import com.squareup.moshi.JsonClass

/**
 * Wire-shape data classes, kept separate from the domain types in
 * data/model/Models.kt so a backend JSON change doesn't ripple into UI code.
 * type/category/condition stay String here rather than typed enums, mapped to
 * the domain enums in toDomain() below — avoids custom Moshi enum adapters and
 * tolerates a future unknown value gracefully instead of a hard adapter crash.
 */

@JsonClass(generateAdapter = true)
data class ListingDto(
    val id: String,
    val type: String,
    val title: String,
    val category: String,
    val providerId: String,
    val providerName: String,
    val providerPhone: String,
    val rating: Double,
    val price: String,
    val locality: String,
    val latitude: Double?,
    val longitude: Double?,
    val condition: String?,
    val description: String,
    val imageUrls: List<String> = emptyList(),
    val isMine: Boolean,
    val isFavourite: Boolean,
    val createdAt: Long,
)

fun ListingDto.toDomain(): Listing {
    val category = Category.valueOf(category)
    return Listing(
        id = id,
        type = ListingType.valueOf(type),
        title = title,
        category = category,
        providerId = providerId,
        providerName = providerName,
        providerPhone = providerPhone,
        rating = rating,
        price = price,
        locality = locality,
        latitude = latitude,
        longitude = longitude,
        condition = condition?.let { Condition.valueOf(it) },
        description = description,
        imageRes = defaultImageFor(category),
        imageUrls = imageUrls,
        isMine = isMine,
        isFavourite = isFavourite,
        createdAt = createdAt,
    )
}

@JsonClass(generateAdapter = true)
data class ListingsResponse(val listings: List<ListingDto>)

@JsonClass(generateAdapter = true)
data class ListingResponse(val listing: ListingDto)

@JsonClass(generateAdapter = true)
data class BestListingResponse(val listing: ListingDto?)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val locality: String,
    val latitude: Double?,
    val longitude: Double?,
    val rating: Double,
    val memberSince: Int,
)

fun UserDto.toDomain(): UserProfile = UserProfile(
    name = name,
    email = email,
    phone = phone,
    locality = locality,
    memberSince = memberSince,
    rating = rating,
)

@JsonClass(generateAdapter = true)
data class PublicProfileDto(
    val id: String,
    val name: String,
    val locality: String,
    val rating: Double,
    val memberSince: Int,
)

fun PublicProfileDto.toDomain(): PublicProfile = PublicProfile(
    id = id,
    name = name,
    locality = locality,
    memberSince = memberSince,
    rating = rating,
)

@JsonClass(generateAdapter = true)
data class PublicProfileResponse(val user: PublicProfileDto)

@JsonClass(generateAdapter = true)
data class AuthResponse(val token: String, val user: UserDto)

@JsonClass(generateAdapter = true)
data class UserResponse(val user: UserDto)

@JsonClass(generateAdapter = true)
data class ErrorResponse(val error: String)

@JsonClass(generateAdapter = true)
data class SignupRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String,
    val locality: String,
    val latitude: Double?,
    val longitude: Double?,
)

@JsonClass(generateAdapter = true)
data class LoginRequest(val email: String, val password: String)

@JsonClass(generateAdapter = true)
data class UpdateLocationRequest(val locality: String, val latitude: Double, val longitude: Double)

@JsonClass(generateAdapter = true)
data class ChatTurnDto(val fromUser: Boolean, val text: String)

@JsonClass(generateAdapter = true)
data class ChatRequest(val message: String, val history: List<ChatTurnDto>)

@JsonClass(generateAdapter = true)
data class ChatResponse(val reply: String, val listingId: String?)
