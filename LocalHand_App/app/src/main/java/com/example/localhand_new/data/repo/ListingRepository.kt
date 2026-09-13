package com.example.localhand_new.data.repo

import com.example.localhand_new.data.location.LatLng
import com.example.localhand_new.data.location.distanceMetres
import com.example.localhand_new.data.model.Category
import com.example.localhand_new.data.model.Condition
import com.example.localhand_new.data.model.Listing
import com.example.localhand_new.data.model.ListingType
import com.example.localhand_new.data.model.PublicProfile
import com.example.localhand_new.data.model.SearchFilters
import com.example.localhand_new.data.remote.LocalHandApi
import com.example.localhand_new.data.remote.toRequestBody
import com.example.localhand_new.data.remote.dto.toDomain
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.MultipartBody

private val existingImageUrlsAdapter = Moshi.Builder().build()
    .adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))

/**
 * Single source of truth for listings — a thin mapping layer over the HTTP
 * API, plus a last-known-good in-memory cache per query. The bottom nav's
 * tab switches destroy and recreate each tab's ViewModel (Navigation's
 * popUpTo/saveState pattern), so without this every switch briefly rendered
 * a blank/empty screen while a fresh request round-tripped to the backend —
 * the cache lets a ViewModel seed its initial state synchronously with the
 * previous result instead, while [getAll]/[getMine]/[getFavourites] still
 * refresh it in the background.
 */
class ListingRepository(private val api: LocalHandApi) {

    private val listingsCache = mutableMapOf<String, List<Listing>>()
    private var mineCache: List<Listing>? = null
    private var favouritesCache: List<Listing>? = null

    private fun cacheKey(filters: SearchFilters) =
        "${filters.type}|${filters.category}|${filters.query.trim().lowercase()}"

    fun cachedAll(filters: SearchFilters = SearchFilters()): List<Listing>? = listingsCache[cacheKey(filters)]
    fun cachedMine(): List<Listing>? = mineCache
    fun cachedFavourites(): List<Listing>? = favouritesCache

    suspend fun getAll(filters: SearchFilters = SearchFilters()): Result<List<Listing>> = runCatching {
        api.listings(
            type = filters.type?.name,
            category = filters.category?.name,
            q = filters.query.trim().ifBlank { null },
        ).listings.map { it.toDomain() }
    }.onSuccess { listingsCache[cacheKey(filters)] = it }

    suspend fun getById(id: String): Result<Listing> = runCatching {
        api.listing(id).listing.toDomain()
    }

    suspend fun getTopRated(limit: Int = 8): Result<List<Listing>> = runCatching {
        api.listings().listings.map { it.toDomain() }
            .sortedByDescending { it.rating }
            .take(limit)
    }

    suspend fun getFavourites(): Result<List<Listing>> = runCatching {
        api.favourites().listings.map { it.toDomain() }
    }.onSuccess { favouritesCache = it }

    suspend fun getMine(): Result<List<Listing>> = runCatching {
        api.myListings().listings.map { it.toDomain() }
    }.onSuccess { mineCache = it }

    suspend fun getByOwner(ownerId: String): Result<List<Listing>> = runCatching {
        api.listings(ownerId = ownerId).listings.map { it.toDomain() }
    }

    suspend fun getProfile(id: String): Result<PublicProfile> = runCatching {
        api.userProfile(id).user.toDomain()
    }

    suspend fun bestInCategory(category: Category): Result<Listing?> = runCatching {
        api.best(category.name).listing?.toDomain()
    }

    suspend fun toggleFavourite(listing: Listing): Result<Unit> = runCatching {
        if (listing.isFavourite) api.removeFavourite(listing.id) else api.addFavourite(listing.id)
    }

    suspend fun create(
        type: ListingType,
        title: String,
        category: Category,
        price: String,
        locality: String,
        latitude: Double?,
        longitude: Double?,
        condition: Condition?,
        description: String,
        photos: List<MultipartBody.Part>,
    ): Result<Listing> = runCatching {
        api.createListing(
            type = type.name.toRequestBody(),
            title = title.toRequestBody(),
            category = category.name.toRequestBody(),
            price = price.toRequestBody(),
            locality = locality.toRequestBody(),
            latitude = latitude?.toString()?.toRequestBody(),
            longitude = longitude?.toString()?.toRequestBody(),
            condition = condition?.name?.toRequestBody(),
            description = description.toRequestBody(),
            photos = photos,
        ).listing.toDomain()
    }

    suspend fun update(
        id: String,
        type: ListingType,
        title: String,
        category: Category,
        price: String,
        locality: String,
        latitude: Double?,
        longitude: Double?,
        condition: Condition?,
        description: String,
        keptImageUrls: List<String>,
        newPhotos: List<MultipartBody.Part>,
    ): Result<Listing> = runCatching {
        api.updateListing(
            id = id,
            type = type.name.toRequestBody(),
            title = title.toRequestBody(),
            category = category.name.toRequestBody(),
            price = price.toRequestBody(),
            locality = locality.toRequestBody(),
            latitude = latitude?.toString()?.toRequestBody(),
            longitude = longitude?.toString()?.toRequestBody(),
            condition = condition?.name?.toRequestBody(),
            description = description.toRequestBody(),
            existingImageUrls = existingImageUrlsAdapter.toJson(keptImageUrls).toRequestBody(),
            photos = newPhotos,
        ).listing.toDomain()
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        api.deleteListing(id)
    }
}

/** A listing paired with how far it is from the user's pin. */
data class NearbyListing(
    val listing: Listing,
    /** Null when either the user or the listing has no coordinates. */
    val metres: Double?,
)

/**
 * Ranks listings by real distance from [from]. Nothing is hidden — the closest
 * simply come first, and anything without coordinates sorts last.
 */
fun List<Listing>.rankByDistance(from: LatLng?): List<NearbyListing> {
    val ranked = map { listing ->
        val point = listing.point()
        NearbyListing(
            listing = listing,
            metres = if (from != null && point != null) distanceMetres(from, point) else null,
        )
    }
    // Without a user pin there is no meaningful distance, so preserve the
    // caller's existing order (recency, or rating for the Near You band).
    if (from == null) return ranked
    return ranked.sortedWith(
        compareBy(nullsLast()) { it.metres },
    )
}

fun Listing.point(): LatLng? {
    val lat = latitude
    val lng = longitude
    return if (lat != null && lng != null) LatLng(lat, lng) else null
}
