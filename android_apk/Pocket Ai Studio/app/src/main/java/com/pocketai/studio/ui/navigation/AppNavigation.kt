package com.pocketai.studio.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketai.studio.ui.chat.ChatScreen
import com.pocketai.studio.ui.chat.ChatViewModel
import com.pocketai.studio.ui.home.HomeScreen
import com.pocketai.studio.ui.home.HomeViewModel
import com.pocketai.studio.ui.modelmanager.ModelManagerScreen
import com.pocketai.studio.ui.modelmanager.ModelManagerViewModel
import com.pocketai.studio.ui.ocr.OcrScreen
import com.pocketai.studio.ui.ocr.OcrViewModel
import com.pocketai.studio.ui.pdf.PdfScreen
import com.pocketai.studio.ui.pdf.PdfViewModel
import com.pocketai.studio.ui.settings.SettingsScreen
import com.pocketai.studio.ui.settings.SettingsViewModel
import com.pocketai.studio.ui.texttools.TextToolsScreen
import com.pocketai.studio.ui.texttools.TextToolsViewModel

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(
        BottomNavItem(NavRoutes.Home.route, "Home", Icons.Filled.Home),
        BottomNavItem(NavRoutes.Models.route, "Models", Icons.Filled.Memory),
        BottomNavItem(NavRoutes.Settings.route, "Settings", Icons.Filled.Settings)
    )

    val showBottomBar = currentDestination?.hierarchy?.any { dest ->
        bottomNavItems.any { it.route == dest.route }
    } == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoutes.Home.route) {
                val viewModel: HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToChat = { chatId -> navController.navigate(NavRoutes.Chat.createRoute(chatId)) },
                    onNavigateToNewChat = { navController.navigate(NavRoutes.NewChat.route) },
                    onNavigateToModels = { navController.navigate(NavRoutes.Models.route) },
                    onNavigateToPdf = { navController.navigate(NavRoutes.PdfAssistant.route) },
                    onNavigateToOcr = { navController.navigate(NavRoutes.Ocr.route) },
                    onNavigateToTextTools = { navController.navigate(NavRoutes.TextTools.route) }
                )
            }

            composable(
                route = NavRoutes.Chat.route,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                val viewModel: ChatViewModel = hiltViewModel()
                ChatScreen(viewModel = viewModel, chatId = chatId, onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.NewChat.route) {
                val viewModel: ChatViewModel = hiltViewModel()
                ChatScreen(viewModel = viewModel, chatId = null, onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.Models.route) {
                val viewModel: ModelManagerViewModel = hiltViewModel()
                ModelManagerScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.PdfAssistant.route) {
                val viewModel: PdfViewModel = hiltViewModel()
                PdfScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.Ocr.route) {
                val viewModel: OcrViewModel = hiltViewModel()
                OcrScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }

            composable(NavRoutes.TextTools.route) {
                val viewModel: TextToolsViewModel = hiltViewModel()
                TextToolsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}