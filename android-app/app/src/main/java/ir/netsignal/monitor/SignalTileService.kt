package ir.netsignal.monitor

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * کاشی Quick Settings (گروه ۵): نمایش نوع شبکه و سیگنال در پنل سریع.
 * لمس کاشی → باز شدن برنامه. روی اندروید ۶ به بالا فعال است.
 */
class SignalTileService : TileService() {

    override fun onStartListening() {
        refreshTile()
    }

    override fun onClick() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pi = android.app.PendingIntent.getActivity(
                        this, 0, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(launch)
                }
            } catch (t: Throwable) {
            }
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        try {
            val dbm = SignalState.dbm
            tile.label = getString(R.string.app_name)
            val dbmText = dbm?.let { (it.toString() + " dBm").toDigits() } ?: "—"
            tile.subtitle = SignalState.netLabel + " • " + dbmText
            tile.state = if (SignalState.updatedAtMs > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        } catch (t: Throwable) {
        }
    }
}
