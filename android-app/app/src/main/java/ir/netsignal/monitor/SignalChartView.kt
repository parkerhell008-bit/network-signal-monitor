package ir.netsignal.monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * نمودار لحظه‌ای قدرت سیگنال (dBm): ۶۰ نمونهٔ آخر به‌صورت خط روند زنده رسم می‌شود.
 * بازهٔ عمودی ثابت است (۴۰- تا ۱۲۰-) تا شکل موج معنی‌دار بماند.
 */
class SignalChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        const val MAX_SAMPLES = 60
        const val TOP_DBM = -40f
        const val BOTTOM_DBM = -120f
    }

    private val samples = arrayOfNulls<Int>(MAX_SAMPLES)
    private var head = 0          // نقطهٔ نوشتن بعدی (حلقه‌ای)
    private var filled = 0        // تعداد نمونه‌های معتبر

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stroke)
        strokeWidth = dp(1f)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.emerald)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.emerald)
        alpha = 28
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.emerald)
        style = Paint.Style.FILL
    }

    /** افزودن نمونهٔ جدید (null = بدون داده در این لحظه) */
    fun addSample(dbm: Int?) {
        samples[head] = dbm
        head = (head + 1) % MAX_SAMPLES
        if (filled < MAX_SAMPLES) filled++
        invalidate()
    }

    fun clearSamples() {
        java.util.Arrays.fill(samples, null)
        head = 0
        filled = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val padTop = dp(6f)
        val padBottom = dp(6f)
        val chartH = h - padTop - padBottom

        // خطوط راهنما
        for (i in 0..3) {
            val y = padTop + chartH * i / 3f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        if (filled == 0) return

        // نمونه‌ها به ترتیب قدیم → جدید (از چپ به راست)
        val ordered = arrayOfNulls<Int>(filled)
        for (i in 0 until filled) {
            ordered[i] = samples[(head - filled + i + MAX_SAMPLES * 2) % MAX_SAMPLES]
        }

        fun yOf(dbm: Int): Float {
            val v = dbm.coerceIn(BOTTOM_DBM.toInt(), TOP_DBM.toInt()).toFloat()
            return padTop + chartH * (v - BOTTOM_DBM) / (TOP_DBM - BOTTOM_DBM)
        }

        val stepX = if (filled > 1) w / (MAX_SAMPLES - 1) else 0f
        val startIdx = MAX_SAMPLES - filled   // نمونه‌های اول از سمت چپِ ناحیهٔ ۶۰تایی

        val path = Path()
        var started = false
        var lastX = 0f
        var lastY = 0f
        for (i in 0 until filled) {
            val v = ordered[i] ?: continue
            val x = (startIdx + i) * stepX
            val y = yOf(v)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }
        if (!started) return

        // ناحیهٔ پرشدهٔ ملایم زیر خط
        val fillPath = Path(path)
        fillPath.lineTo(lastX, h)
        fillPath.lineTo((startIdx) * stepX, h)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)

        canvas.drawPath(path, linePaint)
        canvas.drawCircle(lastX, lastY, dp(3f), dotPaint)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
