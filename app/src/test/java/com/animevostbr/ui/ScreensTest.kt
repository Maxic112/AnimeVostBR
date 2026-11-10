package com.animevostbr.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.animevostbr.core.AppUpdate
import com.animevostbr.core.Diagnostics
import com.animevostbr.core.Repo
import kotlinx.coroutines.delay
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
  * Opens every screen under Robolectric, on a phone and in TV mode. Release 1.8.0 shipped a crash that only
  * showed when "Connection check" added result rows; these tests run in CI before every release.
  * A plain Application: the real one schedules WorkManager jobs, which Robolectric has no runtime for.
  */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Repo.init(context)
        AppUpdate.init(context)
        UiState.tvMode = false
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { AppTheme { content() } }
        compose.waitForIdle()
    }

    @Test
    fun diagnostics_addsRowsOneByOne() {
        val names = (1..15).map { "Проверка $it" }
        show {
            DiagnosticsScreen(onBack = {}, runChecks = { onUpdate ->
                val done = mutableListOf<Diagnostics.Check>()
                for (name in names) {
                    onUpdate(done + Diagnostics.Check(name))
                    delay(10)
                    done += Diagnostics.Check(name, Diagnostics.Status.OK, "ok", 5)
                    onUpdate(done.toList())
                    delay(10)
                }
            })
        }
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithText("Всё работает").assertExists()
        assertTrue(compose.onAllNodesWithText("Проверка 1").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun phoneScreens_open() = openAll()

    @Test
    fun tvScreens_open() {
        UiState.tvMode = true
        openAll()
    }

    private fun openAll() {
        val tv = UiState.tvMode
        val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
            "home" to { HomeScreen(tv, {}, { _, _ -> }, {}, {}, {}, {}) },
            "settings" to { SettingsScreen({}, {}, {}, {}, {}) },
            "search" to { SearchScreen(tv, {}, { _, _ -> }, {}, {}) },
            "favorites" to { FavoritesScreen({}, {}) },
            "schedule" to { ScheduleScreen({}, {}) },
            "downloads" to { DownloadsScreen({ _, _ -> }, {}) },
            "sync" to { SyncScreen {} },
            "update" to { UpdateScreen {} },
            "details" to { DetailsScreen(tv, 4032, {}, { _, _ -> }) },
            "catalog" to { CatalogScreen(tv, com.animevostbr.core.Catalog.genres.first(), {}, { _, _ -> }, {}) }
        )
        // Spinners are endless animations: with the clock running by itself Compose never gets idle,
        // so time moves by hand, a few seconds per screen
        compose.mainClock.autoAdvance = false
        val current = androidx.compose.runtime.mutableStateOf(0)
        compose.setContent { AppTheme { screens[current.value].second() } }
        for (i in screens.indices) {
            current.value = i
            repeat(30) { compose.mainClock.advanceTimeByFrame() }
            compose.mainClock.advanceTimeBy(3_000)
        }
    }
}
