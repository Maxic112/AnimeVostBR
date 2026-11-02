package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GitHub release parsing and version comparison for in-app updates. */
class AppUpdateTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }.readText()

    @Test
    fun parsesLatestRelease() {
        val r = AppUpdate.parseRelease(fixture("github_release_latest.json"))
        assertNotNull(r)
        r!!
        assertEquals("1.2.0", r.version)
        assertEquals("2026-11-02", r.publishedDate)
        assertEquals(3773572L, r.apkSize)
        assertTrue(r.apkUrl.endsWith("/releases/download/v1.2.0/AnimeVostBR-v1.2.0.apk"))
        assertTrue(r.notes.startsWith("### Added"))
    }

    @Test
    fun skipsDraftsPrereleasesAndReleasesWithoutApk() {
        val json = fixture("github_release_latest.json")
        assertNull(AppUpdate.parseRelease(json.replace("\"prerelease\": false", "\"prerelease\": true")))
        assertNull(AppUpdate.parseRelease(json.replace("\"draft\": false", "\"draft\": true")))
        assertNull(AppUpdate.parseRelease(json.replace("AnimeVostBR-v1.2.0.apk\"", "AnimeVostBR-v1.2.0.zip\"")))
        assertNull(AppUpdate.parseRelease("{\"message\":\"Not Found\"}"))
        assertNull(AppUpdate.parseRelease("<html>"))
        assertNull(AppUpdate.parseRelease(null))
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(AppUpdate.isNewer("1.2.1", "1.2.0"))
        assertTrue(AppUpdate.isNewer("1.10.0", "1.9.9"))
        assertTrue(AppUpdate.isNewer("2.0", "1.9.9"))
        assertTrue(AppUpdate.isNewer("v1.3.0", "1.2.0"))
        assertFalse(AppUpdate.isNewer("1.2.0", "1.2.0"))
        assertFalse(AppUpdate.isNewer("1.2", "1.2.0"))
        assertFalse(AppUpdate.isNewer("1.1.9", "1.2.0"))
        assertFalse(AppUpdate.isNewer("latest", "1.2.0"))
    }
}
