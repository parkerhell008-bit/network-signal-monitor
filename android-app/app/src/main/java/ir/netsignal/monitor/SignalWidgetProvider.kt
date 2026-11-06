package ir.netsignal.monitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * ویجت صفحهٔ اصلی (گروه ۵): نوع شبکه + سیگنال لحظه‌ای.
 * به‌روزرسانی توسط سرویس پایش و اکتیویتی انجام می‌شود.
 */
class SignalWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        pushUpdate(context)
    }

    companion object {

        /** ساخت RemoteViews با وضعیت فعلی و اعمال روی همهٔ ویجت‌ها */
        fun pushUpdate(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val views = RemoteViews(context.packageName, R.layout.widget_signal)

                val dbm = SignalState.dbm
                val dbmText = dbm?.let { (it.toString() + " dBm").toDigits() } ?: context.getString(R.string.speed_na)
                views.setTextViewText(
                    R.id.tvWidgetNet,
                    context.getString(R.string.widget_net_na).replace("—", SignalState.netLabel)
                )
                views.setTextViewText(
                    R.id.tvWidgetSignal,
                    context.getString(R.string.widget_signal_na).replace("—", dbmText)
                )

                // لمس ویجت → باز شدن برنامه
                val open = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widgetRoot, open)

                manager.updateAppWidget(
                    ComponentName(context, SignalWidgetProvider::class.java),
                    views
                )
            } catch (t: Throwable) {
                // ویجت نباید هیچ‌گاه سرویس یا اکتیویتی را مختل کند
            }
        }
    }
}
