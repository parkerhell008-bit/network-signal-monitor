package ir.netsignal.monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * قطب‌نمای سفارشی (گروه ۴):
 *  - عقربهٔ خاکستری: جهت شمال نسبت به گوشی (آزیموت سنسور)
 *  - فلش سبز: جهت دکل نسبت به شمال
 * صفحه همیشه با «شمال بالا» رسم می‌شود؛ یعنی کاربر گوشی را می‌چرخاند و
 * فلش‌ها نسبت به خط شمال حرکت می‌کنند.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** آزیموت دستگاه (۰=شمال، ۹۰=شرق) */
    @Volatile
    private var azimuthDeg: Float = 0f

    /** جهت دکل نسبت به شمال؛ null یعنی هنوز مکانی یافت نشده */
    @Volatile
    private var targetBearingDeg: Float? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.stroke)
        strokeWidth = dp(2f)
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.text_secondary)
        strokeWidth = dp(1.5f)
    }

    private val northTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }

    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.gen_unknown)
    }

    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.emerald)
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.text_secondary)
    }

    /** تنظیم آزیموت سنسور (درجه) */
    fun setAzimuth(deg: Float) {
        azimuthDeg = ((deg % 360f) + 360f) % 360f
        invalidate()
    }

    /** تنظیم جهت دکل (درجه از شمال) */
    fun setTargetBearing(deg: Double?) {
        targetBearingDeg = deg?.let { (((it % 360.0) + 360.0) % 360.0).toFloat() }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f - dp(16f)
        if (radius <= 0) return

        // دایرهٔ مدرج
        canvas.drawCircle(cx, cy, radius, trackPaint)
        for (i in 0 until 36) {
            val angle = Math.toRadians(i * 10.0)
            val long = i % 9 == 0
            val inner = radius - if (long) dp(10f) else dp(5f)
            val x1 = cx + sin(angle).toFloat() * inner
            val y1 = cy - cos(angle).toFloat() * inner
            val x2 = cx + sin(angle).toFloat() * radius
            val y2 = cy - cos(angle).toFloat() * radius
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // حروف جهات اصلی (شمال بالا)
        val cardinals = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)
        for ((label, angle) in cardinals) {
            val rad = Math.toRadians(angle)
            val r = radius - dp(20f)
            val x = cx + sin(rad).toFloat() * r
            val y = cy - cos(rad).toFloat() * r + dp(4f)
            canvas.drawText(label, x, y, northTextPaint)
        }

        // عقربهٔ جهت گوشی (خاکستری)
        drawNeedle(canvas, cx, cy, radius - dp(12f), azimuthDeg, headingPaint, filled = false)

        // فلش سبز جهت دکل
        targetBearingDeg?.let {
            drawNeedle(canvas, cx, cy, radius - dp(4f), it, targetPaint, filled = true)
        }

        canvas.drawCircle(cx, cy, dp(4f), centerDotPaint)
    }

    /** رسم یک فلش در زاویهٔ مشخص (۰ = بالا/شمال) */
    private fun drawNeedle(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        length: Float,
        angleDeg: Float,
        paint: Paint,
        filled: Boolean
    ) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val sinA = sin(rad).toFloat()
        val cosA = cos(rad).toFloat()
        val tipX = cx + sinA * length
        val tipY = cy - cosA * length
        val backX = cx - sinA * length * 0.25f
        val backY = cy + cosA * length * 0.25f
        val width = dp(if (filled) 7f else 5f)
        val px = cosA * width
        val py = sinA * width

        val path = Path()
        path.moveTo(tipX, tipY)
        path.lineTo(backX + px, backY + py)
        path.lineTo(backX - px, backY - py)
        path.close()

        if (filled) {
            canvas.drawPath(path, paint)
        } else {
            val stroke = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2f)
            }
            canvas.drawPath(path, stroke)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
