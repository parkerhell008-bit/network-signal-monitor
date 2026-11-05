package ir.netsignal.monitor

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * ذخیره‌سازی محلی نتایج تست سرعت روی SQLite خود گوشی (بدون هیچ وابستگی بیرونی).
 * نتیجهٔ هر تست کامل به‌صورت خودکار ثبت می‌شود و ۳۰ نتیجهٔ آخر
 * برای رسم نمودار روند و فهرست تاریخچه استفاده می‌گردد.
 */
class SpeedTestHistory private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    /** یک نتیجهٔ ذخیره‌شدهٔ تست سرعت */
    data class Entry(
        val id: Long,
        val timestampMs: Long,
        val pingMs: Double,
        val jitterMs: Double,
        val downloadMbps: Double,
        val uploadMbps: Double
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE (" +
                "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COL_TS INTEGER NOT NULL, " +
                "$COL_PING REAL NOT NULL, " +
                "$COL_JITTER REAL NOT NULL, " +
                "$COL_DOWN REAL NOT NULL, " +
                "$COL_UP REAL NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /** ثبت نتیجهٔ جدید و بازگرداندن شناسهٔ ردیف */
    fun insert(
        result: SpeedTester.SpeedResult,
        timestampMs: Long = System.currentTimeMillis()
    ): Long {
        val values = ContentValues().apply {
            put(COL_TS, timestampMs)
            put(COL_PING, result.pingMs)
            put(COL_JITTER, result.jitterMs)
            put(COL_DOWN, result.downloadMbps)
            put(COL_UP, result.uploadMbps)
        }
        return writableDatabase.insert(TABLE, null, values)
    }

    /** آخرین نتایج به ترتیب از جدید به قدیم */
    fun recent(limit: Int): List<Entry> {
        val out = mutableListOf<Entry>()
        readableDatabase.rawQuery(
            "SELECT $COL_ID, $COL_TS, $COL_PING, $COL_JITTER, $COL_DOWN, $COL_UP " +
                "FROM $TABLE ORDER BY $COL_TS DESC LIMIT $limit",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    Entry(
                        id = cursor.getLong(0),
                        timestampMs = cursor.getLong(1),
                        pingMs = cursor.getDouble(2),
                        jitterMs = cursor.getDouble(3),
                        downloadMbps = cursor.getDouble(4),
                        uploadMbps = cursor.getDouble(5)
                    )
                )
            }
        }
        return out
    }

    /** پاک‌سازی کامل تاریخچه */
    fun clear() {
        writableDatabase.execSQL("DELETE FROM $TABLE")
    }

    private companion object {
        const val DB_NAME = "speed_history.db"
        const val DB_VERSION = 1
        const val TABLE = "speed_results"
        const val COL_ID = "id"
        const val COL_TS = "ts"
        const val COL_PING = "ping"
        const val COL_JITTER = "jitter"
        const val COL_DOWN = "download"
        const val COL_UP = "upload"

        @Volatile
        private var instance: SpeedTestHistory? = null

        fun get(context: Context): SpeedTestHistory =
            instance ?: synchronized(this) {
                instance ?: SpeedTestHistory(context).also { instance = it }
            }
    }
}
