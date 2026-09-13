package com.example.localhand_new.ui.views

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.example.localhand_new.R
import com.example.localhand_new.data.location.LatLng
import com.example.localhand_new.data.location.distanceMetres
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/**
 * Map pin picker backed by a real OpenStreetMap [MapView] (via osmdroid — no
 * API key or Google Play Services needed, unlike Maps SDK for Android). The
 * pin is drawn fixed at the view's centre; dragging the map underneath it is
 * how the user repositions the pin, which is the standard "drop pin here"
 * pattern and avoids implementing marker drag-hit-testing ourselves.
 *
 * Keeps the exact contract the rest of the app already codes against
 * ([center], [onPinMoved], [onUseMyLocation], [isLocating]), so callers
 * (SignUpFragment, CreateListingFragment) needed no changes when this
 * replaced the earlier Canvas-drawn grid placeholder.
 *
 * osmdroid's [MapView] needs lifecycle callbacks or it leaks tile-loading
 * threads — call [onResume]/[onPause] from the hosting Fragment's own
 * matching callbacks, and [onDetach] from onDestroyView.
 */
class MapPinPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Below this, a `center` change is treated as our own echo, not a jump. */
    private val recentreThresholdMetres = 2_000.0

    /** Neighbourhood-scale default zoom; matches the old grid's implied span. */
    private val defaultZoom = 16.0

    private var anchor: LatLng? = null

    /** Guards the map-move listener while we're moving the map programmatically. */
    private var isProgrammaticMove = false

    var onPinMoved: ((LatLng) -> Unit)? = null

    /** Shown as a pill bottom-right when set; tapped fires this callback. */
    var onUseMyLocation: (() -> Unit)? = null
        set(value) {
            field = value
            locationPill.isVisible = value != null
        }

    var isLocating: Boolean = false
        set(value) {
            field = value
            locationPillLabel.text = if (value) "Locating…" else "Use my location"
        }

    /**
     * The point the map is centred on. Setting this re-anchors only when the
     * caller jumps somewhere genuinely new (a GPS fix); setting it again with
     * a nearby point — e.g. our own [onPinMoved] echo reflected back through
     * the caller's state — is a no-op, so it never fights an in-progress drag.
     */
    var center: LatLng?
        get() = anchor
        set(value) {
            val here = value ?: return
            val anchored = anchor
            val movedFar = anchored == null ||
                distanceMetres(anchored, here) > recentreThresholdMetres
            if (movedFar) {
                anchor = here
                isProgrammaticMove = true
                mapView.controller.setZoom(defaultZoom)
                mapView.controller.setCenter(GeoPoint(here.latitude, here.longitude))
                isProgrammaticMove = false
            }
        }

    private val mapView: MapView = MapView(context)
    private val locationPill: View
    private val locationPillLabel: TextView

    init {
        ensureOsmdroidConfigured(context)

        mapView.setTileSource(OsmDeTileSource)
        mapView.setMultiTouchControls(true)
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        mapView.minZoomLevel = 4.0
        mapView.maxZoomLevel = 19.0
        mapView.controller.setZoom(defaultZoom)
        addView(mapView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (!isProgrammaticMove) reportCenter()
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean = false
        })

        addView(buildCentrePin(context), buildCentrePinParams(context))
        addView(buildHint(context), buildHintParams(context))

        locationPillLabel = TextView(context).apply {
            text = "Use my location"
            setTextColor(ContextCompat.getColor(context, R.color.lh_text))
            textSize = pxToSp(resources.getDimension(R.dimen.lh_text_label))
        }
        locationPill = buildLocationPill(context, locationPillLabel)
        addView(locationPill, buildLocationPillParams(context))

        outlineProvider = roundedOutline(resources.getDimension(R.dimen.lh_radius_field))
        clipToOutline = true
    }

    private fun buildCentrePin(context: Context): ImageView = ImageView(context).apply {
        setImageDrawable(
            ResourcesCompat.getDrawable(resources, R.drawable.ic_place, null)?.mutate()?.apply {
                setTint(ContextCompat.getColor(context, R.color.lh_accent))
            },
        )
    }

    private fun buildCentrePinParams(context: Context): LayoutParams {
        val size = resources.getDimensionPixelSize(R.dimen.lh_icon_size_large)
        // The drawable's own visual "point" sits at the bottom centre, so the
        // pin's tip — not its bounding box centre — marks the picked
        // coordinate, matching the map's true centre underneath it.
        return LayoutParams(size, size, Gravity.CENTER).apply { bottomMargin = size / 2 }
    }

    private fun buildHint(context: Context): TextView {
        val pad = resources.getDimensionPixelSize(R.dimen.lh_gap_small)
        return TextView(context).apply {
            text = "Drag the map to place your pin"
            setTextColor(ContextCompat.getColor(context, R.color.lh_faint))
            textSize = pxToSp(resources.getDimension(R.dimen.lh_text_label))
            setPadding(pad, pad, pad, pad)
            background = pillBackground(context)
        }
    }

    private fun buildHintParams(context: Context): LayoutParams {
        val m = resources.getDimensionPixelSize(R.dimen.lh_gap_small)
        return LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ).apply {
            topMargin = m
            marginStart = m
        }
    }

    private fun buildLocationPill(context: Context, label: TextView): LinearLayout {
        val icon = ImageView(context).apply {
            setImageDrawable(
                ResourcesCompat.getDrawable(resources, R.drawable.ic_my_location, null)
                    ?.mutate()?.apply { setTint(ContextCompat.getColor(context, R.color.lh_accent)) },
            )
            val size = resources.getDimensionPixelSize(R.dimen.lh_icon_size_small)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = resources.getDimensionPixelSize(R.dimen.lh_gap)
            val padV = resources.getDimensionPixelSize(R.dimen.lh_gap_nudge)
            setPadding(padH, padV, padH, padV)
            background = pillBackground(context)
            isVisible = false
            addView(icon)
            addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = resources.getDimensionPixelSize(R.dimen.lh_gap_tiny) },
            )
            setOnClickListener { if (!isLocating) onUseMyLocation?.invoke() }
        }
    }

    private fun buildLocationPillParams(context: Context): LayoutParams {
        val m = resources.getDimensionPixelSize(R.dimen.lh_gap_small)
        return LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END,
        ).apply {
            bottomMargin = m
            marginEnd = m
        }
    }

    private fun pillBackground(context: Context) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = resources.getDimension(R.dimen.lh_radius_pill)
        setColor(ContextCompat.getColor(context, R.color.lh_surface))
        setStroke(
            resources.getDimensionPixelSize(R.dimen.lh_hairline),
            ContextCompat.getColor(context, R.color.lh_border),
        )
    }

    private fun roundedOutline(radius: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    private fun pxToSp(px: Float) = px / resources.displayMetrics.scaledDensity

    private fun reportCenter() {
        val target = mapView.mapCenter
        val point = LatLng(target.latitude, target.longitude)
        anchor = point
        onPinMoved?.invoke(point)
    }

    /** Forward the hosting Fragment's onResume so osmdroid resumes tile loading. */
    fun onResume() {
        mapView.onResume()
    }

    /** Forward the hosting Fragment's onPause so osmdroid stops tile loading cleanly. */
    fun onPause() {
        mapView.onPause()
    }

    /** Forward the hosting Fragment's onDestroyView so osmdroid releases its tile cache. */
    fun onDetach() {
        mapView.onDetach()
    }

    private companion object {
        private var configured = false

        /**
         * OpenStreetMap Germany's tile server: free, no API key, standard OSM
         * cartography. Used instead of osmdroid's default
         * TileSourceFactory.MAPNIK (tile.openstreetmap.org), whose usage
         * policy forbids embedding in apps and 403s "Access blocked" once
         * traffic is identified as app-embedded, regardless of User-Agent.
         * CartoDB's Voyager basemap was tried first but now gates its style
         * behind an API key ("API KEY REQUIRED" watermark on every tile).
         */
        private val OsmDeTileSource = XYTileSource(
            "OpenStreetMapDe",
            0,
            19,
            256,
            ".png",
            arrayOf(
                "https://a.tile.openstreetmap.de/",
                "https://b.tile.openstreetmap.de/",
                "https://c.tile.openstreetmap.de/",
            ),
            "© OpenStreetMap contributors",
        )

        /**
         * osmdroid's global Configuration must be loaded before any MapView is
         * constructed; LocalHandApp does this at startup, but this is a cheap
         * idempotent safety net for any other entry point that constructs a
         * MapPinPicker directly.
         */
        fun ensureOsmdroidConfigured(context: Context) {
            if (configured) return
            configured = true
            val cfg = Configuration.getInstance()
            if (cfg.userAgentValue.isNullOrBlank()) {
                cfg.load(
                    context.applicationContext,
                    context.applicationContext.getSharedPreferences(
                        "osmdroid_config",
                        Context.MODE_PRIVATE,
                    ),
                )
                cfg.userAgentValue = context.packageName
            }
        }
    }
}
