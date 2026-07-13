package com.pocketai.studio.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object Chat : NavRoutes("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
    }
    data object NewChat : NavRoutes("new_chat")
    data object Models : NavRoutes("models")
    data object Settings : NavRoutes("settings")
}
