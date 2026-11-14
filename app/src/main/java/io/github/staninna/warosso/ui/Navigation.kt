package io.github.staninna.warosso.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.staninna.warosso.ui.find.FindScreen
import io.github.staninna.warosso.ui.settings.OsmRefreshScreen
import io.github.staninna.warosso.ui.settings.SettingsScreen
import io.github.staninna.warosso.ui.setup.EntranceEditorScreen
import io.github.staninna.warosso.ui.setup.SetupScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Find : Route

    @Serializable
    data object Setup : Route

    @Serializable
    data class EntranceEditor(val entranceId: Long) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object OsmRefresh : Route
}

/** Find is home. Everything else stacks on top and slides in from the side. */
@Composable
fun WarOssoNavigation() {
    val backStack = rememberNavBackStack(Route.Find)
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val openEntrance: (Long) -> Unit = { backStack.add(Route.EntranceEditor(it)) }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        transitionSpec = {
            slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 } + fadeOut()
        },
        popTransitionSpec = {
            slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it }
        },
        predictivePopTransitionSpec = {
            slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it }
        },
        entryProvider = entryProvider {
            entry<Route.Find> {
                FindScreen(onOpenSetup = { backStack.add(Route.Setup) }, onOpenSettings = { backStack.add(Route.Settings) })
            }
            entry<Route.Setup> { SetupScreen(onBack = back, onOpenEntrance = openEntrance) }
            entry<Route.EntranceEditor> { route -> EntranceEditorScreen(route.entranceId, onBack = back) }
            entry<Route.Settings> { SettingsScreen(onBack = back, onOpenRefresh = { backStack.add(Route.OsmRefresh) }) }
            entry<Route.OsmRefresh> { OsmRefreshScreen(onBack = back, onOpenEntrance = openEntrance) }
        },
    )
}
