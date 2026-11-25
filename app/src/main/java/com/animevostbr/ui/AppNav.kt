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
import androidx.lifecycle.Lifecycle
import com.animevostbr.core.AppUpdate
import com.animevostbr.core.Catalog
import com.animevostbr.core.CatalogKind
import com.animevostbr.playback.PlayerViewModel

@Composable
fun AppNav(isTv: Boolean, openTitleId: Int?, onTitleOpened: () -> Unit) {
    val nav = rememberNavController()

    // A second press while the screen is already changing (a double tap, a laggy remote) is dropped:
    // it opened the player twice, and the hidden one kept playing.
    fun ready() = nav.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED
    fun go(route: String) { if (ready()) nav.navigate(route) }
    fun back() { if (ready()) nav.popBackStack() }

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
                onOpen = { id -> go("details/$id") },
                onPlay = { id, ep -> go("player/$id/$ep") },
                onSearch = { go("search") },
                onSchedule = { go("schedule") },
                onFavorites = { go("favorites") },
                onSettings = { go("settings") }
            )
        }

        composable("schedule") {
            ScheduleScreen(
                onOpen = { id -> go("details/$id") },
                onBack = ::back
            )
        }

        composable("search") {
            SearchScreen(
                isTv = isTv,
                onOpen = { id -> go("details/$id") },
                onPlay = { id, ep -> go("player/$id/$ep") },
                onCatalog = { f -> go("catalog/${f.kind.name}/${f.slug}") },
                onBack = ::back
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
                onOpen = { id -> go("details/$id") },
                onPlay = { id, ep -> go("player/$id/$ep") },
                onBack = ::back
            )
        }

        composable("favorites") {
            FavoritesScreen(
                onOpen = { id -> go("details/$id") },
                onBack = ::back
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = ::back,
                onDiagnostics = { go("diagnostics") },
                onUpdate = { go("update") },
                onSync = { go("sync") },
                onDownloads = { go("downloads") }
            )
        }

        composable("sync") {
            SyncScreen(onBack = ::back)
        }

        composable("downloads") {
            DownloadsScreen(
                onPlay = { id, ep -> go("player/$id/$ep") },
                onBack = ::back
            )
        }

        composable("update") {
            UpdateScreen(onBack = ::back)
        }

        composable("diagnostics") {
            DiagnosticsScreen(onBack = ::back)
        }

        composable(
            route = "details/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            DetailsScreen(
                isTv = isTv,
                titleId = id,
                onBack = ::back,
                onPlay = { tid, ep -> go("player/$tid/$ep") }
            )
        }

        composable(
            route = "player/{id}/{ep}",
            arguments = listOf(
                navArgument("id") { type = NavType.IntType },
                navArgument("ep") { type = NavType.IntType }
            )
        ) {
            val vm: PlayerViewModel = viewModel()
            PlayerScreen(vm = vm, onBack = ::back)
        }
    }
}
