package com.platinum20.dashboard

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.ui.screens.ArticleScreen
import com.platinum20.dashboard.ui.screens.HomeScreen
import com.platinum20.dashboard.ui.screens.NewsScreen
import com.platinum20.dashboard.ui.screens.SettingsScreen
import com.platinum20.dashboard.ui.screens.UpdatesScreen
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import com.platinum20.dashboard.ui.theme.isAppInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        enableEdgeToEdge()
        setContent {
            val theme by AppSettings.theme.collectAsState()
            val dark = isAppInDarkTheme(theme)

            // Icone della barra di stato chiare o scure in base al tema scelto, non solo al sistema.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            GeneralDashboardTheme(theme = theme) {
                AppRoot()
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val NEWS = "news"
    const val SETTINGS = "settings"
    const val UPDATES = "settings/updates"
    const val ARTICLE = "news/article?url={url}&source={source}"

    fun article(url: String, source: String) = "news/article?url=${Uri.encode(url)}&source=${Uri.encode(source)}"
}

private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val topDestinations = listOf(
    TopDestination(Routes.HOME, "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    TopDestination(Routes.NEWS, "News", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    TopDestination(Routes.SETTINGS, "Impostazioni", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/** Passa a una sezione della barra in basso mantenendo lo stato delle altre. */
private fun NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topDestinations.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topDestinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.switchTab(dest.route) },
                            icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    bottomPadding = padding,
                    onOpenNews = { navController.switchTab(Routes.NEWS) },
                )
            }
            composable(Routes.NEWS) {
                NewsScreen(
                    bottomPadding = padding,
                    onOpenArticle = { navController.navigate(Routes.article(it.link, it.source.name)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    bottomPadding = padding,
                    onOpenUpdates = { navController.navigate(Routes.UPDATES) },
                )
            }
            composable(
                Routes.ARTICLE,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("source") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                ArticleScreen(
                    url = entry.arguments?.getString("url").orEmpty(),
                    sourceName = entry.arguments?.getString("source").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.UPDATES) {
                UpdatesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
