package ir.netsignal.monitor

import java.util.Locale

/**
 * حالت نمایش ارقام بر اساس زبان برنامه:
 * در فارسی ارقام فارسی (۰-۹) و در انگلیسی لاتین (0-9).
 * این پرچم در NetSignalApp بر اساس زبان جاری تنظیم می‌شود.
 */
@Volatile
var persianDigitsEnabled: Boolean = true

/** تبدیل ارقام لاتین رشته به ارقام فارسی (فقط وقتی زبان فارسی است) */
fun String.toDigits(): String {
    if (!persianDigitsEnabled) return this
    val fa = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    val builder = StringBuilder(length)
    for (ch in this) {
        builder.append(if (ch in '0'..'9') fa[ch - '0'] else ch)
    }
    return builder.toString()
}

fun Int.toDigits(): String = toString().toDigits()

fun Long.toDigits(): String = toString().toDigits()

/** قالب عدد با یک رقم اعشار؛ در فارسی ارقام و ممیز فارسی می‌شوند */
fun formatNumber1f(value: Double): String {
    val raw = String.format(Locale.US, "%.1f", value)
    return if (persianDigitsEnabled) raw.replace('.', '٫').toDigits() else raw
}

/** قالب عدد با دو رقم اعشار */
fun formatNumber2f(value: Double): String {
    val raw = String.format(Locale.US, "%.2f", value)
    return if (persianDigitsEnabled) raw.replace('.', '٫').toDigits() else raw
}

/**
 * تاریخ جلالی بدون هیچ وابستگی (پورت الگوریتم استاندارد jalaali).
 * روی همهٔ نسخه‌های اندروید (API 21+) کار می‌کند؛ در زبان انگلیسی میلادی نمایش داده می‌شود.
 */
object JalaliDate {

    /** خروجی: «۱۴۰۳/۰۵/۱۲ ۱۴:۳۰» در فارسی یا «2024/08/02 14:30» در انگلیسی */
    fun formatTimestamp(ms: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val hh = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val mm = cal.get(java.util.Calendar.MINUTE)
        return if (persianDigitsEnabled) {
            val j = jalaliFromGregorian(y, m, d)
            String.format(
                Locale.US, "%04d/%02d/%02d %02d:%02d", j[0], j[1], j[2], hh, mm
            ).toDigits()
        } else {
            String.format(Locale.US, "%04d/%02d/%02d %02d:%02d", y, m, d, hh, mm)
        }
    }

    /** گرفتن روز ژولینی از تاریخ میلادی (الگوریتم jalaali) */
    private fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Int {
        fun div(a: Int, b: Int) = a / b
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
            div(153 * ((gm + 9) % 12) + 2, 5) + gd - 34840408
        d -= div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) - 752
        return d
    }

    /** گرفتن سال میلادی از روز ژولینی (فقط سال لازم است) */
    private fun gregorianYearFromJdn(jdn: Int): Int {
        fun div(a: Int, b: Int) = a / b
        var j = 4 * jdn + 139361631
        j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(j % 1461, 4) * 5 + 308
        val gm = div(i, 153) % 12 + 1
        return div(j, 1461) - 100100 + div(8 - gm, 6)
    }

    /** کبیسه‌بینی و نقطهٔ نوین سال جلالی: خروجی [کبیسه، سال میلادی، روز اسفند/مارس] */
    private fun jalCal(jy: Int): IntArray {
        fun div(a: Int, b: Int) = a / b
        val breaks = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
        )
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(jump % 33, 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(n % 33 + 3, 4)
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = ((n + 1) % 33 - 1) % 4
        if (leap == -1) leap = 4
        return intArrayOf(leap, gy, march)
    }

    /** تبدیل تاریخ میلادی به [سال، ماه، روز] هجری شمسی */
    private fun jalaliFromGregorian(gy: Int, gm: Int, gd: Int): IntArray {
        fun div(a: Int, b: Int) = a / b
        val jdn = gregorianToJdn(gy, gm, gd)
        val gYear = gregorianYearFromJdn(jdn)
        var jy = gYear - 621
        val r = jalCal(jy)
        val jdn1f = gregorianToJdn(r[1], 3, r[2])
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                // ۶ ماه اول سال (۳۱ روزه)
                return intArrayOf(jy, 1 + k / 31, k % 31 + 1)
            }
            k -= 186
        } else {
            // سال جلالی قبل
            jy -= 1
            k += 179
            if (r[0] == 1) k += 1
        }
        // ۶ ماه دوم سال (۳۰ روزه)
        return intArrayOf(jy, 7 + k / 30, k % 30 + 1)
    }
}
