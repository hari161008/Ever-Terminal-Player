package dev.jyotiraditya.dmt

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.jyotiraditya.dmt.ui.DmtAction
import dev.jyotiraditya.dmt.ui.DmtScreen
import dev.jyotiraditya.dmt.ui.PlayerViewModel
import dev.jyotiraditya.dmt.ui.SplashOverlay
import dev.jyotiraditya.dmt.ui.theme.AccentPalette
import dev.jyotiraditya.dmt.ui.theme.DMTTheme
import dev.jyotiraditya.dmt.ui.theme.LocalAccent
import dev.jyotiraditya.dmt.yt.YtWebView

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by playerViewModel.state.collectAsState()
            DMTTheme(isLight = state.settings.lightMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }

                    val view = LocalView.current
                    LaunchedEffect(state.settings.fullScreen, state.settings.lightMode) {
                        val window = this@MainActivity.window
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = state.settings.lightMode
                        controller.isAppearanceLightNavigationBars = state.settings.lightMode
                        if (state.settings.fullScreen) {
                            controller.hide(WindowInsetsCompat.Type.systemBars())
                            controller.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            controller.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }

                    val saveDocumentLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("audio/*")
                    ) { uri ->
                        if (uri != null) {
                            playerViewModel.dispatch(DmtAction.SaveYtTrackTo(uri))
                        }
                    }
                    LaunchedEffect(Unit) {
                        playerViewModel.saveRequests.collect { suggestedName ->
                            saveDocumentLauncher.launch(suggestedName)
                        }
                    }

                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { grants ->
                        playerViewModel.dispatch(
                            DmtAction.Permission(
                                grants[Manifest.permission.READ_MEDIA_AUDIO] == true
                            )
                        )
                    }
                    val requestPermissions = {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.READ_MEDIA_AUDIO,
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        )
                    }
                    CompositionLocalProvider(
                        LocalAccent provides
                            AccentPalette[state.settings.accent % AccentPalette.size].second
                    ) {
                        Box {
                            YtWebView()
                            DmtScreen(
                                state = state,
                                dispatch = playerViewModel::dispatch,
                                onRequestPermission = requestPermissions
                            )
                            AnimatedVisibility(
                                visible = showSplash,
                                exit = fadeOut(tween(450))
                            ) {
                                SplashOverlay { showSplash = false }
                            }
                        }
                    }
                }
            }
        }
    }
}
