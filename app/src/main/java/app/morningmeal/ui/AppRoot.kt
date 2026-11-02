package app.morningmeal.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.morningmeal.ui.addfood.AddFoodScreen
import app.morningmeal.ui.calculator.CalculatorScreen
import app.morningmeal.ui.components.TextTabBar
import app.morningmeal.ui.journal.DayScreen
import app.morningmeal.ui.journal.JournalScreen
import app.morningmeal.ui.settings.SettingsScreen
import java.time.LocalDate

private object Routes {
    const val CALC = "calc"
    const val JOURNAL = "journal"
    const val SETTINGS = "settings"
    const val ADD = "add"
    const val DAY = "day/{day}"
    fun day(date: LocalDate) = "day/${date.toEpochDay()}"
}

private val tabRoutes = listOf(Routes.CALC, Routes.JOURNAL, Routes.SETTINGS)
private val tabLabels = listOf("Count", "Journal", "Settings")

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val selectedTab = when (route) {
        Routes.JOURNAL, Routes.DAY -> 1
        Routes.SETTINGS -> 2
        else -> 0
    }
    // The search screen is a focused task, so it gets the whole screen.
    val showTabs = route != Routes.ADD

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            if (showTabs) {
                TextTabBar(
                    tabs = tabLabels,
                    selected = selectedTab,
                    onSelect = { index ->
                        val target = tabRoutes[index]
                        if (index == selectedTab) {
                            if (route == target) return@TextTabBar
                            // e.g. tapping Journal while inside a day goes back to the list.
                            if (nav.popBackStack(target, inclusive = false)) return@TextTabBar
                        }
                        nav.navigate(target) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.CALC,
            modifier = Modifier
                .padding(inner)
                .consumeWindowInsets(inner),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Routes.CALC) {
                CalculatorScreen(
                    onAddFood = { nav.navigate(Routes.ADD) },
                    onOpenDay = { day -> nav.navigate(Routes.day(day)) },
                )
            }
            composable(Routes.ADD) {
                AddFoodScreen(onDone = { nav.popBackStack() })
            }
            composable(Routes.JOURNAL) {
                JournalScreen(onOpenDay = { day -> nav.navigate(Routes.day(day)) })
            }
            composable(
                Routes.DAY,
                arguments = listOf(navArgument("day") { type = NavType.LongType }),
            ) { entry ->
                val day = LocalDate.ofEpochDay(entry.arguments?.getLong("day") ?: LocalDate.now().toEpochDay())
                DayScreen(day = day, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}
