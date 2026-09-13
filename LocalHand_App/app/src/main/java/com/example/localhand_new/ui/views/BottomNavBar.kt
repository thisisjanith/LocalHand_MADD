package com.example.localhand_new.ui.views

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.example.localhand_new.R

/** The four tab destinations. Favourites is deliberately not among them. */
enum class NavTab(val destinationId: Int) {
    HOME(R.id.homeFragment),
    SEARCH(R.id.searchFragment),
    ASSISTANT(R.id.assistantFragment),
    PROFILE(R.id.profileFragment),
}

/**
 * Thin controller over the `view_bottom_nav_bar.xml` include: wires the four
 * tab slots and the centre FAB, and toggles selected/unselected tint since
 * plain ImageView/TextView aren't natively Checkable (see CLAUDE.md brief —
 * we toggle colour programmatically rather than build a custom Checkable
 * layout).
 */
class BottomNavBar(private val root: View) {

    // view_bottom_nav_bar.xml is a <merge>, so its top-level children were
    // inflated directly into the activity's root ConstraintLayout — there is
    // no single wrapper view for the bar as a whole, so visibility toggles
    // every merged top-level view individually.
    private val divider = root.findViewById<View>(R.id.nav_bar_divider)
    private val surface = root.findViewById<View>(R.id.nav_bar_surface)
    private val fabRing = root.findViewById<View>(R.id.nav_fab_ring)

    private val homeTab = root.findViewById<View>(R.id.nav_tab_home)
    private val searchTab = root.findViewById<View>(R.id.nav_tab_search)
    private val assistantTab = root.findViewById<View>(R.id.nav_tab_assistant)
    private val profileTab = root.findViewById<View>(R.id.nav_tab_profile)
    private val fab = root.findViewById<View>(R.id.nav_fab_create)

    private val homeIcon = root.findViewById<android.widget.ImageView>(R.id.nav_tab_home_icon)
    private val searchIcon = root.findViewById<android.widget.ImageView>(R.id.nav_tab_search_icon)
    private val assistantIcon =
        root.findViewById<android.widget.ImageView>(R.id.nav_tab_assistant_icon)
    private val profileIcon = root.findViewById<android.widget.ImageView>(R.id.nav_tab_profile_icon)

    private val homeLabel = root.findViewById<android.widget.TextView>(R.id.nav_tab_home_label)
    private val searchLabel = root.findViewById<android.widget.TextView>(R.id.nav_tab_search_label)
    private val assistantLabel =
        root.findViewById<android.widget.TextView>(R.id.nav_tab_assistant_label)
    private val profileLabel = root.findViewById<android.widget.TextView>(R.id.nav_tab_profile_label)

    private val accent = ContextCompat.getColor(root.context, R.color.lh_accent)
    private val muted = ContextCompat.getColor(root.context, R.color.lh_muted)

    private val navBarHeight = root.resources.getDimensionPixelSize(R.dimen.lh_nav_bar_height)
    private val fabLift = root.resources.getDimensionPixelSize(R.dimen.lh_fab_lift)

    /**
     * Grows the bar (and lifts the FAB riding above it) by the device's
     * bottom system-bar inset (gesture handle / 3-button nav), so edge-to-edge
     * drawing (see MainActivity's setDecorFitsSystemWindows(false)) doesn't
     * leave them drawn underneath it. The extra space becomes bottom padding
     * inside the bar — tab content stays where it was, just with breathing
     * room below it instead of being squashed. Called once from the
     * activity's own inset listener with the raw inset in pixels.
     */
    fun applyBottomInset(insetPx: Int) {
        surface.updateLayoutParams<android.view.ViewGroup.LayoutParams> {
            height = navBarHeight + insetPx
        }
        surface.updatePadding(bottom = insetPx)
        fabRing.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            bottomMargin = fabLift + insetPx
        }
    }

    fun setVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        divider.visibility = visibility
        surface.visibility = visibility
        fabRing.visibility = visibility
    }

    fun setOnTabSelected(onSelect: (NavTab) -> Unit) {
        homeTab.setOnClickListener { onSelect(NavTab.HOME) }
        searchTab.setOnClickListener { onSelect(NavTab.SEARCH) }
        assistantTab.setOnClickListener { onSelect(NavTab.ASSISTANT) }
        profileTab.setOnClickListener { onSelect(NavTab.PROFILE) }
    }

    fun setOnCreateClicked(onCreate: () -> Unit) {
        fab.setOnClickListener { onCreate() }
    }

    fun setSelected(tab: NavTab) {
        val icons = mapOf(
            NavTab.HOME to homeIcon,
            NavTab.SEARCH to searchIcon,
            NavTab.ASSISTANT to assistantIcon,
            NavTab.PROFILE to profileIcon,
        )
        val labels = mapOf(
            NavTab.HOME to homeLabel,
            NavTab.SEARCH to searchLabel,
            NavTab.ASSISTANT to assistantLabel,
            NavTab.PROFILE to profileLabel,
        )
        NavTab.entries.forEach { t ->
            val color = if (t == tab) accent else muted
            icons.getValue(t).setColorFilter(color)
            labels.getValue(t).setTextColor(color)
        }
    }
}
