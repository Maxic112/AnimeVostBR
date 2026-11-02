package com.animevostbr.core

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ThemeMode {
    const val SYSTEM = 0
    const val DARK = 1
    const val LIGHT = 2
}

/**
  * SharedPreferences storage (JSON via Gson): settings, favorites, watch progress and
  * per-title episode counters used by notifications.
  */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("animevostbr", Context.MODE_PRIVATE)
    private val gson = Gson()

    var themeMode: Int
        get() = sp.getInt("theme_mode", ThemeMode.DARK)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    var defaultQuality: Quality
        get() = if (sp.getString("quality", "HD") == "SD") Quality.SD else Quality.HD
        set(v) = sp.edit().putString("quality", v.label).apply()

    /** Tiles per row in feed and search; 0 = auto (3 on phones, by width on TV). */
    var gridColumns: Int
        get() = sp.getInt("grid_columns", 0)
        set(v) = sp.edit().putInt("grid_columns", v).apply()

    var episodeThumbs: Boolean
        get() = sp.getBoolean("episode_thumbs", false)
        set(v) = sp.edit().putBoolean("episode_thumbs", v).apply()

    /** Ordinal of [SiteMode]. */
    var siteMode: Int
        get() = sp.getInt("site_mode", SiteMode.AUTO.ordinal)
        set(v) = sp.edit().putInt("site_mode", v).apply()

    /** Current site mirror, updated by [Sites.refreshMirror]. */
    var mirrorHost: String
        get() = sp.getString("mirror_host", null) ?: Sites.DEFAULT_MIRROR
        set(v) = sp.edit().putString("mirror_host", v).apply()

    var mirrorCheckedAt: Long
        get() = sp.getLong("mirror_checked_at", 0L)
        set(v) = sp.edit().putLong("mirror_checked_at", v).apply()

    /** Where the current mirror was found (shown in Settings). */
    var mirrorSource: String
        get() = sp.getString("mirror_source", "").orEmpty()
        set(v) = sp.edit().putString("mirror_source", v).apply()

    var notificationsEnabled: Boolean
        get() = sp.getBoolean("notif_enabled", true)
        set(v) = sp.edit().putBoolean("notif_enabled", v).apply()

    private val favType = object : TypeToken<MutableList<TitleLite>>() {}.type

    fun favorites(): MutableList<TitleLite> =
        gson.fromJson<MutableList<TitleLite>>(sp.getString("favorites", "[]"), favType) ?: mutableListOf()

    fun isFavorite(id: Int): Boolean = favorites().any { it.id == id }

    fun toggleFavorite(lite: TitleLite): Boolean {
        val list = favorites()
        val existing = list.indexOfFirst { it.id == lite.id }
        return if (existing >= 0) {
            list.removeAt(existing)
            saveFavorites(list)
            false
        } else {
            list.add(0, lite)
            saveFavorites(list)
            true
        }
    }

    private fun saveFavorites(list: MutableList<TitleLite>) =
        sp.edit().putString("favorites", gson.toJson(list)).apply()

    private val progressType = object : TypeToken<MutableMap<Int, Progress>>() {}.type

    fun progressMap(): MutableMap<Int, Progress> =
        gson.fromJson<MutableMap<Int, Progress>>(sp.getString("progress", "{}"), progressType) ?: mutableMapOf()

    fun progress(id: Int): Progress? = progressMap()[id]

    /** Most recently watched first. */
    fun allProgress(): List<Progress> = progressMap().values.sortedByDescending { it.updatedAt }

    fun saveProgress(p: Progress) {
        val map = progressMap()
        map[p.lite.id] = p.copy(updatedAt = System.currentTimeMillis())
        sp.edit().putString("progress", gson.toJson(map)).apply()
    }

    fun removeProgress(id: Int) {
        val map = progressMap()
        if (map.remove(id) != null) sp.edit().putString("progress", gson.toJson(map)).apply()
    }

    fun clearProgress() = sp.edit().remove("progress").apply()

    private val seenType = object : TypeToken<MutableMap<Int, Int>>() {}.type

    fun seenMap(): MutableMap<Int, Int> =
        gson.fromJson<MutableMap<Int, Int>>(sp.getString("seen", "{}"), seenType) ?: mutableMapOf()

    fun saveSeen(map: Map<Int, Int>) =
        sp.edit().putString("seen", gson.toJson(map)).apply()
}
