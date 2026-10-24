package com.visioo.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.visioo.app.R

enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Home("home", R.string.nav_home, Icons.Default.Home),
    Explore("explore", R.string.nav_explore, Icons.AutoMirrored.Filled.List),
    Movies("movies", R.string.nav_movies, Icons.Default.PlayArrow),
    Series("series", R.string.nav_series, Icons.AutoMirrored.Filled.List),
    Search("search", R.string.nav_search, Icons.Default.Search),
    Watchlist("watchlist", R.string.nav_watchlist, Icons.Default.Add),
    Profile("profile", R.string.nav_profile, Icons.Default.Person),
    Settings("settings", R.string.nav_settings, Icons.Default.Settings),
    ;

    companion object {
        /** Bottom bar on phones and tablets. Search is reached from the header. */
        val Mobile = listOf(Home, Explore, Watchlist, Profile)

        /** Left rail on Android TV. */
        val Tv = listOf(Home, Movies, Series, Search, Watchlist, Profile, Settings)
    }
}
