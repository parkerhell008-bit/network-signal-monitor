package ir.netsignal.monitor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.random.Random

/**
 * موتور تست سرعت مبتنی بر کوروتین (فقط کتابخانه استاندارد + HttpURLConnection):
 *
 * ۱) پینگ: میانگین ۱۰ درخواست به Cloudflare (__down?bytes=0) به‌همراه محاسبه جیتر
 *    (میانگین قدر مطلق اختلاف نمونه‌های متوالی).
 * ۲) دانلود: ۴ جریان موازی HttpURLConnection از
 *    https://speed.cloudflare.com/__down?bytes=26214400 (۲۵ مگابایت در هر جریان)
 *    با سقف زمانی ۱۵ ثانیه و گزارش درصد پیشرفت + Mbps لحظه‌ای.
 * ۳) آپلود: ۴ درخواست POST موازی با ۲ مگابایت بایت تصادفی به
 *    https://speed.cloudflare.com/__up با سقف زمانی ۱۵ ثانیه.
 *
 * همه کال‌بک‌ها روی Dispatchers.Main فراخوانی می‌شوند.
 * لغو تست: کافی است کوروتینی که runTest را اجرا کرده cancel شود؛
 * حلقه‌های خواندن/نوشتن در هر چانک ensureActive() را چک می‌کنند
 * و disconnect() در بلاک finally سوکت‌های مسدودشده را آزاد می‌کند.
 */
object SpeedTester {

    interface Listener {
        fun onPingProgress(sample: Int, total: Int, pingMs: Double, jitterMs: Double)
        fun onDownloadProgress(percent: Int, mbps: Double)
        fun onUploadProgress(percent: Int, mbps: Double)
        fun onFinished(result: SpeedResult)
        fun onError(message: String)
    }

    data class SpeedResult(
        val pingMs: Double,
        val jitterMs: Double,
        val downloadMbps: Double,
        val uploadMbps: Double
    )

    private const val PING_URL = "https://speed.cloudflare.com/__down?bytes=0"
    const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=26214400"
    const val UPLOAD_URL = "https://speed.cloudflare.com/__up"

    private const val STREAM_COUNT = 4
    private const val DOWNLOAD_BYTES_PER_STREAM = 26_214_400L   // ۲۵ مگابایت
    private const val UPLOAD_BYTES_PER_STREAM = 2L * 1024L * 1024L // ۲ مگابایت
    private const val DOWNLOAD_TIME_CAP_MS = 15_000L
    private const val UPLOAD_TIME_CAP_MS = 15_000L
    private const val PING_ROUNDS = 10
    private const val CHUNK_SIZE = 64 * 1024

