package com.animevostbr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TextButton
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import com.animevostbr.core.Sites
import com.animevostbr.core.SiteMode
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.OutlinedButton
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import com.animevostbr.AnimeVostApp
import com.animevostbr.core.AppUpdate
import com.animevostbr.core.Quality
import com.animevostbr.core.Repo
import com.animevostbr.core.ThemeMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    onUpdate: () -> Unit,
    onSync: () -> Unit,
    onDownloads: () -> Unit
) {
    val context = LocalContext.current
    var theme by remember { mutableStateOf(Repo.prefs.themeMode) }
    var quality by remember { mutableStateOf(Repo.prefs.defaultQuality) }
    var mobileQuality by remember { mutableStateOf(Repo.prefs.mobileQuality) }
    var notifs by remember { mutableStateOf(Repo.prefs.notificationsEnabled) }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    var columns by remember { mutableStateOf(Repo.prefs.gridColumns) }
    var listLayout by remember { mutableStateOf(Repo.prefs.listLayout) }
    var posterSize by remember { mutableStateOf(Repo.prefs.listPosterSize) }
    var siteMode by remember { mutableStateOf(Sites.mode) }
    var mirror by remember { mutableStateOf(Sites.mirrorHost) }
    var mirrorInfo by remember { mutableStateOf(mirrorStatus()) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var thumbs by remember { mutableStateOf(Repo.prefs.episodeThumbs) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(modifier = Modifier.focusBorder(CircleShape), onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        val update = AppUpdate.available
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .focusBorder(RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onUpdate)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(text = "Обновление", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (update != null) "Доступна версия ${update.version}" else "Версия $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (update != null) UpdateOrange else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (update != null) UpdateDot(Modifier.padding(end = 8.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
        NavRow(Icons.Filled.Sync, "Синхронизация", "Избранное и прогресс между телефоном и телевизором", onSync)
        NavRow(Icons.Filled.Download, "Загрузки", "Скачанные серии для просмотра без интернета", onDownloads)

        SectionTitle(text = "Оформление", modifier = Modifier.padding(top = 16.dp))
        Text(
            text = "По умолчанию — чёрная тема. Можно следовать системной.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingRadio(
            label = "Тёмная (чёрная)",
            selected = theme == ThemeMode.DARK,
            onClick = {
                theme = ThemeMode.DARK
                Repo.prefs.themeMode = ThemeMode.DARK
                UiState.themeMode = ThemeMode.DARK
            }
        )
        SettingRadio(
            label = "Системная",
            selected = theme == ThemeMode.SYSTEM,
            onClick = {
                theme = ThemeMode.SYSTEM
                Repo.prefs.themeMode = ThemeMode.SYSTEM
                UiState.themeMode = ThemeMode.SYSTEM
            }
        )
        SettingRadio(
            label = "Светлая",
            selected = theme == ThemeMode.LIGHT,
            onClick = {
                theme = ThemeMode.LIGHT
                Repo.prefs.themeMode = ThemeMode.LIGHT
                UiState.themeMode = ThemeMode.LIGHT
            }
        )

        SectionTitle(text = "Сайт AnimeVost", modifier = Modifier.padding(top = 16.dp))
        Text(
            text = "Каталог и серии приходят из API AnimeVost. Сайт нужен для поиска и как запасной " +
                "источник, если API перестанет отвечать. Основной сайт часто блокируют — тогда помогает зеркало.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        for ((m, label) in listOf(
            SiteMode.AUTO to "Авто — основной сайт, при недоступности зеркало",
            SiteMode.MAIN to "Основной сайт (${Sites.MAIN_HOST})",
            SiteMode.MIRROR to "Зеркало ($mirror)"
        )) {
            SettingRadio(
                label = label,
                selected = siteMode == m,
                onClick = {
                    siteMode = m
                    Repo.prefs.siteMode = m.ordinal
                }
            )
        }
        Text(
            text = mirrorInfo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        // both buttons in one row: Left/Right between them, Down goes to the next section
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.focusBorder(RoundedCornerShape(50)),
                // keep enabled while checking: a disabled button loses remote focus
                onClick = {
                    if (!checking) scope.launch {
                        checking = true
                        val r = Sites.refreshMirror(force = true)
                        checking = false
                        mirror = Sites.mirrorHost
                        mirrorInfo = mirrorStatus()
                        val text = when {
                            r == null || !r.alive -> "Не удалось открыть ни одно зеркало — проверьте интернет"
                            r.changed -> "Найдено новое зеркало: ${r.host}"
                            else -> "Зеркало актуально: ${r.host}"
                        }
                        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                    }
                }
            ) { Text(if (checking) "Ищем…" else "Проверить зеркало") }
            OutlinedButton(
                onClick = onDiagnostics,
                modifier = Modifier.focusBorder(RoundedCornerShape(50))
            ) { Text("Проверка соединения") }
        }

        SectionTitle(text = "Вид ленты, поиска и каталога", modifier = Modifier.padding(top = 16.dp))
        SettingRadio(
            label = "Плитки — постеры сеткой",
            selected = !listLayout,
            onClick = { listLayout = false; Repo.prefs.listLayout = false }
        )
        SettingRadio(
            label = "Список — постер, описание и кнопка «Смотреть»",
            selected = listLayout,
            onClick = { listLayout = true; Repo.prefs.listLayout = true }
        )

        if (!listLayout) {
            SectionTitle(text = "Плиток в ряд", modifier = Modifier.padding(top = 16.dp))
            for ((value, label) in listOf(
                0 to "Авто (3 на телефоне, по ширине экрана на TV)",
                2 to "2 — крупные",
                3 to "3",
                4 to "4",
                5 to "5 — мелкие"
            )) {
                SettingRadio(
                    label = label,
                    selected = columns == value,
                    onClick = {
                        columns = value
                        Repo.prefs.gridColumns = value
                    }
                )
            }
        } else {
            SectionTitle(text = "Размер превью в списке", modifier = Modifier.padding(top = 16.dp))
            for ((value, label) in listOf(
                0 to "Авто (поменьше на телефоне, крупнее на TV)",
                1 to "1 — самое маленькое",
                2 to "2",
                3 to "3",
                4 to "4",
                5 to "5 — самое крупное"
            )) {
                SettingRadio(
                    label = label,
                    selected = posterSize == value,
                    onClick = {
                        posterSize = value
                        Repo.prefs.listPosterSize = value
                    }
                )
            }
        }

        SectionTitle(text = "Серии на странице тайтла", modifier = Modifier.padding(top = 16.dp))
        Text(
            text = "Как показывать список серий внутри тайтла. Вид ленты на главном экране — в разделе выше.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SettingRadio(
            label = "Названиями (компактно)",
            selected = !thumbs,
            onClick = { thumbs = false; Repo.prefs.episodeThumbs = false }
        )
        SettingRadio(
            label = "С кадрами-превью",
            selected = thumbs,
            onClick = { thumbs = true; Repo.prefs.episodeThumbs = true }
        )

        SectionTitle(text = "Качество по умолчанию", modifier = Modifier.padding(top = 16.dp))
        SettingRadio(
            label = "Full HD (если есть у серии, иначе HD)",
            selected = quality == Quality.FHD,
            onClick = {
                quality = Quality.FHD
                Repo.prefs.defaultQuality = Quality.FHD
            }
        )
        SettingRadio(
            label = "HD (высокое)",
            selected = quality == Quality.HD,
            onClick = {
                quality = Quality.HD
                Repo.prefs.defaultQuality = Quality.HD
            }
        )
        SettingRadio(
            label = "SD (экономия трафика)",
            selected = quality == Quality.SD,
            onClick = {
                quality = Quality.SD
                Repo.prefs.defaultQuality = Quality.SD
            }
        )

        SectionTitle(text = "Качество в мобильной сети", modifier = Modifier.padding(top = 16.dp))
        Text(
            text = "Для мобильного интернета и точек доступа с ограничением трафика. Серия в Full HD — до нескольких гигабайт.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val mobileOptions = listOf<Pair<Quality?, String>>(
            null to "Как по Wi-Fi",
            Quality.FHD to "Full HD",
            Quality.HD to "HD",
            Quality.SD to "SD (меньше всего трафика)"
        )
        for ((value, label) in mobileOptions) {
            SettingRadio(
                label = label,
                selected = mobileQuality == value,
                onClick = {
                    mobileQuality = value
                    Repo.prefs.mobileQuality = value
                }
            )
        }

        SectionTitle(text = "Уведомления о новых сериях", modifier = Modifier.padding(top = 16.dp))
        Text(
            text = "Для тайтлов из избранного. Проверка каждые 30 минут, когда есть интернет.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = "Включить уведомления",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                modifier = Modifier.focusBorder(RoundedCornerShape(16.dp)),
                checked = notifs,
                onCheckedChange = { enabled ->
                    notifs = enabled
                    Repo.prefs.notificationsEnabled = enabled
                    val app = context.applicationContext
                    if (app is AnimeVostApp) {
                        if (enabled) app.scheduleUpdateWork() else app.cancelUpdateWork()
                    }
                }
            )
        }

        SectionTitle(text = "О приложении", modifier = Modifier.padding(top = 16.dp))
        Text(
            text = "AnimeVostBR $appVersion — неофициальный клиент animevost.org для Android TV и телефонов.\n\n" +
                "Весь контент принадлежит его правообладателям. Приложение не хранит и не распространяет медиафайлы, " +
                "а лишь обеспечивает удобный доступ к публичному сайту.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** Mirror status line: host, last check time and where it was found. */
private fun mirrorStatus(): String {
    val p = Repo.prefs
    val checked = if (p.mirrorCheckedAt > 0)
        " · проверено " + SimpleDateFormat("dd.MM HH:mm", Locale("ru")).format(Date(p.mirrorCheckedAt))
    else " · ещё не проверялось"
    val source = p.mirrorSource.takeIf { it.isNotBlank() && p.mirrorCheckedAt > 0 }?.let { " ($it)" }.orEmpty()
    return "Текущее зеркало: ${p.mirrorHost}$checked$source"
}

@Composable
private fun SettingRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusBorder(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        // onClick = null: the whole row takes focus and clicks, otherwise a remote needs two steps per item
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** A settings row that opens another screen. */
@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .focusBorder(RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}
