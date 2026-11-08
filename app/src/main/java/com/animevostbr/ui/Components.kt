package com.animevostbr.ui

import androidx.compose.foundation.lazy.grid.GridCells
import com.animevostbr.core.Repo
import androidx.compose.foundation.border
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.animevostbr.core.Title
import com.animevostbr.core.TitleLite
import com.animevostbr.core.TitlesParser
import java.util.Locale

/** Title poster card for touch and D-pad (focus scales it up). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: Title,
    isTv: Boolean,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.06f else 1f,
        label = "cardScale"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .then(if (focused) Modifier.border(3.dp, AccentColor, RoundedCornerShape(10.dp)) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF101014)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = title.poster,
                contentDescription = title.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "В избранном",
                    tint = AccentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
            if (title.votes > 0) {
                RatingBadge(
                    title = title,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                )
            }
            if (focused && isTv) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentColor.copy(alpha = 0.10f))
                )
            }
        }
        Text(
            text = title.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
        Text(
            text = listOf(title.year, title.type).filter { it.isNotBlank() }.joinToString(" • "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            // types can be long (a full-length film); without an ellipsis only "2019 •" would remain
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp, bottom = 2.dp)
        )
    }
}

/** Compact card for lists (favorites, continue watching). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowCard(
    lite: TitleLite,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .focusBorder()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        AsyncImage(
            // favorites/progress may still hold old https links to static.openni.ru
            model = TitlesParser.posterUrl(lite.poster),
            contentDescription = lite.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 64.dp, height = 90.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = lite.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        trailing?.invoke()
    }
}

/** Average rating over the poster, e.g. "★ 4,3". */
@Composable
fun RatingBadge(title: Title, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Рейтинг",
            tint = AccentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = String.format(Locale("ru"), "%.1f", title.average),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(start = 3.dp)
        )
    }
}

/** Five-star rating: `rating` is a sum, stars = round(rating / votes). */
@Composable
fun Stars(count: Int, votes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            Icon(
                imageVector = if (i <= count) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (i <= count) AccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = if (votes > 0) " $votes" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LoadingBox(message: String = "Загрузка…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentColor)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            if (onRetry != null) {
                TextButton(modifier = Modifier.focusBorder(), onClick = onRetry) { Text("Повторить") }
            }
        }
    }
}

/** Feed/search columns from settings; defaults to 3 on phones and by width on TV. The list layout is one column. */
fun feedGridCells(isTv: Boolean): GridCells {
    val n = Repo.prefs.gridColumns
    return when {
        Repo.prefs.listLayout -> GridCells.Fixed(1)
        n > 0 -> GridCells.Fixed(n)
        isTv -> GridCells.Adaptive(170.dp)
        else -> GridCells.Fixed(3)
    }
}

val AccentColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/**
  * A clearly visible focus border for TV remotes — the default Material highlight is barely
  * visible from a couch. Buttons get no focus in touch mode, so phones never show it.
  * Apply before `clickable` in the modifier chain.
  */
fun Modifier.focusBorder(shape: Shape = RoundedCornerShape(10.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = AccentColor
    onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(3.dp, color, shape) else Modifier)
}

/** Marks the settings icon and the update entry while a new app version is available. */
val UpdateOrange = Color(0xFFFF9800)

@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(UpdateOrange, CircleShape)
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}
