package com.animevostbr.ui

import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import com.animevostbr.core.TitleParts
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.animevostbr.core.DownloadState
import com.animevostbr.core.Downloads
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.animevostbr.core.PlaylistItem
import com.animevostbr.core.Quality
import com.animevostbr.core.Repo
import com.animevostbr.core.Title
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailsScreen(
    isTv: Boolean,
    titleId: Int,
    onBack: () -> Unit,
    onPlay: (Int, Int) -> Unit
) {
    val title by produceState<Title?>(
        initialValue = Repo.lastCached.firstOrNull { it.id == titleId },
        key1 = titleId
    ) {
        value = Repo.titleById(titleId) ?: value
    }

    val playlist by produceState<List<PlaylistItem>?>(initialValue = null, key1 = titleId) {
        value = Repo.playlist(titleId)
    }

    var favTick by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(Repo.prefs.defaultQuality) }
    val progress = remember(titleId, favTick) { Repo.progress(titleId) }
    var thumbs by remember { mutableStateOf(Repo.prefs.episodeThumbs) }

    // Downloads of this title by video id; long press on an episode opens the download dialog
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadsTick by remember { mutableStateOf(0) }
    val downloads by produceState(emptyMap<String, DownloadState>(), titleId, downloadsTick) {
        value = withContext(Dispatchers.IO) {
            Downloads.statuses(context).filter { it.record.lite.id == titleId }
                .associate { it.record.videoId to it.state }
        }
    }
    var downloadFor by remember { mutableStateOf<PlaylistItem?>(null) }
    var pickDownload by remember { mutableStateOf(false) }
    val wideScreen = LocalConfiguration.current.screenWidthDp >= 600
    downloadFor?.let { ep ->
        val t = title
        val existing = downloads[ep.videoId]
        DownloadDialog(
            label = ep.label,
            existing = existing,
            onDismiss = {
                downloadFor = null
                pickDownload = false
            },
            onDownload = { q ->
                downloadFor = null
                pickDownload = false
                if (t != null) scope.launch {
                    val error = Downloads.start(context, t.toLite(), ep, q)
                    Toast.makeText(context, error ?: "Загрузка началась: ${ep.label}, ${q.label}", Toast.LENGTH_SHORT).show()
                    downloadsTick++
                }
            },
            onDelete = {
                downloadFor = null
                pickDownload = false
                Downloads.records(context).firstOrNull { it.lite.id == titleId && it.videoId == ep.videoId }
                    ?.let { Downloads.remove(context, it) }
                downloadsTick++
            }
        )
    }

    // On TV, focus the Watch button right away: the episode list is far below
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(playlist?.size) {
        if (isTv && !playlist.isNullOrEmpty()) runCatching { playFocus.requestFocus() }
    }

    val t = title
    when {
        t == null -> LoadingBox(message = "Загружаем информацию…")
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    Text(
                        text = "Детали",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Row(Modifier.padding(top = 12.dp)) {
                    AsyncImage(
                        model = t.poster,
                        contentDescription = t.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(if (isTv) 230.dp else 130.dp)
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF101014))
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        TitleHeader(t.title)
                        val meta = listOf(t.year, t.type).filter { it.isNotBlank() }.joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        if (t.genres.isNotEmpty()) {
                            Text(
                                text = t.genres.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (t.director.isNotBlank()) {
                            Text(
                                text = "Режиссёр: ${t.director}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (t.votes > 0) Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Stars(count = t.stars, votes = t.votes)
                            Text(
                                text = "  ${t.percent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val nextText = t.nextEpisodeText ?: t.timer?.let { formatTimer(it) }
                        if (nextText != null) {
                            Text(
                                text = "Следующая серия: $nextText",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentColor,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        val total = t.totalEpisodes()
                        if (total != null || t.episodesOut > 0) {
                            Text(
                                text = "Серий вышло: ${if (t.episodesOut > 0) t.episodesOut else "—"}" +
                                    (total?.takeIf { it >= t.episodesOut }?.let { " из $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        val pl = playlist
                        if (!pl.isNullOrEmpty()) {
                            // started episode → resume; finished → next one; otherwise the first
                            val resume = progress?.takeIf { it.positionMs > 0 && it.episode in 1..pl.size }
                            val next = progress?.takeIf { it.positionMs == 0L }?.episode?.plus(1)?.takeIf { it <= pl.size }
                            val ep = resume?.episode ?: next ?: 1
                            val action = if (resume != null) "Продолжить" else "Смотреть"
                            // the second line names the episode: a single line does not fit on phones
                            val detail = if (resume != null) "${resume.episodeLabel()} · ${formatMs(resume.positionMs)}"
                            else pl[ep - 1].label
                            Button(
                                onClick = { onPlay(t.id, ep) },
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .focusRequester(playFocus)
                                    .focusBorder(RoundedCornerShape(50))
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Column(Modifier.padding(start = 6.dp)) {
                                    Text(action, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            val isFav = remember(favTick) { Repo.isFavorite(t.id) }
                            OutlinedButton(
                                modifier = Modifier.focusBorder(RoundedCornerShape(50)),
                                onClick = { Repo.toggleFavorite(t.toLite()); favTick++ }
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(if (isFav) " В избранном" else " В избранное")
                            }
                        }
                    }
                }
            }

            item {
                if (t.description.isNotBlank()) {
                    SectionTitle(text = "Описание", modifier = Modifier.padding(top = 16.dp))
                    // the description contains <br> tags
                    val clean = remember(t.description) { t.description.replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "") }
                    Text(
                        text = clean,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (clean.length > 300) {
                        TextButton(modifier = Modifier.focusBorder(), onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Свернуть" else "Читать полностью")
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    SectionTitle(text = "Серии", modifier = Modifier.weight(1f))
                    IconButton(
                        modifier = Modifier.focusBorder(CircleShape),
                        onClick = {
                            thumbs = !thumbs
                            Repo.prefs.episodeThumbs = thumbs
                        }
                    ) {
                        Icon(
                            imageVector = if (thumbs) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.ViewModule,
                            contentDescription = if (thumbs) "Показывать списком" else "Показывать с превью"
                        )
                    }
                }
            }

            val pl = playlist
            when {
                pl == null -> item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Загружаем список серий…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }

                pl.isEmpty() -> item {
                    Text(
                        text = "Серии не найдены. Возможно, тайтл ещё не озвучен или временно недоступен.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                else -> {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = "Качество:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            for (q in Quality.entries) {
                                val selected = q == quality
                                TextButton(modifier = Modifier.focusBorder(), onClick = {
                                    quality = q
                                    Repo.prefs.defaultQuality = q
                                }) {
                                    Text(
                                        text = q.label,
                                        color = if (selected) AccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // A remote has no obvious long press: this switches clicks on episodes to downloading
                            TextButton(modifier = Modifier.focusBorder(), onClick = { pickDownload = !pickDownload }) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = if (pickDownload) AccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // icon only on phones: the label would break into a column of letters
                                if (wideScreen) Text(
                                    text = if (pickDownload) "Отмена" else "Скачать серии",
                                    color = if (pickDownload) AccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = when {
                                pickDownload && wideScreen -> "Выберите серию для загрузки."
                                pickDownload -> "Выберите серию для загрузки. Отмена — ещё раз нажмите значок загрузки."
                                wideScreen -> "Чтобы скачать серию для просмотра без интернета, нажмите «Скачать серии» или удерживайте серию."
                                else -> "Чтобы скачать серию для просмотра без интернета, удерживайте её или нажмите значок загрузки."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pickDownload) AccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // lazy: long-running titles have over a thousand episodes
                    items(pl, key = { it.index }) { item ->
                        val watched = progress != null && item.index <= progress.episode
                        val isCurrent = progress != null && item.index == progress.episode
                        val download = downloads[item.videoId]
                        if (thumbs) {
                            EpisodeThumbRow(
                                item = item,
                                watched = watched,
                                isCurrent = isCurrent,
                                download = download,
                                onClick = { if (pickDownload) downloadFor = item else onPlay(t.id, item.index) },
                                onLongClick = { downloadFor = item }
                            )
                        } else {
                            EpisodeRow(
                                item = item,
                                watched = watched,
                                isCurrent = isCurrent,
                                download = download,
                                onClick = { if (pickDownload) downloadFor = item else onPlay(t.id, item.index) },
                                onLongClick = { downloadFor = item }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** "скачано" or "скачивается" next to an episode. */
@Composable
private fun DownloadMark(state: DownloadState?) {
    if (state == null || state == DownloadState.FAILED) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
        Icon(
            imageVector = if (state == DownloadState.DONE) Icons.Filled.DownloadDone else Icons.Filled.Download,
            contentDescription = null,
            tint = AccentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (state == DownloadState.DONE) "скачано" else "скачивается",
            style = MaterialTheme.typography.labelSmall,
            color = AccentColor,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    item: PlaylistItem,
    watched: Boolean,
    isCurrent: Boolean,
    download: DownloadState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrent) AccentColor.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
            .focusBorder(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // The server name already includes the episode number, and some entries have none
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrent) AccentColor else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (watched) {
            Text(
                text = if (isCurrent) "вы смотрите" else "просмотрено",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DownloadMark(download)
    }
}

private val timerFormat = SimpleDateFormat("d MMMM, HH:mm", Locale("ru"))

private fun formatTimer(unixSec: Long): String =
    timerFormat.format(Date(unixSec * 1000L))

/** Episode row with a 16:9 frame preview, name and watched mark. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeThumbRow(
    item: PlaylistItem,
    watched: Boolean,
    isCurrent: Boolean,
    download: DownloadState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isCurrent) AccentColor.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
            .focusBorder(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF101014))
        ) {
            AsyncImage(
                model = item.preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (watched && !isCurrent) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) AccentColor else MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (watched) {
                Text(
                    text = if (isCurrent) "вы смотрите" else "просмотрено",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Box(Modifier.padding(top = 4.dp)) { DownloadMark(download) }
        }
    }
}

/**
  * Title header: Russian name, English name below, bracket tags as chips.
  * A long name is truncated; if it overflows, an arrow appears and a tap expands it.
  */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleHeader(title: String) {
    val parts = remember(title) { TitleParts.of(title) }
    var expanded by remember { mutableStateOf(false) }
    var ruCut by remember { mutableStateOf(false) }
    var enCut by remember { mutableStateOf(false) }
    val expandable = expanded || ruCut || enCut

    Column(
        modifier = Modifier
            .animateContentSize()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (expandable) Modifier
                    .focusBorder(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                else Modifier
            )
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = parts.ru,
                // hyphenate long words by syllable instead of breaking them mid-word
                style = MaterialTheme.typography.titleLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) ruCut = it.hasVisualOverflow },
                modifier = Modifier.weight(1f)
            )
            if (expandable) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Свернуть название" else "Показать название полностью",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (parts.en.isNotEmpty()) {
            Text(
                text = parts.en,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) enCut = it.hasVisualOverflow },
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    if (parts.tags.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            parts.tags.forEach { tag ->
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentColor.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Long press on an episode: download it in a chosen quality, or delete the downloaded file. */
@Composable
private fun DownloadDialog(
    label: String,
    existing: DownloadState?,
    onDismiss: () -> Unit,
    onDownload: (Quality) -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                Text(
                    text = when (existing) {
                        DownloadState.DONE -> "Серия скачана и смотрится без интернета."
                        DownloadState.RUNNING, DownloadState.WAITING -> "Серия скачивается. Ход загрузки — в Настройки → Загрузки."
                        DownloadState.FAILED -> "Прошлая загрузка не удалась. Удалите её и скачайте снова."
                        null -> "Скачать для просмотра без интернета. Серия в HD — около 100–200 МБ, в Full HD — в полтора раза больше."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (existing == null) {
                    for (q in Quality.entries) {
                        TextButton(modifier = Modifier.focusBorder(), onClick = { onDownload(q) }) {
                            Text("Скачать в ${q.label}", color = AccentColor)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (existing != null) {
                TextButton(modifier = Modifier.focusBorder(), onClick = onDelete) {
                    Text(if (existing == DownloadState.DONE) "Удалить файл" else "Отменить загрузку", color = AccentColor)
                }
            }
        },
        dismissButton = {
            TextButton(modifier = Modifier.focusBorder(), onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
