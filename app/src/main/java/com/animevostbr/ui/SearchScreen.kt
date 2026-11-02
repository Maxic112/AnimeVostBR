package com.animevostbr.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.animevostbr.core.Title
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.animevostbr.core.Repo

@Composable
fun SearchScreen(
    isTv: Boolean,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submit = {
        submitted = query.trim()
        keyboard?.hide()
    }
    // Focus the field right away, otherwise phones need an extra tap
    LaunchedEffect(Unit) { if (submitted == null) focusRequester.requestFocus() }

    // Debounced suggestions (350 ms) so the API is not hit on every keystroke
    var suggestions by remember { mutableStateOf<List<Title>>(emptyList()) }
    LaunchedEffect(query, submitted) {
        val q = query.trim()
        if (q.length < 2 || q == submitted) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        suggestions = Repo.suggest(q)
    }

    val results by produceState<List<com.animevostbr.core.Title>?>(initialValue = null, key1 = submitted) {
        submitted?.let { value = Repo.search(it) } ?: run { value = emptyList() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Поиск аниме…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .focusRequester(focusRequester)
                    // Enter on a hardware keyboard or remote also submits
                    .onPreviewKeyEvent {
                        when (it.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                if (it.type == KeyEventType.KeyUp) submit()
                                true
                            }
                            // Remote: the field swallows Down (moves the cursor) — move focus to the results
                            Key.DirectionDown -> {
                                if (it.type == KeyEventType.KeyDown) focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            else -> false
                        }
                    }
            )
        }

        when {
            suggestions.isNotEmpty() -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                items(suggestions, key = { it.id }) { t ->
                    SuggestionRow(title = t, onClick = { onOpen(t.id) })
                }
            }
            submitted == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = "Введите название аниме\nи нажмите поиск",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            results == null -> LoadingBox(message = "Ищем…")
            results!!.isEmpty() -> ErrorBox(
                message = "По запросу «$submitted» ничего не найдено.",
                onRetry = { submitted = query.trim() }
            )
            else -> LazyVerticalGrid(
                columns = feedGridCells(isTv),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp)
            ) {
                items(results!!, key = { it.id }) { t ->
                    var favTick by remember(t.id) { mutableStateOf(0) }
                    val isFav = remember(favTick) { Repo.isFavorite(t.id) }
                    PosterCard(
                        title = t,
                        isTv = isTv,
                        isFavorite = isFav,
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
private fun SuggestionRow(title: Title, onClick: () -> Unit) {
    val ru = title.title.substringBefore(" / ").trim()
    val en = title.title.substringAfter(" / ", "").substringBefore(" [").trim()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .focusBorder(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        AsyncImage(
            model = title.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = ru,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOf(en, title.year).filter { it.isNotBlank() }.joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
