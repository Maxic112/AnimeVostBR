package com.animevostbr.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** A published GitHub release that carries an APK. */
data class AppRelease(
    val version: String,
    val notes: String,
    /** yyyy-MM-dd */
    val publishedDate: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String
)

/**
  * App updates from GitHub Releases of [REPO]: daily check, download and hand-off to the system
  * package installer. Release builds are signed by CI with the same key as the installed app.
  */
object AppUpdate {

    const val REPO = "Maxic112/AnimeVostBR"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val APK_MIME = "application/vnd.android.package-archive"

    /** A release newer than the installed version; drives the orange dots in the UI. */
    var available by mutableStateOf<AppRelease?>(null)
        private set

    var currentVersion = ""
        private set

    private val http by lazy {
        // no call timeout: the APK download may take longer than an API call on a slow connection
        ApiClient.ok.newBuilder().callTimeout(0, TimeUnit.SECONDS).build()
    }

    fun init(context: Context) {
        currentVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        available = parseRelease(Repo.prefs.latestRelease)?.takeIf { isNewer(it.version, currentVersion) }
    }

    /** Fetches the latest release; throws on network errors. */
    suspend fun check(): AppRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("GitHub ответил HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        val release = parseRelease(body) ?: throw IOException("В последнем релизе нет APK-файла")
        Repo.prefs.latestRelease = body
        Repo.prefs.updateCheckedAt = System.currentTimeMillis()
        available = release.takeIf { isNewer(it.version, currentVersion) }
        release
    }

    /** Background check from app start: at most once a day. */
    suspend fun checkIfDue() {
        if (System.currentTimeMillis() - Repo.prefs.updateCheckedAt >= CHECK_INTERVAL_MS) {
            runCatching { check() }
        }
    }

    /** Parses a GitHub `releases/latest` response; null for drafts, pre-releases and releases without an APK. */
    fun parseRelease(json: String?): AppRelease? {
        if (json.isNullOrBlank()) return null
        val o = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        if (o.bool("draft") || o.bool("prerelease")) return null
        val version = o.str("tag_name").removePrefix("v").trim()
        if (version.isEmpty()) return null
        val apk = o.getAsJsonArray("assets")
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.str("name").endsWith(".apk", ignoreCase = true) && it.str("state").ifEmpty { "uploaded" } == "uploaded" }
            ?: return null
        return AppRelease(
            version = version,
            notes = o.str("body").trim(),
            publishedDate = o.str("published_at").take(10),
            apkUrl = apk.str("browser_download_url").ifEmpty { return null },
            apkSize = apk.get("size")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
            pageUrl = o.str("html_url")
        )
    }

    /** Compares dotted versions numerically: 1.10.0 > 1.9.2; a missing part counts as 0. */
    fun isNewer(remote: String, current: String): Boolean {
        val a = versionParts(remote)
        val b = versionParts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun versionParts(v: String): List<Int> =
        v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: return emptyList() }

    /** Downloads the release APK into the cache, reusing a complete earlier download. */
    suspend fun download(context: Context, release: AppRelease, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "AnimeVostBR-v${release.version}.apk")
            if (release.apkSize > 0 && target.length() == release.apkSize) return@withContext target
            dir.listFiles()?.forEach { it.delete() }

            val part = File(dir, target.name + ".part")
            val request = Request.Builder().url(release.apkUrl).build()
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Не удалось скачать файл: HTTP ${r.code}")
                val body = r.body ?: throw IOException("Пустой ответ сервера")
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
            }
            if (release.apkSize > 0 && part.length() != release.apkSize) {
                part.delete()
                throw IOException("Файл скачан не полностью")
            }
            if (!part.renameTo(target)) throw IOException("Не удалось сохранить файл")
            target
        }

    /** Checks the downloaded APK before handing it to the installer; returns an error text or null. */
    fun verify(context: Context, file: File): String? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.path, flags)
            ?: return "Файл обновления повреждён — попробуйте ещё раз".also { file.delete() }
        if (archive.packageName != context.packageName) return "Скачанный файл не является обновлением AnimeVostBR"
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return "Эта или более новая версия уже установлена"
        }
        // Android refuses an update signed with another key anyway; say it plainly instead of a generic installer error
        val newCerts = certificates(archive)
        val ownCerts = certificates(installed)
        if (newCerts.isNotEmpty() && ownCerts.isNotEmpty() && newCerts.none { it in ownCerts }) {
            return "Обновление подписано другим ключом. Установленная сборка не из GitHub Releases — " +
                "удалите её и установите APK со страницы релиза"
        }
        return null
    }

    private fun certificates(info: PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val s = info.signingInfo ?: return emptySet()
            s.apkContentsSigners.orEmpty().toList() + s.signingCertificateHistory.orEmpty()
        } else info.signatures.orEmpty().toList()
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.mapTo(HashSet()) { sig -> md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }
    }

    /** "Install unknown apps" permission for this app (Android 8+). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
      * Android kills the app process when the install permission changes, so the request
      * survives in prefs and the installation resumes on the next start.
      */
    fun markInstallRequested() {
        Repo.prefs.installRequestedAt = System.currentTimeMillis()
    }

    /** The user granted the permission recently and the update is still pending. */
    fun hasPendingInstall(context: Context): Boolean {
        val at = Repo.prefs.installRequestedAt
        return at > 0 && System.currentTimeMillis() - at < PENDING_INSTALL_MS &&
            available != null && canInstall(context)
    }

    fun clearInstallRequest() {
        Repo.prefs.installRequestedAt = 0L
    }

    private const val PENDING_INSTALL_MS = 15 * 60 * 1000L

    /** Opens the system installer; ACTION_INSTALL_PACKAGE avoids file managers in the app chooser. */
    fun startInstall(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        @Suppress("DEPRECATION")
        val install = Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, APK_MIME)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
        for (intent in listOf(install, view)) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: android.content.ActivityNotFoundException) {
            }
        }
        throw IOException("На устройстве нет установщика приложений")
    }

    private fun JsonObject.str(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
}
