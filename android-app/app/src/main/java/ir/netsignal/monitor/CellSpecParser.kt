package ir.netsignal.monitor

import android.os.Build
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr

/**
 * مشخصات کامل یک سلول — هستهٔ بخش «مشخصات ارتباطات».
 * همهٔ مقادیر عددی خام نگهداری می‌شوند و قالب‌بندی نمایشی در Activity انجام می‌شود.
 */
data class CellSpec(
    val rat: String,
    val generation: String,
    val pci: Int?,
    val cid: Long?,
    val tac: Int?,
    val earfcn: Int?,
    val bandLabel: String?,
    val freqMHz: Double?,
    val bandwidthKhz: Int?,
    val dbm: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Int?,
    val rssi: Int?,
    val rscp: Int?,
    val timingAdvance: Int?,
    val mcc: String?,
    val mnc: String?,
    val registered: Boolean
) {
    /** شناسهٔ ایستگاه پایه (eNB) از CID برای LTE: eNB = CID ÷ ۲۵۶ */
    val enbId: Long?
        get() = cid?.let { if (it in 0..Int.MAX_VALUE) it / 256 else null }

    /** کلید یکتای سلول برای تشخیص جابجایی (Handover) */
    val cellKey: String
        get() = "$rat-${cid ?: "?"}-${tac ?: "?"}"
}

/**
 * پارسر CellInfo به CellSpec — با گاردهای نسخهٔ API و مقادیر نامعتبر سازنده‌ها
 * (Integer.MAX_VALUE) مقاوم است و هیچ‌گاه استثنا پرتاب نمی‌کند.
 */
object CellSpecParser {

    private fun valid(v: Int): Int? = v.takeIf { it != Int.MAX_VALUE && it != -1 }

    private fun validLong(v: Long): Long? = v.takeIf { it != Long.MAX_VALUE && it != -1L }

    /** MCC/MNC از CellIdentity؛ در نبودش از networkOperator پیش‌فرض */
    private fun operatorOf(
        mccString: String?,
        mncString: String?,
        fallbackOperator: String?
    ): Pair<String?, String?> {
        if (!mccString.isNullOrBlank() && !mncString.isNullOrBlank()) {
            return mccString to mncString
        }
        val op = fallbackOperator.orEmpty()
        return if (op.length >= 5) {
            op.substring(0, 3) to op.substring(3)
        } else {
            mccString to mncString
        }
    }

