package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.FocusFlowViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS permission at runtime on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        setContent {
            val viewModel: FocusFlowViewModel = viewModel()
            MyApplicationTheme(darkTheme = viewModel.isDarkThemeEnabled) {
                if (!viewModel.isOnboardingCompleted) {
                    OnboardingScreen(viewModel)
                } else {
                    FocusFlowApp(viewModel)
                }
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Today : Screen("today", "Today", Icons.Default.Home)
    object Focus : Screen("focus", "Timer", Icons.Default.PlayArrow)
    object Habits : Screen("habits", "Habits", Icons.Default.CheckCircle)
    object Planner : Screen("planner", "Planner", Icons.Default.DateRange)
    object Insights : Screen("insights", "Insights", Icons.Default.Star)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun FocusFlowApp(viewModel: FocusFlowViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navigationItems = listOf(
        Screen.Today,
        Screen.Focus,
        Screen.Habits,
        Screen.Planner,
        Screen.Insights,
        Screen.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.height(72.dp)
            ) {
                navigationItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(19.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.2.sp
                                ),
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("nav_${screen.route}"),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Today.route) {
                TodayHomeScreen(
                    viewModel = viewModel,
                    onNavigateToFocus = {
                        navController.navigate(Screen.Focus.route) {
                            popUpTo(Screen.Today.route)
                        }
                    },
                    onNavigateToPlanner = {
                        navController.navigate(Screen.Planner.route) {
                            popUpTo(Screen.Today.route)
                        }
                    }
                )
            }
            composable(Screen.Focus.route) {
                FocusTimerScreen(viewModel = viewModel)
            }
            composable(Screen.Habits.route) {
                HabitsScreen(viewModel = viewModel)
            }
            composable(Screen.Planner.route) {
                PlannerScreen(
                    viewModel = viewModel,
                    onNavigateToFocus = {
                        navController.navigate(Screen.Focus.route) {
                            popUpTo(Screen.Planner.route)
                        }
                    }
                )
            }
            composable(Screen.Insights.route) {
                InsightsScreen(
                    viewModel = viewModel,
                    onNavigateToFocus = {
                        navController.navigate(Screen.Focus.route) {
                            popUpTo(Screen.Insights.route)
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
