package com.fagghino.tvvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fagghino.tvvault.ui.viewmodel.MediaViewModel
import com.fagghino.tvvault.ui.viewmodel.MediaViewModelFactory
import com.fagghino.tvvault.ui.screens.DetailScreen
import com.fagghino.tvvault.ui.screens.ImportJobsScreen
import com.fagghino.tvvault.ui.screens.LoginScreen
import com.fagghino.tvvault.ui.screens.MoviesScreen
import com.fagghino.tvvault.ui.screens.ProfileScreen
import com.fagghino.tvvault.ui.screens.ReconciliationScreen
import com.fagghino.tvvault.ui.screens.SettingsScreen
import com.fagghino.tvvault.ui.screens.ShowsScreen
import com.fagghino.tvvault.ui.theme.TVVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as TVVaultApp
        val repository = app.repository
        
        setContent {
            val viewModel: MediaViewModel = viewModel(factory = MediaViewModelFactory(repository, app.tvTimeImporter, app.backupManager, app.authManager, app.syncEngine))
            val themeMode by viewModel.themeMode.collectAsState()
            val userEmail by viewModel.userEmail.collectAsState()

            TVVaultTheme(themeMode = themeMode) {
                if (userEmail == null) {
                    LoginScreen(viewModel = viewModel)
                } else {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MediaViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine which tab is logically active (detail/* belongs to the tab it came from)
    val activeTab = when {
        currentRoute == "shows" -> "shows"
        currentRoute == "movies" -> "movies"
        currentRoute == "profile" -> "profile"
        currentRoute?.startsWith("detail/") == true -> {
            // Look back in the stack to find parent tab
            navController.previousBackStackEntry?.destination?.route ?: "shows"
        }
        else -> "shows"
    }

    fun navigateToTab(tab: String) {
        if (currentRoute == tab) return // already at root of this tab, nothing to do
        navController.navigate(tab) {
            // Pop everything up to (but not including) the start destination,
            // then pop start destination itself only if switching tabs
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
                inclusive = false
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == "shows",
                    onClick = { navigateToTab("shows") },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    label = { Text(stringResource(R.string.title_shows), style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = activeTab == "movies",
                    onClick = { navigateToTab("movies") },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text(stringResource(R.string.title_movies), style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = activeTab == "profile",
                    onClick = { navigateToTab("profile") },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(stringResource(R.string.title_profile), style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "shows",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("shows") {
                ShowsScreen(viewModel = viewModel, onShowClick = { showId -> navController.navigate("detail/$showId") })
            }
            composable("movies") {
                MoviesScreen(viewModel = viewModel, onMovieClick = { movieId -> navController.navigate("detail/$movieId") })
            }
            composable("profile") {
                ProfileScreen(
                    viewModel = viewModel,
                    onSettingsClick = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onManageImportClick = { navController.navigate("import_jobs") }
                )
            }
            composable("detail/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: ""
                DetailScreen(viewModel = viewModel, titleId = id, onBackClick = { navController.popBackStack() })
            }
            composable("import_jobs") {
                ImportJobsScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onReconcileClick = { jobId -> navController.navigate("reconciliation/$jobId") }
                )
            }
            composable("reconciliation/{jobId}") { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId")?.toLongOrNull() ?: 0L
                ReconciliationScreen(
                    viewModel = viewModel,
                    jobId = jobId,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
