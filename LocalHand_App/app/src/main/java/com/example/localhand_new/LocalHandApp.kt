package com.example.localhand_new

import android.app.Application
import com.example.localhand_new.data.location.LocationProvider
import com.example.localhand_new.data.remote.ApiClient
import com.example.localhand_new.data.remote.LocalHandApi
import com.example.localhand_new.data.repo.ListingRepository
import com.example.localhand_new.data.repo.PreferencesRepository
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/**
 * Minimal service locator. The prototype has few dependencies, so a DI
 * framework would add more ceremony than it removes.
 */
class LocalHandApp : Application() {

    lateinit var api: LocalHandApi
        private set
    lateinit var listings: ListingRepository
        private set
    lateinit var preferences: PreferencesRepository
        private set
    lateinit var location: LocationProvider
        private set

    override fun onCreate() {
        super.onCreate()

        // osmdroid requires a distinct user agent (OSM's tile servers block
        // the default one) and a configured cache path before any MapView
        // is created — set once here rather than per-view. TileSourceFactory
        // is referenced so MapPinPicker's default tile source (Mapnik) is
        // guaranteed initialised before first use.
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid_config", MODE_PRIVATE),
        )
        Configuration.getInstance().userAgentValue = packageName
        TileSourceFactory.MAPNIK.name()

        preferences = PreferencesRepository(this)
        api = ApiClient.create(preferences)
        listings = ListingRepository(api)
        location = LocationProvider(this)
    }
}
