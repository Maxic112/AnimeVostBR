package com.animevostbr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animevostbr.core.Repo
import com.animevostbr.core.Sync
import com.animevostbr.core.SyncData
import com.animevostbr.core.SyncNetwork
import com.animevostbr.core.SyncPeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SyncMode { CHOOSE, WAIT, CONNECT }

/** Favorites and watch progress between two devices on one network. */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    var mode by remember { mutableStateOf(SyncMode.CHOOSE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                modifier = Modifier.focusBorder(CircleShape),
                onClick = { if (mode == SyncMode.CHOOSE) onBack() else mode = SyncMode.CHOOSE }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(text = "Синхронизация", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            text = "Избранное и прогресс просмотра переносятся между двумя устройствами в одной сети " +
                "(например, телефон и телевизор дома). Данные объединяются: на обоих окажется всё, " +
                "а для каждого тайтла — место, где смотрели позже.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp)
        )

        when (mode) {
            SyncMode.CHOOSE -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "На одном устройстве нажмите «Показать код», на другом — «Подключиться».",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { mode = SyncMode.WAIT },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    modifier = Modifier.focusBorder(RoundedCornerShape(24.dp))
                ) { Text("Показать код на этом устройстве") }
                OutlinedButton(
                    onClick = { mode = SyncMode.CONNECT },
                    modifier = Modifier.focusBorder(RoundedCornerShape(24.dp))
                ) { Text("Подключиться к другому устройству") }
            }
            SyncMode.WAIT -> WaitPanel()
            SyncMode.CONNECT -> ConnectPanel()
        }
    }
}

private fun summary(d: SyncData): String =
    "Готово. Избранное: ${d.favorites.size}, начатые тайтлы: ${d.progress.size}."

@Composable
private fun WaitPanel() {
    val context = LocalContext.current
    val code = remember { Sync.newCode() }
    val server = remember { Sync.Server(code, { Repo.prefs.syncData() }, { Repo.prefs.applySync(it) }) }
    val advertiser = remember { SyncNetwork.Advertiser(context.applicationContext) }
    val addresses = remember { SyncNetwork.localAddresses() }
    var status by remember { mutableStateOf("Ждём подключения… Экран можно не трогать до 5 минут.") }
    var waiting by remember { mutableStateOf(true) }

    LaunchedEffect(server) {
        advertiser.start(server.port)
        val outcome = withContext(Dispatchers.IO) { server.serve(timeoutMs = 5 * 60 * 1000) }
        advertiser.stop()
        waiting = false
        status = when (outcome) {
            is Sync.Server.Outcome.Done -> summary(outcome.data)
            Sync.Server.Outcome.TooManyWrongCodes -> "Слишком много неверных кодов — ожидание остановлено."
            Sync.Server.Outcome.TimedOut -> "Никто не подключился за 5 минут. Откройте экран заново."
            Sync.Server.Outcome.Closed -> "Ожидание остановлено."
        }
    }
    DisposableEffect(server) {
        onDispose {
            server.close()
            advertiser.stop()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Код для другого устройства:", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = code.chunked(3).joinToString(" "),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = AccentColor
        )
        Text(
            text = "Другое устройство найдёт это само. Если не найдёт, введите там адрес: " +
                addresses.joinToString(" или ") { "$it:${server.port}" }.ifEmpty { "нет сети" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            if (waiting) CircularProgressIndicator(color = AccentColor, modifier = Modifier.padding(end = 12.dp))
            Text(status, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ConnectPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var peers by remember { mutableStateOf<List<SyncPeer>>(emptyList()) }
    var chosen by remember { mutableStateOf<SyncPeer?>(null) }
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val browser = SyncNetwork.Browser(context.applicationContext) { found ->
            // NSD calls back on its own thread; Compose state accepts it
            peers = found
            if (chosen == null && found.size == 1) chosen = found.first()
        }
        browser.start()
        onDispose { browser.stop() }
    }

    fun target(): Pair<String, Int>? {
        val typed = address.trim()
        if (typed.isNotEmpty()) {
            val host = typed.substringBeforeLast(':')
            val port = typed.substringAfterLast(':', "").toIntOrNull() ?: return null
            return host to port
        }
        return chosen?.let { it.host to it.port }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Устройства с открытым кодом в этой сети:", style = MaterialTheme.typography.bodyLarge)
        if (peers.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AccentColor, modifier = Modifier.padding(end = 12.dp))
                Text("Ищем… На другом устройстве должен быть открыт экран с кодом.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        for (p in peers) {
            val selected = p == chosen && address.isBlank()
            Text(
                text = (if (selected) "✓ " else "") + p.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) AccentColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusBorder(RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        chosen = p
                        address = ""
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            singleLine = true,
            label = { Text("Или адрес вручную, например 192.168.1.5:40123") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = code,
            onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
            singleLine = true,
            label = { Text("Код с другого устройства") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = !busy && code.length == 6 && target() != null,
            onClick = {
                val (host, port) = target() ?: return@Button
                busy = true
                status = null
                scope.launch {
                    status = try {
                        val merged = withContext(Dispatchers.IO) { Sync.exchange(host, port, code, Repo.prefs.syncData()) }
                        Repo.prefs.applySync(merged)
                        summary(merged)
                    } catch (e: Sync.SyncException) {
                        e.message
                    } catch (e: Exception) {
                        "Не удалось подключиться. Проверьте, что устройства в одной сети и на другом открыт экран с кодом."
                    }
                    busy = false
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            modifier = Modifier.focusBorder(RoundedCornerShape(24.dp))
        ) { Text(if (busy) "Синхронизируем…" else "Синхронизировать") }
        status?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}
