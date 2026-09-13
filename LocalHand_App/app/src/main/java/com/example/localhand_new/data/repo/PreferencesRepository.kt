package com.example.localhand_new.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localhand_new.data.location.LatLng
import com.example.localhand_new.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "localhand_prefs")

/** Auth session (JWT), onboarding gate, a local mirror of the profile, and dark mode. */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val onboardingSeen = booleanPreferencesKey("onboarding_seen")
        val authToken = stringPreferencesKey("auth_token")
        val darkMode = booleanPreferencesKey("dark_mode")
        val userName = stringPreferencesKey("user_name")
        val userEmail = stringPreferencesKey("user_email")
        val userPhone = stringPreferencesKey("user_phone")
        val userLocality = stringPreferencesKey("user_locality")
        val userLat = doublePreferencesKey("user_lat")
        val userLng = doublePreferencesKey("user_lng")
        val memberSince = intPreferencesKey("member_since")
        val userRating = doublePreferencesKey("user_rating")
    }

    /** A fast local mirror of the last server response — never written speculatively. */
    val userProfile: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.userName]
        val email = prefs[Keys.userEmail]
        if (name == null || email == null) return@map null
        UserProfile(
            name = name,
            email = email,
            phone = prefs[Keys.userPhone].orEmpty(),
            locality = prefs[Keys.userLocality].orEmpty(),
            memberSince = prefs[Keys.memberSince] ?: 0,
            rating = prefs[Keys.userRating] ?: 0.0,
        )
    }

    /**
     * The pin the user dropped at sign-up. Everything the app shows is ranked
     * by distance from here, so this is the anchor for the whole experience.
     */
    val userLocation: Flow<LatLng?> = context.dataStore.data.map { prefs ->
        val lat = prefs[Keys.userLat]
        val lng = prefs[Keys.userLng]
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }

    val onboardingSeen: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.onboardingSeen] ?: false }

    val authToken: Flow<String?> = context.dataStore.data.map { it[Keys.authToken] }

    val isLoggedIn: Flow<Boolean> = authToken.map { !it.isNullOrBlank() }

    /** Null until the user chooses, so the system setting applies by default. */
    val darkMode: Flow<Boolean?> =
        context.dataStore.data.map { it[Keys.darkMode] }

    val userName: Flow<String?> = context.dataStore.data.map { it[Keys.userName] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[Keys.userEmail] }
    val userLocality: Flow<String?> = context.dataStore.data.map { it[Keys.userLocality] }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[Keys.onboardingSeen] = seen }
    }

    suspend fun setAuthToken(token: String?) {
        context.dataStore.edit {
            if (token.isNullOrBlank()) it.remove(Keys.authToken) else it[Keys.authToken] = token
        }
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.dataStore.edit { it[Keys.darkMode] = dark }
    }

    /** Called after every successful login/signup/me response. */
    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit {
            it[Keys.userName] = profile.name
            it[Keys.userEmail] = profile.email
            it[Keys.userPhone] = profile.phone
            it[Keys.userLocality] = profile.locality
            it[Keys.memberSince] = profile.memberSince
            it[Keys.userRating] = profile.rating
        }
    }

    /** Lets the user re-anchor later without going through sign-up again. */
    suspend fun setUserLocation(location: LatLng, locality: String?) {
        context.dataStore.edit {
            it[Keys.userLat] = location.latitude
            it[Keys.userLng] = location.longitude
            if (locality != null) it[Keys.userLocality] = locality
        }
    }
}
