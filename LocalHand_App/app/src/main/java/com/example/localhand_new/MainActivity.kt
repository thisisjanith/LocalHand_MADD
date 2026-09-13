package com.example.localhand_new

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.example.localhand_new.ui.views.BottomNavBar
import com.example.localhand_new.ui.views.NavTab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single Activity hosting a [NavHostFragment]. Start destination is resolved
 * once at startup from the onboarding/logged-in DataStore flags (mirroring
 * the Compose version's "wait for both flags before composing NavHost" gate),
 * and dark mode is applied via [AppCompatDelegate] before the first frame.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var bottomNavBar: BottomNavBar
    private lateinit var navHost: View

    /** Base bar height only — applyBottomInset already folds the system inset
     *  into the bar's own grown height, so it's added again here to size the
     *  content padding to the bar's true total on-screen height. */
    private var lastBottomInset = 0
    private var barCurrentlyVisible = false

    /** Destinations that show the bottom navigation bar. */
    private val barDestinations = setOf(
        R.id.homeFragment,
        R.id.searchFragment,
        R.id.assistantFragment,
        R.id.profileFragment,
        R.id.favouritesFragment,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Applied before super.onCreate so the very first frame is already in
        // the right mode.
        val app = application as LocalHandApp

        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        bottomNavBar = BottomNavBar(findViewById(R.id.root))
        bottomNavBar.setOnTabSelected { tab -> switchTab(tab) }
        bottomNavBar.setOnCreateClicked {
            navController.navigate(R.id.createListingFragment)
        }

        // Edge-to-edge (setDecorFitsSystemWindows(false) above) means nothing
        // reserves space for the device's own gesture handle / 3-button nav
        // bar by default — without this, our bottom bar and its FAB draw
        // underneath it instead of sitting flush above it.
        //
        // Every fragment root also declares fitsSystemWindows="true" (for the
        // *top* status-bar inset), but that alone would only pad each
        // fragment's content by the raw system inset — not by our own bottom
        // nav bar's actual height, which is taller and grows by that same
        // inset again. Left alone, fragment content (e.g. Home's
        // NestedScrollView, which fills the whole screen) draws underneath
        // and even in front of the FAB near the bar's top edge. So the bottom
        // inset is consumed here instead, and nav_host_fragment gets bottom
        // padding sized to the bar's real total height, guaranteeing content
        // never extends behind it. Top/side insets still propagate down
        // unchanged for each fragment's own fitsSystemWindows to consume.
        navHost = findViewById(R.id.nav_host_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            lastBottomInset = systemBars.bottom
            bottomNavBar.applyBottomInset(systemBars.bottom)
            applyNavHostBottomPadding()
            WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(systemBars.left, systemBars.top, systemBars.right, 0),
                )
                .build()
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBar = destination.id in barDestinations
            bottomNavBar.setVisible(showBar)
            barCurrentlyVisible = showBar
            applyNavHostBottomPadding()
            if (showBar) {
                // Favourites keeps Profile shown as the active tab.
                val selected = when (destination.id) {
                    R.id.searchFragment -> NavTab.SEARCH
                    R.id.assistantFragment -> NavTab.ASSISTANT
                    R.id.profileFragment, R.id.favouritesFragment -> NavTab.PROFILE
                    else -> NavTab.HOME
                }
                bottomNavBar.setSelected(selected)
            }
        }

        // Resolve dark mode once at startup: null preference falls back to
        // following the system setting.
        lifecycleScope.launch {
            val darkPreference = app.preferences.darkMode.first()
            AppCompatDelegate.setDefaultNightMode(
                when (darkPreference) {
                    true -> AppCompatDelegate.MODE_NIGHT_YES
                    false -> AppCompatDelegate.MODE_NIGHT_NO
                    null -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                },
            )
        }

        // Gate the start destination on the stored flags, same as the Compose
        // version waiting for both to be non-null before composing NavHost.
        // Runs once at cold start only — the graph is already set to
        // onboardingFragment by the nav host's own inflation, so nothing to do
        // when that's genuinely the right start destination.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val onboardingSeen = app.preferences.onboardingSeen.first()
                val loggedIn = app.preferences.isLoggedIn.first()
                val start = when {
                    !onboardingSeen -> R.id.onboardingFragment
                    !loggedIn -> R.id.loginFragment
                    else -> R.id.homeFragment
                }
                if (start != R.id.onboardingFragment) {
                    val graph = navController.navInflater.inflate(R.navigation.nav_graph)
                    graph.setStartDestination(start)
                    navController.graph = graph
                }
            }
        }
    }

    /** Reserves exactly the bar's current on-screen height as bottom padding
     *  on the fragment host so no fragment's content can ever draw behind it
     *  — zero when the bar is hidden (e.g. login, listing detail). */
    private fun applyNavHostBottomPadding() {
        val navBarHeight = resources.getDimensionPixelSize(R.dimen.lh_nav_bar_height)
        navHost.updatePadding(
            bottom = if (barCurrentlyVisible) navBarHeight + lastBottomInset else 0,
        )
    }

    private fun switchTab(tab: NavTab) {
        val options = navOptions {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        navController.navigate(tab.destinationId, null, options)
    }
}
