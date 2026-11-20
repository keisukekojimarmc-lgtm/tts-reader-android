package com.example.minutesreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minutesreader.ui.ListScreen
import com.example.minutesreader.ui.MinutesTheme
import com.example.minutesreader.ui.SettingsScreen
import com.example.minutesreader.ui.SetupScreen
import com.example.minutesreader.ui.ViewerScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MinutesTheme { AppRoot(vm) }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val stack by vm.stack.collectAsStateWithLifecycle()
    BackHandler(enabled = stack.size > 1) { vm.back() }
    when (val s = stack.last()) {
        Screen.Setup -> SetupScreen(vm)
        Screen.List -> ListScreen(vm)
        is Screen.Viewer -> ViewerScreen(vm, s.id)
        Screen.Settings -> SettingsScreen(vm)
    }
}
