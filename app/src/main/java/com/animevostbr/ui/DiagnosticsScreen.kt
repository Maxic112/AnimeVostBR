package com.animevostbr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.animevostbr.core.Diagnostics
import com.animevostbr.core.Diagnostics.Status

/** [runChecks] is replaceable for screen tests: they feed results row by row without network. */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    runChecks: suspend (onUpdate: (List<Diagnostics.Check>) -> Unit) -> Unit = { Diagnostics.run(it) }
) {
    var checks by remember { mutableStateOf<List<Diagnostics.Check>>(emptyList()) }
    var run by remember { mutableStateOf(0) }
    val running = checks.any { it.status == Status.RUNNING } || (run > 0 && checks.isEmpty())
    val buttonFocus = remember { FocusRequester() }

    LaunchedEffect(run) {
        checks = emptyList()
        runChecks { checks = it }
        if (UiState.tvMode) runCatching { buttonFocus.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    text = "Проверка соединения",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        item {
            val summary = Diagnostics.summary(checks)
            val ok = !running && checks.none { it.status == Status.FAIL }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background((if (ok) AccentColor else Color(0xFFFF8A65)).copy(alpha = if (running) 0.08f else 0.16f))
                    .padding(14.dp)
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    enabled = !running,
                    onClick = { run++ },
                    modifier = Modifier
                        .focusRequester(buttonFocus)
                        .focusBorder(RoundedCornerShape(50))
                ) { Text(if (running) "Идёт…" else "Проверить снова") }
            }
        }
        items(checks, key = { it.name }) { CheckRow(it) }
        item {
            Text(
                text = "Каталог и серии приходят из API AnimeVost. Если API не отвечает, приложение " +
                    "автоматически берёт данные с сайта или зеркала (настройка «Сайт AnimeVost»). " +
                    "Видео в любом случае идёт с видеосервера сайта.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun CheckRow(check: Diagnostics.Check) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        when (check.status) {
            Status.RUNNING -> CircularProgressIndicator(color = AccentColor, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            Status.OK -> Icon(Icons.Filled.CheckCircle, contentDescription = "Работает", tint = AccentColor)
            Status.WARN -> Icon(Icons.Filled.Warning, contentDescription = "Есть замечания", tint = Color(0xFFFFB300))
            Status.FAIL -> Icon(Icons.Filled.Error, contentDescription = "Не работает", tint = Color(0xFFE57373))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(check.name, style = MaterialTheme.typography.bodyLarge)
            if (check.detail.isNotBlank()) {
                Text(
                    check.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (check.status != Status.RUNNING) {
            Text(
                "${check.ms} мс",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
