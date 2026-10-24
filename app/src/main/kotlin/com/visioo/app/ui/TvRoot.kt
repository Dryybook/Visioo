package com.visioo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.visioo.app.navigation.Destination
import com.visioo.app.navigation.VisiooNavHost
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooSpacing
import com.visioo.core.designsystem.visiooFocus

@Composable
fun TvRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Row(Modifier.fillMaxSize().background(VisiooColors.Black)) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(88.dp)
                .background(VisiooColors.Surface)
                .padding(vertical = VisiooSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(VisiooSpacing.Md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Destination.Tv.forEach { destination ->
                val selected = currentRoute == destination.route
                Box(
                    Modifier
                        .size(56.dp)
                        .visiooFocus(CircleShape)
                        .clip(CircleShape)
                        .background(if (selected) VisiooColors.SurfaceElevated else VisiooColors.Surface)
                        .clickable(role = Role.Tab) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        destination.icon,
                        contentDescription = stringResource(destination.label),
                        tint = if (selected) VisiooColors.VioletBright else VisiooColors.TextSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        VisiooNavHost(navController, isTv = true, modifier = Modifier.weight(1f))
    }
}
