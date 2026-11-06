package ir.netsignal.monitor

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import org.osmdroid.config.Configuration
import java.io.File
import java.util.Locale

/**
 * کلاس اپلیکیشن: اعمال تم ذخیره‌شده، رنگ پویا (اندروید ۱۲+)،
 * پیکربندی osmdroid و حالت ارقام فارسی/انگلیسی.
 */
class NetSignalApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ارقام: فارسی به‌جز وقتی کاربر زبان انگلیسی انتخاب کرده
        persianDigitsEnabled = Locale.getDefault().language != "en"

        // تم ذخیره‌شده (سیستم/روشن/تاریک) پیش از ساخته‌شدن اکتیویتی
        AppCompatDelegate.setDefaultNightMode(
            when (AppPrefs.themeMode(this)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        // رنگ پویا — فقط اندروید ۱۲+ و اگر کاربر روشن گذاشته باشد
        if (AppPrefs.dynamicColor(this)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        // پیکربندی osmdroid: کش روی حافظهٔ برنامه + شناسهٔ کاربر
        try {
            val config = Configuration.getInstance()
            config.userAgentValue = packageName
            val base = File(cacheDir, "osmdroid")
            config.osmdroidBasePath = base
            config.osmdroidTileCache = File(base, "tiles")
        } catch (t: Throwable) {
        }
    }
}
