package com.animevostbr.ui

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import com.animevostbr.core.CatalogView
import com.animevostbr.core.CatalogSort
import com.animevostbr.core.CatalogKind
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.animevostbr.core.CatalogFilter
import com.animevostbr.core.Diagnostics
import com.animevostbr.core.Repo
import com.animevostbr.core.Title
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Titles of one genre, type or year from the site, ten per page with infinite scroll. */
@Composable
fun CatalogScreen(
    isTv: Boolean,
    filter: CatalogFilter,
    onOpen: (Int) -> Unit,
    onPlay: (id: Int, episode: Int) -> Unit,
    onBack: () -> Unit
) {
    var titles by remember(filter) { mutableStateOf<List<Title>>(emptyList()) }
    var page by remember(filter) { mutableStateOf(0) }
    var end by remember(filter) { mutableStateOf(false) }
    var loading by remember(filter) { mutableStateOf(false) }
    var error by remember(filter) { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    // Extra filters and order over the loaded titles (see CatalogView)
    var view by remember(filter) { mutableStateOf(CatalogView()) }
    var ratings by remember(filter) { mutableStateOf<Map<Int, Double>>(emptyMap()) }
    var showView by remember { mutableStateOf(false) }
    val shown = remember(titles, view, ratings) { view.apply(titles, ratings) }

    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 6
        }
    }
    // Load pages one after another while the end of the grid is in sight, or while a filter leaves
    // too few titles to fill the screen (up to MAX_FILTER_PAGES pages)
    LaunchedEffect(filter, retryTick) {
        while (!end && error == null) {
            snapshotFlow { nearEnd || (!view.isDefault && shown.size < MIN_FILTERED && page < MAX_FILTER_PAGES) }.first { it }
            loading = true
            try {
                val next = Repo.catalogPage(filter, page + 1)
                val known = titles.mapTo(HashSet()) { it.id }
                val fresh = next.filter { it.id !in known }
                if (fresh.isEmpty()) end = true
                titles = titles + fresh
                page++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = Diagnostics.humanize(e)
            } finally {
                loading = false
            }
        }
    }

    // Site cards have no rating: for the rating order, fetch the full cards of the loaded titles
    var ratingsLoading by remember { mutableStateOf(false) }
    LaunchedEffect(view.sort, titles.size) {
        if (view.sort != CatalogSort.RATING) return@LaunchedEffect
        val missing = titles.take(MAX_RATED).filter { it.id !in ratings && it.votes == 0 }
        if (missing.isEmpty()) return@LaunchedEffect
        ratingsLoading = true
        try {
            for (chunk in missing.chunked(4)) {
                val got = coroutineScope {
                    chunk.map { t -> async { t.id to Repo.titleById(t.id)?.takeIf { it.votes > 0 }?.average } }.awaitAll()
                }
                ratings = ratings + got.mapNotNull { (id, r) -> r?.let { id to it } }
            }
        } finally {
            ratingsLoading = false
        }
    }

    if (showView) CatalogViewDialog(
        kind = filter.kind,
        titles = titles,
        current = view,
        onApply = { view = it; showView = false },
        onDismiss = { showView = false }
    )

    val firstFocus = remember { FocusRequester() }
    var focusPlaced by remember(filter) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(text = "${filter.kind.label}: ${filter.name}", style = MaterialTheme.typography.titleLarge)
                if (!view.isDefault || ratingsLoading) Text(
                    text = view.summary() + if (ratingsLoading) " · загружаем рейтинги…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentColor
                )
            }
            TextButton(modifier = Modifier.focusBorder(), onClick = { showView = true }) {
                Icon(Icons.Filled.FilterList, contentDescription = null)
                Text("Фильтры", modifier = Modifier.padding(start = 4.dp))
            }
        }

        when {
            titles.isNotEmpty() && shown.isEmpty() && (end || page >= MAX_FILTER_PAGES) -> ErrorBox(
                message = "Под выбранные фильтры ничего не нашлось среди ${titles.size} тайтлов.",
                onRetry = { view = CatalogView() }
            )
            titles.isNotEmpty() && shown.isEmpty() -> LoadingBox(message = "Ищем подходящие тайтлы…")
            titles.isEmpty() && error != null -> ErrorBox(
                message = "Не удалось загрузить список.\n$error",
                onRetry = { error = null; retryTick++ }
            )
            titles.isEmpty() && end -> ErrorBox(message = "Здесь пока ничего нет.", onRetry = null)
            titles.isEmpty() -> LoadingBox(message = "Загружаем…")
            else -> LazyVerticalGrid(
                columns = feedGridCells(isTv),
                state = gridState,
                contentPadding = PaddingValues(if (isTv) 8.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp)
            ) {
                items(shown, key = { it.id }) { t ->
                    val first = t.id == shown.first().id
                    if (first && isTv && !focusPlaced) {
                        LaunchedEffect(Unit) {
                            runCatching { firstFocus.requestFocus() }
                            focusPlaced = true
                        }
                    }
                    TitleItem(
                        title = t,
                        isTv = isTv,
                        modifier = if (first) Modifier.focusRequester(firstFocus) else Modifier,
                        onOpen = onOpen,
                        onPlay = onPlay
                    )
                }
                if (loading || error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            if (error != null) ErrorBox(
                                message = "Не удалось загрузить продолжение.",
                                onRetry = { error = null; retryTick++ }
                            ) else CircularProgressIndicator(color = AccentColor, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/** At least this many titles should pass the filters before the loader waits for scrolling again. */
private const val MIN_FILTERED = 20
/** Pages loaded in search of filtered titles (10 titles each). */
private const val MAX_FILTER_PAGES = 30
/** Ratings fetched for the rating order: one API call per title. */
private const val MAX_RATED = 120

/** Order and filters; the choice applies with "Применить". Built of buttons, so a remote reaches every item. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogViewDialog(
    kind: CatalogKind,
    titles: List<Title>,
    current: CatalogView,
    onApply: (CatalogView) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(current) }
    val (types, years, genres) = remember(titles) { CatalogView.options(titles, kind) }

    @Composable
    fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
        TextButton(modifier = Modifier.focusBorder(), onClick = onClick) {
            Text(
                text = if (selected) "✓ $label" else label,
                color = if (selected) AccentColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    @Composable
    fun Group(title: String, values: List<String>, chosen: String?, onPick: (String?) -> Unit) {
        if (values.isEmpty()) return
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        FlowRow {
            Option("Все", chosen == null) { onPick(null) }
            for (v in values) Option(v, v.equals(chosen, ignoreCase = true)) { onPick(v) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтры и сортировка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Сортировка", style = MaterialTheme.typography.titleSmall)
                FlowRow {
                    for (s in CatalogSort.entries) Option(s.label, draft.sort == s) { draft = draft.copy(sort = s) }
                }
                Group("Тип", types, draft.type) { draft = draft.copy(type = it) }
                Group("Год", years, draft.year) { draft = draft.copy(year = it) }
                Group("Жанр", genres, draft.genre) { draft = draft.copy(genre = it) }
                Text(
                    text = "Варианты — из загруженных тайтлов (${titles.size}); при фильтре приложение догружает выборку само.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.focusBorder(), onClick = { onApply(draft) }) { Text("Применить", color = AccentColor) }
        },
        dismissButton = {
            TextButton(modifier = Modifier.focusBorder(), onClick = { onApply(CatalogView()) }) { Text("Сбросить") }
        }
    )
}
