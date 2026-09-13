package com.example.localhand_new.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localhand_new.data.model.Category
import com.example.localhand_new.data.model.Condition
import com.example.localhand_new.data.model.Listing
import com.example.localhand_new.data.model.ListingType
import com.example.localhand_new.data.location.LatLng
import com.example.localhand_new.data.location.LocationProvider
import com.example.localhand_new.data.remote.toFormDataPart
import com.example.localhand_new.data.remote.toUserMessage
import com.example.localhand_new.data.repo.ListingRepository
import com.example.localhand_new.data.repo.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Dragging emits continuously; settle before hitting the geocoder. */
private const val GEOCODE_DEBOUNCE_MS = 400L

const val MAX_LISTING_PHOTOS = 5

/** One slot in the photo strip — either a locally-picked file awaiting upload, or an already-hosted URL. */
sealed interface ListingPhoto {
    data class Local(val path: String) : ListingPhoto
    data class Remote(val url: String) : ListingPhoto
}

/** Form state for Create Listing. The shape adapts to [type]. */
data class CreateListingState(
    val type: ListingType = ListingType.SERVICE,
    val title: String = "",
    val category: Category? = null,
    val price: String = "",
    val locality: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val condition: Condition? = null,
    val description: String = "",
    /** Up to [MAX_LISTING_PHOTOS], in display order — first is the cover photo. */
    val photos: List<ListingPhoto> = emptyList(),
    val errors: Map<Field, String> = emptyMap(),
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
) {
    enum class Field { TITLE, CATEGORY, PRICE, LOCALITY, CONDITION, DESCRIPTION }

    /** The price label switches Rate <-> Price with the listing type. */
    val priceLabel: String
        get() = if (type == ListingType.SERVICE) "Rate" else "Price"

    val pricePlaceholder: String
        get() = if (type == ListingType.SERVICE) "e.g. Rs 1,000 / hour" else "e.g. Rs 5,000"

    /** Condition is a marketplace-only concept. */
    val showCondition: Boolean get() = type == ListingType.MARKETPLACE

    val canAddMorePhotos: Boolean get() = photos.size < MAX_LISTING_PHOTOS

    /** The listing's own pin, if one has been set. */
    fun pin(): LatLng? {
        val lat = latitude
        val lng = longitude
        return if (lat != null && lng != null) LatLng(lat, lng) else null
    }
}

