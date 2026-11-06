package ir.netsignal.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * گزارش جابجایی دکل (Handover Log):
 * هر بار سلول سروینگ عوض شود یک رخداد ثبت می‌شود؛
 * در حافظه نگهداری و روی حافظهٔ خود گوشی هم JSON می‌شود (حداکثر ۵۰ رخداد).
 */
object HandoverLog {

    data class Event(val timestampMs: Long, val fromCell: String, val toCell: String)

    private const val MAX_EVENTS = 50
    private const val FILE_NAME = "handover_log.json"

    private val events = ArrayDeque<Event>()

    /** ثبت جابجایی جدید (از سلول قبلی به فعلی) */
    @Synchronized
    fun record(context: Context, fromCell: String, toCell: String) {
        if (fromCell.isBlank() || toCell.isBlank() || fromCell == toCell) return
        events.addFirst(Event(System.currentTimeMillis(), fromCell, toCell))
        while (events.size > MAX_EVENTS) events.removeLast()
        persist(context)
    }

    /** آخرین رخدادها (از جدید به قدیم) */
    @Synchronized
    fun recent(count: Int = 15): List<Event> = events.take(count)

    /** پاک‌سازی کامل */
    @Synchronized
    fun clear(context: Context) {
        events.clear()
        fileOf(context).delete()
    }

    /** بارگذاری رخدادهای ذخیره‌شده (یک‌بار در شروع) */
    @Synchronized
    fun load(context: Context) {
        events.clear()
        val f = fileOf(context)
        if (!f.exists()) return
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                events.addLast(
                    Event(
                        obj.getLong("ts"),
                        obj.optString("from", "—"),
                        obj.optString("to", "—")
                    )
                )
            }
        } catch (t: Throwable) {
            // فایل خراب → نادیده گرفته می‌شود
        }
    }

    private fun persist(context: Context) {
        try {
            val arr = JSONArray()
            for (e in events) {
                arr.put(
                    JSONObject()
                        .put("ts", e.timestampMs)
                        .put("from", e.fromCell)
                        .put("to", e.toCell)
                )
            }
            val tmp = File(fileOf(context).parentFile, FILE_NAME + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(fileOf(context))
        } catch (t: Throwable) {
            // ذخیره‌سازی اختیاری است؛ نباید جریان اصلی را مختل کند
        }
    }

    private fun fileOf(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)
}