    suspend fun runTest(listener: Listener): SpeedResult? = coroutineScope {
        try {
            // ─────────── فاز ۱: پینگ ───────────
            val samples = ArrayList<Double>(PING_ROUNDS)
            for (i in 1..PING_ROUNDS) {
                coroutineContext.ensureActive()
                val rtt = withContext(Dispatchers.IO) { pingOnce() }
                if (rtt != null) samples.add(rtt)
                val avg = if (samples.isEmpty()) 0.0 else samples.average()
                val jitter = jitterOf(samples)
                withContext(Dispatchers.Main) {
                    listener.onPingProgress(i, PING_ROUNDS, avg, jitter)
                }
            }
            if (samples.isEmpty()) {
                withContext(Dispatchers.Main) {
                    listener.onError("اندازه‌گیری پینگ ناموفق بود؛ اتصال اینترنت را بررسی کنید.")
                }
                return@coroutineScope null
            }
            val pingAvg = samples.average()
            val jitter = jitterOf(samples)

            // ─────────── فاز ۲: دانلود (۴ جریان موازی) ───────────
            val downloaded = AtomicLong(0L)
            val downloadFailures = AtomicInteger(0)
            val expectedDownload = DOWNLOAD_BYTES_PER_STREAM * STREAM_COUNT
            val downloadStart = System.nanoTime()
            val downloadPoller = launch(Dispatchers.Main) {
                while (isActive) {
                    delay(200)
                    val (pct, mbps) = progressOf(downloaded.get(), downloadStart, expectedDownload)
                    listener.onDownloadProgress(pct, mbps)
                }
            }
            runParallelStreams { downloadStream(downloaded) }
            downloadPoller.cancel()
            val downloadElapsedS = (System.nanoTime() - downloadStart) / 1e9
            val downloadBytes = downloaded.get()
            if (downloadBytes <= 0L) {
                throw IOException(
                    "هیچ داده‌ای دانلود نشد" +
                        if (downloadFailures.get() > 0) {
                            " (خطای " + downloadFailures.get().toString().toDigits() + " جریان از ۴ جریان موازی)"
                        } else {
                            " (احتمالاً اتصال مسدود است)"
                        }
                )
            }
            val downloadMbps = downloadBytes * 8.0 / 1e6 / downloadElapsedS
            withContext(Dispatchers.Main) { listener.onDownloadProgress(100, downloadMbps) }

            // ─────────── فاز ۳: آپلود (۴ جریان موازی) ───────────
            val uploaded = AtomicLong(0L)
            val uploadFailures = AtomicInteger(0)
            val expectedUpload = UPLOAD_BYTES_PER_STREAM * STREAM_COUNT
            val uploadStart = System.nanoTime()
            val uploadPoller = launch(Dispatchers.Main) {
                while (isActive) {
                    delay(200)
                    val (pct, mbps) = progressOf(uploaded.get(), uploadStart, expectedUpload)
                    listener.onUploadProgress(pct, mbps)
                }
            }
            runParallelStreams { uploadStream(uploaded) }
            uploadPoller.cancel()
            val uploadElapsedS = (System.nanoTime() - uploadStart) / 1e9
            val uploadBytes = uploaded.get()
            if (uploadBytes <= 0L) {
                throw IOException(
                    "هیچ داده‌ای ارسال نشد" +
                        if (uploadFailures.get() > 0) {
                            " (خطای " + uploadFailures.get().toString().toDigits() + " جریان از ۴ جریان موازی)"
                        } else {
                            ""
                        }
                )
            }
            val uploadMbps = uploadBytes * 8.0 / 1e6 / uploadElapsedS
            withContext(Dispatchers.Main) { listener.onUploadProgress(100, uploadMbps) }

            val result = SpeedResult(pingAvg, jitter, downloadMbps, uploadMbps)
            withContext(Dispatchers.Main) { listener.onFinished(result) }
            result
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                listener.onError(e.message ?: "خطای ناشناخته در تست سرعت")
            }
            null
        }
    }

    /**
     * اجرای همزمان ۴ جریان شبکه روی Dispatchers.IO.
     * خطای تک‌جریان کل تست را نابود نمی‌کند (شبکه‌های موبایل ناپایدارند)؛
     * اگر همهٔ جریان‌ها شکست بخورند، گاردهای bytes<=0 در فاز بعدی با پیام دقیق خطا می‌دهند.
     */
    private suspend fun runParallelStreams(stream: suspend () -> Unit) = coroutineScope {
        repeat(STREAM_COUNT) {
            launch(Dispatchers.IO) {
                try {
                    stream()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    // جریان ناموفق: بایت‌های شمرده‌شده تا همین لحظه معتبر می‌مانند
                }
            }
        }
    }

    /** خواندن حداکثر ۲۵ مگابایت از سرور و شمارش بایت‌ها */
    private suspend fun downloadStream(counter: AtomicLong) {
        val connection = openConnection(DOWNLOAD_URL)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("کد وضعیت HTTP در دانلود: $code")
            val buffer = ByteArray(CHUNK_SIZE)
            val streamStart = System.nanoTime()
            connection.inputStream.use { input ->
                while (true) {
                    coroutineContext.ensureActive()
                    if (elapsedMs(streamStart) > DOWNLOAD_TIME_CAP_MS) break
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) counter.addAndGet(read.toLong())
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** ارسال حداکثر ۲ مگابایت بایت تصادفی و شمارش بایت‌های نوشته‌شده */
    private suspend fun uploadStream(counter: AtomicLong) {
        val connection = openConnection(UPLOAD_URL)
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(UPLOAD_BYTES_PER_STREAM.toInt())
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            val payload = ByteArray(CHUNK_SIZE)
            Random.nextBytes(payload)
            val streamStart = System.nanoTime()
            var written = 0L
            connection.outputStream.use { output ->
                while (written < UPLOAD_BYTES_PER_STREAM) {
                    coroutineContext.ensureActive()
                    if (elapsedMs(streamStart) > UPLOAD_TIME_CAP_MS) break
                    val chunk = minOf(CHUNK_SIZE.toLong(), UPLOAD_BYTES_PER_STREAM - written).toInt()
                    output.write(payload, 0, chunk)
                    output.flush()
                    written += chunk
                    counter.addAndGet(chunk.toLong())
                }
            }
            if (written >= UPLOAD_BYTES_PER_STREAM) {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("کد وضعیت HTTP در آپلود: $code")
                connection.inputStream?.use { it.readBytes() }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** یک درخواست کوچک برای اندازه‌گیری RTT؛ خروجی برحسب میلی‌ثانیه */
    private fun pingOnce(): Double? {
        val connection = openConnection(PING_URL)
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val start = System.nanoTime()
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream?.use { it.readBytes() }
                (System.nanoTime() - start) / 1e6
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "NetworkSignalMonitor/1.0")
        connection.setRequestProperty("Accept-Encoding", "identity")
        return connection
    }

    private fun progressOf(bytes: Long, startNanos: Long, expectedBytes: Long): Pair<Int, Double> {
        val elapsedS = (System.nanoTime() - startNanos) / 1e9
        val mbps = if (elapsedS > 0) bytes * 8.0 / 1e6 / elapsedS else 0.0
        val percent = if (expectedBytes > 0) {
            ((bytes * 100) / expectedBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        return percent to mbps
    }

    /** جیتر = میانگین قدر مطلق اختلاف نمونه‌های متوالی پینگ */
    private fun jitterOf(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until samples.size) {
            sum += abs(samples[i] - samples[i - 1])
        }
        return sum / (samples.size - 1)
    }

    private fun elapsedMs(startNanos: Long): Long =
        (System.nanoTime() - startNanos) / 1_000_000
}
