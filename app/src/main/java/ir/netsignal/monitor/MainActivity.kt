package ir.netsignal.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
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
import android.telephony.GsmCellLocation
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    // ───────────────────────── سازنده‌های داخلی ─────────────────────────

    /** اطلاعات عددی سلول سروینگ برای پرس‌وجوی OpenCelliD */
    private data class NumericServing(
        val cid: Int?,
        val lac: Int?,
        val mcc: String?,
        val mnc: String?,
        val radio: String?,
        val bandwidthMhz: Int? = null
    )

    private companion object {
        const val PREFS_NAME = "net_signal_prefs"
        const val KEY_OPENCELLID = "opencellid_key"
        const val CELL_POLL_INTERVAL_MS = 3000L
    }

    // ───────────────────────── سرویس‌ها و وضعیت ─────────────────────────

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prefs: SharedPreferences
    private lateinit var cellAdapter: CellAdapter

    private var signalCallback: TelephonyCallback? = null
    private var phoneListener: PhoneStateListener? = null
    private var cellPollJob: Job? = null
    private var distanceJob: Job? = null
    private var speedTestJob: Job? = null

    private var lastDbm: Int? = null
    private var lastDistanceText: String? = null
    private var lastServingGeneration: String? = null
    private var numericServing: NumericServing? = null

    // ───────────────────────── ویوها ─────────────────────────

    private lateinit var tvPermissionGate: TextView
    private lateinit var gaugeView: SignalGaugeView
    private lateinit var barsView: SignalBarsView
    private lateinit var chipNetwork: Chip
    private lateinit var tvOperatorName: TextView
    private lateinit var tvSimOperator: TextView
    private lateinit var tvSimState: TextView
    private lateinit var tvCountryIso: TextView
    private lateinit var tvRoaming: TextView
    private lateinit var tvDataState: TextView
    private lateinit var tvDataActivity: TextView
    private lateinit var tvPhoneCount: TextView
    private lateinit var tvDbm: TextView
    private lateinit var tvAsu: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvRsrp: TextView
    private lateinit var tvRsrq: TextView
    private lateinit var tvSnr: TextView
    private lateinit var tvRssi: TextView
    private lateinit var tvServingRat: TextView
    private lateinit var tvServingPci: TextView
    private lateinit var tvServingCid: TextView
    private lateinit var tvServingTac: TextView
    private lateinit var tvServingEarfcn: TextView
    private lateinit var tvServingMccMnc: TextView
    private lateinit var tvServingBandwidth: TextView
    private lateinit var recyclerCells: RecyclerView
    private lateinit var tvNoCells: TextView
    private lateinit var btnLocateTower: MaterialButton
    private lateinit var tvTowerDistance: TextView
    private lateinit var tvTowerBearing: TextView
    private lateinit var tvGpsCoords: TextView
    private lateinit var etApiKey: TextInputEditText
    private lateinit var btnSaveKey: MaterialButton
    private lateinit var llSimList: LinearLayout
    private lateinit var tvSimCount: TextView
    private lateinit var btnSpeedTest: MaterialButton
    private lateinit var progressSpeed: LinearProgressIndicator
    private lateinit var tvPing: TextView
    private lateinit var tvDownload: TextView
    private lateinit var tvUpload: TextView
    private lateinit var tvSpeedStatus: TextView

    // ───────────────────────── مجوزها ─────────────────────────

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            handlePermissionResult(grants)
        }

    // ───────────────────────── چرخه حیات ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        subscriptionManager =
            getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        bindViews()
        setupCellsRecyclerView()
        setupButtons()

        etApiKey.setText(prefs.getString(KEY_OPENCELLID, "") ?: "")
    }

    override fun onResume() {
        super.onResume()
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            startAll()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onPause() {
        super.onPause()
        stopAll()
    }

    // ───────────────────────── راه‌اندازی UI ─────────────────────────

    private fun bindViews() {
        tvPermissionGate = findViewById(R.id.tvPermissionGate)
        gaugeView = findViewById(R.id.gaugeView)
        barsView = findViewById(R.id.barsView)
        chipNetwork = findViewById(R.id.chipNetwork)
        tvOperatorName = findViewById(R.id.tvOperatorName)
        tvSimOperator = findViewById(R.id.tvSimOperator)
        tvSimState = findViewById(R.id.tvSimState)
        tvCountryIso = findViewById(R.id.tvCountryIso)
        tvRoaming = findViewById(R.id.tvRoaming)
        tvDataState = findViewById(R.id.tvDataState)
        tvDataActivity = findViewById(R.id.tvDataActivity)
        tvPhoneCount = findViewById(R.id.tvPhoneCount)
        tvDbm = findViewById(R.id.tvDbm)
        tvAsu = findViewById(R.id.tvAsu)
        tvLevel = findViewById(R.id.tvLevel)
        tvRsrp = findViewById(R.id.tvRsrp)
        tvRsrq = findViewById(R.id.tvRsrq)
        tvSnr = findViewById(R.id.tvSnr)
        tvRssi = findViewById(R.id.tvRssi)
        tvServingRat = findViewById(R.id.tvServingRat)
        tvServingPci = findViewById(R.id.tvServingPci)
        tvServingCid = findViewById(R.id.tvServingCid)
        tvServingTac = findViewById(R.id.tvServingTac)
        tvServingEarfcn = findViewById(R.id.tvServingEarfcn)
        tvServingMccMnc = findViewById(R.id.tvServingMccMnc)
        tvServingBandwidth = findViewById(R.id.tvServingBandwidth)
        recyclerCells = findViewById(R.id.recyclerCells)
        tvNoCells = findViewById(R.id.tvNoCells)
        btnLocateTower = findViewById(R.id.btnLocateTower)
        tvTowerDistance = findViewById(R.id.tvTowerDistance)
        tvTowerBearing = findViewById(R.id.tvTowerBearing)
        tvGpsCoords = findViewById(R.id.tvGpsCoords)
        etApiKey = findViewById(R.id.etApiKey)
        btnSaveKey = findViewById(R.id.btnSaveKey)
        llSimList = findViewById(R.id.llSimList)
        tvSimCount = findViewById(R.id.tvSimCount)
        btnSpeedTest = findViewById(R.id.btnSpeedTest)
        progressSpeed = findViewById(R.id.progressSpeed)
        tvPing = findViewById(R.id.tvPing)
        tvDownload = findViewById(R.id.tvDownload)
        tvUpload = findViewById(R.id.tvUpload)
        tvSpeedStatus = findViewById(R.id.tvSpeedStatus)
    }

    private fun setupCellsRecyclerView() {
        cellAdapter = CellAdapter()
        recyclerCells.layoutManager = LinearLayoutManager(this)
        recyclerCells.adapter = cellAdapter
        recyclerCells.isNestedScrollingEnabled = false
    }

    private fun setupButtons() {
        btnSaveKey.setOnClickListener { saveApiKey() }
        btnLocateTower.setOnClickListener { onLocateTowerClicked() }
        btnSpeedTest.setOnClickListener { toggleSpeedTest() }
    }

    // ───────────────────────── مجوزها ─────────────────────────

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasPhonePermission(): Boolean =
        hasPermission(Manifest.permission.READ_PHONE_STATE)

    private fun hasLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun missingPermissions(): List<String> {
        val wanted = mutableListOf<String>()
        if (!hasPhonePermission()) wanted.add(Manifest.permission.READ_PHONE_STATE)
        if (!hasLocationPermission()) {
            wanted.add(Manifest.permission.ACCESS_FINE_LOCATION)
            wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return wanted
    }

    private fun handlePermissionResult(grants: Map<String, Boolean>) {
        val phoneGranted = grants[Manifest.permission.READ_PHONE_STATE] == true
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when {
            phoneGranted -> {
                startAll()
                // اگر موقعیت رد شده باشد، API 30+ لیست سلول‌ها را خالی برمی‌گرداند؛
                // به‌جای سکوت، راهنمای شفاف نمایش می‌دهیم.
                if (!locationGranted) {
                    toast(getString(R.string.location_needed_for_cells))
                }
            }
            locationGranted -> {
                tvPermissionGate.visibility = View.VISIBLE
                tvPermissionGate.text = getString(R.string.sim_no_permission)
            }
            else -> {
                tvPermissionGate.visibility = View.VISIBLE
                tvPermissionGate.text = getString(R.string.permission_gate)
                toast(getString(R.string.permission_gate))
            }
        }
    }

    // ───────────────────────── شروع/توقف به‌روزرسانی‌ها ─────────────────────────

    private fun startAll() {
        tvPermissionGate.visibility = View.GONE
        refreshTelephonyInfo()
        refreshDualSim()
        registerSignalListener()
        startCellPolling()
        updateNetworkType()
    }

    private fun stopAll() {
        unregisterSignalListener()
        cellPollJob?.cancel()
        cellPollJob = null
        // توقف کارهای در جریان (تست سرعت / GPS+OpenCelliD) هنگام خروج از صفحه
        distanceJob?.cancel()
        distanceJob = null
        speedTestJob?.cancel()
        speedTestJob = null
    }

    // ───────────────────────── اطلاعات تلفن / سیم‌کارت ─────────────────────────

    private fun refreshTelephonyInfo() {
        if (!hasPhonePermission()) {
            tvOperatorName.text = "—"
            tvSimOperator.text = "—"
            tvSimState.text = "—"
            tvCountryIso.text = "—"
            tvRoaming.text = "—"
            tvDataState.text = "—"
            tvDataActivity.text = "—"
            tvPhoneCount.text = "—"
            return
        }
        try {
            val operatorName = telephonyManager.networkOperatorName
                .ifBlank {
                    telephonyManager.simOperatorName
                        .ifBlank { getString(R.string.unknown_operator) }
                }
            tvOperatorName.text = operatorName
            tvSimOperator.text = telephonyManager.simOperatorName
                .ifBlank { getString(R.string.unknown_operator) }
            tvSimState.text = simStateText(telephonyManager.simState)
            tvCountryIso.text = telephonyManager.networkCountryIso
                .uppercase(Locale.US)
                .ifBlank { "—" }
            tvRoaming.text = if (telephonyManager.isNetworkRoaming) {
                getString(R.string.roaming_on)
            } else {
                getString(R.string.roaming_off)
            }
            tvDataState.text = dataStateText(telephonyManager.dataState)
            tvDataActivity.text = dataActivityText(telephonyManager.dataActivity)
            val modems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager.activeModemCount
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.phoneCount
            }
            tvPhoneCount.text = modems.toPersianDigits()
        } catch (e: SecurityException) {
            tvOperatorName.text = getString(R.string.sim_no_permission)
        }
    }

    private fun simStateText(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> "سیم‌کارت موجود نیست"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "قفل با PIN"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "قفل با PUK"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "قفل شبکه"
        TelephonyManager.SIM_STATE_READY -> "آماده"
        TelephonyManager.SIM_STATE_NOT_READY -> "غیرفعال"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "غیرفعال دائمی"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "خطای سخت‌افزاری سیم‌کارت"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "محدودشده"
        else -> "نامشخص"
    }

    private fun dataStateText(state: Int): String = when (state) {
        TelephonyManager.DATA_CONNECTED -> "متصل"
        TelephonyManager.DATA_CONNECTING -> "در حال اتصال"
        TelephonyManager.DATA_DISCONNECTED -> "قطع"
        TelephonyManager.DATA_SUSPENDED -> "معلق"
        else -> "نامشخص"
    }

    private fun dataActivityText(activity: Int): String = when (activity) {
        TelephonyManager.DATA_ACTIVITY_NONE -> "بدون ترافیک"
        TelephonyManager.DATA_ACTIVITY_IN -> "دریافت داده"
        TelephonyManager.DATA_ACTIVITY_OUT -> "ارسال داده"
        TelephonyManager.DATA_ACTIVITY_INOUT -> "ارسال و دریافت"
        TelephonyManager.DATA_ACTIVITY_DORMANT -> "خوابیده (Dormant)"
        else -> "نامشخص"
    }

    private fun levelText(level: Int): String = when (level) {
        4 -> "عالی"
        3 -> "خوب"
        2 -> "متوسط"
        1 -> "ضعیف"
        0 -> "خیلی ضعیف"
        else -> "نامشخص"
    }

    // ───────────────────────── تشخیص نوع / نسل شبکه ─────────────────────────

    private fun currentNetworkType(tm: TelephonyManager): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tm.dataNetworkType
        } else {
            @Suppress("DEPRECATION")
            tm.networkType
        }
    } catch (e: SecurityException) {
        TelephonyManager.NETWORK_TYPE_UNKNOWN
    } catch (e: IllegalArgumentException) {
        TelephonyManager.NETWORK_TYPE_UNKNOWN
    }

    /** خروجی: برچسب فارسی کامل + رشته نسل (برای نشانِ رنگی سلول‌ها) */
    private fun networkTypeInfo(type: Int): Pair<String, String> = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "2G – GPRS" to "2G"
        TelephonyManager.NETWORK_TYPE_EDGE -> "2G – EDGE" to "2G"
        TelephonyManager.NETWORK_TYPE_IDEN -> "2G – iDEN" to "2G"
        TelephonyManager.NETWORK_TYPE_CDMA -> "3G – CDMA" to "3G"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "3G – 1xRTT" to "3G"
        TelephonyManager.NETWORK_TYPE_UMTS -> "3G – UMTS" to "3G"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "3G – EVDO Rev.0" to "3G"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G – EVDO Rev.A" to "3G"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G – EVDO Rev.B" to "3G"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "3G – eHRPD" to "3G"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "3G – HSDPA" to "3G"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "3G – HSUPA" to "3G"
        TelephonyManager.NETWORK_TYPE_HSPA -> "3G – HSPA" to "3G"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G+ – HSPA+" to "3G"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G – TD-SCDMA" to "3G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G – LTE" to "4G"
        TelephonyManager.NETWORK_TYPE_LTE_CA -> "4G+ – LTE-A (CA)" to "4G"
        TelephonyManager.NETWORK_TYPE_NR -> "5G – NR" to "5G"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN" to "WLAN"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "نامشخص" to "—"
        else -> "نوع ناشناخته (" + type.toString().toPersianDigits() + ")" to "—"
    }

    private fun updateNetworkType() {
        if (!hasPhonePermission()) {
            chipNetwork.text = "—"
            return
        }
        val (label, _) = networkTypeInfo(currentNetworkType(telephonyManager))
        chipNetwork.text = label
    }

    // ───────────────────────── شنونده قدرت سیگنال ─────────────────────────

    private fun registerSignalListener() {
        if (!hasPhonePermission()) return
        // جلوگیری از ثبت تکراری کال‌بک (نشت حافظه) در جریان onResume مجدد
        unregisterSignalListener()

        // توجه: طبق مستندات رسمی اندروید، registerTelephonyCallback/TelephonyCallback
        // از API 31 (Android 12 / S) اضافه شده است؛ بنابراین بازهٔ API 26 تا 30
        // با PhoneStateListener (منسوخ‌شده اما هنوز پشتیبانی‌شده) پوشش داده می‌شود.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    onSignalUpdated(signalStrength)
                }
            }
            signalCallback = callback
            telephonyManager.registerTelephonyCallback(
                ContextCompat.getMainExecutor(this),
                callback
            )
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    signalStrength?.let { onSignalUpdated(it) }
                }
            }
            phoneListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }
    }

    private fun unregisterSignalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            signalCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        } else {
            phoneListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
        signalCallback = null
        phoneListener = null
    }

    private fun onSignalUpdated(ss: SignalStrength) {
        // SignalStrength.getDbm() از API 29 در دسترس است؛ در API 26-28 از محاسبهٔ قدیمی GSM استفاده می‌کنیم
        val dbmRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ss.dbm
        } else {
            @Suppress("DEPRECATION")
            legacyGsmDbm(ss)
        }
        val dbm = dbmRaw.takeIf { it != Int.MAX_VALUE && it != 0 }
        lastDbm = dbm

        dbm?.let { gaugeView.setDbm(it) }
        barsView.setLevel(ss.level)

        tvDbm.text = formatDbmOrNull(dbm)
        tvLevel.text = levelText(ss.level)

        val asu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ss.asuLevel else -1
        tvAsu.text = if (asu in 0..999) asu.toPersianDigits() else "—"

        var rsrp: Int? = null
        var rsrq: Int? = null
        var snr: Int? = null
        var rssi: Int? = null

        // SignalStrength.cellSignalStrengths از API 29 در دسترس است
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (css in ss.cellSignalStrengths) {
                when (css) {
                    is CellSignalStrengthLte -> {
                        rsrp = validSignal(css.rsrp) ?: rsrp
                        rsrq = validSignal(css.rsrq) ?: rsrq
                        rssi = validSignal(css.rssi) ?: rssi
                        snr = validSignal(css.rssnr) ?: snr
                    }
                    is CellSignalStrengthNr -> {
                        rsrp = validSignal(css.ssRsrp) ?: rsrp
                        rsrq = validSignal(css.ssRsrq) ?: rsrq
                        snr = validSignal(css.ssSinr) ?: snr
                    }
                }
            }
        }

        tvRsrp.text = formatDbmOrNull(rsrp)
        tvRsrq.text = rsrq?.let { it.toPersianDigits() + " dB" } ?: "—"
        tvSnr.text = snr?.let { it.toPersianDigits() + " dB" } ?: "—"
        tvRssi.text = formatDbmOrNull(rssi)
    }

    /** مقادیر نامعتبر (Integer.MAX_VALUE) را حذف می‌کند */
    private fun validSignal(v: Int): Int? = v.takeIf { it != Int.MAX_VALUE }

    /**
     * محاسبهٔ قدیمی dBm برای اندروید ۸ تا ۱۰ (API 26-28) که getDbm در دسترس نیست:
     * فرمول استاندارد GSM: dBm = -113 + 2 × ASU
     */
    @Suppress("DEPRECATION")
    private fun legacyGsmDbm(ss: SignalStrength): Int {
        if (!ss.isGsm) return Int.MAX_VALUE
        val asu = ss.gsmSignalStrength
        return if (asu in 1..99) asu * 2 - 113 else Int.MAX_VALUE
    }

    private fun formatDbmOrNull(v: Int?): String =
        v?.let { it.toString().toPersianDigits() + " dBm" } ?: "—"

    // ───────────────────────── پایش سلول‌ها (هر ۳ ثانیه) ─────────────────────────

    private fun startCellPolling() {
        cellPollJob?.cancel()
        if (!hasPhonePermission()) return
        cellPollJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                refreshCellInfo()
                updateNetworkType()
                delay(CELL_POLL_INTERVAL_MS)
            }
        }
    }

    private fun refreshCellInfo() {
        if (!hasPhonePermission()) return

        val rows = mutableListOf<CellRow>()
        var servingNumeric: NumericServing? = null

        val all: List<CellInfo>? = try {
            @Suppress("DEPRECATION")
            telephonyManager.allCellInfo
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }

        if (!all.isNullOrEmpty()) {
            for (info in all) {
                try {
                    parseCellInfo(info)?.let { rows.add(it) }
                    if (info.isRegistered) {
                        servingNumeric = extractNumeric(info) ?: servingNumeric
                    }
                } catch (t: Throwable) {
                    // بعضی سازندگان CellInfo ناقص برمی‌گردانند؛ این سلول نادیده گرفته می‌شود
                }
            }
        }

        if (rows.isEmpty()) {
            appendFallbackCell(rows)
        }

        // فاصله محاسبه‌شده (در صورت وجود) به ردیف سروینگ اعمال می‌شود
        val dist = lastDistanceText
        val finalRows = if (dist == null) {
            rows
        } else {
            rows.map { if (it.registered) it.copy(distanceText = dist) else it }
        }

        cellAdapter.submit(finalRows)
        tvNoCells.visibility = if (finalRows.isEmpty()) View.VISIBLE else View.GONE

        val serving = finalRows.firstOrNull { it.registered } ?: finalRows.firstOrNull()
        if (serving != null) {
            updateServingTowerCard(serving)
        }

        numericServing = servingNumeric ?: numericServing
        updateMccMncRow()
    }

    private fun parseCellInfo(info: CellInfo): CellRow? {
        return when (info) {
            is CellInfoLte -> {
                val id = info.cellIdentity
                val sig = info.cellSignalStrength
                CellRow(
                    rat = "LTE",
                    generation = "4G",
                    pciOrPsc = fmtId(id.pci),
                    cidOrNci = fmtId(id.ci),
                    tacOrLac = fmtId(id.tac),
                    earfcnOrArfcn = fmtId(id.earfcn),
                    dbm = validSignal(sig.dbm),
                    registered = info.isRegistered
                )
            }
            is CellInfoWcdma -> {
                val id = info.cellIdentity
                val sig = info.cellSignalStrength
                CellRow(
                    rat = "UMTS",
                    generation = "3G",
                    pciOrPsc = fmtId(id.psc),
                    cidOrNci = fmtId(id.cid),
                    tacOrLac = fmtId(id.lac),
                    earfcnOrArfcn = fmtId(id.uarfcn),
                    dbm = validSignal(sig.dbm),
                    registered = info.isRegistered
                )
            }
            is CellInfoGsm -> {
                val id = info.cellIdentity
                val sig = info.cellSignalStrength
                val bsic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) id.bsic else -1
                CellRow(
                    rat = "GSM",
                    generation = "2G",
                    pciOrPsc = "BSIC " + fmtId(bsic),
                    cidOrNci = fmtId(id.cid),
                    tacOrLac = fmtId(id.lac),
                    earfcnOrArfcn = fmtId(id.arfcn),
                    dbm = validSignal(sig.dbm),
                    registered = info.isRegistered
                )
            }
            is CellInfoCdma -> {
                val id = info.cellIdentity
                val sig = info.cellSignalStrength
                CellRow(
                    rat = "CDMA",
                    generation = "3G",
                    pciOrPsc = "SID " + fmtId(id.systemId),
                    cidOrNci = fmtId(id.basestationId),
                    tacOrLac = "NID " + fmtId(id.networkId),
                    earfcnOrArfcn = "—",
                    dbm = validSignal(sig.dbm),
                    registered = info.isRegistered
                )
            }
            else -> {
                // CellInfoNr فقط از API 29 موجود است؛ چک نسخه قبل از instanceof
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info is CellInfoNr) {
                    val id = info.cellIdentity
                    val sig = info.cellSignalStrength
                    CellRow(
                        rat = "NR",
                        generation = "5G",
                        pciOrPsc = fmtId(id.pci),
                        cidOrNci = fmtId(id.nci),
                        tacOrLac = fmtId(id.tac),
                        earfcnOrArfcn = "NR-ARFCN " + fmtId(id.nrarfcn),
                        dbm = validSignal(sig.dbm),
                        registered = info.isRegistered
                    )
                } else {
                    null
                }
            }
        }
    }

    /** استخراج مقادیر عددی سلول سروینگ برای پرس‌وجوی OpenCelliD */
    private fun extractNumeric(info: CellInfo): NumericServing? {
        return when (info) {
            is CellInfoLte -> {
                val id = info.cellIdentity
                val (mcc, mnc) = idStrings(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else null,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else null
                )
                NumericServing(
                    cid = id.ci.takeIf { it != Int.MAX_VALUE },
                    lac = id.tac.takeIf { it != Int.MAX_VALUE },
                    mcc = mcc,
                    mnc = mnc,
                    radio = "lte",
                    bandwidthMhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        id.bandwidth.takeIf { it != Int.MAX_VALUE }
                    } else {
                        null
                    }
                )
            }
            is CellInfoWcdma -> {
                val id = info.cellIdentity
                val (mcc, mnc) = idStrings(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else null,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else null
                )
                NumericServing(
                    cid = id.cid.takeIf { it != Int.MAX_VALUE },
                    lac = id.lac.takeIf { it != Int.MAX_VALUE },
                    mcc = mcc,
                    mnc = mnc,
                    radio = "wcdma"
                )
            }
            is CellInfoGsm -> {
                val id = info.cellIdentity
                val (mcc, mnc) = idStrings(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else null,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else null
                )
                NumericServing(
                    cid = id.cid.takeIf { it != Int.MAX_VALUE },
                    lac = id.lac.takeIf { it != Int.MAX_VALUE },
                    mcc = mcc,
                    mnc = mnc,
                    radio = "gsm"
                )
            }
            is CellInfoCdma -> {
                val id = info.cellIdentity
                NumericServing(
                    cid = id.basestationId.takeIf { it != Int.MAX_VALUE && it != -1 },
                    lac = id.networkId.takeIf { it != Int.MAX_VALUE && it != -1 },
                    mcc = null,
                    mnc = null,
                    radio = "cdma"
                )
            }
            else -> null
        }
    }

    /** MCC/MNC را از CellIdentity می‌گیرد و در نبود آن از networkOperator */
    private fun idStrings(mccFromId: String?, mncFromId: String?): Pair<String?, String?> {
        if (!mccFromId.isNullOrBlank() && !mncFromId.isNullOrBlank()) {
            return mccFromId to mncFromId
        }
        val op = try {
            telephonyManager.networkOperator
        } catch (e: SecurityException) {
            ""
        }
        return if (op.length >= 5) {
            op.substring(0, 3) to op.substring(3)
        } else {
            mccFromId to mncFromId
        }
    }

    /** جایگزین: وقتی getAllCellInfo خالی است از GsmCellLocation استفاده می‌شود */
    private fun appendFallbackCell(rows: MutableList<CellRow>) {
        try {
            @Suppress("DEPRECATION")
            val loc = telephonyManager.cellLocation as? GsmCellLocation ?: return
            val cid = loc.cid
            val lac = loc.lac
            val psc = loc.psc
            rows.add(
                CellRow(
                    rat = "GSM/UMTS",
                    generation = "2G/3G",
                    pciOrPsc = if (psc != -1) fmtId(psc) else "—",
                    cidOrNci = fmtId(cid),
                    tacOrLac = fmtId(lac),
                    earfcnOrArfcn = "—",
                    dbm = lastDbm,
                    registered = true
                )
            )
            if (numericServing == null) {
                val (mcc, mnc) = idStrings(null, null)
                numericServing = NumericServing(
                    cid = cid.takeIf { it != -1 },
                    lac = lac.takeIf { it != -1 },
                    mcc = mcc,
                    mnc = mnc,
                    radio = "gsm"
                )
            }
        } catch (e: SecurityException) {
            // مجوز موقعیت در دسترس نیست
        }
    }

    private fun fmtId(v: Int): String =
        if (v == Int.MAX_VALUE || v == -1) "—" else v.toPersianDigits()

    private fun fmtId(v: Long): String =
        if (v == Long.MAX_VALUE || v == -1L) "—" else v.toPersianDigits()

    private fun updateServingTowerCard(row: CellRow) {
        lastServingGeneration = row.generation
        tvServingRat.text = row.generation + " – " + row.rat
        tvServingPci.text = row.pciOrPsc
        tvServingCid.text = row.cidOrNci
        tvServingTac.text = row.tacOrLac
        tvServingEarfcn.text = row.earfcnOrArfcn
        updateBandwidthRow()
    }

    private fun updateMccMncRow() {
        val op = try {
            telephonyManager.networkOperator
        } catch (e: SecurityException) {
            ""
        }
        tvServingMccMnc.text = if (op.length >= 5) {
            (op.substring(0, 3) + " – " + op.substring(3)).toPersianDigits()
        } else {
            "—"
        }
    }

    private fun updateBandwidthRow() {
        tvServingBandwidth.text = numericServing?.bandwidthMhz
            ?.let { it.toPersianDigits() + " مگاهرتز" }
            ?: "—"
    }

    // ───────────────────────── دو سیم‌کارت (Dual SIM) ─────────────────────────

    private fun refreshDualSim() {
        llSimList.removeAllViews()
        if (!hasPhonePermission()) {
            tvSimCount.text = "—"
            llSimList.addView(makeInfoRow(getString(R.string.sim_no_permission)))
            return
        }
        val subs: List<SubscriptionInfo>? = try {
            subscriptionManager.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            null
        }
        if (subs.isNullOrEmpty()) {
            tvSimCount.text = getString(R.string.sim_count_fmt, 0).toPersianDigits()
            llSimList.addView(makeInfoRow(getString(R.string.sim_none)))
            return
        }
        tvSimCount.text = getString(R.string.sim_count_fmt, subs.size).toPersianDigits()
        for (sub in subs) {
            val subTm = try {
                telephonyManager.createForSubscriptionId(sub.subscriptionId)
            } catch (e: Exception) {
                null
            }
            val typeLabel = if (subTm != null) {
                networkTypeInfo(currentNetworkType(subTm)).first
            } else {
                "—"
            }
            val carrier = sub.carrierName?.toString().orEmpty()
                .ifBlank { getString(R.string.unknown_operator) }
            // clamp: simSlotIndex طبق مستندات ممکن است -1 باشد
            val slotNumber = (sub.simSlotIndex.coerceAtLeast(0)) + 1
            llSimList.addView(
                makeInfoRow(
                    getString(R.string.sim_row_fmt, slotNumber, sub.simSlotIndex, carrier, typeLabel)
                )
            )
        }
    }

    private fun makeInfoRow(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 14f
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tv.setPadding(0, dp(6), 0, dp(6))
        return tv
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ───────────────────────── فاصله تا دکل (GPS + OpenCelliD) ─────────────────────────

    private fun onLocateTowerClicked() {
        if (!hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        if (lastServingGeneration == "5G") {
            toast(getString(R.string.err_nr_distance))
            return
        }
        val cell = numericServing
        if (cell == null || cell.cid == null || cell.lac == null || cell.radio == "cdma") {
            toast(getString(R.string.err_no_cell))
            return
        }
        val key = prefs.getString(KEY_OPENCELLID, null)
        if (key.isNullOrBlank()) {
            tvTowerDistance.text = getString(R.string.err_no_key)
            toast(getString(R.string.err_no_key))
            return
        }
        val url = buildOpenCelliDUrl(cell, key)
        if (url == null) {
            toast(getString(R.string.err_no_cell))
            return
        }
        if (distanceJob?.isActive == true) return

        tvTowerDistance.text = getString(R.string.msg_getting_gps)
        tvTowerBearing.text = "—"
        tvGpsCoords.text = "—"

        distanceJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                val location = getCurrentLocationSuspend()
                if (location == null) {
                    tvTowerDistance.text =
                        getString(R.string.err_generic, getString(R.string.err_gps_failed))
                    return@launch
                }
                tvGpsCoords.text = formatCoords(location.latitude, location.longitude)
                tvTowerDistance.text = getString(R.string.msg_querying_opencellid)

                val tower = withContext(Dispatchers.IO) { fetchTowerLocation(url) }
                val distance = haversineMeters(
                    location.latitude, location.longitude, tower.first, tower.second
                )
                val bearing = bearingDegrees(
                    location.latitude, location.longitude, tower.first, tower.second
                )
                val distStr = formatDistance(distance)
                lastDistanceText = distStr
                tvTowerDistance.text = distStr
                tvTowerBearing.text = formatBearing(bearing)
            } catch (e: TimeoutCancellationException) {
                // تایم‌اوت ۳۰ ثانیه‌ای GPS نباید بی‌صدا رد شود — پیام واضح نمایش داده می‌شود
                tvTowerDistance.text =
                    getString(R.string.err_generic, getString(R.string.err_gps_failed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tvTowerDistance.text =
                    getString(R.string.err_generic, e.message ?: "خطای ناشناخته")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocationSuspend(): Location? =
        withTimeout(30_000) {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.cancel()
                    }
            }
        }

    /**
     * آدرس OpenCelliD: برای LTE مقدار TAC به‌عنوان پارامتر lac و CID به‌عنوان cellid ارسال می‌شود.
     */
    private fun buildOpenCelliDUrl(cell: NumericServing, key: String): String? {
        val cid = cell.cid ?: return null
        val lac = cell.lac ?: return null
        val mcc = cell.mcc ?: return null
        val mnc = cell.mnc ?: return null
        // فیلد radio دقت جستجو در OpenCelliD را بالا می‌برد
        val radio = when (cell.radio) {
            "lte" -> "lte"
            "nr" -> "nr"
            "umts" -> "umts"
            else -> "gsm"
        }
        return "https://opencellid.org/cell/get" +
            "?token=" + key +
            "&mcc=" + mcc +
            "&mnc=" + mnc +
            "&lac=" + lac +
            "&cellid=" + cid +
            "&radio=" + radio
    }

    /** فراخوانی شبکه‌ای OpenCelliD (روی Dispatchers.IO اجرا می‌شود) و برگرداندن lat/lon دکل */
    private fun fetchTowerLocation(url: String): Pair<Double, Double> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException("کد پاسخ سرور: $code")
            }
            val obj = JSONObject(body)
            if (obj.has("lat") && obj.has("lon")) {
                return obj.getDouble("lat") to obj.getDouble("lon")
            }
            // کلیدهای خطای احتمالی OpenCelliD: message / error / description
            val message = listOf("message", "error", "description")
                .firstOrNull { obj.optString(it, "").isNotEmpty() }
                ?.let { k -> obj.optString(k) }
                ?: ""
            throw IOException(
                if (message.isNotEmpty()) message else "پاسخ نامعتبر از OpenCelliD"
            )
        } finally {
            connection.disconnect()
        }
    }

    /** فرمول هاورساین: فاصلهٔ کروی بین دو نقطه برحسب متر */
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /** آزیموت (یال) از کاربر به دکل برحسب درجه از شمال جغرافیایی */
    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun bearingToPersianName(bearing: Double): String {
        val names = arrayOf(
            "شمال", "شمال‌شرقی", "شرقی", "جنوب‌شرقی",
            "جنوب", "جنوب‌غربی", "غرب", "شمال‌غربی"
        )
        val index = Math.round(bearing / 45.0).toInt() % 8
        return names[index]
    }

    private fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            String.format(Locale.US, "%.0f", meters).toPersianDigits() + " متر"
        } else {
            String.format(Locale.US, "%.1f", meters / 1000.0)
                .replace('.', '٫')
                .toPersianDigits() + " کیلومتر"
        }
    }

    private fun formatBearing(bearing: Double): String {
        val deg = String.format(Locale.US, "%.0f", bearing).toPersianDigits()
        return bearingToPersianName(bearing) + " (" + deg + " درجه)"
    }

    private fun formatCoords(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon).toPersianDigits()

    private fun saveApiKey() {
        val key = etApiKey.text?.toString()?.trim().orEmpty()
        if (key.isEmpty()) {
            toast(getString(R.string.msg_key_empty))
            return
        }
        prefs.edit().putString(KEY_OPENCELLID, key).apply()
        toast(getString(R.string.msg_key_saved))
    }

    // ───────────────────────── تست سرعت ─────────────────────────

    private fun toggleSpeedTest() {
        val job = speedTestJob
        if (job != null && job.isActive) {
            job.cancel()
            speedTestJob = null
            resetSpeedUi()
            tvSpeedStatus.text = getString(R.string.speed_status_idle)
            return
        }
        if (!isNetworkOnline()) {
            tvSpeedStatus.text = getString(R.string.speed_no_internet)
            return
        }
        btnSpeedTest.text = getString(R.string.btn_stop_speed)
        tvSpeedStatus.text = getString(R.string.speed_status_running)
        tvPing.text = "—"
        tvDownload.text = "—"
        tvUpload.text = "—"
        progressSpeed.setProgressCompat(0, false)

        speedTestJob = lifecycleScope.launch(Dispatchers.Main) {
            SpeedTester.runTest(object : SpeedTester.Listener {
                override fun onPingProgress(sample: Int, total: Int, pingMs: Double, jitterMs: Double) {
                    tvPing.text = formatMs(pingMs) +
                        " (جیتر: " + formatNumber1f(jitterMs) + ")"
                    progressSpeed.setProgressCompat(sample * 20 / total, true)
                }

                override fun onDownloadProgress(percent: Int, mbps: Double) {
                    tvDownload.text = formatMbps(mbps)
                    progressSpeed.setProgressCompat(20 + percent * 50 / 100, true)
                }

                override fun onUploadProgress(percent: Int, mbps: Double) {
                    tvUpload.text = formatMbps(mbps)
                    progressSpeed.setProgressCompat(70 + percent * 30 / 100, true)
                }

                override fun onFinished(result: SpeedTester.SpeedResult) {
                    progressSpeed.setProgressCompat(100, true)
                    tvPing.text = formatMs(result.pingMs) +
                        " (جیتر: " + formatNumber1f(result.jitterMs) + ")"
                    tvDownload.text = formatMbps(result.downloadMbps)
                    tvUpload.text = formatMbps(result.uploadMbps)
                    tvSpeedStatus.text = getString(R.string.speed_status_done)
                    finishSpeedUi()
                }

                override fun onError(message: String) {
                    tvSpeedStatus.text = getString(R.string.speed_status_error) + ": " + message
                    finishSpeedUi()
                }
            })
        }
    }

    private fun finishSpeedUi() {
        speedTestJob = null
        btnSpeedTest.text = getString(R.string.btn_start_speed)
    }

    private fun resetSpeedUi() {
        btnSpeedTest.text = getString(R.string.btn_start_speed)
        progressSpeed.setProgressCompat(0, true)
    }

    private fun isNetworkOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ───────────────────────── ابزارهای قالب‌بندی ─────────────────────────

    private fun formatMs(ms: Double): String = formatNumber1f(ms) + " میلی‌ثانیه"

    private fun formatNumber1f(value: Double): String =
        String.format(Locale.US, "%.1f", value)
            .replace('.', '٫')
            .toPersianDigits()

    private fun formatMbps(mbps: Double): String =
        String.format(Locale.US, "%.2f", mbps)
            .replace('.', '٫')
            .toPersianDigits() + " مگابیت بر ثانیه"

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
