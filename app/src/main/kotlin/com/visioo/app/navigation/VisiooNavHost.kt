package com.visioo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.visioo.app.feature.account.AccountScreen
import com.visioo.app.feature.account.RecoveryFlowScreen
import com.visioo.app.feature.home.HomeScreen
import com.visioo.app.feature.vip.VipActivationScreen
import com.visioo.app.ui.ComingSoonScreen
import com.visioo.app.ui.MainActions

private const val ROUTE_VIP = "vip"
private const val ROUTE_RECOVERY = "recovery"

@Composable
fun VisiooNavHost(
    navController: NavHostController,
    isTv: Boolean,
    actions: MainActions,
    modifier: Modifier = Modifier,
) {
    NavHost(navController, startDestination = Destination.Home.route, modifier = modifier) {
        composable(Destination.Home.route) {
            // Content details arrive in Phase 4.
            HomeScreen(isTv = isTv, onContentClick = {})
        }
        composable(Destination.Profile.route) {
            AccountScreen(
                onActivateVip = { navController.navigate(ROUTE_VIP) },
                onRecovery = { navController.navigate(ROUTE_RECOVERY) },
                onSwitchProfile = actions.onSwitchProfile,
                onLogout = actions.onLogout,
            )
        }
        composable(ROUTE_VIP) { VipActivationScreen(onDone = { navController.popBackStack() }) }
        composable(ROUTE_RECOVERY) { RecoveryFlowScreen(onFinished = { navController.popBackStack() }) }
        // Remaining destinations are implemented in later phases (see README).
        Destination.entries.filter { it != Destination.Home && it != Destination.Profile }.forEach { destination ->
            composable(destination.route) { ComingSoonScreen() }
        }
    }
}
