package ir.netsignal.monitor

/**
 * وضعیت لحظه‌ای سیگنال — بین اکتیویتی، سرویس پس‌زمینه، ویجت و کاشی سریع
 * به‌اشتراک گذاشته می‌شود (همه در یک پروسه هستند).
 */
object SignalState {
    @Volatile
    var netLabel: String = "—"

    @Volatile
    var dbm: Int? = null

    @Volatile
    var downKbps: Double = 0.0

    @Volatile
    var upKbps: Double = 0.0

    @Volatile
    var updatedAtMs: Long = 0L
}
