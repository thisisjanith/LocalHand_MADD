package com.example.localhand_new.data.model

enum class ListingType { SERVICE, MARKETPLACE }

enum class Category { REPAIRS, TUTORING, CLEANING, GARDEN, ERRANDS, ELECTRONICS, FURNITURE, OTHER }

enum class Condition { LIKE_NEW, GOOD, FAIR }

data class Listing(
    val id: String,
    val type: ListingType,
    val title: String,
    val category: Category,
    val providerId: String,
    val providerName: String,
    val providerPhone: String,
    val rating: Double,
    val price: String,          // display string, e.g. "Rs 800 / hour"
    val locality: String,       // the listing's named area
    val latitude: Double?,
    val longitude: Double?,
    val condition: Condition?,  // marketplace only
    val description: String,
    val imageRes: Int,          // fallback category artwork, computed from category on every fetch
    val imageUrls: List<String>, // the poster's uploaded photos (remote R2 URLs), in display order; empty if none
    val isMine: Boolean,
    val isFavourite: Boolean,
    val createdAt: Long,
) {
    /** The photo list rows and search results show — takes priority over imageRes. */
    val imageUrl: String? get() = imageUrls.firstOrNull()
}

data class UserProfile(
    val name: String,
    val email: String,
    val phone: String,
    val locality: String,
    val memberSince: Int,
    val rating: Double,
)

/** Another user's public info, shown on their profile screen — no email/phone. */
data class PublicProfile(
    val id: String,
    val name: String,
    val locality: String,
    val memberSince: Int,
    val rating: Double,
)

data class ChatMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    /** A listing the assistant is recommending, if any — lets the bubble deep-link to it. */
    val listingId: String? = null,
    /** A placeholder bubble shown while the assistant's reply is in flight. */
    val isTyping: Boolean = false,
)

data class SearchFilters(
    val query: String = "",
    val type: ListingType? = null,   // null = All
    val category: Category? = null,  // null = All
)

/** Display label for a category, e.g. REPAIRS -> "Repairs". */
val Category.label: String
    get() = when (this) {
        Category.REPAIRS -> "Repairs"
        Category.TUTORING -> "Tutoring"
        Category.CLEANING -> "Cleaning"
        Category.GARDEN -> "Garden"
        Category.ERRANDS -> "Errands"
        Category.ELECTRONICS -> "Electronics"
        Category.FURNITURE -> "Furniture"
        Category.OTHER -> "Other"
    }

val ListingType.label: String
    get() = when (this) {
        ListingType.SERVICE -> "Service"
        ListingType.MARKETPLACE -> "Marketplace"
    }

val Condition.label: String
    get() = when (this) {
        Condition.LIKE_NEW -> "Like New"
        Condition.GOOD -> "Good"
        Condition.FAIR -> "Fair"
    }
