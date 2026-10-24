package com.visioo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.visioo.app.VisiooApplication
import com.visioo.app.feature.account.RecoveryFlowScreen
import com.visioo.app.feature.auth.LoginScreen
import com.visioo.app.feature.auth.OnboardingScreen
import com.visioo.app.feature.auth.RecoverScreen
import com.visioo.app.feature.auth.RegisterScreen
import com.visioo.app.feature.profiles.ProfileSelectScreen
import com.visioo.app.feature.vip.VipActivationScreen
import com.visioo.app.data.session.SessionState
import com.visioo.core.designsystem.VisiooColors
import com.visioo.core.designsystem.VisiooMotion
import com.visioo.core.designsystem.VisiooWordmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActions(val onSwitchProfile: () -> Unit, val onLogout: () -> Unit)

@Composable
fun VisiooApp(isTv: Boolean) {
    val session = (LocalContext.current.applicationContext as VisiooApplication).container.sessionRepository
    val state by session.state.collectAsStateWithLifecycle()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(900)
        splashDone = true
    }

    val current = state
    when {
        !splashDone -> SplashScreen()
        current is SessionState.LoggedOut -> AuthNav(isTv)
        current is SessionState.LoggedIn && current.setupPending -> SetupFlow(onFinished = session::completeSetup)
        current is SessionState.LoggedIn && !current.profileSelected -> ProfileSelectScreen(
            onSelected = session::selectProfile,
            onProfileDeleted = { id -> if (id == session.selectedProfileId()) session.clearProfile() },
        )
        else -> {
            val actions = MainActions(
                onSwitchProfile = session::clearProfile,
                onLogout = { scope.launch { session.logout() } },
            )
            if (isTv) TvRoot(actions) else MobileRoot(actions)
        }
    }
}

@Composable
private fun SplashScreen() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(VisiooMotion.HeroMs)) }
    Box(Modifier.fillMaxSize().background(VisiooColors.Black), contentAlignment = Alignment.Center) {
        VisiooWordmark(Modifier.alpha(progress.value).scale(0.9f + 0.1f * progress.value))
    }
}

@Composable
private fun AuthNav(isTv: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "onboarding") {
        composable("onboarding") { OnboardingScreen(onStart = { nav.navigate("register") }, onLogin = { nav.navigate("login") }) }
        composable("login") { LoginScreen(isTv, onRegister = { nav.navigate("register") }, onForgot = { nav.navigate("recover") }) }
        composable("register") { RegisterScreen(onLogin = { nav.navigate("login") }) }
        composable("recover") { RecoverScreen(onBack = { nav.popBackStack() }) }
    }
}

/** Right after sign-up or recovery: recovery phrase, then an optional VIP key. */
@Composable
private fun SetupFlow(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    when (step) {
        0 -> RecoveryFlowScreen(onFinished = { step = 1 }, onSkip = { step = 1 })
        else -> VipActivationScreen(onDone = onFinished, onSkip = onFinished)
    }
}
