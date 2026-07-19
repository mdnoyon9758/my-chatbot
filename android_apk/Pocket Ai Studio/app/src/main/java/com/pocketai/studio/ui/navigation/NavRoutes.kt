package com.pocketai.studio.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object Chat : NavRoutes("chat/{chatId}") {
        fun createRoute(chatId: String) = "chat/$chatId"
    }
    data object NewChat : NavRoutes("new_chat")
    data object Models : NavRoutes("models")
    data object Settings : NavRoutes("settings")
    data object Ocr : NavRoutes("ocr")
    data object Pdf : NavRoutes("pdf")
    data object TextTools : NavRoutes("text_tools")
    data object Arena : NavRoutes("arena")
    data object ArenaHistory : NavRoutes("arena_history")
    data object ProviderSettings : NavRoutes("provider_settings")
    data object DocumentChat : NavRoutes("document_chat")
}
