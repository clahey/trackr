package net.clahey.trackr

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import net.clahey.trackr.reminders.ReminderReceiver
import net.clahey.trackr.ui.navigation.AppScaffold
import net.clahey.trackr.ui.navigation.Routes
import net.clahey.trackr.ui.theme.TrackrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// @spec APP-DI-001, APP-NAV-001, APP-NAV-005, APP-NAV-006, APP-PROC-001
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var uiStartupWork: UiStartupWork

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialQuickLogCategoryId = intent.getStringExtra(ReminderReceiver.EXTRA_CATEGORY_ID)
        setContent {
            TrackrTheme {
                val controller = rememberNavController()
                navController = controller
                AppScaffold(navController = controller, initialQuickLogCategoryId = initialQuickLogCategoryId)
            }
        }
        uiStartupWork.runOnce()
    }

    // @spec APP-NAV-006
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val categoryId = intent.getStringExtra(ReminderReceiver.EXTRA_CATEGORY_ID) ?: return
        navController?.navigate(Routes.timeline(categoryId)) {
            popUpTo(Routes.TIMELINE) { inclusive = true }
        }
    }
}
