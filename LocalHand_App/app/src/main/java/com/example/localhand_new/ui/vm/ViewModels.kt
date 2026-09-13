package com.example.localhand_new.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.example.localhand_new.LocalHandApp
import com.example.localhand_new.data.model.Category
import com.example.localhand_new.data.model.Listing
import com.example.localhand_new.data.model.PublicProfile
import com.example.localhand_new.data.model.SearchFilters
import com.example.localhand_new.data.model.UserProfile
import com.example.localhand_new.data.remote.LocalHandApi
import com.example.localhand_new.data.remote.dto.LoginRequest
import com.example.localhand_new.data.remote.dto.SignupRequest
import com.example.localhand_new.data.remote.dto.toDomain
import com.example.localhand_new.data.remote.toUserMessage
import com.example.localhand_new.data.repo.ListingRepository
import com.example.localhand_new.data.repo.NearbyListing
import com.example.localhand_new.data.repo.rankByDistance
import com.example.localhand_new.data.repo.PreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Debounce for free-text search so typing doesn't fire a request per keystroke. */
private const val SEARCH_DEBOUNCE_MS = 400L

/** Factory shared by every ViewModel, pulling repositories off the Application. */
val LocalHandViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { HomeViewModel(app().listings, app().preferences) }
    initializer { SearchViewModel(app().listings, app().preferences) }
    initializer { ListingDetailViewModel(app().listings) }
    initializer { ProviderProfileViewModel(app().listings) }
    initializer { CreateListingViewModel(app().listings, app().preferences, app().location) }
    initializer { ProfileViewModel(app().listings, app().preferences) }
    initializer { FavouritesViewModel(app().listings) }
    initializer { AssistantViewModel(app().api, app().preferences) }
    initializer { AuthViewModel(app().api, app().preferences) }
    initializer { SignUpViewModel(app().api, app().preferences, app().location) }
}

private fun CreationExtras.app(): LocalHandApp =
    this[APPLICATION_KEY] as LocalHandApp

/**
 * Home: a genuinely-nearest band plus the full recent list.
 *
 * "Near You" ranks by real distance from the pin the user dropped at sign-up,
 * falling back to rating when no pin is set yet.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val user: UserProfile? = null,
    val nearYou: List<NearbyListing> = emptyList(),
    val recent: List<Listing> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val repo: ListingRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    // Seeded from the repo's cache (if any) so a tab switch back to Home
    // paints the previous listings immediately instead of a blank screen
    // while load() below re-fetches in the background.
    private val _state = MutableStateFlow(
        repo.cachedAll()?.let { cached ->
            HomeUiState(
                isLoading = false,
                nearYou = cached.sortedByDescending { it.rating }.rankByDistance(null).take(NEAR_YOU_LIMIT),
                recent = cached,
            )
        } ?: HomeUiState(),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val here = prefs.userLocation.first()
            val profile = prefs.userProfile.first()
            // Applied as soon as it's read from local storage, independent of
            // the network fetch below — the locality/avatar header shouldn't
            // wait on a round trip just because the listings do.
            _state.update { it.copy(user = profile) }
            repo.getAll()
                .onSuccess { listings ->
                    val nearYou = if (here == null) {
                        listings.sortedByDescending { it.rating }.rankByDistance(null)
                    } else {
                        listings.rankByDistance(here)
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            nearYou = nearYou.take(NEAR_YOU_LIMIT),
                            recent = listings,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun toggleFavourite(listing: Listing) = viewModelScope.launch {
        repo.toggleFavourite(listing).onSuccess { load() }
    }

    fun errorShown() = _state.update { it.copy(error = null) }

    private companion object {
        const val NEAR_YOU_LIMIT = 8
    }
}

/**
 * Search: filters compose, the count reflects them live, and results are
 * ordered by how close they are to the user.
 */
data class SearchUiState(
    val isLoading: Boolean = false,
    val filters: SearchFilters = SearchFilters(),
    val results: List<NearbyListing> = emptyList(),
    val error: String? = null,
)

