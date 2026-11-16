package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PermissionWizardScreen
import com.example.ui.screens.RecordDetailScreen
import com.example.ui.screens.SettingsSyncScreen
import com.example.ui.theme.OneBookDialerTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.PipelineViewModel
import com.example.ui.viewmodel.RecordDetailViewModel
import com.example.ui.viewmodel.SettingsSyncViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWrapUpIntent(intent)

        val app = application as OneBookApplication
        setContent {
            OneBookDialerTheme {
                OneBookNavRoot(app = app)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWrapUpIntent(intent)
    }

    private fun handleWrapUpIntent(intent: Intent?) {
        val callId = intent?.getLongExtra(OneBookApplication.EXTRA_WRAP_UP_CALL_ID, -1L) ?: -1L
        if (callId > 0L) {
            (application as OneBookApplication).triggerPostCallWrapUp(callId)
            intent?.removeExtra(OneBookApplication.EXTRA_WRAP_UP_CALL_ID)
        }
    }
}

@Composable
private fun OneBookNavRoot(app: OneBookApplication) {
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(app.repository)
    )
    val session by authViewModel.session.collectAsStateWithLifecycle()
    val onboardingDone by authViewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val pendingWrapUpId by app.pendingWrapUpCallId.collectAsStateWithLifecycle()

    if (session == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination: AppRoute = when {
        session?.isAuthenticated != true -> AppRoute.Login
        !onboardingDone -> AppRoute.PermissionWizard
        else -> AppRoute.Home
    }

    val navController = rememberNavController()
    val pipelineViewModel: PipelineViewModel = viewModel(
        factory = PipelineViewModel.Factory(
            app.repository,
            app.database.callLogDao(),
            app.audioPlaybackManager
        )
    )

    LaunchedEffect(pendingWrapUpId) {
        val id = pendingWrapUpId
        if (id != null && id > 0L) {
            pipelineViewModel.openWrapUpForCall(id)
            app.clearPendingWrapUp()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<AppRoute.Login> {
            LoginScreen(
                viewModel = authViewModel,
                onAuthenticated = {
                    val next = if (onboardingDone) AppRoute.Home else AppRoute.PermissionWizard
                    navController.navigate(next) {
                        popUpTo<AppRoute.Login> { inclusive = true }
                    }
                }
            )
        }

        composable<AppRoute.PermissionWizard> {
            val canGoBack = navController.previousBackStackEntry != null
            PermissionWizardScreen(
                showBackButton = canGoBack,
                onBack = { navController.popBackStack() },
                onComplete = {
                    authViewModel.completeOnboarding()
                    if (canGoBack) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(AppRoute.Home) {
                            popUpTo<AppRoute.PermissionWizard> { inclusive = true }
                        }
                    }
                }
            )
        }

        composable<AppRoute.Home> {
            HomeScreen(
                viewModel = pipelineViewModel,
                onOpenWizard = { navController.navigate(AppRoute.PermissionWizard) },
                onOpenSettingsSync = { navController.navigate(AppRoute.SyncAndSettings) },
                onOpenRecord = { doctype, docname ->
                    navController.navigate(AppRoute.RecordDetail(doctype, docname))
                }
            )
        }

        composable<AppRoute.RecordDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.RecordDetail>()
            val detailViewModel: RecordDetailViewModel = viewModel(
                factory = RecordDetailViewModel.Factory(
                    doctype = route.doctype,
                    docname = route.docname,
                    repository = app.repository,
                    audioPlaybackManager = app.audioPlaybackManager
                )
            )
            RecordDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable<AppRoute.SyncAndSettings> {
            val settingsViewModel: SettingsSyncViewModel = viewModel(
                factory = SettingsSyncViewModel.Factory(app, app.repository)
            )
            SettingsSyncScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onOpenPermissionWizard = { navController.navigate(AppRoute.PermissionWizard) },
                onSignedOut = {
                    navController.navigate(AppRoute.Login) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
