package com.iut.studytracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.iut.studytracker.ui.screens.MainTrackerScreen
import com.iut.studytracker.ui.theme.StudyTrackerAmoledTheme
import com.iut.studytracker.viewmodel.StudyTrackerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: StudyTrackerViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Foreground timer notification permission handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            StudyTrackerAmoledTheme {
                MainTrackerScreen(viewModel = viewModel)
            }
        }
    }
}
