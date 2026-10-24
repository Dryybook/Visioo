package com.visioo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.visioo.app.R
import com.visioo.app.feature.account.AccountScreen
import com.visioo.app.feature.account.RecoveryFlowScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.visioo.app.data.ContentType
import com.visioo.app.feature.details.DetailsScreen
import com.visioo.app.feature.explore.ExploreScreen
import com.visioo.app.feature.home.HomeScreen
import com.visioo.app.feature.player.PlayKind
import com.visioo.app.feature.player.PlayerScreen
import com.visioo.app.feature.search.SearchScreen
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
        val openDetails = { item: com.visioo.app.data.ContentSummary -> navController.navigate("details/${item.type.path}/${item.id}") }
        composable(Destination.Home.route) { HomeScreen(isTv = isTv, onContentClick = openDetails) }
        composable(Destination.Explore.route) { ExploreScreen(ContentType.Movie, isTv, openDetails) }
        composable(Destination.Movies.route) { ExploreScreen(ContentType.Movie, isTv, openDetails) }
        composable(Destination.Series.route) { ExploreScreen(ContentType.Series, isTv, openDetails) }
        composable(Destination.Search.route) { SearchScreen(isTv, openDetails) }
        composable(
            "details/{type}/{id}",
            arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val type = ContentType.fromPath(entry.arguments?.getString("type").orEmpty())
            val id = entry.arguments?.getString("id").orEmpty()
            if (type != null && id.isNotEmpty()) {
                DetailsScreen(
                    type = type, id = id, isTv = isTv,
                    onPlayMovie = { navController.navigate("player/movie/$it") },
                    onPlayEpisode = { navController.navigate("player/episode/$it") },
                    onActivateVip = { navController.navigate(ROUTE_VIP) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            "player/{kind}/{id}",
            arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val kind = PlayKind.fromPath(entry.arguments?.getString("kind").orEmpty())
            val id = entry.arguments?.getString("id").orEmpty()
            if (kind != null && id.isNotEmpty()) {
                PlayerScreen(
                    kind = kind, id = id, isTv = isTv,
                    onBack = { navController.popBackStack() },
                    // Autoplay replaces the finished episode instead of stacking players on the back stack.
                    onPlayEpisode = { next -> navController.navigate("player/episode/$next") { popUpTo(entry.destination.id) { inclusive = true } } },
                    onActivateVip = { navController.navigate(ROUTE_VIP) },
                )
            }
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
        Destination.entries.filter { it in setOf(Destination.Watchlist, Destination.Settings) }.forEach { destination ->
            composable(destination.route) { ComingSoonScreen(R.string.coming_soon) }
        }
    }
}
