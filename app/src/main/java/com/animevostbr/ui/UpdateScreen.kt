package com.animevostbr.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.animevostbr.core.AppRelease
import com.animevostbr.core.AppUpdate
import com.animevostbr.core.Diagnostics
import com.animevostbr.core.Repo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Checks GitHub Releases on open and installs a newer version. */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(true) }
    var latest by remember { mutableStateOf<AppRelease?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    // set while the user is in system settings granting "Install unknown apps"
    var awaitingPermission by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val mainButton = remember { FocusRequester() }

    // the "Check again" button is hidden while checking, so checks never overlap
    fun check() {
        scope.launch {
            checking = true
            error = null
            try {
                latest = AppUpdate.check()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Не удалось проверить обновления. " + Diagnostics.humanize(e)
            }
            checking = false
        }
    }

    fun install(release: AppRelease) {
        if (progress != null) return
        error = null
        AppUpdate.clearInstallRequest()
        if (!AppUpdate.canInstall(context)) {
            awaitingPermission = true
            permissionDenied = false
            AppUpdate.markInstallRequested()
            if (!openPermissionSettings(context)) {
                awaitingPermission = false
                AppUpdate.clearInstallRequest()
                error = "Не удалось открыть настройки. Разрешите AnimeVostBR установку приложений вручную: " +
                    "Настройки → Приложения → Специальный доступ → Установка неизвестных приложений."
            }
            return
        }
        scope.launch {
            progress = 0f
            try {
                val file = AppUpdate.download(context, release) { progress = it }
                val problem = AppUpdate.verify(context, file)
                if (problem != null) error = problem else AppUpdate.startInstall(context, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Не удалось загрузить обновление. " + Diagnostics.humanize(e)
            }
            progress = null
        }
    }

    LaunchedEffect(Unit) {
        // the app was restarted after the install permission was granted
        val pending = AppUpdate.available.takeIf { AppUpdate.hasPendingInstall(context) }
        if (pending != null) install(pending)
        check()
    }

    // Back from the permission screen without a restart (permission left unchanged)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingPermission) {
                awaitingPermission = false
                val release = AppUpdate.available
                if (AppUpdate.canInstall(context) && release != null) install(release) else {
                    permissionDenied = true
                    AppUpdate.clearInstallRequest()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val available = AppUpdate.available
    // Remote: put focus on the main action once the check finishes
    LaunchedEffect(checking, available != null) {
        if (UiState.tvMode && !checking) runCatching { mainButton.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = "Обновление",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            text = "Установлена версия ${AppUpdate.currentVersion}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp)
        )

        when {
            checking && available == null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp, start = 4.dp)
            ) {
                CircularProgressIndicator(color = AccentColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text(text = "Проверяем обновления на GitHub…", modifier = Modifier.padding(start = 12.dp))
            }

            available != null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp)
                ) {
                    UpdateDot()
                    Text(
                        text = "Доступна версия ${available.version}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                Text(
                    text = listOfNotNull(
                        formatDate(available.publishedDate)?.let { "Выпущена $it" },
                        available.apkSize.takeIf { it > 0 }?.let { "%.1f МБ".format(Locale("ru"), it / 1048576.0) }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )

                val p = progress
                Button(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .focusRequester(mainButton)
                        .focusBorder(RoundedCornerShape(50)),
                    colors = ButtonDefaults.buttonColors(containerColor = UpdateOrange),
                    // keep enabled while downloading: a disabled button loses remote focus
                    onClick = { install(available) }
                ) {
                    Text(if (p != null) "Загрузка… ${(p * 100).toInt()}%" else "Установить")
                }
                if (p != null) {
                    LinearProgressIndicator(
                        progress = { p },
                        color = UpdateOrange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
                val hint = when {
                    awaitingPermission || permissionDenied ->
                        "Разрешите установку приложений для AnimeVostBR: включите переключатель " +
                            "у AnimeVostBR и вернитесь в приложение — установка продолжится сама."
                    !AppUpdate.canInstall(context) ->
                        "Android попросит разрешить AnimeVostBR установку приложений — это нужно один раз."
                    else -> null
                }
                if (hint != null) Hint(hint)
                // right under the button: release notes below can be long
                error?.let { ErrorText(it) }

                if (available.notes.isNotBlank()) {
                    SectionTitle(text = "Что нового", modifier = Modifier.padding(top = 24.dp))
                    ReleaseNotes(available.notes)
                }
            }

            latest != null -> {
                Text(
                    text = "У вас последняя версия",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp)
                )
                Text(
                    text = "Проверено " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))
                        .format(Date(Repo.prefs.updateCheckedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }

        if (available == null) error?.let { ErrorText(it) }

        if (available == null && !checking) {
            OutlinedButton(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .focusRequester(mainButton)
                    .focusBorder(RoundedCornerShape(50)),
                onClick = { check() }
            ) { Text("Проверить снова") }
        }

        Hint(
            "Проверка выполняется автоматически раз в сутки. Обновления загружаются из GitHub Releases: " +
                "github.com/${AppUpdate.REPO}",
            Modifier.padding(top = 24.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 16.dp, start = 4.dp)
    )
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier.padding(top = 12.dp)) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

/** Release notes are CHANGELOG markdown: "### Section" headings and "- item" bullets with wrapped lines. */
@Composable
private fun ReleaseNotes(markdown: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    ) {
        for (line in notesLines(markdown)) {
            if (line.startsWith("#")) {
                Text(
                    text = line.trimStart('#', ' '),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text(
                    text = line.replace("`", "").replace(Regex("^[-*] "), "•  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Joins wrapped continuation lines back to their bullet. */
private fun notesLines(markdown: String): List<String> {
    val out = mutableListOf<String>()
    for (raw in markdown.lines()) {
        if (raw.isBlank()) continue
        if (raw.startsWith(" ") && out.isNotEmpty() && !out.last().startsWith("#")) {
            out[out.lastIndex] = out.last() + " " + raw.trim()
        } else out += raw.trim()
    }
    return out
}

/** yyyy-MM-dd → dd.MM.yyyy */
private fun formatDate(isoDate: String): String? =
    isoDate.split('-').takeIf { it.size == 3 }?.let { (y, m, d) -> "$d.$m.$y" }

private fun openPermissionSettings(context: Context): Boolean = try {
    context.startActivity(AppUpdate.permissionIntent(context))
    true
} catch (_: ActivityNotFoundException) {
    false
}
