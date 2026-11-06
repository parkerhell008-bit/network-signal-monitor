package ir.netsignal.monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.max

/**
 * نمودار خطی روند نتایج تست سرعت (دانلود و آپلود) از تاریخچهٔ SQLite.
 * محور افقی: از قدیمی‌ترین نتیجه (چپ) تا جدیدترین (راست).
 * محور عمودی: مگابیت بر ثانیه با خطوط راهنما و برچسب حداکثر.
 */
class HistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var entries: List<SpeedTestHistory.Entry> = emptyList()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stroke)
        strokeWidth = dp(1f)
    }

    private val downloadPaint = linePaint(R.color.sig_good)
    private val uploadPaint = linePaint(R.color.sig_mid)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = sp(10f)
    }

    private fun linePaint(colorRes: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorRes)
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** تنظیم داده‌ها (از قدیم به جدید) و رسم مجدد */
    fun setData(data: List<SpeedTestHistory.Entry>) {
        entries = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padLeft = dp(38f)
        val padRight = dp(10f)
        val padTop = dp(22f)
        val padBottom = dp(12f)
        val chartW = w - padLeft - padRight
        val chartH = h - padTop - padBottom

        // خطوط راهنمای افقی (۵ خط)
        for (i in 0..4) {
            val y = padTop + chartH * i / 4f
            canvas.drawLine(padLeft, y, w - padRight, y, gridPaint)
        }
        if (entries.isEmpty()) return

        val best = max(
            maxOf(entries.maxOf { it.downloadMbps }, entries.maxOf { it.uploadMbps }),
            1.0
        ) * 1.15

        fun yOf(v: Double): Float = (padTop + chartH * (1.0 - v / best)).toFloat()

        val xs = FloatArray(entries.size) { i ->
            if (entries.size == 1) {
                padLeft + chartW / 2f
            } else {
                padLeft + chartW * i / (entries.size - 1)
            }
        }
        val downloadYs = FloatArray(entries.size) { i -> yOf(entries[i].downloadMbps) }
        val uploadYs = FloatArray(entries.size) { i -> yOf(entries[i].uploadMbps) }

        drawSeries(canvas, xs, downloadYs, downloadPaint)
        drawSeries(canvas, xs, uploadYs, uploadPaint)

        // برچسب حداکثر محور عمودی
        val label = context.getString(R.string.chart_max_fmt, formatMbpsShort(best))
        canvas.drawText(label, padLeft, padTop - dp(6f), labelPaint)
    }

    private fun drawSeries(canvas: Canvas, xs: FloatArray, ys: FloatArray, paint: Paint) {
        if (ys.isEmpty()) return
        val path = Path()
        for (i in ys.indices) {
            if (i == 0) path.moveTo(xs[i], ys[i]) else path.lineTo(xs[i], ys[i])
        }
        canvas.drawPath(path, paint)
        dotPaint.color = paint.color
        for (i in ys.indices) {
            canvas.drawCircle(xs[i], ys[i], dp(2.5f), dotPaint)
        }
    }

    private fun formatMbpsShort(v: Double): String =
        String.format(Locale.US, "%.1f", v).replace('.', '٫').toDigits()

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity
}
