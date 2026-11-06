package ir.netsignal.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * سرویس پایش پس‌زمینه (گروه ۵):
 *  - هر ۵ ثانیه سیگنال را نمونه‌برداری و در CSV ثبت می‌کند
 *  - اعلان دائمی با وضعیت لحظه‌ای نشان می‌دهد
 *  - هنگام افت ناگهانی سیگنال هشدار می‌دهد (حداکثر هر ۱۰ دقیقه)
 *  - ویجت صفحهٔ اصلی را به‌روز نگه می‌دارد
 */
class SignalLogService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var telephony: android.telephony.TelephonyManager? = null

    private var lastDbmForDrop: Int? = null
    private var lastDropNotifyMs = 0L
    private var lastNotifUpdateMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        telephony = getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        scope.launch {
            var ticks = 0
            while (isActive) {
                sample()
                ticks++
                if (ticks % 6 == 0) {
                    // هر ۳۰ ثانیه اعلان تازه شود (نه هر نمونه — برای باتری)
                    updateNotification()
                }
                delay(5_000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        isRunning = false
        super.onDestroy()
    }

    /** یک نمونه سیگنال بگیر، ثبت کن و هشدار/ویجت را به‌روز کن */
    private suspend fun sample() {
        val tm = telephony ?: return
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_PHONE_STATE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val dbm: Int? = try {
            val ss = tm.signalStrength
            SignalDbmExtractor.extract(ss)
        } catch (t: Throwable) {
            null
        }
        val ratLabel: String = try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
            RatLabel.of(type)
        } catch (t: Throwable) {
            "—"
        }

        SignalState.dbm = dbm ?: SignalState.dbm
        SignalState.netLabel = ratLabel
        SignalState.updatedAtMs = System.currentTimeMillis()

        // ثبت CSV
        val operator = try {
            tm.networkOperatorName ?: ""
        } catch (t: Throwable) {
            ""
        }
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        SignalLogStore.append(
            this,
            ts + "," + (dbm?.toString() ?: "") + "," + ratLabel.replace(",", " ") + "," +
                operator.replace(",", " ") + ","
        )

        // هشدار افت ناگهانی
        checkDrop(dbm)

        // ویجت همیشه تازه باشد
        withContext(Dispatchers.Main) {
            SignalWidgetProvider.pushUpdate(applicationContext)
        }
    }

    private fun checkDrop(dbm: Int?) {
        val now = System.currentTimeMillis()
        val prev = lastDbmForDrop
        lastDbmForDrop = dbm ?: prev
        if (dbm == null || prev == null) return
        val dropped = prev >= -95 && dbm <= -110
        if (dropped && now - lastDropNotifyMs > 10 * 60 * 1000) {
            lastDropNotifyMs = now
            showDropNotification(dbm)
        }
    }

    private fun showDropNotification(dbm: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val text = getString(R.string.notif_drop_text_fmt, (dbm.toString() + " dBm").toDigits())
        val notification = NotificationCompat.Builder(this, DROP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_signal)
            .setContentTitle(getString(R.string.notif_drop_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            manager.notify(DROP_NOTIF_ID, notification)
        } catch (t: Throwable) {
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notification = buildOngoingNotification()
        try {
            manager.notify(NOTIF_ID, notification)
        } catch (t: Throwable) {
        }
    }

    private fun buildOngoingNotification(): Notification {
        val dbmText = SignalState.dbm?.let { (it.toString() + " dBm").toDigits() } ?: "—"
        val text = getString(R.string.notif_text_fmt, SignalState.netLabel, dbmText)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_signal)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startForegroundCompat() {
        val notification = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.notif_channel_desc)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
            val drop = NotificationChannel(
                DROP_CHANNEL_ID,
                getString(R.string.notif_drop_title),
                NotificationManager.IMPORTANCE_HIGH
            )
            drop.description = getString(R.string.notif_drop_title)
            manager.createNotificationChannel(drop)
        }
    }

    companion object {
        const val CHANNEL_ID = "signal_monitor"
        const val DROP_CHANNEL_ID = "signal_drop"
        const val NOTIF_ID = 1001
        const val DROP_NOTIF_ID = 1002

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, SignalLogService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                // اندروید ۱۲+: شروع سرویس از پس‌زمینه مجاز نیست — از UI شروع می‌کنیم
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, SignalLogService::class.java))
            } catch (t: Throwable) {
            }
        }
    }
}

/** استخراج dBm از SignalStrength — مشترک بین اکتیویتی و سرویس */
object SignalDbmExtractor {

    fun extract(ss: android.telephony.SignalStrength?): Int? {
        if (ss == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ss.cellSignalStrengths.firstNotNullOfOrNull { css ->
                    css.dbm.takeIf { it != Int.MAX_VALUE && it != 0 }
                } ?: legacyDbm(ss)
            } else {
                @Suppress("DEPRECATION")
                legacyDbm(ss)
            }
        } catch (t: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyDbm(ss: android.telephony.SignalStrength): Int {
        val asu = ss.gsmSignalStrength
        return if (asu in 1..99) asu * 2 - 113 else Int.MAX_VALUE
    }
}

/** برچسب کوتاه نسل شبکه از NETWORK_TYPE — مشترک بین ماژول‌ها */
object RatLabel {

    fun of(type: Int): String = when (type) {
        android.telephony.TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        android.telephony.TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
        android.telephony.TelephonyManager.NETWORK_TYPE_CDMA -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0 -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
        android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
        android.telephony.TelephonyManager.NETWORK_TYPE_NR -> "5G"
        else -> "—"
    }
}
