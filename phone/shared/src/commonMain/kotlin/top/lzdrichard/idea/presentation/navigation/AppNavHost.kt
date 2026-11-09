package top.lzdrichard.idea.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import top.lzdrichard.idea.presentation.feature.detail.DetailScreen
import top.lzdrichard.idea.presentation.feature.home.HomeScreen
import top.lzdrichard.idea.presentation.feature.login.LoginScreen
import top.lzdrichard.idea.presentation.feature.templates.TemplatesScreen

/** 导航图：登录 → 选体裁（仅首登）→ 首页 → 详情。 */
@Composable
fun AppNavHost(start: String, afterLogin: () -> String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    val next = afterLogin()
                    navController.navigate(next) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.TEMPLATES) {
            TemplatesScreen(
                onSaved = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.TEMPLATES) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDetail = { clientId -> navController.navigate(Routes.detail(clientId)) },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.DETAIL_ARG) { type = NavType.StringType }),
        ) { entry ->
            DetailScreen(
                clientId = entry.arguments?.read { getStringOrNull(Routes.DETAIL_ARG) }.orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
