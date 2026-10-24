package com.visioo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.visioo.app.feature.home.HomeScreen
import com.visioo.app.ui.ComingSoonScreen

@Composable
fun VisiooNavHost(
    navController: NavHostController,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(navController, startDestination = Destination.Home.route, modifier = modifier) {
        composable(Destination.Home.route) {
            // Content details arrive in Phase 4.
            HomeScreen(isTv = isTv, onContentClick = {})
        }
        // Remaining destinations are implemented in later phases (see README).
        Destination.entries.filter { it != Destination.Home }.forEach { destination ->
            composable(destination.route) { ComingSoonScreen() }
        }
    }
}
