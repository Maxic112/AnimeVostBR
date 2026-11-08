package com.animevostbr.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.animevostbr.core.DownloadState
import com.animevostbr.core.DownloadStatus
import com.animevostbr.core.Downloads
import com.animevostbr.core.TitleParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Downloaded episodes: play offline or delete; running downloads show their progress. */
@Composable
fun DownloadsScreen(onPlay: (titleId: Int, episode: Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var list by remember { mutableStateOf<List<DownloadStatus>>(emptyList()) }
    var free by remember { mutableStateOf(0L) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        while (true) {
            list = withContext(Dispatchers.IO) { Downloads.statuses(context) }
            free = withContext(Dispatchers.IO) { Downloads.freeBytes(context) }
            if (list.none { it.state == DownloadState.RUNNING || it.state == DownloadState.WAITING }) break
            delay(1000)
        }
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
            Text(text = "Загрузки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            text = "Свободно на устройстве: ${Formatter.formatShortFileSize(context, free)}. " +
                "Скачать серию — кнопка «Скачать серии» на странице тайтла.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Скачанных серий пока нет.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp)
            ) {
                items(list, key = { it.record.dmId }) { s ->
                    val r = s.record
                    val state = when (s.state) {
                        DownloadState.DONE -> "скачано · ${Formatter.formatShortFileSize(context, s.total)}"
                        DownloadState.RUNNING -> if (s.total > 0) "скачивается: ${s.bytes * 100 / s.total}% из ${Formatter.formatShortFileSize(context, s.total)}" else "скачивается…"
                        DownloadState.WAITING -> "ожидает сети"
                        DownloadState.FAILED -> "ошибка загрузки — удалите и скачайте снова"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RowCard(
                            lite = r.lite.copy(title = TitleParts.of(r.lite.title).ru),
                            subtitle = "${r.label} · ${r.quality.label} · $state",
                            onClick = { if (s.state == DownloadState.DONE) onPlay(r.lite.id, r.index) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = {
                            Downloads.remove(context, r)
                            tick++
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = if (s.state == DownloadState.DONE) "Удалить" else "Отменить")
                        }
                    }
                }
            }
        }
    }
}
