package net.clahey.trackr.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import net.clahey.trackr.R
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.clahey.trackr.ui.about.AboutScreen
import net.clahey.trackr.ui.category.CategoryEditScreen
import net.clahey.trackr.ui.category.CategoryListScreen
import net.clahey.trackr.ui.home.EventEditScreen
import net.clahey.trackr.ui.home.HomeScreen

object Routes {
    const val TIMELINE = "timeline?quickLogCategoryId={quickLogCategoryId}"
    const val CATEGORY_LIST = "categoryList"
    const val EVENT_EDIT = "eventEdit/{eventId}?filterCategoryId={filterCategoryId}"
    const val CATEGORY_EDIT = "categoryEdit?categoryId={categoryId}&parentId={parentId}"
    const val ABOUT = "about"

    // @spec APP-NAV-005, APP-NAV-006
    fun timeline(quickLogCategoryId: String? = null) =
        if (quickLogCategoryId != null) "timeline?quickLogCategoryId=$quickLogCategoryId" else "timeline"
    fun eventEdit(eventId: String, filterCategoryId: String? = null) =
        if (filterCategoryId != null) "eventEdit/$eventId?filterCategoryId=$filterCategoryId"
        else "eventEdit/$eventId"
    fun categoryEdit(categoryId: String?) =
        if (categoryId != null) "categoryEdit?categoryId=$categoryId" else "categoryEdit"
    fun categoryEditNewSubCategory(parentId: String) = "categoryEdit?parentId=$parentId"
}

// @spec APP-NAV-001, APP-NAV-002, APP-UI-001, APP-UI-002, APP-UI-003, APP-UI-004, APP-UI-005, APP-NAV-005
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppScaffold(
    navController: NavHostController = rememberNavController(),
    initialQuickLogCategoryId: String? = null,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute == Routes.TIMELINE || currentRoute == Routes.CATEGORY_LIST

    Scaffold(
        // Expose Compose testTags as resource-ids so tooling (screenshots, uiautomator) can target
        // elements by id rather than fragile text/coordinates. Fine to ship — the app is FOSS.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        // @spec APP-UI-002 — animate the bar in/out so its bottom-inset contribution eases rather
        // than snapping when navigating to/from a detail screen, which otherwise jolts content
        // (most visibly a vertically-centered empty state) down as the bar disappears.
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + expandVertically(),
                exit = slideOutVertically { it } + shrinkVertically(),
            ) {
                NavigationBar {
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_timeline"),
                        selected = currentRoute == Routes.TIMELINE,
                        onClick = {
                            if (currentRoute != Routes.TIMELINE) {
                                navController.navigate(Routes.timeline()) {
                                    popUpTo(Routes.TIMELINE) { inclusive = true }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.nav_timeline)) },
                        label = { Text(stringResource(R.string.nav_timeline)) },
                    )
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_categories"),
                        selected = currentRoute == Routes.CATEGORY_LIST,
                        onClick = {
                            if (currentRoute != Routes.CATEGORY_LIST) {
                                navController.navigate(Routes.CATEGORY_LIST) {
                                    popUpTo(Routes.TIMELINE)
                                }
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = stringResource(R.string.nav_categories)) },
                        label = { Text(stringResource(R.string.nav_categories)) },
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            initialQuickLogCategoryId = initialQuickLogCategoryId,
        )
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    initialQuickLogCategoryId: String? = null,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.timeline(initialQuickLogCategoryId),
        modifier = modifier,
    ) {
        composable(
            route = Routes.TIMELINE,
            arguments = listOf(
                navArgument("quickLogCategoryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            HomeScreen(
                onNavigateToEventEdit = { eventId, filterCategoryId ->
                    navController.navigate(Routes.eventEdit(eventId, filterCategoryId))
                },
                // @spec EL-NAV-020
                onNavigateToCreateCategory = {
                    navController.navigate(Routes.categoryEdit(null))
                },
                onNavigateToCreateSubCategory = { parentId ->
                    navController.navigate(Routes.categoryEditNewSubCategory(parentId))
                },
                // @spec APP-NAV-010
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                pendingSnackbarMessage = entry.savedStateHandle
                    .getStateFlow<String?>("snackbar_message", null),
                onSnackbarMessageConsumed = {
                    entry.savedStateHandle.remove<String>("snackbar_message")
                },
                // @spec EL-NAV-021
                pendingCreatedCategoryId = entry.savedStateHandle
                    .getStateFlow<String?>("created_category_id", null),
                onCreatedCategoryConsumed = {
                    entry.savedStateHandle.remove<String>("created_category_id")
                },
            )
        }
        composable(
            route = Routes.EVENT_EDIT,
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType },
                navArgument("filterCategoryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
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
                // @spec APP-NAV-010
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
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
                // @spec CAT-NAV-010
                onNavigateToCreateSubCategory = { parentId ->
                    navController.navigate(Routes.categoryEditNewSubCategory(parentId))
                },
                // @spec CAT-NAV-020 — report the new id to whoever initiated the create
                onCategoryCreated = { id ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("created_category_id", id)
                },
            )
        }
        // @spec APP-UI-010, APP-NAV-010
        composable(Routes.ABOUT) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
