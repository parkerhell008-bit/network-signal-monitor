package ir.netsignal.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
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

    private companion object {
        const val CELL_POLL_INTERVAL_MS = 3000L
        const val REPO_URL = "https://github.com/parkerhell008-bit/network-signal-monitor"
    }

    // ───────────────────────── سرویس‌ها و وضعیت ─────────────────────────

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var cellAdapter: CellAdapter
    private lateinit var historyDb: SpeedTestHistory

    private var phoneListener: PhoneStateListener? = null
    private var cellPollJob: Job? = null
    private var distanceJob: Job? = null
    private var speedTestJob: Job? = null
    private var liveTrafficJob: Job? = null
    private var pingJob: Job? = null
    private var dnsJob: Job? = null

    private var autoPromptedOnce = false

    /** اسکوپ مستقل از چرخهٔ حیات برای ثبت تاریخچه */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // وضعیت سیگنال/شبکه
    private var lastDbm: Int? = null
    private val dbmSamples = ArrayDeque<Int>()
    private var lastRsrpVal: Int? = null
    private var lastServingSpec: CellSpec? = null
    private var lastDistanceText: String? = null
    private var lastAutoDistCellKey: String? = null

    // آخرین نتایج تست‌ها (ورودی امتیاز ترکیبی)
    private var lastPingMs: Double? = null
    private var lastJitterMs: Double? = null
    private var lastDownMbps: Double? = null
    private var lastUpMbps: Double? = null
    private var lastSpeedSummary: String? = null

    // سیم‌کارت‌ها
    private data class SimCardHolder(
        val sub: SubscriptionInfo,
        val subTm: TelephonyManager?,
        val root: View,
        val tvNetType: TextView,
        val tvSignal: TextView,
        val tvRoaming: TextView,
        val btnInspect: MaterialButton
    )

    private val simHolders = mutableListOf<SimCardHolder>()

    /** اگر کاربر «نمایش دکل این سیم» را زده باشد، شناسهٔ آن سابسکریپشن */
    private var inspectSubId: Int? = null

    // سنسور قطب‌نما
    private var sensorManager: SensorManager? = null
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            val rotation = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotation, orientation)
            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            compassView.setAzimuth(((azimuth % 360f) + 360f) % 360f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    // ───────────────────────── ویوها ─────────────────────────

    private lateinit var tvPermissionGate: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var btnRefresh: androidx.appcompat.widget.AppCompatImageView
    private lateinit var btnShare: androidx.appcompat.widget.AppCompatImageView

    // صفحات
    private lateinit var pageDashboard: ScrollView
    private lateinit var pageNetwork: ScrollView
    private lateinit var pageSim: ScrollView
    private lateinit var pageTools: ScrollView
    private lateinit var pageSettings: ScrollView
    private var activeScrollView: ScrollView? = null

    // داشبورد
    private lateinit var gaugeView: SignalGaugeView
    private lateinit var barsView: SignalBarsView
    private lateinit var chipNetwork: Chip
    private lateinit var tvQualityScore: TextView
    private lateinit var tvScoreLabel: TextView
    private lateinit var tvSignalScore: TextView
    private lateinit var signalChart: SignalChartView
    private lateinit var tvSigStats: TextView
    private lateinit var tvDbm: TextView
    private lateinit var tvAsu: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvRsrp: TextView
    private lateinit var tvRsrq: TextView
    private lateinit var tvSnr: TextView
    private lateinit var tvRssi: TextView
    private lateinit var tvOperatorName: TextView
    private lateinit var tvSimOperator: TextView
    private lateinit var tvSimState: TextView
    private lateinit var tvCountryIso: TextView
    private lateinit var tvRoaming: TextView
    private lateinit var tvDataState: TextView
    private lateinit var tvDataActivity: TextView
    private lateinit var tvPhoneCount: TextView
    private lateinit var tvLiveDown: TextView
    private lateinit var tvLiveUp: TextView
    private lateinit var btnSpeedTest: MaterialButton
    private lateinit var progressSpeed: LinearProgressIndicator
    private lateinit var tvPing: TextView
    private lateinit var tvDownload: TextView
    private lateinit var tvUpload: TextView
    private lateinit var tvSpeedStatus: TextView
    private lateinit var historyChart: HistoryChartView
    private lateinit var tvHistoryCount: TextView
    private lateinit var tvHistoryEmpty: TextView
    private lateinit var llHistoryList: LinearLayout
    private lateinit var btnClearHistory: MaterialButton

    // شبکه
    private lateinit var tvServingRat: TextView
    private lateinit var tvServingMccMnc: TextView
    private lateinit var tvServingCid: TextView
    private lateinit var tvServingEnb: TextView
    private lateinit var tvServingPci: TextView
    private lateinit var tvServingTac: TextView
    private lateinit var tvServingEarfcn: TextView
    private lateinit var tvServingBand: TextView
    private lateinit var tvServingFreq: TextView
    private lateinit var tvServingBandwidth: TextView
    private lateinit var tvServingTa: TextView
    private lateinit var tvTaDistance: TextView
    private lateinit var tvServingRsrp: TextView
    private lateinit var tvServingRsrq: TextView
    private lateinit var tvServingSinr: TextView
    private lateinit var tvServingRssi: TextView
    private lateinit var tvTowerDistance: TextView
    private lateinit var tvDistanceSource: TextView
    private lateinit var tvDistanceAcc: TextView
    private lateinit var btnLocateTower: MaterialButton
    private lateinit var tvTowerBearing: TextView
    private lateinit var tvGpsCoords: TextView
    private lateinit var compassView: CompassView
    private lateinit var mapView: org.osmdroid.views.MapView
    private lateinit var towerMap: TowerMapController
    private lateinit var recyclerCells: RecyclerView
    private lateinit var tvNoCells: TextView
    private lateinit var llHandover: LinearLayout
    private lateinit var tvHandoverEmpty: TextView
    private lateinit var btnClearHandover: MaterialButton

    // سیم‌کارت
    private lateinit var tvSimCount: TextView
    private lateinit var tvInspectStatus: TextView
    private lateinit var llSimList: LinearLayout

    // ابزارها
    private lateinit var chipPingTargets: ChipGroup
    private lateinit var btnRunPing: MaterialButton
    private lateinit var tvPingMin: TextView
    private lateinit var tvPingAvg: TextView
    private lateinit var tvPingMax: TextView
    private lateinit var tvPingJitter: TextView
    private lateinit var tvPingLoss: TextView
    private lateinit var tvPingPackets: TextView
    private lateinit var tvPingMethod: TextView
    private lateinit var tvPingStatus: TextView
    private lateinit var btnRunDns: MaterialButton
    private lateinit var llDnsRows: LinearLayout
    private lateinit var tvDnsStatus: TextView
    private lateinit var btnExportCsv: MaterialButton
    private lateinit var btnExportPdf: MaterialButton
    private lateinit var btnExportSpeedCsv: MaterialButton

    // تنظیمات
    private lateinit var toggleTheme: MaterialButtonToggleGroup
    private lateinit var btnThemeSystem: MaterialButton
    private lateinit var btnThemeLight: MaterialButton
    private lateinit var btnThemeDark: MaterialButton
    private lateinit var swDynamic: SwitchMaterial
    private lateinit var toggleLang: MaterialButtonToggleGroup
    private lateinit var btnLangFa: MaterialButton
    private lateinit var btnLangEn: MaterialButton
    private lateinit var swMonitor: SwitchMaterial
    private lateinit var tvAboutVersion: TextView
    private lateinit var tvAboutRepo: TextView

    // ───────────────────────── مجوزها ─────────────────────────

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            handlePermissionResult(grants)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                SignalLogService.start(this)
                toast(getString(R.string.monitor_started))
            } else {
                toast(getString(R.string.monitor_permission_needed))
            }
        }

    // ───────────────────────── چرخه حیات ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // اگر زبان عوض شده باشد، حالت ارقام فوراً به‌روز شود
        persianDigitsEnabled = Locale.getDefault().language != "en"

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        subscriptionManager =
            getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        historyDb = SpeedTestHistory.get(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        bindViews()
        setupTabs()
        setupButtons()
        setupPingChips()
        setupSettingsControls()

        HandoverLog.load(this)
        refreshHistory()
    }

    override fun onResume() {
        super.onResume()
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            startAll()
        } else if (!autoPromptedOnce) {
            autoPromptedOnce = true
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            showPermissionGate(getString(R.string.permission_gate))
        }
        // سرویس پایش اگر کاربر روشن گذاشته باشد و از UI باز شده باشیم
        if (AppPrefs.monitorEnabled(this) && !SignalLogService.isRunning) {
            SignalLogService.start(this)
        }
        towerMap.onResume()
        registerCompass()
    }

    override fun onPause() {
        super.onPause()
        stopAll()
        towerMap.onPause()
        unregisterCompass()
    }

    // ───────────────────────── اتصال ویوها ─────────────────────────

    private fun bindViews() {
        tvPermissionGate = findViewById(R.id.tvPermissionGate)
        tabLayout = findViewById(R.id.tabLayout)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnShare = findViewById(R.id.btnShare)

        pageDashboard = findViewById(R.id.pageDashboard)
        pageNetwork = findViewById(R.id.pageNetwork)
        pageSim = findViewById(R.id.pageSim)
        pageTools = findViewById(R.id.pageTools)
        pageSettings = findViewById(R.id.pageSettings)

        // داشبورد
        gaugeView = findViewById(R.id.gaugeView)
        barsView = findViewById(R.id.barsView)
        chipNetwork = findViewById(R.id.chipNetwork)
        tvQualityScore = findViewById(R.id.tvQualityScore)
        tvScoreLabel = findViewById(R.id.tvScoreLabel)
        tvSignalScore = findViewById(R.id.tvSignalScore)
        signalChart = findViewById(R.id.signalChart)
        tvSigStats = findViewById(R.id.tvSigStats)
        tvDbm = findViewById(R.id.tvDbm)
        tvAsu = findViewById(R.id.tvAsu)
        tvLevel = findViewById(R.id.tvLevel)
        tvRsrp = findViewById(R.id.tvRsrp)
        tvRsrq = findViewById(R.id.tvRsrq)
        tvSnr = findViewById(R.id.tvSnr)
        tvRssi = findViewById(R.id.tvRssi)
        tvOperatorName = findViewById(R.id.tvOperatorName)
        tvSimOperator = findViewById(R.id.tvSimOperator)
        tvSimState = findViewById(R.id.tvSimState)
        tvCountryIso = findViewById(R.id.tvCountryIso)
        tvRoaming = findViewById(R.id.tvRoaming)
        tvDataState = findViewById(R.id.tvDataState)
        tvDataActivity = findViewById(R.id.tvDataActivity)
        tvPhoneCount = findViewById(R.id.tvPhoneCount)
        tvLiveDown = findViewById(R.id.tvLiveDown)
        tvLiveUp = findViewById(R.id.tvLiveUp)
        btnSpeedTest = findViewById(R.id.btnSpeedTest)
        progressSpeed = findViewById(R.id.progressSpeed)
        tvPing = findViewById(R.id.tvPing)
        tvDownload = findViewById(R.id.tvDownload)
        tvUpload = findViewById(R.id.tvUpload)
        tvSpeedStatus = findViewById(R.id.tvSpeedStatus)
        historyChart = findViewById(R.id.historyChart)
        tvHistoryCount = findViewById(R.id.tvHistoryCount)
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty)
        llHistoryList = findViewById(R.id.llHistoryList)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        // شبکه
        tvServingRat = findViewById(R.id.tvServingRat)
        tvServingMccMnc = findViewById(R.id.tvServingMccMnc)
        tvServingCid = findViewById(R.id.tvServingCid)
        tvServingEnb = findViewById(R.id.tvServingEnb)
        tvServingPci = findViewById(R.id.tvServingPci)
        tvServingTac = findViewById(R.id.tvServingTac)
        tvServingEarfcn = findViewById(R.id.tvServingEarfcn)
        tvServingBand = findViewById(R.id.tvServingBand)
        tvServingFreq = findViewById(R.id.tvServingFreq)
        tvServingBandwidth = findViewById(R.id.tvServingBandwidth)
        tvServingTa = findViewById(R.id.tvServingTa)
        tvTaDistance = findViewById(R.id.tvTaDistance)
        tvServingRsrp = findViewById(R.id.tvServingRsrp)
        tvServingRsrq = findViewById(R.id.tvServingRsrq)
        tvServingSinr = findViewById(R.id.tvServingSinr)
        tvServingRssi = findViewById(R.id.tvServingRssi)
        tvTowerDistance = findViewById(R.id.tvTowerDistance)
        tvDistanceSource = findViewById(R.id.tvDistanceSource)
        tvDistanceAcc = findViewById(R.id.tvDistanceAcc)
        btnLocateTower = findViewById(R.id.btnLocateTower)
        tvTowerBearing = findViewById(R.id.tvTowerBearing)
        tvGpsCoords = findViewById(R.id.tvGpsCoords)
        compassView = findViewById(R.id.compassView)
        mapView = findViewById(R.id.mapView)
        towerMap = TowerMapController(mapView)
        recyclerCells = findViewById(R.id.recyclerCells)
        tvNoCells = findViewById(R.id.tvNoCells)
        llHandover = findViewById(R.id.llHandover)
        tvHandoverEmpty = findViewById(R.id.tvHandoverEmpty)
        btnClearHandover = findViewById(R.id.btnClearHandover)

        // سیم‌کارت
        tvSimCount = findViewById(R.id.tvSimCount)
        tvInspectStatus = findViewById(R.id.tvInspectStatus)
        llSimList = findViewById(R.id.llSimList)

        // ابزارها
        chipPingTargets = findViewById(R.id.chipPingTargets)
        btnRunPing = findViewById(R.id.btnRunPing)
        tvPingMin = findViewById(R.id.tvPingMin)
        tvPingAvg = findViewById(R.id.tvPingAvg)
        tvPingMax = findViewById(R.id.tvPingMax)
        tvPingJitter = findViewById(R.id.tvPingJitter)
        tvPingLoss = findViewById(R.id.tvPingLoss)
        tvPingPackets = findViewById(R.id.tvPingPackets)
        tvPingMethod = findViewById(R.id.tvPingMethod)
        tvPingStatus = findViewById(R.id.tvPingStatus)
        btnRunDns = findViewById(R.id.btnRunDns)
        llDnsRows = findViewById(R.id.llDnsRows)
        tvDnsStatus = findViewById(R.id.tvDnsStatus)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        btnExportPdf = findViewById(R.id.btnExportPdf)
        btnExportSpeedCsv = findViewById(R.id.btnExportSpeedCsv)

        // تنظیمات
        toggleTheme = findViewById(R.id.toggleTheme)
        btnThemeSystem = findViewById(R.id.btnThemeSystem)
        btnThemeLight = findViewById(R.id.btnThemeLight)
        btnThemeDark = findViewById(R.id.btnThemeDark)
        swDynamic = findViewById(R.id.swDynamic)
        toggleLang = findViewById(R.id.toggleLang)
        btnLangFa = findViewById(R.id.btnLangFa)
        btnLangEn = findViewById(R.id.btnLangEn)
        swMonitor = findViewById(R.id.swMonitor)
        tvAboutVersion = findViewById(R.id.tvAboutVersion)
        tvAboutRepo = findViewById(R.id.tvAboutRepo)

        setupCellsRecyclerView()
    }

    private fun setupCellsRecyclerView() {
        cellAdapter = CellAdapter()
        recyclerCells.layoutManager = LinearLayoutManager(this)
        recyclerCells.adapter = cellAdapter
        recyclerCells.isNestedScrollingEnabled = false
    }

    // ───────────────────────── تب‌ها و اسکرول ─────────────────────────

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_dashboard))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_network))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_sim))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_tools))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_settings))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showPage(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) {
                activeScrollView?.smoothScrollTo(0, 0)
            }
        })
        showPage(0)
    }

    private fun showPage(position: Int) {
        pageDashboard.visibility = if (position == 0) View.VISIBLE else View.GONE
        pageNetwork.visibility = if (position == 1) View.VISIBLE else View.GONE
        pageSim.visibility = if (position == 2) View.VISIBLE else View.GONE
        pageTools.visibility = if (position == 3) View.VISIBLE else View.GONE
        pageSettings.visibility = if (position == 4) View.VISIBLE else View.GONE
        activeScrollView = when (position) {
            0 -> pageDashboard
            1 -> pageNetwork
            2 -> pageSim
            3 -> pageTools
            else -> pageSettings
        }
        if (position == 1) {
            // نقشه فقط وقتی تب شبکه باز می‌شود راه‌اندازی/تازه می‌شود
            towerMap.initialize()
            towerMap.onResume()
        }
    }

    // ───────────────────────── دکمه‌ها ─────────────────────────

    private fun setupButtons() {
        btnLocateTower.setOnClickListener { onLocateTowerClicked() }
        btnSpeedTest.setOnClickListener { toggleSpeedTest() }
        btnClearHistory.setOnClickListener { onClearHistoryClicked() }
        btnShare.setOnClickListener { shareDiagnostics() }
        btnClearHandover.setOnClickListener {
            HandoverLog.clear(this)
            refreshHandoverList()
            toast(getString(R.string.handover_cleared))
        }
        btnRunPing.setOnClickListener { togglePingTest() }
        btnRunDns.setOnClickListener { toggleDnsTest() }
        btnExportCsv.setOnClickListener { exportSignalCsv() }
        btnExportSpeedCsv.setOnClickListener { exportSpeedCsv() }
        btnExportPdf.setOnClickListener { exportPdfReport() }
        tvAboutRepo.setOnClickListener { openRepo() }

        // ═══ رفع باگ «رفرش عمل نمی‌کند» ═══
        // ۱) دکمهٔ رفرش در سربرگ — همیشه در دسترس
        btnRefresh.setOnClickListener {
            swipeRefresh.isRefreshing = true
            refreshAll()
        }
        // ۲) کشیدن به پایین — با تشخیص درست اسکرول صفحهٔ فعال
        swipeRefresh.setOnRefreshListener { refreshAll() }
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            // canScrollVertically(-1) یعنی «می‌شود به بالا اسکرول کرد؟»
            (activeScrollView ?: pageDashboard).canScrollVertically(-1)
        }
    }

    private fun setupPingChips() {
        chipPingTargets.check(R.id.chipPingGoogle)
    }

    private fun selectedPingTarget(): PingTester.Target {
        return when (chipPingTargets.checkedChipId) {
            R.id.chipPingCloudflare -> PingTester.TARGETS[1]
            R.id.chipPingShecan -> PingTester.TARGETS[2]
            R.id.chipPingBegzar -> PingTester.TARGETS[3]
            R.id.chipPingLevel3 -> PingTester.TARGETS[4]
            else -> PingTester.TARGETS[0]
        }
    }

    // ───────────────────────── تنظیمات ─────────────────────────

    private fun setupSettingsControls() {
        // تم
        when (AppPrefs.themeMode(this)) {
            1 -> toggleTheme.check(R.id.btnThemeLight)
            2 -> toggleTheme.check(R.id.btnThemeDark)
            else -> toggleTheme.check(R.id.btnThemeSystem)
        }
        toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.btnThemeLight -> 1
                R.id.btnThemeDark -> 2
                else -> 0
            }
            AppPrefs.setThemeMode(this, mode)
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }

        // رنگ پویا
        swDynamic.isChecked = AppPrefs.dynamicColor(this)
        swDynamic.setOnCheckedChangeListener { _, checked ->
            AppPrefs.setDynamicColor(this, checked)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                toast(getString(R.string.dynamic_color_applied))
            }
        }

        // زبان
        val isEnglish = Locale.getDefault().language == "en"
        toggleLang.check(if (isEnglish) R.id.btnLangEn else R.id.btnLangFa)
        toggleLang.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val targetLang = if (checkedId == R.id.btnLangEn) "en" else "fa"
            if (Locale.getDefault().language != targetLang) {
                val locales = androidx.core.os.LocaleListCompat.forLanguageTags(targetLang)
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }

        // پایش پس‌زمینه
        swMonitor.isChecked = AppPrefs.monitorEnabled(this) || SignalLogService.isRunning
        swMonitor.setOnCheckedChangeListener { _, checked ->
            AppPrefs.setMonitorEnabled(this, checked)
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    SignalLogService.start(this)
                    toast(getString(R.string.monitor_started))
                }
            } else {
                SignalLogService.stop(this)
                toast(getString(R.string.monitor_stopped))
            }
        }

        // درباره
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (t: Throwable) {
            "—"
        }
        tvAboutVersion.text = getString(R.string.about_version_fmt, version)
    }

    private fun openRepo() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
        } catch (t: Throwable) {
            toast(getString(R.string.toast_no_app))
        }
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
                if (!locationGranted) {
                    toast(getString(R.string.location_needed_for_cells))
                }
            }
            locationGranted -> {
                showPermissionGate(getString(R.string.sim_no_permission))
            }
            else -> {
                showPermissionGate(getString(R.string.permission_gate))
                toast(getString(R.string.permission_gate))
            }
        }
    }

    private fun showPermissionGate(message: String) {
        tvPermissionGate.visibility = View.VISIBLE
        tvPermissionGate.text = message
        tvPermissionGate.setOnClickListener {
            val missing = missingPermissions()
            if (missing.isEmpty()) {
                startAll()
            } else {
                permissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    // ───────────────────────── شروع/توقف به‌روزرسانی‌ها ─────────────────────────

    private fun startAll() {
        tvPermissionGate.visibility = View.GONE
        tvPermissionGate.setOnClickListener(null)
        refreshTelephonyInfo()
        refreshDualSimCards()
        registerSignalListener()
        startCellPolling()
        startLiveTraffic()
        refreshHandoverList()
    }

    private fun stopAll() {
        unregisterSignalListener()
        cellPollJob?.cancel()
        cellPollJob = null
        if (speedTestJob?.isActive == true) {
            speedTestJob?.cancel()
            resetSpeedUi()
            tvSpeedStatus.text = getString(R.string.speed_status_idle)
        }
        speedTestJob = null
        if (distanceJob?.isActive == true) {
            distanceJob?.cancel()
            tvTowerDistance.text = getString(R.string.distance_na)
            tvTowerBearing.text = "—"
        }
        distanceJob = null
        if (liveTrafficJob?.isActive == true) {
            liveTrafficJob?.cancel()
            tvLiveDown.text = getString(R.string.speed_na)
            tvLiveUp.text = getString(R.string.speed_na)
        }
        liveTrafficJob = null
    }

    // ═══════════════════════════════════════════════════════════════
    // رفرش سراسری — رفع باگ «رفرش عمل نمی‌کند»:
    // همهٔ بخش‌ها مجبور به تازه‌سازی می‌شوند، فاصله هم دوباره محاسبه
    // می‌شود و اسپینر یک لحظه نمایان می‌ماند.
    // ═══════════════════════════════════════════════════════════════
    private fun refreshAll() {
        if (missingPermissions().isNotEmpty()) {
            showPermissionGate(getString(R.string.permission_gate))
            swipeRefresh.isRefreshing = false
            return
        }
        lastAutoDistCellKey = null
        refreshTelephonyInfo()
        refreshDualSimCards()
        registerSignalListener()
        refreshCellInfo()
        refreshDeviceInfo()
        refreshHistory()
        refreshHandoverList()
        if (distanceJob?.isActive != true) {
            computeTowerDistance()
        }
        swipeRefresh.postDelayed({
            swipeRefresh.isRefreshing = false
        }, 700)
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
            tvRoaming.text = getString(
                if (telephonyManager.isNetworkRoaming) R.string.roaming_on else R.string.roaming_off
            )
            tvDataState.text = dataStateText(telephonyManager.dataState)
            tvDataActivity.text = dataActivityText(telephonyManager.dataActivity)
            val modems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager.activeModemCount
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.phoneCount
            }
            tvPhoneCount.text = modems.toDigits()
        } catch (e: SecurityException) {
            tvOperatorName.text = getString(R.string.sim_no_permission)
        }
    }

    private fun simStateText(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> getString(R.string.sim_state_absent)
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> getString(R.string.sim_state_pin)
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> getString(R.string.sim_state_puk)
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> getString(R.string.sim_state_network_locked)
        TelephonyManager.SIM_STATE_READY -> getString(R.string.sim_state_ready)
        TelephonyManager.SIM_STATE_NOT_READY -> getString(R.string.sim_state_not_ready)
        TelephonyManager.SIM_STATE_PERM_DISABLED -> getString(R.string.sim_state_perm_disabled)
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> getString(R.string.sim_state_card_io)
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> getString(R.string.sim_state_restricted)
        else -> getString(R.string.unknown)
    }

    private fun dataStateText(state: Int): String = when (state) {
        TelephonyManager.DATA_CONNECTED -> getString(R.string.data_state_connected)
        TelephonyManager.DATA_CONNECTING -> getString(R.string.data_state_connecting)
        TelephonyManager.DATA_DISCONNECTED -> getString(R.string.data_state_disconnected)
        TelephonyManager.DATA_SUSPENDED -> getString(R.string.data_state_suspended)
        else -> getString(R.string.unknown)
    }

    private fun dataActivityText(activity: Int): String = when (activity) {
        TelephonyManager.DATA_ACTIVITY_NONE -> getString(R.string.data_activity_none)
        TelephonyManager.DATA_ACTIVITY_IN -> getString(R.string.data_activity_in)
        TelephonyManager.DATA_ACTIVITY_OUT -> getString(R.string.data_activity_out)
        TelephonyManager.DATA_ACTIVITY_INOUT -> getString(R.string.data_activity_inout)
        TelephonyManager.DATA_ACTIVITY_DORMANT -> getString(R.string.data_activity_dormant)
        else -> getString(R.string.unknown)
    }

    private fun levelText(level: Int): String = when (level) {
        4 -> getString(R.string.level_excellent)
        3 -> getString(R.string.level_good)
        2 -> getString(R.string.level_moderate)
        1 -> getString(R.string.level_weak)
        0 -> getString(R.string.level_very_weak)
        else -> getString(R.string.unknown)
    }

    // ───────────────────────── نوع / نسل شبکه ─────────────────────────

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

    /** خروجی: برچسب کامل نسل + رشتهٔ نسل کوتاه */
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
        TelephonyManager.NETWORK_TYPE_LTE -> "4G – LTE / LTE-A" to "4G"
        TelephonyManager.NETWORK_TYPE_NR -> "5G – NR" to "5G"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN" to "WLAN"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> getString(R.string.unknown) to "—"
        else -> getString(R.string.unknown_type_fmt, type) to "—"
    }

    private fun updateNetworkType() {
        if (!hasPhonePermission()) {
            chipNetwork.text = "—"
            return
        }
        val (label, _) = networkTypeInfo(currentNetworkType(activeTelephonyManager()))
        chipNetwork.text = label
        SignalState.netLabel = label
    }

    // ───────────────────────── شنوندهٔ قدرت سیگنال ─────────────────────────

    private fun registerSignalListener() {
        if (!hasPhonePermission()) return
        unregisterSignalListener()

        // PhoneStateListener در همهٔ نسخه‌ها کارا است (TelephonyCallback در برخی
        // SDKهای اخیر حذف شده است)
        @Suppress("DEPRECATION")
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                signalStrength?.let { runCatching { onSignalUpdated(it) } }
            }
        }
        phoneListener = listener
        @Suppress("DEPRECATION")
        telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
    }

    private fun unregisterSignalListener() {
        phoneListener?.let {
            @Suppress("DEPRECATION")
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        phoneListener = null
    }

    private fun onSignalUpdated(ss: SignalStrength) {
        val dbm = SignalDbmExtractor.extract(ss)
        lastDbm = dbm

        dbm?.let { gaugeView.setDbm(it) }
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ss.level
        } else {
            levelFromDbm(dbm)
        }
        barsView.setLevel(level)

        tvDbm.text = formatDbmOrNull(dbm)
        tvLevel.text = levelText(level)

        var rsrp: Int? = null
        var rsrq: Int? = null
        var snr: Int? = null
        var rssi: Int? = null
        var asu: Int? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (css in ss.cellSignalStrengths) {
                when (css) {
                    is android.telephony.CellSignalStrengthLte -> {
                        rsrp = validSignal(css.rsrp) ?: rsrp
                        rsrq = validSignal(css.rsrq) ?: rsrq
                        rssi = validSignal(css.rssi) ?: rssi
                        snr = validSignal(css.rssnr) ?: snr
                    }
                    is android.telephony.CellSignalStrengthNr -> {
                        rsrp = validSignal(css.ssRsrp) ?: rsrp
                        rsrq = validSignal(css.ssRsrq) ?: rsrq
                        snr = validSignal(css.ssSinr) ?: snr
                    }
                }
                val a = runCatching { css.asuLevel }.getOrNull()
                if (asu == null && a != null && a in 0..999) asu = a
            }
        }

        tvRsrp.text = formatDbmOrNull(rsrp)
        tvRsrq.text = rsrq?.let { it.toDigits() + " dB" } ?: "—"
        tvSnr.text = snr?.let { it.toDigits() + " dB" } ?: "—"
        tvRssi.text = formatDbmOrNull(rssi)
        tvAsu.text = asu?.let { it.toDigits() } ?: "—"

        lastRsrpVal = rsrp
        signalChart.addSample(dbm)
        addDbmSample(dbm)
        updateQualityScore(dbm)

        // وضعیت اشتراکی برای ویجت/کاشی/سرویس
        SignalState.dbm = dbm
        SignalState.updatedAtMs = System.currentTimeMillis()
        SignalWidgetProvider.pushUpdate(applicationContext)
    }

    private fun addDbmSample(dbm: Int?) {
        if (dbm == null) return
        dbmSamples.addLast(dbm)
        while (dbmSamples.size > 60) dbmSamples.removeFirst()
        val avg = dbmSamples.average()
        val mn = dbmSamples.min()
        val mx = dbmSamples.max()
        tvSigStats.text = getString(
            R.string.sig_stats_fmt,
            formatNumber1f(avg),
            formatNumber1f(mn.toDouble()),
            formatNumber1f(mx.toDouble())
        )
    }

    /** امتیاز ترکیبی کیفیت (سیگنال + پینگ + سرعت) */
    private fun updateQualityScore(dbm: Int?) {
        val score = NetworkQuality.score(
            dbm = dbm ?: lastDbm,
            rsrp = lastRsrpVal,
            pingMs = lastPingMs,
            jitterMs = lastJitterMs,
            downMbps = lastDownMbps,
            upMbps = lastUpMbps
        )
        val label = NetworkQuality.label(this, score)
        tvQualityScore.text = score.toString().toDigits()
        tvScoreLabel.text = label
        val color = ContextCompat.getColor(this, NetworkQuality.colorRes(score))
        tvScoreLabel.setTextColor(color)
        tvQualityScore.setTextColor(color)
        tvSignalScore.text =
            getString(R.string.score_fmt, score.toString().toDigits()) + " — " + label
        tvSignalScore.setTextColor(color)
    }

    private fun validSignal(v: Int): Int? = v.takeIf { it != Int.MAX_VALUE }

    private fun levelFromDbm(dbm: Int?): Int = when {
        dbm == null -> -1
        dbm >= -85 -> 4
        dbm >= -95 -> 3
        dbm >= -105 -> 2
        dbm >= -115 -> 1
        else -> 0
    }

    private fun formatDbmOrNull(v: Int?): String =
        v?.let { it.toString().toDigits() + " dBm" } ?: "—"

    // ───────────────────────── پایش دوره‌ای سلول‌ها ─────────────────────────

    private fun startCellPolling() {
        cellPollJob?.cancel()
        if (!hasPhonePermission()) return
        cellPollJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                refreshCellInfo()
                updateNetworkType()
                refreshSimLiveValues()
                refreshDeviceInfo()
                delay(CELL_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * TelephonyManager فعال: اگر کاربر «نمایش دکل این سیم» را زده باشد،
     * همان سابسکریپشن، وگرنه پیش‌فرض — رفع باگ سیم‌کارت دوم.
     */
    private fun activeTelephonyManager(): TelephonyManager {
        val subId = inspectSubId
        if (subId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val holder = simHolders.firstOrNull { it.sub.subscriptionId == subId }
            if (holder?.subTm != null) return holder.subTm!!
        }
        return telephonyManager
    }

    private fun refreshCellInfo() {
        if (!hasPhonePermission()) return
        val tm = activeTelephonyManager()
        val fallbackOperator = try {
            tm.networkOperator
        } catch (e: SecurityException) {
            ""
        }

        val specs = mutableListOf<CellSpec>()
        val all: List<CellInfo>? = try {
            @Suppress("DEPRECATION")
            tm.allCellInfo
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
        if (!all.isNullOrEmpty()) {
            for (info in all) {
                CellSpecParser.parse(info, fallbackOperator)?.let { specs.add(it) }
            }
        }

        // تشخیص جابجایی دکل (Handover)
        val servingNow = specs.firstOrNull { it.registered } ?: specs.firstOrNull()
        detectHandover(servingNow)

        val rows: MutableList<CellRow> = if (specs.isNotEmpty()) {
            specs.map { rowOf(it) }.toMutableList()
        } else {
            // جایگزین: فقط قدرت سیگنال در دسترس است
            listOfNotNull(
                lastDbm?.let {
                    val info = networkTypeInfo(currentNetworkType(tm))
                    CellRow(
                        rat = info.first,
                        generation = if (info.second == "—") "" else info.second,
                        pciOrPsc = "—",
                        cidOrNci = "—",
                        tacOrLac = "—",
                        earfcnOrArfcn = "—",
                        dbm = it,
                        registered = true
                    )
                }
            ).toMutableList()
        }
        if (rows.size == 1 && rows[0].generation.isBlank()) {
            rows[0] = rows[0].copy(
                generation = networkTypeInfo(currentNetworkType(tm)).second
            )
        }

        val dist = lastDistanceText
        val finalRows = if (dist == null) {
            rows
        } else {
            rows.map { if (it.registered) it.copy(distanceText = dist) else it }
        }

        cellAdapter.submit(finalRows)
        tvNoCells.visibility = if (finalRows.isEmpty()) View.VISIBLE else View.GONE

        if (servingNow != null) {
            updateServingSpecs(servingNow)
        }

        maybeAutoDistance()
    }

    private fun rowOf(spec: CellSpec): CellRow = CellRow(
        rat = spec.rat,
        generation = spec.generation,
        pciOrPsc = spec.pci?.toDigits() ?: "—",
        cidOrNci = spec.cid?.toDigits() ?: "—",
        tacOrLac = spec.tac?.toDigits() ?: "—",
        earfcnOrArfcn = spec.earfcn?.toDigits() ?: "—",
        dbm = spec.dbm,
        registered = spec.registered,
        distanceText = "—",
        bandText = spec.bandLabel ?: "—"
    )

    /** پرکردن کارت «مشخصات کامل ارتباطات» از CellSpec */
    private fun updateServingSpecs(spec: CellSpec) {
        lastServingSpec = spec
        tvServingRat.text = spec.generation + " – " + spec.rat
        tvServingPci.text = spec.pci?.toDigits() ?: "—"
        tvServingCid.text = spec.cid?.toDigits() ?: "—"
        tvServingEnb.text = spec.enbId?.toDigits() ?: "—"
        tvServingTac.text = spec.tac?.toDigits() ?: "—"
        tvServingEarfcn.text = spec.earfcn?.toDigits() ?: "—"
        tvServingBand.text = spec.bandLabel?.toDigits() ?: "—"
        tvServingFreq.text = spec.freqMHz?.let {
            getString(R.string.freq_fmt, formatNumber1f(it))
        } ?: "—"
        tvServingBandwidth.text = spec.bandwidthKhz?.let {
            if (it > 0) {
                val mhz = it / 1000.0
                formatNumber1f(mhz) + " " + getString(R.string.unit_mhz)
            } else {
                "—"
            }
        } ?: "—"
        tvServingTa.text = spec.timingAdvance?.toDigits() ?: "—"
        val taDist = CellSpecParser.taDistanceMeters(spec.timingAdvance)
        tvTaDistance.text = taDist?.let {
            getString(R.string.ta_dist_fmt, formatDistance(it))
        } ?: "—"
        tvServingRsrp.text = formatDbmOrNull(spec.rsrp ?: lastRsrpVal)
        tvServingRsrq.text = spec.rsrq?.let { it.toDigits() + " dB" } ?: "—"
        tvServingSinr.text = spec.sinr?.let { it.toDigits() + " dB" } ?: "—"
        tvServingRssi.text = formatDbmOrNull(spec.rssi)

        // MCC/MNC
        val mccMnc = if (!spec.mcc.isNullOrBlank() && !spec.mnc.isNullOrBlank()) {
            spec.mcc + " – " + spec.mnc
        } else {
            val op = try {
                activeTelephonyManager().networkOperator
            } catch (e: SecurityException) {
                ""
            }
            if (op.length >= 5) op.substring(0, 3) + " – " + op.substring(3) else null
        }
        tvServingMccMnc.text = mccMnc?.toDigits() ?: "—"
    }

    /** تشخیص و ثبت جابجایی دکل */
    private fun detectHandover(serving: CellSpec?) {
        if (serving == null || !serving.registered) return
        val key = serving.cellKey
        val previous = lastServingSpec?.cellKey
        if (previous != null && previous != key) {
            HandoverLog.record(this, previous, key)
            refreshHandoverList()
        }
        lastServingSpec = serving
    }

    private fun refreshHandoverList() {
        val events = HandoverLog.recent(12)
        tvHandoverEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        llHandover.removeAllViews()
        for (e in events) {
            llHandover.addView(
                makeInfoRow(
                    getString(
                        R.string.handover_fmt,
                        JalaliDate.formatTimestamp(e.timestampMs),
                        e.fromCell,
                        e.toCell
                    )
                )
            )
        }
    }

    // ───────────────────────── سیم‌کارت‌ها (رفع باگ سیم دوم) ─────────────────────────

    private fun refreshDualSimCards() {
        llSimList.removeAllViews()
        simHolders.clear()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            tvSimCount.text = "—"
            llSimList.addView(makeInfoRow(getString(R.string.sim_requires_api22)))
            tvInspectStatus.text = ""
            return
        }
        if (!hasPhonePermission()) {
            tvSimCount.text = "—"
            llSimList.addView(makeInfoRow(getString(R.string.sim_no_permission)))
            tvInspectStatus.text = ""
            return
        }
        val subs: List<SubscriptionInfo>? = try {
            subscriptionManager.activeSubscriptionInfoList
        } catch (e: SecurityException) {
            null
        }
        if (subs.isNullOrEmpty()) {
            tvSimCount.text = getString(R.string.sim_count_fmt, 0).toDigits()
            llSimList.addView(makeInfoRow(getString(R.string.sim_none)))
            tvInspectStatus.text = ""
            return
        }
        tvSimCount.text = getString(R.string.sim_count_fmt, subs.size).toDigits()

        for ((index, sub) in subs.withIndex()) {
            val subTm: TelephonyManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    telephonyManager.createForSubscriptionId(sub.subscriptionId)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            val root = layoutInflater.inflate(R.layout.item_sim_card, llSimList, false)
            val tvTitle = root.findViewById<TextView>(R.id.tvSimTitle)
            val tvBadge = root.findViewById<TextView>(R.id.tvSimBadge)
            val tvCarrier = root.findViewById<TextView>(R.id.tvSimCarrier)
            val tvDisplay = root.findViewById<TextView>(R.id.tvSimDisplay)
            val tvNetType = root.findViewById<TextView>(R.id.tvSimNetType)
            val tvSignal = root.findViewById<TextView>(R.id.tvSimSignal)
            val tvMccMnc = root.findViewById<TextView>(R.id.tvSimMccMnc)
            val tvSimRoaming = root.findViewById<TextView>(R.id.tvSimRoaming)
            val tvSubId = root.findViewById<TextView>(R.id.tvSimSubId)
            val btnInspect = root.findViewById<MaterialButton>(R.id.btnInspectSim)

            val slotNumber = index + 1
            val slotText = if (sub.simSlotIndex >= 0) sub.simSlotIndex.toString() else "—"
            tvTitle.text =
                getString(R.string.sim_card_title_fmt, slotNumber, slotText).toDigits()
            tvBadge.text = slotNumber.toString().toDigits()
            tvCarrier.text = sub.carrierName?.toString().orEmpty()
                .ifBlank { getString(R.string.unknown_operator) }
            tvDisplay.text = sub.displayName?.toString().orEmpty()
                .ifBlank { "—" }

            // مشخصات ثابت از TelephonyManager مخصوص همان سیم
            var mccMnc = "—"
            var roaming = "—"
            if (subTm != null) {
                try {
                    val op = subTm.simOperator
                    if (op.length >= 5) {
                        mccMnc = op.substring(0, 3) + " – " + op.substring(3)
                    }
                } catch (t: Throwable) {
                }
                try {
                    roaming = getString(
                        if (subTm.isNetworkRoaming) R.string.roaming_on else R.string.roaming_off
                    )
                } catch (t: Throwable) {
                }
            }
            tvMccMnc.text = mccMnc.toDigits()
            tvSimRoaming.text = roaming
            tvSubId.text = sub.subscriptionId.toString().toDigits()

            btnInspect.setOnClickListener {
                inspectSubId =
                    if (inspectSubId == sub.subscriptionId) null else sub.subscriptionId
                updateInspectButtons()
                refreshCellInfo()
                updateNetworkType()
            }

            llSimList.addView(root)
            simHolders.add(
                SimCardHolder(sub, subTm, root, tvNetType, tvSignal, tvSimRoaming, btnInspect)
            )
        }
        updateInspectButtons()
        refreshSimLiveValues()
    }

    /** مقادیر زندهٔ هر سیم: نوع شبکه و قدرت سیگنال همان سابسکریپشن */
    private fun refreshSimLiveValues() {
        if (!hasPhonePermission()) return
        for (holder in simHolders) {
            val tm = holder.subTm ?: continue
            // نوع شبکهٔ همان سیم
            val typeLabel = try {
                networkTypeInfo(currentNetworkType(tm)).first
            } catch (t: Throwable) {
                "—"
            }
            holder.tvNetType.text = typeLabel

            // سیگنال همان سیم — API 26 به بالا مستقیم؛ پایین‌تر «—»
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val dbm = try {
                    SignalDbmExtractor.extract(tm.signalStrength)
                } catch (t: Throwable) {
                    null
                }
                holder.tvSignal.text =
                    dbm?.let { formatDbmOrNull(it) } ?: getString(R.string.sim_signal_na)
            } else {
                holder.tvSignal.text = getString(R.string.sim_signal_na)
            }
        }
        updateInspectButtons()
    }

    private fun updateInspectButtons() {
        for (holder in simHolders) {
            val selected = inspectSubId != null &&
                holder.sub.subscriptionId == inspectSubId
            holder.btnInspect.text = getString(
                if (selected) R.string.btn_inspect_default else R.string.btn_inspect_sim
            )
        }
        val activeIdx = simHolders.indexOfFirst { it.sub.subscriptionId == inspectSubId }
        tvInspectStatus.text = if (activeIdx >= 0) {
            getString(R.string.inspect_active_fmt, activeIdx + 1)
        } else {
            ""
        }
    }

    private fun makeInfoRow(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 13f
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tv.setPadding(0, dp(6), 0, dp(6))
        return tv
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ───────────────────────── فاصله تا دکل (خودکار) ─────────────────────────

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
        computeTowerDistance()
    }

    private fun maybeAutoDistance() {
        if (!hasLocationPermission()) return
        if (distanceJob?.isActive == true) return
        val cell = lastServingSpec ?: return
        val key = cell.cellKey
        if (key == lastAutoDistCellKey) return
        lastAutoDistCellKey = key
        computeTowerDistance()
    }

    private data class TowerFix(
        val lat: Double,
        val lon: Double,
        val sourceRes: Int,
        val accuracyMeters: Int? = null
    )

    private fun computeTowerDistance() {
        if (distanceJob?.isActive == true) return

        tvTowerDistance.text = getString(R.string.msg_getting_gps)
        tvTowerBearing.text = "—"
        tvGpsCoords.text = "—"
        tvDistanceSource.text = ""
        tvDistanceAcc.text = ""

        distanceJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                val location = getCurrentLocationSuspend()
                if (location == null) {
                    tvTowerDistance.text =
                        getString(R.string.err_generic, getString(R.string.err_gps_failed))
                    return@launch
                }
                tvGpsCoords.text = formatCoords(location.latitude, location.longitude)
                tvTowerDistance.text = getString(R.string.msg_querying_db)

                val cell = lastServingSpec
                val fix = withContext(Dispatchers.IO) { resolveTowerFix(cell) }

                if (fix != null) {
                    val distance = haversineMeters(
                        location.latitude, location.longitude, fix.lat, fix.lon
                    )
                    val bearing = bearingDegrees(
                        location.latitude, location.longitude, fix.lat, fix.lon
                    )
                    val distStr = formatDistance(distance)
                    lastDistanceText = distStr
                    tvTowerDistance.text = distStr
                    tvTowerBearing.text = formatBearing(bearing)
                    tvDistanceSource.text = getString(fix.sourceRes)
                    fix.accuracyMeters?.let {
                        tvDistanceAcc.text =
                            getString(R.string.distance_acc_fmt, formatDistance(it.toDouble()))
                    }
                    // نقشه + قطب‌نما
                    towerMap.setTower(fix.lat, fix.lon, getString(R.string.map_tower))
                    towerMap.setUser(
                        location.latitude, location.longitude, getString(R.string.map_user)
                    )
                    compassView.setTargetBearing(bearing)
                } else {
                    val estimate = estimateDistanceFromSignal()
                    if (estimate != null) {
                        lastDistanceText = estimate
                        tvTowerDistance.text = estimate
                        tvTowerBearing.text = "—"
                        tvDistanceSource.text = getString(R.string.src_estimate)
                    } else {
                        tvTowerDistance.text =
                            getString(R.string.err_generic, getString(R.string.err_no_cell))
                    }
                }
            } catch (e: TimeoutCancellationException) {
                tvTowerDistance.text =
                    getString(R.string.err_generic, getString(R.string.err_gps_failed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tvTowerDistance.text =
                    getString(R.string.err_generic, e.message ?: getString(R.string.unknown))
            }
        }
    }

    /** زنجیرهٔ ارائه‌دهنده‌های موقعیت دکل — کاملاً خودکار و بدون کلید */
    private fun resolveTowerFix(spec: CellSpec?): TowerFix? {
        if (spec == null) return null
        val radio = radioOf(spec)
        val cid = spec.cid?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
        val lac = spec.tac

        // ۱) Mylnikov — رایگان و بدون کلید
        if (radio != "nr" && radio != "cdma" &&
            cid != null && lac != null &&
            !spec.mcc.isNullOrBlank() && !spec.mnc.isNullOrBlank()
        ) {
            try {
                return fetchMylnikov(spec.mcc!!, spec.mnc!!, lac, cid)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // ارائه‌دهندهٔ بعدی
            }
        }
        return null
    }

    private fun radioOf(spec: CellSpec): String = when (spec.rat) {
        "LTE" -> "lte"
        "NR" -> "nr"
        "UMTS" -> "umts"
        "CDMA" -> "cdma"
        else -> "gsm"
    }

    private fun fetchMylnikov(
        mcc: String,
        mnc: String,
        lac: Int,
        cid: Int
    ): TowerFix {
        val url = "https://api.mylnikov.org/geolocation/get?v=1.1" +
            "&mcc=" + mcc +
            "&mnc=" + mnc +
            "&lac=" + lac +
            "&cellid=" + cid
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException("Mylnikov: $code")
            }
            val obj = JSONObject(body)
            if (obj.optInt("result", -1) == 1) {
                val data = obj.getJSONObject("data")
                val accuracy = data.optInt("range", 0).takeIf { it > 0 }
                return TowerFix(
                    data.getDouble("lat"),
                    data.getDouble("lon"),
                    R.string.src_mylnikov,
                    accuracy
                )
            }
            throw IOException("Mylnikov: not found")
        } finally {
            connection.disconnect()
        }
    }

    /** تخمین فاصله از قدرت سیگنال (مدل تضعیف لگاریتمی) */
    private fun estimateDistanceFromSignal(): String? {
        val gen = lastServingSpec?.generation
        val perRe = gen == "4G" || gen == "5G"
        val dbm = if (perRe) (lastRsrpVal ?: lastDbm ?: return null) else (lastDbm ?: return null)
        if (dbm >= 0 || dbm < -140) return null

        val freqMHz = when (gen) {
            "5G" -> 3500.0
            "4G" -> lastServingSpec?.freqMHz ?: 1800.0
            "3G" -> 2100.0
            else -> 900.0
        }
        val eirp = if (perRe) 27.0 else 58.0
        val fspl = eirp - dbm - 25.0
        if (fspl <= 10.0) return formatDistance(40.0)

        val km = Math.pow(10.0, (fspl - 20.0 * Math.log10(freqMHz) - 32.44) / 20.0)
        val meters = (km * 1000.0).coerceIn(30.0, 50_000.0)
        return formatDistance(meters)
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

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun bearingName(bearing: Double): String {
        val res = when (Math.round(bearing / 45.0).toInt() % 8) {
            1 -> R.string.bearing_ne
            2 -> R.string.bearing_e
            3 -> R.string.bearing_se
            4 -> R.string.bearing_s
            5 -> R.string.bearing_sw
            6 -> R.string.bearing_w
            7 -> R.string.bearing_nw
            else -> R.string.bearing_n
        }
        return getString(res)
    }

    private fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            formatNumber1f(meters) + " " + getString(R.string.unit_meters)
        } else {
            formatNumber1f(meters / 1000.0) + " " + getString(R.string.unit_kilometers)
        }
    }

    private fun formatBearing(bearing: Double): String = getString(
        R.string.bearing_fmt,
        bearingName(bearing),
        formatNumber1f(bearing),
        getString(R.string.unit_degrees)
    )

    private fun formatCoords(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon).toDigits()

    // ───────────────────────── ترافیک لحظه‌ای ─────────────────────────

    private fun startLiveTraffic() {
        liveTrafficJob?.cancel()
        liveTrafficJob = lifecycleScope.launch(Dispatchers.Main) {
            var lastRx = TrafficStats.getTotalRxBytes()
            var lastTx = TrafficStats.getTotalTxBytes()
            var lastT = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1_000)
                val rx = TrafficStats.getTotalRxBytes()
                val tx = TrafficStats.getTotalTxBytes()
                val t = SystemClock.elapsedRealtime()
                val dtS = (t - lastT) / 1000.0
                val supported = rx != TrafficStats.UNSUPPORTED.toLong() &&
                    lastRx != TrafficStats.UNSUPPORTED.toLong()
                if (supported && dtS > 0) {
                    val downKbps = (rx - lastRx).coerceAtLeast(0L) * 8.0 / 1e3 / dtS
                    val upKbps = (tx - lastTx).coerceAtLeast(0L) * 8.0 / 1e3 / dtS
                    tvLiveDown.text = formatLiveSpeed(downKbps)
                    tvLiveUp.text = formatLiveSpeed(upKbps)
                    SignalState.downKbps = downKbps
                    SignalState.upKbps = upKbps
                }
                lastRx = rx
                lastTx = tx
                lastT = t
            }
        }
    }

    private fun formatLiveSpeed(kbps: Double): String {
        return if (kbps >= 1000.0) {
            formatNumber1f(kbps / 1000.0) + " Mbps"
        } else {
            formatNumber1f(kbps) + " Kbps"
        }
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
                    tvPing.text = getString(R.string.unit_ms_fmt, formatNumber1f(pingMs)) +
                        " (" + getString(R.string.ping_jitter) + ": " +
                        formatNumber1f(jitterMs) + ")"
                    progressSpeed.setProgressCompat(sample * 20 / total, true)
                }

                override fun onDownloadProgress(percent: Int, mbps: Double) {
                    tvDownload.text = getString(R.string.unit_mbps_fmt, formatNumber2f(mbps))
                    progressSpeed.setProgressCompat(20 + percent * 50 / 100, true)
                }

                override fun onUploadProgress(percent: Int, mbps: Double) {
                    tvUpload.text = getString(R.string.unit_mbps_fmt, formatNumber2f(mbps))
                    progressSpeed.setProgressCompat(70 + percent * 30 / 100, true)
                }

                override fun onFinished(result: SpeedTester.SpeedResult) {
                    progressSpeed.setProgressCompat(100, true)
                    tvPing.text = getString(R.string.unit_ms_fmt, formatNumber1f(result.pingMs)) +
                        " (" + getString(R.string.ping_jitter) + ": " +
                        formatNumber1f(result.jitterMs) + ")"
                    tvDownload.text = getString(R.string.unit_mbps_fmt, formatNumber2f(result.downloadMbps))
                    tvUpload.text = getString(R.string.unit_mbps_fmt, formatNumber2f(result.uploadMbps))
                    lastPingMs = result.pingMs
                    lastJitterMs = result.jitterMs
                    lastDownMbps = result.downloadMbps
                    lastUpMbps = result.uploadMbps
                    lastSpeedSummary = "↓ " + formatNumber1f(result.downloadMbps) + " / ↑ " +
                        formatNumber1f(result.uploadMbps) + " Mbps — " +
                        formatNumber1f(result.pingMs) + "ms"
                    tvSpeedStatus.text = getString(R.string.speed_status_done)
                    finishSpeedUi()
                    updateQualityScore(lastDbm)
                    appScope.launch {
                        historyDb.insert(result)
                        withContext(Dispatchers.Main) { refreshHistory() }
                    }
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

    @Suppress("DEPRECATION")
    private fun isNetworkOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        val info = cm.activeNetworkInfo
        return info != null && info.isConnected
    }

    // ───────────────────────── تاریخچه تست سرعت ─────────────────────────

    private fun refreshHistory() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { historyDb.recent(30) }
            historyChart.setData(entries.asReversed())
            tvHistoryCount.text =
                getString(R.string.history_count_fmt, entries.size).toDigits()
            if (entries.isEmpty()) {
                tvHistoryEmpty.visibility = View.VISIBLE
                llHistoryList.removeAllViews()
                return@launch
            }
            tvHistoryEmpty.visibility = View.GONE
            llHistoryList.removeAllViews()
            for (entry in entries.take(8)) {
                llHistoryList.addView(
                    makeInfoRow(
                        JalaliDate.formatTimestamp(entry.timestampMs) +
                            " — ↓ " + formatNumber1f(entry.downloadMbps) +
                            " • ↑ " + formatNumber1f(entry.uploadMbps) +
                            " • " + formatNumber1f(entry.pingMs) + "ms"
                    )
                )
            }
        }
    }

    private fun onClearHistoryClicked() {
        lifecycleScope.launch(Dispatchers.IO) {
            historyDb.clear()
            withContext(Dispatchers.Main) {
                refreshHistory()
                toast(getString(R.string.history_cleared))
            }
        }
    }

    // ───────────────────────── ابزارها: پینگ و DNS ─────────────────────────

    private fun togglePingTest() {
        val job = pingJob
        if (job != null && job.isActive) {
            job.cancel()
            pingJob = null
            resetPingUi()
            return
        }
        if (!isNetworkOnline()) {
            tvPingStatus.text = getString(R.string.speed_no_internet)
            return
        }
        val target = selectedPingTarget()
        btnRunPing.text = getString(R.string.btn_ping_stop)
        tvPingStatus.text = getString(R.string.ping_status_running)
        tvPingMin.text = "—"
        tvPingAvg.text = "—"
        tvPingMax.text = "—"
        tvPingJitter.text = "—"
        tvPingLoss.text = "—"
        tvPingPackets.text = "—"
        tvPingMethod.text = ""

        val totalSteps = 8
        pingJob = lifecycleScope.launch(Dispatchers.Main) {
            val result = PingTester.run(target.host, totalSteps) { _, _, rtt ->
                if (rtt != null) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        tvPingAvg.text = formatMsShort(rtt)
                    }
                }
                Unit
            }
            btnRunPing.text = getString(R.string.btn_ping_run)
            pingJob = null
            if (result == null) {
                tvPingStatus.text = getString(R.string.err_ping_failed)
                return@launch
            }
            tvPingMin.text = formatMsShort(result.minMs)
            tvPingAvg.text = formatMsShort(result.avgMs)
            tvPingMax.text = formatMsShort(result.maxMs)
            tvPingJitter.text = formatMsShort(result.jitterMs)
            tvPingLoss.text = getString(R.string.loss_fmt, formatNumber1f(result.lossPercent))
            tvPingPackets.text = getString(
                R.string.ping_packets_fmt,
                result.received.toString().toDigits(),
                result.sent.toString().toDigits()
            )
            tvPingMethod.text = getString(
                if (result.usedIcmp) R.string.ping_method_icmp else R.string.ping_method_http
            )
            tvPingStatus.text = getString(R.string.speed_status_done)
            lastPingMs = result.avgMs
            lastJitterMs = result.jitterMs
            updateQualityScore(lastDbm)
        }
    }

    private fun resetPingUi() {
        btnRunPing.text = getString(R.string.btn_ping_run)
        tvPingStatus.text = getString(R.string.ping_status_idle)
    }

    private fun toggleDnsTest() {
        val job = dnsJob
        if (job != null && job.isActive) {
            job.cancel()
            dnsJob = null
            tvDnsStatus.text = getString(R.string.dns_status_idle)
            return
        }
        if (!isNetworkOnline()) {
            tvDnsStatus.text = getString(R.string.speed_no_internet)
            return
        }
        btnRunDns.text = getString(R.string.btn_ping_stop)
        tvDnsStatus.text = getString(R.string.dns_status_running)
        llDnsRows.removeAllViews()

        dnsJob = lifecycleScope.launch(Dispatchers.Main) {
            DnsTester.run { _, _, row ->
                lifecycleScope.launch(Dispatchers.Main) {
                    addDnsRow(row)
                }
            }
            btnRunDns.text = getString(R.string.btn_dns_run)
            tvDnsStatus.text = getString(R.string.speed_status_done)
            dnsJob = null
        }
    }

    private fun addDnsRow(row: DnsTester.Row) {
        val value = row.ms?.let { formatMsShort(it) } ?: getString(R.string.dns_fail)
        llDnsRows.addView(
            makeInfoRow(getString(R.string.dns_row_fmt, row.host, value))
        )
    }

    private fun formatMsShort(ms: Double): String =
        getString(R.string.unit_ms_fmt, formatNumber1f(ms))

    // ───────────────────────── خروجی‌ها (CSV / PDF) ─────────────────────────

    private fun exportSignalCsv() {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { CsvExporter.exportSignalLog(this@MainActivity) }
            if (file == null) {
                toast(getString(R.string.export_none))
            } else {
                shareFile(file, "text/csv")
                toast(getString(R.string.export_done))
            }
        }
    }

    private fun exportSpeedCsv() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { historyDb.recent(500) }
            val file = withContext(Dispatchers.IO) {
                CsvExporter.exportSpeedHistory(this@MainActivity, entries)
            }
            if (file == null) {
                toast(getString(R.string.export_none))
            } else {
                shareFile(file, "text/csv")
                toast(getString(R.string.export_done))
            }
        }
    }

    private fun exportPdfReport() {
        lifecycleScope.launch {
            toast(getString(R.string.report_building))
            try {
                val data = buildPdfData()
                val file = withContext(Dispatchers.IO) {
                    PdfReport.build(this@MainActivity, data)
                }
                shareFile(file, "application/pdf")
                toast(getString(R.string.export_done))
            } catch (e: Exception) {
                toast(getString(R.string.export_failed, e.message ?: getString(R.string.unknown)))
            }
        }
    }

    private fun buildPdfData(): PdfReport.Data {
        val entries = historyDb.recent(10)
        val historyLines = entries.map {
            JalaliDate.formatTimestamp(it.timestampMs) +
                " — ↓ " + formatNumber1f(it.downloadMbps) +
                " • ↑ " + formatNumber1f(it.uploadMbps) +
                " • " + formatNumber1f(it.pingMs) + "ms"
        }
        val simSummary = if (simHolders.isEmpty()) {
            getString(R.string.sim_none)
        } else {
            simHolders.joinToString("\n") { h ->
                (h.sub.carrierName?.toString() ?: "?") +
                    " — " + h.tvNetType.text + " — " + h.tvSignal.text
            }
        }
        val score = NetworkQuality.score(
            lastDbm, lastRsrpVal, lastPingMs, lastJitterMs, lastDownMbps, lastUpMbps
        )
        val data = PdfReport.Data(
            operator = tvOperatorName.text.toString(),
            networkType = chipNetwork.text.toString(),
            dbm = tvDbm.text.toString(),
            rsrp = tvRsrp.text.toString(),
            rsrq = tvRsrq.text.toString(),
            sinr = tvSnr.text.toString(),
            band = tvServingBand.text.toString(),
            frequency = tvServingFreq.text.toString(),
            cid = tvServingCid.text.toString(),
            enbId = tvServingEnb.text.toString(),
            pci = tvServingPci.text.toString(),
            tac = tvServingTac.text.toString(),
            taDistance = tvTaDistance.text.toString(),
            distance = tvTowerDistance.text.toString(),
            bearing = tvTowerBearing.text.toString(),
            gpsCoords = tvGpsCoords.text.toString(),
            qualityScore = score.toString().toDigits() + " / 100 — " +
                NetworkQuality.label(this, score),
            speedSummary = lastSpeedSummary,
            signalStats = tvSigStats.text.toString(),
            simSummary = simSummary,
            historyLines = historyLines
        )
        return data
    }

    private fun shareFile(file: File, mime: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this, packageName + ".fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        } catch (t: Throwable) {
            toast(getString(R.string.toast_no_app))
        }
    }

    /** اشتراک‌گذاری گزارش متنی سریع از تولبار */
    private fun shareDiagnostics() {
        val sb = StringBuilder(getString(R.string.share_header))
        sb.append(getString(R.string.label_operator)).append(": ")
            .append(tvOperatorName.text).append('\n')
        sb.append(getString(R.string.label_rat)).append(": ")
            .append(chipNetwork.text).append('\n')
        sb.append(getString(R.string.label_dbm)).append(": ")
            .append(tvDbm.text).append('\n')
        sb.append(getString(R.string.label_band)).append(": ")
            .append(tvServingBand.text).append('\n')
        sb.append(getString(R.string.label_cid)).append(": ")
            .append(tvServingCid.text).append('\n')
        sb.append(getString(R.string.label_distance)).append(": ")
            .append(tvTowerDistance.text).append('\n')
        sb.append(getString(R.string.score_title)).append(": ")
            .append(tvQualityScore.text).append('\n')
        if (tvTowerBearing.text.toString() != "—") {
            sb.append(getString(R.string.label_bearing)).append(": ")
                .append(tvTowerBearing.text).append('\n')
        }
        lastSpeedSummary?.let {
            sb.append(getString(R.string.card_speed_title)).append(": ").append(it).append('\n')
        }
        sb.append('\n').append(getString(R.string.app_name))

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    // ───────────────────── اطلاعات دستگاه (ویجت) ─────────────────────

    private fun refreshDeviceInfo() {
        // ویجت صفحهٔ اصلی را تازه نگه می‌دارد
        SignalWidgetProvider.pushUpdate(applicationContext)
    }

    // ───────────────────────── قطب‌نما ─────────────────────────

    private fun registerCompass() {
        val manager = sensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        try {
            manager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (t: Throwable) {
        }
    }

    private fun unregisterCompass() {
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (t: Throwable) {
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
