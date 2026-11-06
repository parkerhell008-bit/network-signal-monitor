package ir.netsignal.monitor

import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * کنترلر نقشهٔ دکل (گروه ۴) — OpenStreetMap بدون نیاز به Google Play Services.
 * نشانگر سبز/آبی پیش‌فرض osmdroid: دکل و موقعیت کاربر.
 * همهٔ فراخوانی‌ها در برابر نبود دیتا/خطا مقاوم‌اند.
 */
class TowerMapController(private val map: MapView) {

    private var towerMarker: Marker? = null
    private var userMarker: Marker? = null
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = true
        try {
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.controller.setZoom(15.5)
            // مرکز پیش‌فرض: تهران تا نقشهٔ خالی زشت نباشد
            map.controller.setCenter(GeoPoint(35.6892, 51.3890))
        } catch (t: Throwable) {
        }
    }

    /** نمایش/جابجایی نشانگر دکل */
    fun setTower(lat: Double, lon: Double, title: String) {
        try {
            initialize()
            val point = GeoPoint(lat, lon)
            val marker = towerMarker ?: Marker(map).also {
                it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                towerMarker = it
                map.overlays.add(it)
            }
            marker.position = point
            marker.title = title
            map.controller.setCenter(point)
            map.invalidate()
        } catch (t: Throwable) {
        }
    }

    /** نمایش/جابجایی نشانگر موقعیت کاربر */
    fun setUser(lat: Double, lon: Double, title: String) {
        try {
            initialize()
            val point = GeoPoint(lat, lon)
            val marker = userMarker ?: Marker(map).also {
                it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                userMarker = it
                map.overlays.add(it)
            }
            marker.position = point
            marker.title = title
            map.invalidate()
        } catch (t: Throwable) {
        }
    }

    fun onResume() {
        try {
            map.onResume()
        } catch (t: Throwable) {
        }
    }

    fun onPause() {
        try {
            map.onPause()
        } catch (t: Throwable) {
        }
    }
}
