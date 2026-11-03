package com.animevostbr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.animevostbr.core.AppUpdate
import com.animevostbr.core.Catalog
import com.animevostbr.core.CatalogKind
import com.animevostbr.playback.PlayerViewModel

@Composable
fun AppNav(isTv: Boolean, openTitleId: Int?, onTitleOpened: () -> Unit) {
    val nav = rememberNavController()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (AppUpdate.hasPendingInstall(context)) nav.navigate("update")
    }

    LaunchedEffect(openTitleId) {
        if (openTitleId != null) {
            nav.navigate("details/$openTitleId")
            onTitleOpened()
        }
    }

    NavHost(navController = nav, startDestination = "home") {

        composable("home") {
            HomeScreen(
                isTv = isTv,
                onOpen = { id -> nav.navigate("details/$id") },
                onPlay = { id, ep -> nav.navigate("player/$id/$ep") },
                onSearch = { nav.navigate("search") },
                onFavorites = { nav.navigate("favorites") },
                onSettings = { nav.navigate("settings") }
            )
        }

        composable("search") {
            SearchScreen(
                isTv = isTv,
                onOpen = { id -> nav.navigate("details/$id") },
                onPlay = { id, ep -> nav.navigate("player/$id/$ep") },
                onCatalog = { f -> nav.navigate("catalog/${f.kind.name}/${f.slug}") },
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            route = "catalog/{kind}/{slug}",
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("slug") { type = NavType.StringType }
            )
        ) { entry ->
            val kind = runCatching { CatalogKind.valueOf(entry.arguments?.getString("kind").orEmpty()) }
                .getOrDefault(CatalogKind.GENRE)
            val filter = remember(entry) { Catalog.find(kind, entry.arguments?.getString("slug").orEmpty()) }
            CatalogScreen(
                isTv = isTv,
                filter = filter,
                onOpen = { id -> nav.navigate("details/$id") },
                onPlay = { id, ep -> nav.navigate("player/$id/$ep") },
                onBack = { nav.popBackStack() }
            )
        }

        composable("favorites") {
            FavoritesScreen(
                onOpen = { id -> nav.navigate("details/$id") },
                onBack = { nav.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onDiagnostics = { nav.navigate("diagnostics") },
                onUpdate = { nav.navigate("update") }
            )
        }

        composable("update") {
            UpdateScreen(onBack = { nav.popBackStack() })
        }

        composable("diagnostics") {
            DiagnosticsScreen(onBack = { nav.popBackStack() })
        }

        composable(
            route = "details/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            DetailsScreen(
                isTv = isTv,
                titleId = id,
                onBack = { nav.popBackStack() },
                onPlay = { tid, ep -> nav.navigate("player/$tid/$ep") }
            )
        }

        composable(
            route = "player/{id}/{ep}",
            arguments = listOf(
                navArgument("id") { type = NavType.IntType },
                navArgument("ep") { type = NavType.IntType }
            )
        ) { entry ->
            val vm: PlayerViewModel = viewModel()
            PlayerScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
