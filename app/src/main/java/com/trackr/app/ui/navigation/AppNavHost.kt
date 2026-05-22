package com.trackr.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trackr.app.ui.category.CategoryEditScreen
import com.trackr.app.ui.category.CategoryListScreen
import com.trackr.app.ui.home.EventEditScreen
import com.trackr.app.ui.home.HomeScreen

object Routes {
    const val TIMELINE = "timeline"
    const val CATEGORY_LIST = "categoryList"
    const val EVENT_EDIT = "eventEdit/{eventId}"
    const val CATEGORY_EDIT = "categoryEdit?categoryId={categoryId}&parentId={parentId}"

    fun eventEdit(eventId: String) = "eventEdit/$eventId"
    fun categoryEdit(categoryId: String?) =
        if (categoryId != null) "categoryEdit?categoryId=$categoryId" else "categoryEdit"
    fun categoryEditNewSubCategory(parentId: String) = "categoryEdit?parentId=$parentId"
}

// @spec APP-NAV-001, APP-NAV-002, APP-UI-001, APP-UI-002, APP-UI-003, APP-UI-004, APP-UI-005
@Composable
fun AppScaffold(navController: NavHostController = rememberNavController()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute == Routes.TIMELINE || currentRoute == Routes.CATEGORY_LIST

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.TIMELINE,
                        onClick = {
                            if (currentRoute != Routes.TIMELINE) {
                                navController.navigate(Routes.TIMELINE) {
                                    popUpTo(Routes.TIMELINE) { inclusive = true }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Timeline") },
                        label = { Text("Timeline") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.CATEGORY_LIST,
                        onClick = {
                            if (currentRoute != Routes.CATEGORY_LIST) {
                                navController.navigate(Routes.CATEGORY_LIST) {
                                    popUpTo(Routes.TIMELINE)
                                }
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Categories") },
                        label = { Text("Categories") },
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(navController = navController, modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()))
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.TIMELINE,
        modifier = modifier,
    ) {
        composable(Routes.TIMELINE) { entry ->
            HomeScreen(
                onNavigateToEventEdit = { eventId ->
                    navController.navigate(Routes.eventEdit(eventId))
                },
                pendingSnackbarMessage = entry.savedStateHandle
                    .getStateFlow<String?>("snackbar_message", null),
                onSnackbarMessageConsumed = {
                    entry.savedStateHandle.remove<String>("snackbar_message")
                },
            )
        }
        composable(
            route = Routes.EVENT_EDIT,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
        ) {
            EventEditScreen(
                onNavigateBack = { errorMessage ->
                    if (errorMessage != null) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("snackbar_message", errorMessage)
                    }
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.CATEGORY_LIST) { entry ->
            CategoryListScreen(
                onNavigateToCategoryEdit = { categoryId ->
                    navController.navigate(Routes.categoryEdit(categoryId))
                },
                pendingSnackbarMessage = entry.savedStateHandle
                    .getStateFlow<String?>("snackbar_message", null),
                onSnackbarMessageConsumed = {
                    entry.savedStateHandle.remove<String>("snackbar_message")
                },
            )
        }
        composable(
            route = Routes.CATEGORY_EDIT,
            arguments = listOf(
                navArgument("categoryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("parentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            CategoryEditScreen(
                onNavigateBack = { errorMessage ->
                    if (errorMessage != null) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("snackbar_message", errorMessage)
                    }
                    navController.popBackStack()
                },
            )
        }
    }
}
