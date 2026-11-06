package ir.netsignal.monitor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * تست پینگ حرفه‌ای:
 * ۱) اول تلاش با باینری ICMP سیستم (/system/bin/ping) — پینگ واقعی
 * ۲) اگر مسدود/ناموجود بود، جایگزین خودکار HTTP (۱۰ درخواست کوچک)
 *
 * خروجی: کمینه/میانگین/بیشترین/جیتر/درصد بستهٔ گم‌شده + روش استفاده‌شده.
 */
object PingTester {

    /** سرورهای هدف — شامل دو سرور ایرانی */
    data class Target(val id: String, val host: String)

    val TARGETS = listOf(
        Target("google", "8.8.8.8"),
        Target("cloudflare", "1.1.1.1"),
        Target("shecan", "178.22.122.100"),
        Target("begzar", "185.55.226.26"),
        Target("level3", "4.2.2.4")
    )

    data class Result(
        val host: String,
        val minMs: Double,
        val avgMs: Double,
        val maxMs: Double,
        val jitterMs: Double,
        val lossPercent: Double,
        val sent: Int,
        val received: Int,
        val usedIcmp: Boolean
    )

    /**
     * اجرای تست؛ onProgress(current, total, rtt یا null برای گم‌شده) روی
     * همان دیسپچری فراخوانی می‌شود که run در آن اجرا شده — فراخواننده UI را
     * با Dispatchers.Main هندل می‌کند.
     */
    suspend fun run(host: String, count: Int = 8, onProgress: (Int, Int, Double?) -> Unit): Result? {
        val icmp = withContext(Dispatchers.IO) {
            runIcmp(host, count, onProgress)
        }
        if (icmp != null) return icmp

        // جایگزین HTTP
        return withContext(Dispatchers.IO) {
            runHttp(count, onProgress)
        }
    }

    /** پینگ واقعی ICMP با باینری سیستم؛ خروجی null یعنی ICMP در دسترس نبود */
    private fun runIcmp(host: String, count: Int, onProgress: (Int, Int, Double?) -> Unit): Result? {
        val samples = mutableListOf<Double>()
        var process: Process? = null
        try {
            process = ProcessBuilder(
                "/system/bin/ping",
                "-c", count.toString(),
                "-i", "0.3",
                "-W", "2",
                "-w", "15",
                host
            ).redirectErrorStream(true).start()
        } catch (t: Throwable) {
            return null
        }

        try {
            val reader = process!!.inputStream.bufferedReader()
            val timeRegex = Regex("time[=<]\\s*([0-9.]+)\\s*ms")
            while (true) {
                val line = reader.readLine() ?: break
                val match = timeRegex.find(line)
                if (match != null) {
                    val ms = match.groupValues[1].toDoubleOrNull()
                    if (ms != null && ms > 0.0) samples.add(ms)
                }
            }
            val finished = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) return null
            // برخی نسخه‌ها بستهٔ گم‌شده را با خط خروجی timeout گزارش می‌کنند؛
            // اگر هیچ نمونه‌ای نداشتیم یعنی ICMP کار نکرده (نه شبکه بد) → HTTP
            if (samples.isEmpty()) return null
        } catch (t: Throwable) {
            return null
        } finally {
            process?.destroy()
        }

        if (samples.size < count) {
            // بسته‌های گم‌شده را به‌عنوان progress null گزارش کن
            for (i in samples.size until count) {
                onProgress(i + 1, count, null)
            }
        }
        samples.forEachIndexed { i, ms -> onProgress(i + 1, count, ms) }
        return buildResult(host, count, samples, usedIcmp = true)
    }

    /** جایگزین: ۱۰ درخواست کوچک HTTPS به Cloudflare (وقتی ICMP مسدود است) */
    private suspend fun runHttp(count: Int, onProgress: (Int, Int, Double?) -> Unit): Result? {
        val samples = mutableListOf<Double>()
        val total = 10
        for (i in 1..total) {
            try {
                coroutineContext.ensureActive()
            } catch (e: CancellationException) {
                throw e
            }
            val rtt = httpOnce()
            samples.addIfNotNull(rtt)
            onProgress(i, total, rtt)
        }
        if (samples.isEmpty()) return null
        return buildResult("speed.cloudflare.com", total, samples, usedIcmp = false)
    }

    private fun httpOnce(): Double? {
        val connection = try {
            URL("https://speed.cloudflare.com/__down?bytes=0").openConnection() as HttpURLConnection
        } catch (t: Throwable) {
            return null
        }
        return try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.setRequestProperty("User-Agent", "NetworkSignalMonitor/1.0")
            val start = System.nanoTime()
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream?.use { it.readBytes() }
                (System.nanoTime() - start) / 1e6
            } else {
                null
            }
        } catch (t: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun MutableList<Double>.addIfNotNull(v: Double?) {
        if (v != null) add(v)
    }

    private fun buildResult(host: String, sent: Int, samples: List<Double>, usedIcmp: Boolean): Result {
        val avg = samples.average()
        var jitter = 0.0
        if (samples.size >= 2) {
            var sum = 0.0
            for (i in 1 until samples.size) {
                sum += abs(samples[i] - samples[i - 1])
            }
            jitter = sum / (samples.size - 1)
        }
        val lost = (sent - samples.size).coerceAtLeast(0)
        return Result(
            host = host,
            minMs = samples.min(),
            avgMs = avg,
            maxMs = samples.max(),
            jitterMs = jitter,
            lossPercent = lost * 100.0 / sent,
            sent = sent,
            received = samples.size,
            usedIcmp = usedIcmp
        )
    }
}

/**
 * تست سرعت DNS: زمان تبدیل نام دامنه به آی‌پی برای چند دامنهٔ
 * بین‌المللی و ایرانی.
 */
object DnsTester {

    data class Row(val host: String, val ms: Double?)

    val HOSTS = listOf(
        "google.com",
        "cloudflare.com",
        "shecan.ir",
        "aparat.com",
        "irna.ir"
    )

    /** اجرای تست روی Dispatchers.IO؛ هر ردیف به‌محض آماده شدن گزارش می‌شود */
    suspend fun run(onRow: (Int, Int, Row) -> Unit): List<Row> {
        return withContext(Dispatchers.IO) {
            val rows = mutableListOf<Row>()
            for ((index, host) in HOSTS.withIndex()) {
                try {
                    coroutineContext.ensureActive()
                } catch (e: CancellationException) {
                    throw e
                }
                val ms = resolveMs(host)
                val row = Row(host, ms)
                rows.add(row)
                onRow(index, HOSTS.size, row)
            }
            rows
        }
    }

    private fun resolveMs(host: String): Double? {
        return try {
            val start = System.nanoTime()
            val addresses = java.net.InetAddress.getAllByName(host)
            val elapsed = (System.nanoTime() - start) / 1e6
            if (addresses.isNotEmpty()) elapsed else null
        } catch (t: Throwable) {
            null
        }
    }
}
