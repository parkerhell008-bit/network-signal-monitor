package ir.netsignal.monitor

import android.content.Context

/**
 * امتیاز ترکیبی کیفیت شبکه (۰ تا ۱۰۰):
 *  - ۴۵٪ سیگنال (RSRP اگر موجود، وگرنه dBm)
 *  - ۳۰٪ تأخیر مؤثر (پینگ + ۴ برابر جیتر)
 *  - ۲۵٪ توان عبوری (میانگین هندسی دانلود و آپلود)
 */
object NetworkQuality {

    fun score(
        dbm: Int?,
        rsrp: Int?,
        pingMs: Double?,
        jitterMs: Double?,
        downMbps: Double?,
        upMbps: Double?
    ): Int {
        val signal = signalScore(dbm, rsrp)
        val latency = latencyScore(pingMs, jitterMs)
        val throughput = throughputScore(downMbps, upMbps)
        return (0.45 * signal + 0.30 * latency + 0.25 * throughput).toInt().coerceIn(0, 100)
    }

    /** زیرامتیاز سیگنال: RSRP بین ۸۰- (بهترین) تا ۱۲۰- (بدترین) */
    fun signalScore(dbm: Int?, rsrp: Int?): Int {
        val v = rsrp ?: dbm ?: return 50
        val lo = -120.0
        val hi = -80.0
        val t = ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)
        return (t * 100).toInt()
    }

    /** زیرامتیاز تأخیر: ۲۰ms بهترین، ۴۰۰ms بدترین (جیتر با ضریب ۴ جمع می‌شود) */
    fun latencyScore(pingMs: Double?, jitterMs: Double?): Int {
        if (pingMs == null || pingMs <= 0.0) return 50
        val effective = pingMs + 4.0 * (jitterMs ?: 0.0)
        val t = 1.0 - ((effective - 20.0) / (400.0 - 20.0))
        return (t.coerceIn(0.0, 1.0) * 100).toInt()
    }

    /** زیرامتیاز توان: مقیاس لگارییمی ۰٫۵ تا ۱۰۰ مگابیت (میانگین دانلود/آپلود) */
    fun throughputScore(downMbps: Double?, upMbps: Double?): Int {
        if (downMbps == null || downMbps <= 0.0) return 50
        val combined = if (upMbps != null && upMbps > 0.0) {
            kotlin.math.sqrt(downMbps * upMbps)
        } else {
            downMbps
        }
        val lo = kotlin.math.log10(0.5)
        val hi = kotlin.math.log10(100.0)
        val t = (kotlin.math.log10(combined.coerceAtLeast(0.05)) - lo) / (hi - lo)
        return (t.coerceIn(0.0, 1.0) * 100).toInt()
    }

    /** برچسب فارسی/انگلیسی متناسب با زبان برنامه */
    fun label(context: Context, score: Int): String {
        val res = when {
            score >= 80 -> R.string.score_excellent
            score >= 60 -> R.string.score_good
            score >= 40 -> R.string.score_moderate
            score >= 20 -> R.string.score_weak
            else -> R.string.score_very_weak
        }
        return context.getString(res)
    }

    /** رنگ متناسب با امتیاز برای متن/نمودار */
    fun colorRes(score: Int): Int = when {
        score >= 60 -> R.color.sig_good
        score >= 40 -> R.color.sig_mid
        else -> R.color.sig_bad
    }
}