class CreateListingViewModel(
    private val repo: ListingRepository,
    private val prefs: PreferencesRepository,
    private val location: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateListingState())
    val state: StateFlow<CreateListingState> = _state.asStateFlow()

    init {
        // Default a new listing to wherever the user is, so most posts need no
        // map interaction at all.
        viewModelScope.launch {
            val here = prefs.userLocation.first()
            val area = prefs.userLocality.first()
            _state.update { current ->
                if (current.latitude != null) current
                else current.copy(
                    latitude = here?.latitude,
                    longitude = here?.longitude,
                    locality = current.locality.ifBlank { area.orEmpty() },
                )
            }
        }
    }

    /** True once the user types their own area name, so we stop overwriting it. */
    private var areaEditedByHand = false
    private var geocodeJob: Job? = null

    /** Editing an existing listing reuses this same form. */
    private var editingId: String? = null

    fun setType(type: ListingType) = _state.update {
        // Dropping to a service clears the condition, which no longer applies.
        it.copy(type = type, condition = if (type == ListingType.SERVICE) null else it.condition)
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setCategory(v: Category) = _state.update { it.copy(category = v) }
    fun setPrice(v: String) = _state.update { it.copy(price = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setCondition(v: Condition) = _state.update { it.copy(condition = v) }

    /** Appends newly-picked photos, silently dropping any past the cap. */
    fun addPhotos(paths: List<String>) = _state.update { s ->
        val room = MAX_LISTING_PHOTOS - s.photos.size
        if (room <= 0) return@update s
        s.copy(photos = s.photos + paths.take(room).map { ListingPhoto.Local(it) })
    }

    fun removePhotoAt(index: Int) = _state.update { s ->
        s.copy(photos = s.photos.filterIndexed { i, _ -> i != index })
    }

    fun setLocality(v: String) {
        areaEditedByHand = true
        _state.update { it.copy(locality = v) }
    }

    /**
     * Moving the pin also renames the area, so the two never disagree. The user
     * can still overwrite the name by typing — [areaEditedByHand] stops us
     * clobbering that on the next drag.
     */
    fun setPin(lat: Double, lng: Double) {
        _state.update { it.copy(latitude = lat, longitude = lng) }
        if (areaEditedByHand) return

        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            // Dragging emits continuously; settle before hitting the geocoder.
            delay(GEOCODE_DEBOUNCE_MS)
            location.areaName(LatLng(lat, lng))?.let { name ->
                if (!areaEditedByHand) _state.update { it.copy(locality = name) }
            }
        }
    }

    fun startEditing(listing: Listing) {
        areaEditedByHand = true
        editingId = listing.id
        _state.value = CreateListingState(
            type = listing.type,
            title = listing.title,
            category = listing.category,
            price = listing.price,
            locality = listing.locality,
            latitude = listing.latitude,
            longitude = listing.longitude,
            condition = listing.condition,
            description = listing.description,
            photos = listing.imageUrls.map { ListingPhoto.Remote(it) },
        )
    }

    /**
     * Validates every field. Returns true and persists when the form is
     * complete; otherwise populates per-field errors and returns false.
     */
    fun submit(onSaved: () -> Unit) {
        val s = _state.value
        val errors = buildMap {
            if (s.title.isBlank()) put(CreateListingState.Field.TITLE, "Give your listing a title")
            if (s.category == null) put(CreateListingState.Field.CATEGORY, "Pick a category")
            if (s.price.isBlank()) {
                put(CreateListingState.Field.PRICE, "Add a ${s.priceLabel.lowercase()}")
            }
            if (s.locality.isBlank()) put(CreateListingState.Field.LOCALITY, "Choose a locality")
            if (s.showCondition && s.condition == null) {
                put(CreateListingState.Field.CONDITION, "Select the item's condition")
            }
            if (s.description.isBlank()) {
                put(CreateListingState.Field.DESCRIPTION, "Describe what you're offering")
            }
        }
        if (errors.isNotEmpty()) {
            _state.value = s.copy(errors = errors)
            return
        }

        val category = s.category ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, submitError = null) }
            val newPhotoParts = s.photos.filterIsInstance<ListingPhoto.Local>()
                .mapIndexed { i, local -> File(local.path).toFormDataPart("photo", "photo_$i.jpg") }
            val keptUrls = s.photos.filterIsInstance<ListingPhoto.Remote>().map { it.url }

            val result = editingId?.let { id ->
                repo.update(
                    id = id,
                    type = s.type,
                    title = s.title.trim(),
                    category = category,
                    price = s.price.trim(),
                    locality = s.locality,
                    latitude = s.latitude,
                    longitude = s.longitude,
                    condition = if (s.showCondition) s.condition else null,
                    description = s.description.trim(),
                    keptImageUrls = keptUrls,
                    newPhotos = newPhotoParts,
                )
            } ?: repo.create(
                type = s.type,
                title = s.title.trim(),
                category = category,
                price = s.price.trim(),
                locality = s.locality,
                latitude = s.latitude,
                longitude = s.longitude,
                condition = if (s.showCondition) s.condition else null,
                description = s.description.trim(),
                photos = newPhotoParts,
            )

            result
                .onSuccess {
                    _state.update { it.copy(isSubmitting = false) }
                    onSaved()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSubmitting = false, submitError = e.toUserMessage()) }
                }
        }
    }

    fun reset() {
        geocodeJob?.cancel()
        areaEditedByHand = false
        editingId = null
        _state.value = CreateListingState()
    }
}
