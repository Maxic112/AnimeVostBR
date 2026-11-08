package com.animevostbr.ui

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

    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - 6
        }
    }
    // Load pages one after another while the end of the grid is in sight
    LaunchedEffect(filter, retryTick) {
        while (!end && error == null) {
            snapshotFlow { nearEnd }.first { it }
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
            Text(
                text = "${filter.kind.label}: ${filter.name}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        when {
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
                items(titles, key = { it.id }) { t ->
                    val first = t.id == titles.first().id
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
