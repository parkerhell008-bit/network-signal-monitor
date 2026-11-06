package ir.netsignal.monitor

import android.content.Context
import java.io.File

/**
 * ذخیره‌ساز CSV نمونه‌های سیگنال (سرویس پس‌زمینه هر ۵ ثانیه یک سطر می‌نویسد).
 * فایل روی حافظهٔ خود گوشی نگهداری می‌شود و برای خروجی CSV استفاده می‌گردد.
 */
object SignalLogStore {

    private const val FILE_NAME = "signal_log.csv"
    private const val MAX_LINES = 8000
    private const val TRIM_TO = 6000
    private const val HEADER = "timestamp,dbm,rat,operator,cellid"

    private fun fileOf(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)

    /** افزودن سطر نمونه (thread-safe) + هرس خودکار فایل‌های بلند */
    @Synchronized
    fun append(context: Context, line: String) {
        try {
            val f = fileOf(context)
            if (!f.exists()) {
                f.writeText(HEADER + "\n")
            }
            f.appendText(line + "\n")
            trimIfNeeded(f)
        } catch (t: Throwable) {
            // خطای دیسک هرگز نباید سرویس را بکشد
        }
    }

    /** فایل اصلی (برای خروجی) — ممکن است null باشد اگر هنوز داده‌ای نیست */
    fun file(context: Context): File? {
        val f = fileOf(context)
        return if (f.exists() && f.length() > HEADER.length + 1) f else null
    }

    fun clear(context: Context) {
        try {
            fileOf(context).delete()
        } catch (t: Throwable) {
        }
    }

    private fun trimIfNeeded(f: File) {
        if (f.length() < 512 * 1024) return
        val lines = f.readLines()
        if (lines.size <= MAX_LINES) return
        f.writeText((listOf(HEADER) + lines.takeLast(TRIM_TO)).joinToString("\n") + "\n")
    }
}
