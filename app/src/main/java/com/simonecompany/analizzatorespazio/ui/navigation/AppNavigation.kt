package com.simonecompany.analizzatorespazio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.simonecompany.analizzatorespazio.ui.screens.DetailScreen
import com.simonecompany.analizzatorespazio.ui.screens.MainScreen
import com.simonecompany.analizzatorespazio.viewmodel.StorageViewModel
import com.simonecompany.analizzatorespazio.viewmodel.StorageViewModelFactory

object Destinations {
    const val MAIN = "main"
    const val DETAIL_PATTERN = "detail/{categoryKey}/{indices}"

    fun detailBase(categoryKey: String) = "detail/$categoryKey/root"
    fun detailPath(categoryKey: String, path: List<Int>) =
        "detail/$categoryKey/${path.joinToString(".")}"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val viewModel: StorageViewModel = viewModel(
        factory = StorageViewModelFactory(context.applicationContext)
    )

    NavHost(
        navController = navController,
        startDestination = Destinations.MAIN
    ) {
        composable(Destinations.MAIN) {
            MainScreen(
                onCategoryClick = { categoryKey ->
                    navController.navigate(Destinations.detailBase(categoryKey))
                },
                viewModel = viewModel
            )
        }

        composable(
            route = Destinations.DETAIL_PATTERN,
            arguments = listOf(
                navArgument("categoryKey") { type = NavType.StringType },
                navArgument("indices") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val categoryKey = backStackEntry.arguments?.getString("categoryKey").orEmpty()
            val indices = parsePath(backStackEntry.arguments?.getString("indices").orEmpty())
            DetailScreen(
                categoryKey = categoryKey,
                indices = indices,
                onBack = {
                    if (indices.isNotEmpty()) {
                        val parentPath = indices.dropLast(1)
                        if (parentPath.isEmpty()) {
                            navController.popBackStack(Destinations.MAIN, inclusive = false)
                        } else {
                            navController.navigate(Destinations.detailPath(categoryKey, parentPath)) {
                                launchSingleTop = true
                                popUpTo(Destinations.MAIN)
                            }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                viewModel = viewModel
            )
        }
    }
}

private fun parsePath(raw: String): List<Int> {
    if (raw.isEmpty() || raw == "root") return emptyList()
    return raw.split(".").mapNotNull { it.toIntOrNull() }
}