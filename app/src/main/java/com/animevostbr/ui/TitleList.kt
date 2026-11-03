package com.animevostbr.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.animevostbr.core.Repo
import com.animevostbr.core.Title
import com.animevostbr.core.TitleParts

/**
  * One title in the feed, search or catalog: a poster tile or, in the list layout, a row with
  * details and a Watch button. Long press toggles the favorite in both layouts.
  */
@Composable
fun TitleItem(
    title: Title,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onOpen: (Int) -> Unit,
    onPlay: (id: Int, episode: Int) -> Unit
) {
    var favTick by remember(title.id) { mutableStateOf(0) }
    val isFav = remember(title.id, favTick) { Repo.isFavorite(title.id) }
    val toggleFavorite: () -> Unit = {
        Repo.toggleFavorite(title.toLite())
        favTick++
    }
    if (Repo.prefs.listLayout) {
        TitleRow(
            title = title,
            posterWidth = listPosterWidth(isTv),
            isFavorite = isFav,
            modifier = modifier,
            onFocused = onFocused,
            onClick = { onOpen(title.id) },
            onToggleFavorite = toggleFavorite,
            onPlay = onPlay
        )
    } else {
        PosterCard(
            title = title,
            isTv = isTv,
            isFavorite = isFav,
            modifier = modifier,
            onFocused = onFocused,
            onClick = { onOpen(title.id) },
            onLongClick = toggleFavorite
        )
    }
}

/** Poster widths of the five list sizes; auto is size 2 on phones and 4 on TV. */
private val LIST_POSTER_WIDTHS = listOf(64.dp, 84.dp, 104.dp, 128.dp, 160.dp)

fun listPosterWidth(isTv: Boolean): Dp {
    val size = Repo.prefs.listPosterSize.takeIf { it in 1..5 } ?: if (isTv) 4 else 2
    return LIST_POSTER_WIDTHS[size - 1]
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class) // combinedClickable, FocusRequester.Cancel
@Composable
private fun TitleRow(
    title: Title,
    posterWidth: Dp,
    isFavorite: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: (id: Int, episode: Int) -> Unit
) {
    // site listings carry no description: fetch the full card once the row is shown
    val full by produceState(initialValue = title, title.id) {
        if (title.description.isBlank()) Repo.titleById(title.id)?.let { value = it }
    }
    val t = full
    val parts = remember(t.title) { TitleParts.of(t.title) }
    val progress = remember(t.id) { Repo.progress(t.id) }
    val playFocus = remember { FocusRequester() }
    // Smaller sizes show less, so the row really shrinks with the poster
    val descriptionLines = when {
        posterWidth < 80.dp -> 1
        posterWidth < 100.dp -> 2
        posterWidth < 120.dp -> 3
        posterWidth < 150.dp -> 5
        else -> 7
    }
    val showEnglish = posterWidth >= 80.dp
    val showStars = posterWidth >= 100.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
    ) {
        // The info area and the buttons are separate focus targets: a remote cannot step into
        // a button that lies inside another focusable element. [modifier] carries the caller's
        // focus requester, so it goes on the info area.
        Row(
            modifier = modifier
                .fillMaxWidth()
                // Down goes to Watch: by geometry the remote would pick the heart, the nearer one.
                // Nothing is to the right in a list; without Cancel the home pager switches tabs.
                .focusProperties {
                    down = playFocus
                    right = FocusRequester.Cancel
                }
                .focusBorder(RoundedCornerShape(10.dp))
                .onFocusChanged { if (it.isFocused) onFocused() }
                .combinedClickable(onClick = onClick, onLongClick = onToggleFavorite)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(posterWidth)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101014))
            ) {
                AsyncImage(
                    model = t.poster,
                    contentDescription = parts.ru,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = parts.ru,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (showEnglish && parts.en.isNotBlank()) {
                    Text(
                        text = parts.en,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val episodes = when {
                    t.episodesOut > 0 -> t.totalEpisodes()?.takeIf { it >= t.episodesOut }
                        ?.let { "${t.episodesOut} из $it серий" } ?: "серий: ${t.episodesOut}"
                    else -> parts.tags.firstOrNull()
                }
                val meta = listOfNotNull(t.year.ifBlank { null }, t.type.ifBlank { null }, episodes).joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (t.genres.isNotEmpty()) {
                    Text(
                        text = t.genres.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showStars && t.votes > 0) Stars(count = t.stars, votes = t.votes)
                if (t.description.isNotBlank()) {
                    val clean = remember(t.description) {
                        t.description.replace(Regex("<br\\s*/?>"), " ").replace(Regex("<[^>]+>"), "")
                            .replace(Regex("\\s+"), " ").trim()
                    }
                    Text(
                        text = clean,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = descriptionLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = posterWidth + 20.dp, bottom = 8.dp, end = 8.dp)
        ) {
            // started → resume it; finished → the next one (the player clamps to the last episode)
            val episode = progress?.let { if (it.positionMs > 0) it.episode else it.episode + 1 } ?: 1
            Button(
                onClick = { onPlay(t.id, episode) },
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                modifier = Modifier
                    .height(36.dp)
                    .focusRequester(playFocus)
                    .focusBorder(RoundedCornerShape(50))
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = if (progress?.positionMs?.let { it > 0 } == true) "Продолжить" else "Смотреть",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            IconButton(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .focusProperties { right = FocusRequester.Cancel }
                    .focusBorder(CircleShape),
                onClick = onToggleFavorite
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
                    tint = if (isFavorite) AccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