    fun parse(info: CellInfo, fallbackOperator: String?): CellSpec? {
        return try {
            when (info) {
                is CellInfoLte -> parseLte(info.cellIdentity, info.cellSignalStrength, info.isRegistered, fallbackOperator)
                is CellInfoWcdma -> parseWcdma(info.cellIdentity, info.cellSignalStrength, info.isRegistered, fallbackOperator)
                is CellInfoGsm -> parseGsm(info.cellIdentity, info.cellSignalStrength, info.isRegistered, fallbackOperator)
                is CellInfoCdma -> parseCdma(info.cellIdentity, info.cellSignalStrength, info.isRegistered)
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
                        // getCellIdentity() در CellInfoNr نوع پایه برمی‌گرداند؛ کست صریح لازم است
                        val id = info.cellIdentity as? CellIdentityNr
                        val sig = info.cellSignalStrength as? CellSignalStrengthNr
                        if (id != null && sig != null) {
                            parseNr(id, sig, info.isRegistered, fallbackOperator)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }
        } catch (t: Throwable) {
            // برخی سازندگان CellInfo ناقص برمی‌گردانند — سلول خراب نباید برنامه را بکشد
            null
        }
    }

    private fun parseLte(
        id: CellIdentityLte,
        sig: CellSignalStrengthLte,
        registered: Boolean,
        fallbackOperator: String?
    ): CellSpec {
        val earfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) valid(id.earfcn) else null
        val (mcc, mnc) = operatorOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else null,
            fallbackOperator
        )
        return CellSpec(
            rat = "LTE",
            generation = "4G",
            pci = valid(id.pci),
            cid = valid(id.ci)?.toLong(),
            tac = valid(id.tac),
            earfcn = earfcn,
            bandLabel = BandTables.lteBand(earfcn)?.let { "B$it" },
            freqMHz = BandTables.lteFreqMHz(earfcn),
            bandwidthKhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) valid(id.bandwidth) else null,
            dbm = valid(sig.dbm),
            rsrp = valid(sig.rsrp),
            rsrq = valid(sig.rsrq),
            sinr = valid(sig.rssnr),
            rssi = valid(sig.rssi),
            rscp = null,
            timingAdvance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sig.timingAdvance.takeIf { it in 0..1282 }
            } else {
                null
            },
            mcc = mcc,
            mnc = mnc,
            registered = registered
        )
    }

    private fun parseNr(
        id: CellIdentityNr,
        sig: CellSignalStrengthNr,
        registered: Boolean,
        fallbackOperator: String?
    ): CellSpec {
        val nrarfcn = valid(id.nrarfcn)
        val (mcc, mnc) = operatorOf(id.mccString, id.mncString, fallbackOperator)
        return CellSpec(
            rat = "NR",
            generation = "5G",
            pci = valid(id.pci),
            cid = validLong(id.nci),
            tac = valid(id.tac),
            earfcn = nrarfcn,
            bandLabel = BandTables.nrBandLabelFromArfcn(nrarfcn),
            freqMHz = BandTables.nrFreqMHz(nrarfcn),
            bandwidthKhz = null,
            dbm = valid(sig.dbm),
            rsrp = valid(sig.ssRsrp),
            rsrq = valid(sig.ssRsrq),
            sinr = valid(sig.ssSinr),
            rssi = null,
            rscp = null,
            timingAdvance = null,
            mcc = mcc,
            mnc = mnc,
            registered = registered
        )
    }

    private fun parseWcdma(
        id: CellIdentityWcdma,
        sig: android.telephony.CellSignalStrengthWcdma,
        registered: Boolean,
        fallbackOperator: String?
    ): CellSpec {
        val uarfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) valid(id.uarfcn) else null
        val (mcc, mnc) = operatorOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else null,
            fallbackOperator
        )
        return CellSpec(
            rat = "UMTS",
            generation = "3G",
            pci = valid(id.psc),
            cid = valid(id.cid)?.toLong(),
            tac = valid(id.lac),
            earfcn = uarfcn,
            bandLabel = BandTables.wcdmaBandLabel(uarfcn),
            freqMHz = BandTables.wcdmaFreqMHz(uarfcn),
            bandwidthKhz = null,
            dbm = valid(sig.dbm),
            rsrp = null,
            rsrq = null,
            sinr = null,
            rssi = null,
            rscp = null,
            timingAdvance = null,
            mcc = mcc,
            mnc = mnc,
            registered = registered
        )
    }

    private fun parseGsm(
        id: CellIdentityGsm,
        sig: android.telephony.CellSignalStrengthGsm,
        registered: Boolean,
        fallbackOperator: String?
    ): CellSpec {
        val arfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) valid(id.arfcn) else null
        val (mcc, mnc) = operatorOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else null,
            fallbackOperator
        )
        return CellSpec(
            rat = "GSM",
            generation = "2G",
            pci = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) valid(id.bsic) else null,
            cid = valid(id.cid)?.toLong(),
            tac = valid(id.lac),
            earfcn = arfcn,
            bandLabel = BandTables.gsmBandLabel(arfcn),
            freqMHz = BandTables.gsmFreqMHz(arfcn),
            bandwidthKhz = null,
            dbm = valid(sig.dbm),
            rsrp = null,
            rsrq = null,
            sinr = null,
            rssi = null,
            rscp = null,
            timingAdvance = null,
            mcc = mcc,
            mnc = mnc,
            registered = registered
        )
    }

    private fun parseCdma(
        id: CellIdentityCdma,
        sig: android.telephony.CellSignalStrengthCdma,
        registered: Boolean
    ): CellSpec {
        return CellSpec(
            rat = "CDMA",
            generation = "3G",
            pci = null,
            cid = valid(id.basestationId)?.toLong(),
            tac = valid(id.networkId),
            earfcn = null,
            bandLabel = null,
            freqMHz = null,
            bandwidthKhz = null,
            dbm = valid(sig.dbm),
            rsrp = null,
            rsrq = null,
            sinr = null,
            rssi = null,
            rscp = null,
            timingAdvance = null,
            mcc = null,
            mnc = null,
            registered = registered
        )
    }

    /**
     * فاصلهٔ رادیویی از Timing Advance در LTE:
     * هر واحد TA ≈ ۷۸٫۱۲ متر (TA × 16 × Ts × c ÷ 2)
     */
    fun taDistanceMeters(ta: Int?): Double? {
        if (ta == null || ta < 0) return null
        return ta * 78.12
    }
}
