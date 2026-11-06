package ir.netsignal.monitor

/**
 * جداول استاندارد 3GPP برای تبدیل کانال فرکانسی به باند و فرکانس:
 *  - LTE (TS 36.101): EARFCN → باند + فرکانس دانلینک
 *  - NR (TS 38.104): NR-ARFCN → فرکانس → باند
 *  - GSM و WCDMA: تخمین فرکانس/باند از ARFCN/UARFCN
 * همهٔ توابع خالص‌اند و در واحد تست ذهنی با مقادیر شناخته‌شده بررسی شده‌اند.
 */
object BandTables {

    /**
     * جدول LTE — هر ردیف: [باند، فرکانس پایه DL (MHz)، آفست EARFCN، کمینه، بیشینه]
     * فرکانس: F = fLow + 0.1 × (earfcn − nOffs)
     */
    private val LTE_TABLE = arrayOf(
        intArrayOf(1, 2110, 0, 0, 599),
        intArrayOf(2, 1930, 600, 600, 1199),
        intArrayOf(3, 1805, 1200, 1200, 1949),
        intArrayOf(4, 2110, 1950, 1950, 2399),
        intArrayOf(5, 869, 2400, 2400, 2649),
        intArrayOf(6, 875, 2650, 2650, 2749),
        intArrayOf(7, 2620, 2750, 2750, 3449),
        intArrayOf(8, 925, 3450, 3450, 3799),
        intArrayOf(9, 1844, 3800, 3800, 4149),
        intArrayOf(10, 2110, 4150, 4150, 4749),
        intArrayOf(11, 1475, 4750, 4750, 4949),
        intArrayOf(12, 729, 5010, 5010, 5179),
        intArrayOf(13, 746, 5180, 5180, 5279),
        intArrayOf(14, 758, 5280, 5280, 5379),
        intArrayOf(17, 734, 5730, 5730, 5849),
        intArrayOf(18, 860, 5850, 5850, 5999),
        intArrayOf(19, 875, 6000, 6000, 6149),
        intArrayOf(20, 791, 6150, 6150, 6449),
        intArrayOf(21, 1495, 6450, 6450, 6599),
        intArrayOf(22, 3510, 6600, 6600, 7399),
        intArrayOf(23, 2180, 7500, 7500, 7699),
        intArrayOf(24, 1525, 7700, 7700, 8039),
        intArrayOf(25, 1930, 8040, 8040, 8689),
        intArrayOf(26, 859, 8690, 8690, 9039),
        intArrayOf(27, 852, 9040, 9040, 9209),
        intArrayOf(28, 758, 9210, 9210, 9659),
        intArrayOf(29, 717, 9660, 9660, 9769),
        intArrayOf(30, 2305, 9770, 9770, 9869),
        intArrayOf(31, 462, 9870, 9870, 9919),
        intArrayOf(32, 1452, 9920, 9920, 10359),
        intArrayOf(33, 1900, 36000, 36000, 36199),
        intArrayOf(34, 2010, 36200, 36200, 36349),
        intArrayOf(35, 1850, 36350, 36350, 36949),
        intArrayOf(36, 1930, 36950, 36950, 37549),
        intArrayOf(37, 1910, 37550, 37550, 37749),
        intArrayOf(38, 2570, 37750, 37750, 38249),
        intArrayOf(39, 1880, 38250, 38250, 38649),
        intArrayOf(40, 2300, 38650, 38650, 39649),
        intArrayOf(41, 2496, 39650, 39650, 41589),
        intArrayOf(42, 3400, 41590, 41590, 43589),
        intArrayOf(43, 3600, 43590, 43590, 45589),
        intArrayOf(44, 703, 45590, 45590, 46589),
        intArrayOf(45, 1447, 46590, 46590, 46789),
        intArrayOf(46, 5150, 46790, 46790, 54539),
        intArrayOf(47, 5855, 54540, 54540, 55239),
        intArrayOf(48, 3550, 55240, 55240, 56739),
        intArrayOf(49, 3550, 56740, 56740, 58239),
        intArrayOf(50, 1432, 58240, 58240, 59139),
        intArrayOf(51, 1427, 59140, 59140, 59339),
        intArrayOf(52, 3300, 59340, 59340, 59539),
        intArrayOf(53, 2483, 59540, 59540, 59739),
        intArrayOf(65, 2110, 65536, 65536, 66435),
        intArrayOf(66, 2110, 66436, 66436, 67335),
        intArrayOf(67, 738, 67336, 67336, 67535),
        intArrayOf(68, 753, 67536, 67536, 67835),
        intArrayOf(69, 728, 67836, 67836, 68335),
        intArrayOf(70, 1695, 68336, 68336, 68585),
        intArrayOf(71, 617, 68586, 68586, 68935),
        intArrayOf(72, 461, 68936, 68936, 68995),
        intArrayOf(73, 461, 68996, 68996, 69035),
        intArrayOf(74, 1475, 69036, 69036, 69165),
        intArrayOf(75, 1432, 69166, 69166, 69365),
        intArrayOf(76, 1475, 69366, 69366, 69465),
        intArrayOf(85, 728, 69466, 69466, 70365)
    )

    /** شمارهٔ باند LTE از EARFCN؛ نامعتبر → null */
    fun lteBand(earfcn: Int?): Int? {
        if (earfcn == null || earfcn < 0) return null
        for (row in LTE_TABLE) {
            if (earfcn >= row[3] && earfcn <= row[4]) return row[0]
        }
        return null
    }

