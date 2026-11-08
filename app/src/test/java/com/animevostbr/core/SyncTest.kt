package com.animevostbr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.concurrent.thread

class SyncTest {

    private fun lite(id: Int) = TitleLite(id, "Тайтл $id", "")
    private fun progress(id: Int, ep: Int, at: Long) = Progress(lite(id), ep, 60_000, 1_400_000, "$ep серия", at)

    private val phone = SyncData(
        favorites = listOf(lite(1), lite(2)),
        progress = mapOf(1 to progress(1, 5, at = 200), 3 to progress(3, 1, at = 50)),
        seen = mapOf(1 to 5)
    )
    private val tv = SyncData(
        favorites = listOf(lite(2), lite(4)),
        progress = mapOf(1 to progress(1, 3, at = 100), 4 to progress(4, 2, at = 70)),
        seen = mapOf(1 to 6, 4 to 2)
    )

    @Test
    fun merge_keepsEverythingAndTheLatestProgress() {
        val m = Sync.merge(phone, tv)
        assertEquals(listOf(1, 2, 4), m.favorites.map { it.id })
        assertEquals(setOf(1, 3, 4), m.progress.keys)
        assertEquals(5, m.progress.getValue(1).episode) // the phone watched episode 1 later
        assertEquals(mapOf(1 to 6, 4 to 2), m.seen)
    }

    @Test
    fun exchange_overLoopbackGivesBothSidesTheSameData() {
        var tvKept: SyncData? = null
        val server = Sync.Server(code = "123456", local = { tv }, onMerged = { tvKept = it })
        var outcome: Sync.Server.Outcome? = null
        val t = thread { outcome = server.serve(timeoutMs = 10_000) }

        // a wrong code is refused and the server keeps waiting
        try {
            Sync.exchange("127.0.0.1", server.port, "000000", phone)
            fail("wrong code accepted")
        } catch (e: Sync.SyncException) {
            assertEquals("Неверный код", e.message)
        }

        val phoneKept = Sync.exchange("127.0.0.1", server.port, "123456", phone)
        t.join(10_000)
        assertTrue(outcome is Sync.Server.Outcome.Done)
        assertEquals(phoneKept, tvKept)
        assertEquals(Sync.merge(tv, phone).progress, phoneKept.progress)
        assertEquals(setOf(1, 2, 4), phoneKept.favorites.map { it.id }.toSet())
    }

    @Test
    fun server_stopsAfterTooManyWrongCodes() {
        val server = Sync.Server(code = "123456", local = { tv }, onMerged = { fail("must not merge") })
        var outcome: Sync.Server.Outcome? = null
        val t = thread { outcome = server.serve(timeoutMs = 10_000) }
        repeat(5) { runCatching { Sync.exchange("127.0.0.1", server.port, "111111", phone) } }
        t.join(10_000)
        assertEquals(Sync.Server.Outcome.TooManyWrongCodes, outcome)
    }

    @Test
    fun newCode_isSixDigits() {
        repeat(20) { assertTrue(Sync.newCode().matches(Regex("""\d{6}"""))) }
    }
}
