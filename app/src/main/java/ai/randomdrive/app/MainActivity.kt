package ai.randomdrive.app

import ai.randomdrive.app.trip.TripPhase
import ai.randomdrive.app.ui.DrawingScreen
import ai.randomdrive.app.ui.FinishingScreen
import ai.randomdrive.app.ui.HomeScreen
import ai.randomdrive.app.ui.MainViewModel
import ai.randomdrive.app.ui.PreTrip
import ai.randomdrive.app.ui.RecapScreen
import ai.randomdrive.app.ui.ResultScreen
import ai.randomdrive.app.ui.TripScreen
import ai.randomdrive.app.ui.theme.RandomDriveTheme
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val container get() = (application as RandomDriveApp).container

    private val vm: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(application, container) as T
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!container.location.hasPermission()) requestPermissions()
        setContent {
            RandomDriveTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(state.message) {
                    state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
                }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        val s = state.session
                        when {
                            s == null -> when (val p = state.preTrip) {
                                PreTrip.Home -> {
                                    // 최상위 화면 Back = 앱 종료가 아니라 백그라운드로(CCP 가이드)
                                    BackHandler { moveTaskToBack(true) }
                                    HomeScreen(state, ::requestPermissions, ::openLocationSettings, vm::draw, vm::retryNaviConnect)
                                }
                                is PreTrip.Drawing -> {
                                    BackHandler { }
                                    DrawingScreen(p.radiusKm)
                                }
                                is PreTrip.Result -> {
                                    BackHandler(enabled = !p.sending) { vm.backToHome() }
                                    ResultScreen(p, vm::startNavigation, { vm.draw(p.radiusKm, reroll = true) }, vm::backToHome)
                                }
                            }
                            s.phase == TripPhase.RECORDING || s.phase == TripPhase.ARRIVED -> {
                                BackHandler { moveTaskToBack(true) }
                                TripScreen(s, vm::showNavigation, vm::endTrip, vm::discardTrip)
                            }
                            s.phase == TripPhase.FINISHING -> {
                                BackHandler { moveTaskToBack(true) }
                                FinishingScreen(s, state.finish, vm::retryFinish, vm::discardTrip)
                            }
                            else -> {
                                BackHandler { vm.newTrip() }
                                RecapScreen(s, vm::newTrip)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setForeground(true)
    }

    override fun onStop() {
        vm.setForeground(false)
        super.onStop()
    }

    private fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun openLocationSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    }
}
