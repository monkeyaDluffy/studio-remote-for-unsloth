package io.github.monkeyadluffy.studioremote

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.monkeyadluffy.studioremote.ui.C
import io.github.monkeyadluffy.studioremote.ui.MainScreen
import io.github.monkeyadluffy.studioremote.ui.SetupScreen
import io.github.monkeyadluffy.studioremote.ui.UnslothTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Needed for the "replying…" and "reply ready" notifications on Android 13+. */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var askedNotifications = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            UnslothTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(C.Bg)) {
                    Crossfade(targetState = state.screen, label = "screen") { screen ->
                        when (screen) {
                            Screen.Splash -> Box(Modifier.fillMaxSize())
                            Screen.Setup -> SetupScreen(vm, state.settings)
                            Screen.Main -> MainScreen(vm, state)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.appVisible = true
    }

    override fun onStop() {
        vm.appVisible = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (vm.state.value.screen == Screen.Main) vm.refreshAll()
        if (Build.VERSION.SDK_INT >= 33 && !askedNotifications &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
