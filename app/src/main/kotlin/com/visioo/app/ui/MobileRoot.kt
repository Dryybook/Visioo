package com.visioo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.visioo.app.navigation.Destination
import com.visioo.app.navigation.VisiooNavHost
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooSpacing

@Composable
fun MobileRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    fun go(destination: Destination) = navController.navigate(destination.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Column(Modifier.fillMaxSize().background(VisiooColors.Black)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = VisiooSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("VISIOO", color = VisiooColors.VioletBright, fontSize = 22.sp, fontWeight = FontWeight.Black)
            IconButton(onClick = { go(Destination.Search) }) {
                Icon(
                    Destination.Search.icon,
                    contentDescription = stringResource(Destination.Search.label),
                    tint = VisiooColors.TextPrimary,
                )
            }
        }
        VisiooNavHost(navController, isTv = false, modifier = Modifier.weight(1f))
        NavigationBar(containerColor = VisiooColors.Surface) {
            Destination.Mobile.forEach { destination ->
                NavigationBarItem(
                    selected = currentRoute == destination.route,
                    onClick = { go(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.label)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VisiooColors.VioletBright,
                        selectedTextColor = VisiooColors.VioletBright,
                        indicatorColor = VisiooColors.SurfaceElevated,
                        unselectedIconColor = VisiooColors.TextMuted,
                        unselectedTextColor = VisiooColors.TextMuted,
                    ),
                )
            }
        }
    }
}