class SearchViewModel(
    private val repo: ListingRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    // Seeded from the repo's cache for the default (no-filter) query, which is
    // what a plain tab switch back to Search re-requests — avoids the
    // "Nothing matches yet" empty state flashing before the refresh lands.
    private val _state = MutableStateFlow(
        repo.cachedAll(SearchFilters())?.let { cached ->
            SearchUiState(isLoading = false, results = cached.rankByDistance(null))
        } ?: SearchUiState(),
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init { runSearch(debounce = false) }

    fun setQuery(query: String) {
        _state.update { it.copy(filters = it.filters.copy(query = query)) }
        runSearch(debounce = true)
    }

    fun setType(type: com.example.localhand_new.data.model.ListingType?) {
        _state.update { it.copy(filters = it.filters.copy(type = type)) }
        runSearch(debounce = false)
    }

    fun setCategory(category: Category?) {
        _state.update { it.copy(filters = it.filters.copy(category = category)) }
        runSearch(debounce = false)
    }

    /** Applies a category carried in from a Home tile. */
    fun applyIncomingCategory(category: Category?) {
        if (category != null && _state.value.filters.category != category) {
            _state.update { it.copy(filters = it.filters.copy(category = category)) }
            runSearch(debounce = false)
        }
    }

    fun toggleFavourite(listing: Listing) = viewModelScope.launch {
        repo.toggleFavourite(listing).onSuccess { runSearch(debounce = false) }
    }

    private fun runSearch(debounce: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(isLoading = true, error = null) }
            val here = prefs.userLocation.first()
            repo.getAll(_state.value.filters)
                .onSuccess { listings ->
                    _state.update { it.copy(isLoading = false, results = listings.rankByDistance(here)) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }
}

data class ListingDetailUiState(
    val isLoading: Boolean = true,
    val listing: Listing? = null,
    val error: String? = null,
)

class ListingDetailViewModel(private val repo: ListingRepository) : ViewModel() {

    private val _state = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = _state.asStateFlow()

    private var currentId: String? = null

    fun load(id: String) {
        currentId = id
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.getById(id)
                .onSuccess { listing -> _state.update { it.copy(isLoading = false, listing = listing) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun toggleFavourite(listing: Listing) = viewModelScope.launch {
        repo.toggleFavourite(listing).onSuccess { currentId?.let { load(it) } }
    }
}

data class ProviderProfileUiState(
    val isLoading: Boolean = true,
    val profile: PublicProfile? = null,
    val listings: List<Listing> = emptyList(),
    val error: String? = null,
)

/** Another user's public profile + their listings, shown from a listing's provider row. */
class ProviderProfileViewModel(private val repo: ListingRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProviderProfileUiState())
    val state: StateFlow<ProviderProfileUiState> = _state.asStateFlow()

    private var currentUserId: String? = null

    fun toggleFavourite(listing: Listing) = viewModelScope.launch {
        repo.toggleFavourite(listing).onSuccess { currentUserId?.let { load(it) } }
    }

    fun load(userId: String) {
        currentUserId = userId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            coroutineScope {
                val profileDeferred = async { repo.getProfile(userId) }
                val listingsDeferred = async { repo.getByOwner(userId) }
                val profileResult = profileDeferred.await()
                val listingsResult = listingsDeferred.await()
                profileResult
                    .onSuccess { profile ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                profile = profile,
                                listings = listingsResult.getOrDefault(emptyList()),
                            )
                        }
                    }
                    .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
            }
        }
    }
}

data class FavouritesUiState(
    val isLoading: Boolean = true,
    val favourites: List<Listing> = emptyList(),
    val error: String? = null,
)

class FavouritesViewModel(private val repo: ListingRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        repo.cachedFavourites()?.let { cached -> FavouritesUiState(isLoading = false, favourites = cached) }
            ?: FavouritesUiState(),
    )
    val state: StateFlow<FavouritesUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.getFavourites()
                .onSuccess { list -> _state.update { it.copy(isLoading = false, favourites = list) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun toggleFavourite(listing: Listing) = viewModelScope.launch {
        repo.toggleFavourite(listing).onSuccess { load() }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: UserProfile? = null,
    val myListings: List<Listing> = emptyList(),
    val favouriteCount: Int = 0,
    val darkMode: Boolean? = null,
    val error: String? = null,
)

class ProfileViewModel(
    private val repo: ListingRepository,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    // Seeded from cache so switching back to Profile shows the last-known
    // listings/favourite count immediately rather than blanking the stats
    // tiles and list while load() re-fetches both in the background.
    private val _state = MutableStateFlow(
        ProfileUiState(
            isLoading = repo.cachedMine() == null && repo.cachedFavourites() == null,
            myListings = repo.cachedMine().orEmpty(),
            favouriteCount = repo.cachedFavourites().orEmpty().size,
        ),
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            prefs.darkMode.collectLatest { dark -> _state.update { it.copy(darkMode = dark) } }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val profile = prefs.userProfile.first()
            // Applied immediately — the name/locality/rating header shouldn't
            // wait on the listings/favourites network round trip below.
            _state.update { it.copy(user = profile) }
            coroutineScope {
                val mineDeferred = async { repo.getMine() }
                val favesDeferred = async { repo.getFavourites() }
                val mine = mineDeferred.await()
                val faves = favesDeferred.await()
                if (mine.isSuccess && faves.isSuccess) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            myListings = mine.getOrDefault(emptyList()),
                            favouriteCount = faves.getOrDefault(emptyList()).size,
                        )
                    }
                } else {
                    val error = mine.exceptionOrNull() ?: faves.exceptionOrNull()
                    _state.update { it.copy(isLoading = false, error = error?.toUserMessage()) }
                }
            }
        }
    }

    fun setDarkMode(dark: Boolean) = viewModelScope.launch { prefs.setDarkMode(dark) }

    fun delete(listing: Listing) = viewModelScope.launch {
        repo.delete(listing.id)
            .onSuccess { load() }
            .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
    }

    fun signOut() = viewModelScope.launch { prefs.setAuthToken(null) }
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class AuthViewModel(
    private val api: LocalHandApi,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    fun completeOnboarding() = viewModelScope.launch { prefs.setOnboardingSeen(true) }

    fun logIn(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            runCatching { api.login(LoginRequest(email, password)) }
                .onSuccess { response ->
                    prefs.setAuthToken(response.token)
                    prefs.saveProfile(response.user.toDomain())
                    _loginState.value = LoginUiState.Idle
                    onSuccess()
                }
                .onFailure { e -> _loginState.value = LoginUiState.Error(e.toUserMessage()) }
        }
    }
}
