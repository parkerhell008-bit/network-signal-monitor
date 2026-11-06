package ir.netsignal.monitor

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ساخت فایل‌های خروجی CSV در cache/exports برای اشتراک‌گذاری از FileProvider.
 */
object CsvExporter {

    private fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** خروجی CSV از تاریخچهٔ نمونه‌های سیگنال (فایل خام سرویس) */
    fun exportSignalLog(context: Context): File? {
        val src = SignalLogStore.file(context) ?: return null
        return try {
            val out = File(exportsDir(context), "signal_log_${stamp()}.csv")
            src.copyTo(out, overwrite = true)
            out
        } catch (t: Throwable) {
            null
        }
    }

    /** خروجی CSV از تاریخچهٔ تست‌های سرعت */
    fun exportSpeedHistory(context: Context, entries: List<SpeedTestHistory.Entry>): File? {
        if (entries.isEmpty()) return null
        return try {
            val out = File(exportsDir(context), "speed_history_${stamp()}.csv")
            val sb = StringBuilder("timestamp,ping_ms,jitter_ms,download_mbps,upload_mbps\n")
            for (e in entries) {
                val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(e.timestampMs))
                sb.append(iso).append(',')
                    .append(String.format(Locale.US, "%.1f", e.pingMs)).append(',')
                    .append(String.format(Locale.US, "%.1f", e.jitterMs)).append(',')
                    .append(String.format(Locale.US, "%.2f", e.downloadMbps)).append(',')
                    .append(String.format(Locale.US, "%.2f", e.uploadMbps)).append('\n')
            }
            out.writeText(sb.toString())
            out
        } catch (t: Throwable) {
            null
        }
    }
}
