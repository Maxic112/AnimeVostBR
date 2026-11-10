package com.animevostbr.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Mic
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.speech.RecognizerIntent
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import com.animevostbr.core.Catalog
import com.animevostbr.core.CatalogFilter
import com.animevostbr.core.Repo

@Composable
fun SearchScreen(
    isTv: Boolean,
    onOpen: (Int) -> Unit,
    onPlay: (id: Int, episode: Int) -> Unit,
    onCatalog: (CatalogFilter) -> Unit,
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
    // Phones: focus the field right away, otherwise they need an extra tap. TV: start on the
    // catalog — the on-screen keyboard would cover it; Up reaches the field.
    // Only on the first visit: coming back from a genre must not pop the keyboard up again.
    val catalogFocus = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (submitted == null && !initialFocusDone) {
            runCatching { if (isTv) catalogFocus.requestFocus() else focusRequester.requestFocus() }
        }
        initialFocusDone = true
    }

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

    // Voice search: the microphone button on a TV remote or a phone; hidden without a speech recognizer
    val context = LocalContext.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Название аниме")
    }
    val voiceAvailable = remember { context.packageManager.queryIntentActivities(voiceIntent, 0).isNotEmpty() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            query = it
            submit()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
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
            if (voiceAvailable) {
                IconButton(
                    modifier = Modifier.focusBorder(CircleShape),
                    onClick = { runCatching { voice.launch(voiceIntent) } }
                ) { Icon(Icons.Filled.Mic, contentDescription = "Голосовой поиск") }
            }
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
            // nothing searched yet or the field was cleared: offer the catalog
            submitted.isNullOrEmpty() || query.isBlank() -> CatalogBrowser(firstFocus = catalogFocus, onCatalog = onCatalog)
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
                    TitleItem(title = t, isTv = isTv, onOpen = onOpen, onPlay = onPlay)
                }
            }
        }
    }
}

/** Shown while the query is empty: browse by genre, type and year, like the site menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogBrowser(firstFocus: FocusRequester, onCatalog: (CatalogFilter) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp)
    ) {
        Text(
            text = "Введите название или выберите жанр, тип или год",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        for ((section, filters) in listOf(
            "Жанры" to Catalog.genres,
            "Тип" to Catalog.types,
            "Год" to Catalog.years
        )) {
            SectionTitle(text = section, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (f in filters) {
                    Text(
                        text = f.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .then(if (f == Catalog.genres.first()) Modifier.focusRequester(firstFocus) else Modifier)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .focusBorder(RoundedCornerShape(50))
                            .clickable { onCatalog(f) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
        PosterImage(
            url = title.poster,
            contentDescription = null,
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
