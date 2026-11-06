package ir.netsignal.monitor

import android.content.Context
import android.content.SharedPreferences

/** تنظیمات سادهٔ برنامه (تم، رنگ پویا، سرویس پایش) */
object AppPrefs {

    private const val NAME = "net_signal_prefs"
    const val KEY_THEME = "theme_mode"          // 0=سیستم 1=روشن 2=تاریک
    const val KEY_DYNAMIC = "dynamic_color"     // اندروید ۱۲+
    const val KEY_MONITOR = "monitor_enabled"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun themeMode(context: Context): Int = prefs(context).getInt(KEY_THEME, 0)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME, mode).apply()
    }

    fun dynamicColor(context: Context): Boolean = prefs(context).getBoolean(KEY_DYNAMIC, true)

    fun setDynamicColor(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun monitorEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_MONITOR, false)

    fun setMonitorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITOR, enabled).apply()
    }
}