    /** فرکانس دانلینک LTE برحسب مگاهرتز؛ نامعتبر → null */
    fun lteFreqMHz(earfcn: Int?): Double? {
        if (earfcn == null || earfcn < 0) return null
        for (row in LTE_TABLE) {
            if (earfcn >= row[3] && earfcn <= row[4]) {
                return row[1] + 0.1 * (earfcn - row[2])
            }
        }
        return null
    }

    /** فرکانس NR برحسب مگاهرتز از NR-ARFCN (TS 38.104 بخش 5.4.2.1) */
    fun nrFreqMHz(nrarfcn: Int?): Double? {
        if (nrarfcn == null || nrarfcn < 0) return null
        return when {
            nrarfcn < 600_000 -> 0.005 * nrarfcn
            nrarfcn < 2_016_667 -> 3000.0 + 0.015 * (nrarfcn - 600_000)
            else -> 24250.08 + 0.015 * (nrarfcn - 2_016_667)
        }
    }

    /**
     * برچسب باند NR از فرکانس. برخی بازه‌ها بین دو باند مشترک‌اند
     * (مثل n77/n78) و برچسب ترکیبی برمی‌گردانند؛ ناشناخته → null
     */
    fun nrBandLabel(freqMHz: Double?): String? {
        if (freqMHz == null) return null
        return when {
            freqMHz in 617.0..652.0 -> "n71"
            freqMHz in 717.0..728.0 -> "n29"
            freqMHz in 729.0..746.0 -> "n12"
            freqMHz in 746.0..756.0 -> "n13"
            freqMHz in 758.0..768.0 -> "n14/n28"
            freqMHz in 768.0..803.0 -> "n28"
            freqMHz in 869.0..894.0 -> "n5"
            freqMHz in 859.0..893.0 -> "n26"
            freqMHz in 925.0..960.0 -> "n8"
            freqMHz in 1427.0..1432.0 -> "n51"
            freqMHz in 1432.0..1474.0 -> "n50/n75"
            freqMHz in 1475.0..1517.0 -> "n74/n50"
            freqMHz in 1525.0..1559.0 -> "n24"
            freqMHz in 1805.0..1880.0 -> "n3"
            freqMHz in 1880.0..1920.0 -> "n39"
            freqMHz in 1930.0..1990.0 -> "n2"
            freqMHz in 1990.0..1995.0 -> "n25"
            freqMHz in 1995.0..2020.0 -> "n70"
            freqMHz in 2010.0..2025.0 -> "n34"
            freqMHz in 2110.0..2170.0 -> "n1"
            freqMHz in 2170.0..2200.0 -> "n65/n66"
            freqMHz in 2300.0..2400.0 -> "n40"
            freqMHz in 2483.5..2495.0 -> "n53"
            freqMHz in 2496.0..2569.0 -> "n41"
            freqMHz in 2570.0..2620.0 -> "n38/n41"
            freqMHz in 2620.0..2690.0 -> "n7/n41"
            freqMHz in 3300.01..3800.0 -> "n77/n78"
            freqMHz in 3800.0..4200.0 -> "n77"
            freqMHz in 4400.0..5000.0 -> "n79"
            freqMHz in 5150.0..5854.0 -> "n46"
            freqMHz in 5855.0..5925.0 -> "n47/n46"
            freqMHz in 24250.0..26500.0 -> "n258"
            freqMHz in 26500.0..27500.0 -> "n257/n258"
            freqMHz in 27500.0..28350.0 -> "n261"
            freqMHz in 37000.0..40000.0 -> "n260"
            else -> null
        }
    }

    /** باند NR از NR-ARFCN */
    fun nrBandLabelFromArfcn(nrarfcn: Int?): String? = nrBandLabel(nrFreqMHz(nrarfcn))

    /**
     * تخمین فرکانس GSM از ARFCN (مگاهرتز):
     * GSM900 (1-124)، E-GSM (975-1023)، DCS1800 (512-885)
     */
    fun gsmFreqMHz(arfcn: Int?): Double? {
        if (arfcn == null || arfcn < 0) return null
        return when (arfcn) {
            in 1..124 -> 935.0 + 0.2 * arfcn
            in 975..1023 -> 925.0 + 0.2 * (arfcn - 975)
            in 512..885 -> 1805.0 + 0.1 * (arfcn - 512)
            else -> null
        }
    }

    /** تخمین باند GSM از فرکانس */
    fun gsmBandLabel(arfcn: Int?): String? {
        val f = gsmFreqMHz(arfcn) ?: return null
        return when {
            f < 1000.0 -> "B8"
            f < 1900.0 -> "B3"
            else -> "B2"
        }
    }

    /**
     * تخمین باند WCDMA از UARFCN (بسته‌های رایج):
     * B1 (10562-10838)، B8 (2937-3088)، B3 (11625-11813)، B2 (9662-9938)
     */
    fun wcdmaBandLabel(uarfcn: Int?): String? {
        if (uarfcn == null || uarfcn < 0) return null
        return when (uarfcn) {
            in 10562..10838 -> "B1"
            in 2937..3088 -> "B8"
            in 11625..11813 -> "B3"
            in 9662..9938 -> "B2"
            else -> null
        }
    }

    /** تخمین فرکانس WCDMA از UARFCN برای باندهای رایج */
    fun wcdmaFreqMHz(uarfcn: Int?): Double? {
        if (uarfcn == null || uarfcn < 0) return null
        return when (uarfcn) {
            in 10562..10838 -> 2110.0 + 0.2 * (uarfcn - 10562)
            in 2937..3088 -> 925.0 + 0.2 * (uarfcn - 2937)
            in 11625..11813 -> 1805.0 + 0.2 * (uarfcn - 11625)
            in 9662..9938 -> 1930.0 + 0.2 * (uarfcn - 9662)
            else -> null
        }
    }
}
