package ir.netsignal.monitor

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * گزارش PDF کامل شبکه (گروه ۶) با PdfDocument خود اندروید —
 * بدون هیچ وابستگی بیرونی. متن فارسی/انگلیسی و راست‌چین پشتیبانی می‌شود.
 */
object PdfReport {

    /** داده‌های گزارش — همه از قبل قالب‌بندی متنی شده‌اند */
    data class Data(
        val operator: String,
        val networkType: String,
        val dbm: String,
        val rsrp: String,
        val rsrq: String,
        val sinr: String,
        val band: String,
        val frequency: String,
        val cid: String,
        val enbId: String,
        val pci: String,
        val tac: String,
        val taDistance: String,
        val distance: String,
        val bearing: String,
        val gpsCoords: String,
        val qualityScore: String,
        val speedSummary: String?,
        val signalStats: String,
        val simSummary: String,
        val historyLines: List<String>
    )

    private const val PAGE_W = 595   // A4
    private const val PAGE_H = 842
    private const val MARGIN = 46f
    private const val BOTTOM_LIMIT = 60f

    fun build(context: Context, data: Data): File {
        val doc = PdfDocument()
        val state = PageState(doc)

        val titlePaint = paint(19f, true, Color.rgb(4, 120, 87))
        val sectionPaint = paint(13f, true, Color.rgb(5, 150, 105))
        val labelPaint = paint(10.5f, false, Color.rgb(120, 135, 150))
        val valuePaint = paint(11f, true, Color.rgb(25, 40, 55))
        val bodyPaint = paint(10.5f, false, Color.rgb(50, 65, 80))
        val alignRight = persianDigitsEnabled

        val generated = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())

        // ───── سربرگ ─────
        state.ensure(60f)
        state.drawText(context.getString(R.string.app_name), titlePaint, alignRight)
        state.advance(20f)
        val sub = context.getString(R.string.about_version_fmt, versionName(context)) +
            " — " + generated.toDigits()
        state.drawText(sub, labelPaint, alignRight)
        state.advance(10f)

        // ───── بخش مشخصات شبکه ─────
        state.section(context.getString(R.string.card_tower_title), sectionPaint, labelPaint, alignRight)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_operator), data.operator)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_rat), data.networkType)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_dbm), data.dbm)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_rsrp), data.rsrp)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_rsrq), data.rsrq)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_snr), data.sinr)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_band), data.band)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_frequency), data.frequency)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_cid), data.cid)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_enb), data.enbId)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_pci), data.pci)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_tac), data.tac)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_ta_distance), data.taDistance)

        // ───── بخش موقعیت ─────
        state.section(context.getString(R.string.card_distance_title), sectionPaint, labelPaint, alignRight)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_distance), data.distance)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_bearing), data.bearing)
        row(state, alignRight, valuePaint,
            context.getString(R.string.label_gps), data.gpsCoords)

        // ───── بخش کیفیت و سرعت ─────
        state.section(context.getString(R.string.score_title), sectionPaint, labelPaint, alignRight)
        row(state, alignRight, valuePaint,
            context.getString(R.string.score_title), data.qualityScore)
        row(state, alignRight, valuePaint,
            context.getString(R.string.chart_live_hint), data.signalStats)
        data.speedSummary?.let {
            row(state, alignRight, valuePaint,
                context.getString(R.string.card_speed_title), it)
        }

        // ───── سیم‌کارت‌ها ─────
        state.section(context.getString(R.string.card_sim_title), sectionPaint, labelPaint, alignRight)
        state.drawWrapped(data.simSummary, bodyPaint, alignRight)

        // ───── تاریخچه ─────
        state.section(context.getString(R.string.card_history_title), sectionPaint, labelPaint, alignRight)
        for (line in data.historyLines) {
            state.ensure(18f)
            state.drawWrapped(line, bodyPaint, alignRight)
        }

        doc.finishPage(state.page)

        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val out = File(dir, "network_report_$stamp.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun row(state: PageState, alignRight: Boolean, paint: Paint, label: String, value: String) {
        state.ensure(20f)
        val text = "$label:  $value"
        val avail = PAGE_W - MARGIN * 2
        val fit = paint.breakText(text, true, avail, null)
        state.drawText(text.substring(0, fit), paint, alignRight)
        state.advance(18f)
    }

    /** مدیریت صفحه و موقعیت عمودی در گزارش */
    private class PageState(private val doc: PdfDocument) {
        var pageNum = 1
        var page: PdfDocument.Page = doc.startPage(
            PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
        )
        var canvas = page.canvas
        var y = 56f

        fun ensure(need: Float) {
            if (y + need > PAGE_H - BOTTOM_LIMIT) newPage()
        }

        private fun newPage() {
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
            )
            canvas = page.canvas
            y = 56f
        }

        fun advance(dy: Float) {
            y += dy
        }

        fun drawText(text: String, paint: Paint, alignRight: Boolean) {
            paint.textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
            val x = if (alignRight) PAGE_W - MARGIN else MARGIN
            canvas.drawText(text, x, y, paint)
        }

        fun section(title: String, sectionPaint: Paint, linePaint: Paint, alignRight: Boolean) {
            ensure(40f)
            advance(12f)
            drawText(title, sectionPaint, alignRight)
            advance(6f)
            linePaint.strokeWidth = 1f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint)
            advance(12f)
        }

        /** متن چندخطی با شکستن خطوط بلند */
        fun drawWrapped(text: String, paint: Paint, alignRight: Boolean) {
            val avail = PAGE_W - MARGIN * 2
            for (line in text.split('\n')) {
                if (line.isBlank()) continue
                var remaining = line
                while (remaining.isNotEmpty()) {
                    ensure(18f)
                    val fit = paint.breakText(remaining, true, avail, null)
                    if (fit <= 0) break
                    drawText(remaining.substring(0, fit), paint, alignRight)
                    advance(15f)
                    remaining = remaining.substring(fit)
                }
            }
        }
    }

    private fun paint(size: Float, bold: Boolean, color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) {
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            } else {
                Typeface.SANS_SERIF
            }
        }

    private fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (t: Throwable) {
        "—"
    }
}
