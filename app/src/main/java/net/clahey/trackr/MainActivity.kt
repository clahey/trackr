package net.clahey.trackr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import net.clahey.trackr.ui.navigation.AppScaffold
import net.clahey.trackr.ui.theme.TrackrTheme
import dagger.hilt.android.AndroidEntryPoint

// @spec APP-DI-001, APP-NAV-001
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackrTheme {
                AppScaffold()
            }
        }
    }
}
