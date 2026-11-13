package com.tamer.smartfilemanagerai.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tamer.smartfilemanagerai.ui.screens.SmartFileManagerRootScreen
import com.tamer.smartfilemanagerai.ui.theme.SmartFileManagerTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by mainViewModel.preferences.collectAsState()
            val safTreeLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    mainViewModel.onSafTreeGranted(uri)
                }
            }

            SmartFileManagerTheme(darkTheme = prefs.darkThemeEnabled) {
                SmartFileManagerRootScreen(
                    viewModel = mainViewModel,
                    onRequestSafFolderAccess = {
                        safTreeLauncher.launch(null)
                    }
                )
            }
        }
    }
}
