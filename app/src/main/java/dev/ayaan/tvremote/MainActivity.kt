package dev.ayaan.tvremote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ayaan.tvremote.ui.CodeScreen
import dev.ayaan.tvremote.ui.ControlScreen
import dev.ayaan.tvremote.ui.DiscoverScreen
import dev.ayaan.tvremote.ui.RemoteViewModel
import dev.ayaan.tvremote.ui.Step
import dev.ayaan.tvremote.ui.TvRemoteTheme

class
MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TvRemoteTheme {
                val vm: RemoteViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val context = LocalContext.current
                // Deliberately not auto-starting a voice session from this callback: it fires
                // asynchronously, well outside the press-and-hold gesture that triggered the
                // request, so a start here would have no matching release to stop it. Once
                // granted, the user just holds Mic again -- same as any other app.
                //
                // The result still has to be reported, though. After two dismissals Android
                // permanently denies RECORD_AUDIO and `launch` returns here without ever
                // showing a dialog, so an empty callback leaves the key looking simply dead.
                val micPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    vm.report(
                        if (granted) "Microphone allowed -- hold the Google key again to speak"
                        else "Microphone permission is off. Turn it on in Settings > Apps > " +
                            "TV Remote > Permissions, then hold the Google key again."
                    )
                }

                LaunchedEffect(state.error) {
                    state.error?.let {
                        snackbar.showSnackbar(it)
                        vm.dismissError()
                    }
                }

                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
                    Box(Modifier.fillMaxSize().padding(insets)) {
                    when (state.step) {
                        Step.Discover -> DiscoverScreen(
                            devices = state.devices,
                            busy = state.busy,
                            onSelect = vm::select,
                            onManual = vm::connectManually
                        )

                        Step.EnterCode -> CodeScreen(
                            busy = state.busy,
                            onSubmit = vm::submitCode,
                            onCancel = vm::back
                        )

                        Step.Control -> ControlScreen(
                            name = state.connecting?.name ?: "TV",
                            link = state.link,
                            textPrompt = state.textPrompt,
                            listening = state.listening,
                            onKey = { vm.press(it) },
                            onSendText = vm::sendText,
                            onDismissTextPrompt = vm::dismissTextPrompt,
                            onMicPressed = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) vm.onMicPressed() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onMicReleased = vm::onMicReleased,
                            onLaunchApp = vm::launchApp,
                            onBack = vm::back,
                            onForget = vm::forget
                        )
                    }
                    }
                }
            }
        }
    }
}
