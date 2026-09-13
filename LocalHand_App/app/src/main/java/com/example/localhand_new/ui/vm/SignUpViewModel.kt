package com.example.localhand_new.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localhand_new.data.location.LatLng
import com.example.localhand_new.data.location.LocationProvider
import com.example.localhand_new.data.remote.LocalHandApi
import com.example.localhand_new.data.remote.dto.SignupRequest
import com.example.localhand_new.data.remote.dto.toDomain
import com.example.localhand_new.data.remote.toUserMessage
import com.example.localhand_new.data.repo.PreferencesRepository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Dragging emits continuously; settle before hitting the geocoder. */
private const val GEOCODE_DEBOUNCE_MS = 400L

/**
 * Sign-up state. Location accuracy matters here because the pin the user drops
 * becomes the anchor for every "near you" ranking in the app.
 */
data class SignUpState(
    val pin: LatLng? = null,
    val areaName: String = "",
    val isLocating: Boolean = false,
    /** Set when we asked for a fix and could not get one. */
    val locationError: String? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
)

class SignUpViewModel(
    private val api: LocalHandApi,
    private val prefs: PreferencesRepository,
    private val location: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpState())
    val state: StateFlow<SignUpState> = _state.asStateFlow()

    /** True once the user types their own area name, so we stop overwriting it. */
    private var areaEditedByHand = false
    private var geocodeJob: Job? = null

    fun hasLocationPermission() = location.hasPermission()

    /**
     * Called once permission is settled. Centres the pin on the device and
     * names the area, so most users never have to touch the map.
     */
    fun locateMe() {
        if (_state.value.isLocating) return
        _state.update { it.copy(isLocating = true, locationError = null) }

        viewModelScope.launch {
            val point = location.current()
            if (point == null) {
                _state.update {
                    it.copy(
                        isLocating = false,
                        locationError = "Couldn't get your location. " +
                            "Drop a pin on the map instead.",
                    )
                }
                return@launch
            }
            // An explicit "use my location" overrides anything typed earlier.
            val name = location.areaName(point)
            areaEditedByHand = false
            _state.update {
                it.copy(
                    pin = point,
                    areaName = name ?: it.areaName,
                    isLocating = false,
                )
            }
        }
    }

    /** The user moved the pin by hand; re-derive the area name from it. */
    fun movePin(point: LatLng) {
        _state.update { it.copy(pin = point, locationError = null) }
        if (areaEditedByHand) return

        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            // Dragging emits continuously; settle before hitting the geocoder.
            delay(GEOCODE_DEBOUNCE_MS)
            location.areaName(point)?.let { name ->
                if (!areaEditedByHand) _state.update { it.copy(areaName = name) }
            }
        }
    }

    /** Typing a name wins: later pin moves stop renaming it. */
    fun setAreaName(name: String) {
        areaEditedByHand = true
        _state.update { it.copy(areaName = name) }
    }

    fun permissionDenied() = _state.update {
        it.copy(
            isLocating = false,
            locationError = "Location is off. Drop a pin on the map to set your area.",
        )
    }

    fun signUp(name: String, email: String, password: String, phone: String, onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, submitError = null) }
            runCatching {
                api.signup(
                    SignupRequest(
                        name = name,
                        email = email,
                        password = password,
                        phone = phone,
                        locality = s.areaName.ifBlank { "Your Area" },
                        latitude = s.pin?.latitude,
                        longitude = s.pin?.longitude,
                    ),
                )
            }
                .onSuccess { response ->
                    prefs.setAuthToken(response.token)
                    prefs.saveProfile(response.user.toDomain())
                    _state.update { it.copy(isSubmitting = false) }
                    onDone()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSubmitting = false, submitError = e.toUserMessage()) }
                }
        }
    }
}
