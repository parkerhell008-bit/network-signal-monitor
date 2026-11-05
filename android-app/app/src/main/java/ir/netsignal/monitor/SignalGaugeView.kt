package ir.netsignal.monitor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * گیج ۲۷۰ درجه‌ای قدرت سیگنال از 0 تا -120 dBm:
 * قوس پس‌زمینه + قوس رنگی گرادیانی (قرمز → زرد → سبز) + عقربه متحرک (ValueAnimator)
 * + متن dBm در مرکز.
 */
class SignalGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        const val START_ANGLE = 135f          // زاویه شروع قوس (پایین-چپ)
        const val SWEEP_ANGLE = 270f          // پوشش قوس: ۲۷۰ درجه
        const val MIN_DBM = -120f             // ضعیف‌ترین سیگنال
        // بازه واقعی سیگنال‌های قابل استفاده حدود ۴۰- تا ۱۲۰- دسی‌بل است؛
        // سقف صفر بودن باعث می‌شد سیگنال عالی (مثلاً ۵۰-) فقط نیمی از قوس را پر کند.
        const val MAX_DBM = -40f
    }

    private var displayedDbm = MIN_DBM
    private var lastValidDbm: Int? = null

    private val arcRect = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.gauge_track)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.text_primary)
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }

    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.emerald)
    }

    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = sp(32f)
        isFakeBoldText = true
    }

    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = sp(12f)
    }

    private val needleAnimator: ValueAnimator = ValueAnimator.ofFloat(MIN_DBM, MIN_DBM).apply {
        duration = 700L
        interpolator = DecelerateInterpolator()
        addUpdateListener { animator ->
            displayedDbm = animator.animatedValue as Float
            invalidate()
        }
    }

    /** تنظیم dBm جدید؛ عقربه به‌صورت انیمیت به مقدار جدید حرکت می‌کند */
    fun setDbm(value: Int) {
        val clamped = value.coerceIn(MIN_DBM.toInt(), MAX_DBM.toInt())
        lastValidDbm = clamped
        needleAnimator.cancel()
        needleAnimator.setFloatValues(displayedDbm, clamped.toFloat())
        needleAnimator.start()
    }

    /** بازگرداندن گیج به حالت «بدون داده» */
    fun clear() {
        lastValidDbm = null
        needleAnimator.cancel()
        displayedDbm = MIN_DBM
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val stroke = size * 0.09f
        val cx = width / 2f
        val cy = height / 2f
        val radius = size / 2f - stroke

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // ۱) قوس پس‌زمینه (کامل)
        trackPaint.strokeWidth = stroke
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint)

        // ۲) قوس رنگی تا موقعیت فعلی عقربه (گرادیان قرمز → زرد → سبز)
        val fraction = fractionOf(displayedDbm)
        valuePaint.strokeWidth = stroke
        valuePaint.shader = buildShader(cx, cy)
        if (fraction > 0f) {
            canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE * fraction, false, valuePaint)
        }

        // ۳) عقربه
        val angleRad = Math.toRadians((START_ANGLE + SWEEP_ANGLE * fraction).toDouble())
        val needleLen = radius - stroke * 1.4f
        val tailLen = radius * 0.18f
        val tipX = cx + (cos(angleRad) * needleLen).toFloat()
        val tipY = cy + (sin(angleRad) * needleLen).toFloat()
        val tailX = cx - (cos(angleRad) * tailLen).toFloat()
        val tailY = cy - (sin(angleRad) * tailLen).toFloat()
        canvas.drawLine(tailX, tailY, tipX, tipY, needlePaint)
        canvas.drawCircle(cx, cy, dp(5f), hubPaint)

        // ۴) متن مرکزی: مقدار dBm + واحد
        val textBaseline = cy + radius * 0.34f
        val valueText = lastValidDbm?.let { it.toString().toPersianDigits() } ?: "—"
        canvas.drawText(valueText, cx, textBaseline, valueTextPaint)
        canvas.drawText(
            context.getString(R.string.gauge_unit_dbm),
            cx,
            textBaseline + sp(20f),
            unitTextPaint
        )
    }

    private fun fractionOf(dbm: Float): Float =
        ((dbm - MIN_DBM) / (MAX_DBM - MIN_DBM)).coerceIn(0f, 1f)

    /**
     * گرادیان چرخشی: موقعیت ۰ قوس (۱۳۵ درجه) قرمز (سیگنال خیلی ضعیف)
     * و انتهای قوس (۴۰۵ درجه) سبز (سیگنال عالی) است؛
     * پس شیدر ۱۳۵ درجه می‌چرخد تا رنگ‌ها با ابتدای قوس هم‌تراز شوند.
     */
    private fun buildShader(cx: Float, cy: Float): SweepGradient {
        val red = ContextCompat.getColor(context, R.color.sig_bad)
        val amber = ContextCompat.getColor(context, R.color.sig_mid)
        val green = ContextCompat.getColor(context, R.color.sig_good)
        val gradient = SweepGradient(
            cx, cy,
            intArrayOf(red, amber, green, green),
            floatArrayOf(0f, 0.375f, 0.75f, 1f)
        )
        val matrix = Matrix()
        matrix.postRotate(START_ANGLE, cx, cy)
        gradient.setLocalMatrix(matrix)
        return gradient
    }

    override fun onDetachedFromWindow() {
        needleAnimator.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity
}
