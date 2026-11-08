package com.animevostbr.core

import com.google.gson.Gson
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.security.SecureRandom

/** What two devices exchange: favorites, watch progress and the new-episode counters. */
data class SyncData(
    val favorites: List<TitleLite> = emptyList(),
    val progress: Map<Int, Progress> = emptyMap(),
    val seen: Map<Int, Int> = emptyMap(),
    val version: Int = Sync.VERSION
)

/**
  * Phone ↔ TV sync over the local network, no server of our own. One device waits with a code
  * ([Server]), the other connects ([exchange]) and sends its data; the waiting one merges, keeps the
  * result and sends it back, so both end up with the same data.
  *
  * Plain HTTP: `POST /sync`, header `X-Sync-Code`, JSON [SyncData] both ways.
  */
object Sync {

    const val VERSION = 1
    const val SERVICE_TYPE = "_animevostbr._tcp."

    private const val PATH = "/sync"
    private const val CODE_HEADER = "X-Sync-Code"
    private const val MAX_BODY = 8 * 1024 * 1024
    private const val MAX_WRONG_CODES = 5

    private val gson = Gson()
    private val random = SecureRandom()

    fun newCode(): String = (0 until 6).joinToString("") { random.nextInt(10).toString() }

    /**
      * Union of favorites; for each title the progress watched most recently; the higher new-episode
      * counter. A favorite removed on one device comes back from the other: removals are not tracked.
      */
    fun merge(a: SyncData, b: SyncData): SyncData {
        val favorites = (a.favorites + b.favorites).distinctBy { it.id }
        val progress = (a.progress.keys + b.progress.keys).associateWith { id ->
            listOfNotNull(a.progress[id], b.progress[id]).maxBy { it.updatedAt }
        }
        val seen = (a.seen.keys + b.seen.keys).associateWith { maxOf(a.seen[it] ?: 0, b.seen[it] ?: 0) }
        return SyncData(favorites, progress, seen)
    }

    class SyncException(message: String) : IOException(message)

    /** The connecting side: sends [local], returns the merged data to keep. */
    fun exchange(host: String, port: Int, code: String, local: SyncData, timeoutMs: Int = 10_000): SyncData {
        val conn = URL("http", host, port, PATH).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty(CODE_HEADER, code)
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(gson.toJson(local).toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                200 -> {
                    val merged = conn.inputStream.use { gson.fromJson(it.reader(Charsets.UTF_8), SyncData::class.java) }
                    if (merged.version != VERSION) throw SyncException("На другом устройстве другая версия приложения — обновите оба")
                    return merged
                }
                403 -> throw SyncException("Неверный код")
                409 -> throw SyncException("На другом устройстве другая версия приложения — обновите оба")
                else -> throw SyncException("Другое устройство ответило ошибкой ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
      * The waiting side. [serve] blocks until one successful exchange, too many wrong codes, a timeout
      * or [close]; [onMerged] gets the merged data before it is sent back.
      */
    class Server(
        private val code: String,
        private val local: () -> SyncData,
        private val onMerged: (SyncData) -> Unit
    ) {
        private val socket = ServerSocket().apply { bind(InetSocketAddress(0)) }
        val port: Int get() = socket.localPort

        sealed interface Outcome {
            data class Done(val data: SyncData) : Outcome
            data object TooManyWrongCodes : Outcome
            data object TimedOut : Outcome
            data object Closed : Outcome
        }

        fun serve(timeoutMs: Int): Outcome {
            socket.soTimeout = timeoutMs
            var wrong = 0
            try {
                while (true) {
                    val client = try {
                        socket.accept()
                    } catch (e: SocketTimeoutException) {
                        return Outcome.TimedOut
                    }
                    client.use { s ->
                        when (val r = handle(s)) {
                            is Handled.Merged -> return Outcome.Done(r.data)
                            Handled.WrongCode -> if (++wrong >= MAX_WRONG_CODES) return Outcome.TooManyWrongCodes
                            Handled.Ignored -> Unit
                        }
                    }
                }
            } catch (e: IOException) {
                return Outcome.Closed
            } finally {
                close()
            }
        }

        fun close() {
            runCatching { socket.close() }
        }

        private sealed interface Handled {
            data class Merged(val data: SyncData) : Handled
            data object WrongCode : Handled
            data object Ignored : Handled
        }

        private fun handle(s: Socket): Handled {
            s.soTimeout = 10_000
            val input = BufferedInputStream(s.getInputStream())
            val requestLine = readLine(input) ?: return Handled.Ignored
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: return Handled.Ignored
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            }
            val out = s.getOutputStream()
            fun respond(status: String, body: String = "") {
                val bytes = body.toByteArray(Charsets.UTF_8)
                out.write(
                    ("HTTP/1.1 $status\r\nContent-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                )
                out.write(bytes)
                out.flush()
            }
            if (!requestLine.startsWith("POST $PATH ")) {
                respond("404 Not Found")
                return Handled.Ignored
            }
            if (headers[CODE_HEADER.lowercase()] != code) {
                respond("403 Forbidden")
                return Handled.WrongCode
            }
            val length = headers["content-length"]?.toIntOrNull()
            if (length == null || length < 0 || length > MAX_BODY) {
                respond("413 Payload Too Large")
                return Handled.Ignored
            }
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) return Handled.Ignored
                read += n
            }
            val remote = runCatching { gson.fromJson(String(body, Charsets.UTF_8), SyncData::class.java) }.getOrNull()
            if (remote == null || remote.version != VERSION) {
                respond("409 Conflict")
                return Handled.Ignored
            }
            val merged = merge(local(), remote)
            onMerged(merged)
            respond("200 OK", gson.toJson(merged))
            return Handled.Merged(merged)
        }

        private fun readLine(input: InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0) return if (sb.isEmpty()) null else sb.toString()
                if (c == '\n'.code) return sb.toString().trimEnd('\r')
                if (sb.length > 8192) return null
                sb.append(c.toChar())
            }
        }
    }
}
