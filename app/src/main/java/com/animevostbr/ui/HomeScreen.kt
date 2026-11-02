package com.animevostbr.ui

import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.clipToBounds
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animevostbr.core.Progress
import com.animevostbr.core.Repo
import com.animevostbr.core.Title

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class) // HorizontalPager, PullToRefresh
@Composable
fun HomeScreen(
    isTv: Boolean,
    onOpen: (Int) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit
) {
    var refresh by rememberSaveable { mutableStateOf(0) }
    var list by remember { mutableStateOf(Repo.lastCached) }
    var failed by remember { mutableStateOf(false) }
    // Returning home must not reload the feed, or the loaded pages would be lost
    LaunchedEffect(refresh) {
        if (refresh > 0 || Repo.lastCached.isEmpty()) {
            list = Repo.fetchLast()
            failed = list.isEmpty()
        }
    }
    var progressList by remember { mutableStateOf(Repo.allProgress()) }

    // Remote: the last focused card (survives a round trip to details)
    var focusedId by rememberSaveable { mutableStateOf<Int?>(null) }
    // Set focus once on entry, otherwise nothing is highlighted on TV
    var restoreFocus by remember { mutableStateOf(isTv) }

    // Infinite scroll: load the next page when fewer than 8 cards remain
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, list.size) {
        if (nearEnd && !Repo.lastExhausted) list = Repo.loadMoreLast()
    }

    // Refresh: pull down on phones, header button on TV
    val context = LocalContext.current
    val pullState = rememberPullToRefreshState()
    var tvRefreshing by remember { mutableStateOf(false) }
    val headerFocus = remember { FocusRequester() }
    suspend fun reload() {
        val before = list.associate { it.id to it.episodesOut }
        val fresh = Repo.fetchLast()
        list = fresh
        failed = fresh.isEmpty()
        progressList = Repo.allProgress()
        gridState.scrollToItem(0)
        // a new title or a new episode of a known one
        val changed = fresh.count { before[it.id] != it.episodesOut }
        val message = when {
            !Repo.lastFetchOk -> "Не удалось обновить: нет соединения"
            before.isEmpty() -> null
            changed > 0 -> "Обновлено: новых серий и тайтлов — $changed"
            else -> "Новых серий пока нет"
        }
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            reload()
            pullState.endRefresh()
        }
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "AnimeVostBR",
                style = MaterialTheme.typography.headlineSmall,
                color = AccentColor,
                modifier = Modifier.weight(1f)
            )
            if (isTv) {
                IconButton(
                    modifier = Modifier
                        .focusRequester(headerFocus)
                        .focusBorder(CircleShape),
                    // keep enabled while loading: a disabled button loses remote focus
                    onClick = {
                        if (!tvRefreshing) scope.launch {
                            tvRefreshing = true
                            reload()
                            tvRefreshing = false
                        }
                    }
                ) {
                    if (tvRefreshing) CircularProgressIndicator(color = AccentColor, modifier = Modifier.size(22.dp))
                    else Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                }
            }
            IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Поиск") }
            IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onFavorites) { Icon(Icons.Filled.FavoriteBorder, contentDescription = "Избранное") }
            IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Настройки") }
        }

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = AccentColor,
            modifier = Modifier
                .padding(top = 4.dp, bottom = 8.dp)
                // remote: nothing is above the tabs on the left (icons are on the right) — route Up to the header
                .focusProperties { if (isTv) up = headerFocus }
        ) {
            val titles = listOf(
                "Новинки",
                if (progressList.isEmpty()) "Продолжить" else "Продолжить (${progressList.size})"
            )
            titles.forEachIndexed { i, title ->
                Tab(
                    modifier = Modifier.focusBorder(),
                    selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(title) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> Box(
                    Modifier
                        .nestedScroll(pullState.nestedScrollConnection)
                        .clipToBounds() // at rest the indicator hides behind the top edge of the feed
                ) {
                    FeedPage(
                        isTv = isTv,
                        list = list,
                        failed = failed,
                        gridState = gridState,
                        focusTargetId = if (restoreFocus) focusedId ?: list.firstOrNull()?.id else null,
                        onFocusRestored = { restoreFocus = false },
                        onFocused = { focusedId = it },
                        onRetry = { refresh++ },
                        onOpen = onOpen
                    )
                    if (pullState.progress > 0f || pullState.isRefreshing) PullToRefreshContainer(
                        state = pullState,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = AccentColor,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
                else -> ContinuePage(
                    progressList = progressList,
                    onOpen = onOpen,
                    onRemove = { id ->
                        Repo.removeProgress(id)
                        progressList = Repo.allProgress()
                    },
                    onClear = {
                        Repo.clearProgress()
                        progressList = emptyList()
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedPage(
    isTv: Boolean,
    list: List<Title>,
    failed: Boolean,
    gridState: LazyGridState,
    focusTargetId: Int?,
    onFocusRestored: () -> Unit,
    onFocused: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpen: (Int) -> Unit
) {
    when {
        failed -> ErrorBox(
            message = "Не удалось загрузить ленту.\nПроверьте интернет-соединение.",
            onRetry = onRetry
        )
        list.isEmpty() -> LoadingBox(message = "Загружаем каталог…")
        else -> Column {
        // the API failed and the feed came from the site — say so
        Repo.feedFromSite?.let { host ->
            Text(
                text = "API не отвечает — данные с сайта $host",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        LazyVerticalGrid(
            columns = feedGridCells(isTv),
            state = gridState,
            // room for the scaled-up focused card, otherwise its border is clipped
            contentPadding = PaddingValues(if (isTv) 8.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list, key = { it.id }) { t ->
                var favTick by remember(t.id) { mutableStateOf(0) }
                val isFav = remember(favTick) { Repo.isFavorite(t.id) }
                val focusRequester = remember { FocusRequester() }
                if (t.id == focusTargetId) {
                    LaunchedEffect(Unit) {
                        runCatching { focusRequester.requestFocus() }
                        onFocusRestored()
                    }
                }
                PosterCard(
                    title = t,
                    isTv = isTv,
                    isFavorite = isFav,
                    modifier = Modifier.focusRequester(focusRequester),
                    onFocused = { onFocused(t.id) },
                    onClick = { onOpen(t.id) },
                    onLongClick = {
                        Repo.toggleFavorite(t.toLite())
                        favTick++
                    }
                )
            }
        }
    }
    }
}

@Composable
private fun ContinuePage(
    progressList: List<Progress>,
    onOpen: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit
) {
    if (progressList.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Здесь появятся начатые серии",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить «Продолжить просмотр»?") },
            text = { Text("Прогресс всех начатых серий будет удалён.") },
            confirmButton = {
                TextButton(modifier = Modifier.focusBorder(), onClick = { confirmClear = false; onClear() }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(modifier = Modifier.focusBorder(), onClick = { confirmClear = false }) { Text("Отмена") }
            }
        )
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(modifier = Modifier.focusBorder(), onClick = { confirmClear = true }) { Text("Очистить всё") }
            }
        }
        items(progressList, key = { it.lite.id }) { p ->
            RowCard(
                lite = p.lite,
                subtitle = "${p.episodeLabel()} • ${formatMs(p.positionMs)}",
                onClick = { onOpen(p.lite.id) },
                trailing = {
                    IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = { onRemove(p.lite.id) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Убрать из «Продолжить просмотр»",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
