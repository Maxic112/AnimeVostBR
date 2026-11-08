package com.animevostbr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.animevostbr.core.Repo
import com.animevostbr.core.Schedule
import com.animevostbr.core.ScheduleDay
import com.animevostbr.core.TitleParts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RU = Locale("ru")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", RU)

/** Upcoming episodes of the favorites, by day. */
@Composable
fun ScheduleScreen(onOpen: (Int) -> Unit, onBack: () -> Unit) {
    val favoritesCount = remember { Repo.favorites().size }
    var days by remember { mutableStateOf<List<ScheduleDay>?>(null) }
    var undated by remember { mutableIntStateOf(0) }
    val zone = remember { ZoneId.systemDefault() }

    LaunchedEffect(Unit) {
        if (favoritesCount == 0) return@LaunchedEffect
        val (d, u) = Schedule.days(Repo.favoriteCards(), System.currentTimeMillis(), zone)
        days = d
        undated = u.size
    }

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
                text = "Расписание",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            text = "Ближайшие серии тайтлов из избранного, по вашему времени.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )

        val list = days
        when {
            favoritesCount == 0 -> CenterHint("Добавьте тайтлы в избранное долгим нажатием на карточку —\nздесь появятся даты их новых серий.")
            list == null -> LoadingBox("Загружаем даты серий…")
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp)
            ) {
                if (list.isEmpty()) item {
                    Text(
                        text = "Ни у одного тайтла из избранного пока нет даты следующей серии.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                for (day in list) {
                    item(key = day.date.toString()) {
                        Text(
                            text = dayTitle(day.date, zone),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentColor,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                    }
                    items(day.entries, key = { "${day.date}-${it.title.id}" }) { e ->
                        val time = Instant.ofEpochMilli(e.at).atZone(zone).format(TIME_FORMAT)
                        RowCard(
                            lite = e.title.toLite().copy(title = TitleParts.of(e.title.title).ru),
                            subtitle = listOfNotNull(time, e.episode).joinToString(" • "),
                            onClick = { onOpen(e.title.id) }
                        )
                    }
                }
                if (undated > 0) item {
                    Text(
                        text = "Ещё из избранного без даты: $undated — вышли целиком или дата пока неизвестна.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

private fun dayTitle(date: LocalDate, zone: ZoneId): String {
    val today = LocalDate.now(zone)
    val text = date.format(DAY_FORMAT)
    return when (date) {
        today -> "Сегодня, ${text.substringAfter(", ")}"
        today.plusDays(1) -> "Завтра, ${text.substringAfter(", ")}"
        else -> text.replaceFirstChar { it.titlecase(RU) }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
