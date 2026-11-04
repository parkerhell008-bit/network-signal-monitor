package ir.netsignal.monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * پنج نوار صعودی سیگنال (مثل نوار وضعیت گوشی‌های قدیمی).
 * level بر اساس SignalStrength.getLevel(): 0 تا 4 (و -1 برای «نامشخص»).
 * رنگ نوارهای فعال بر اساس شدت سیگنال انتخاب می‌شود.
 */
class SignalBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        const val BAR_COUNT = 5
    }

    private var level = -1

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gauge_track)
    }

    /** تنظیم سطح سیگنال: 0..4 (یا -1 برای نامشخص) */
    fun setLevel(newLevel: Int) {
        level = newLevel.coerceIn(-1, 4)
        invalidate()
    }

    fun currentLevel(): Int = level

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val gap = w * 0.06f
        val barWidth = (w - gap * (BAR_COUNT - 1)) / BAR_COUNT
        val cornerRadius = barWidth / 2f
        activePaint.color = colorForLevel(level)

        for (i in 0 until BAR_COUNT) {
            val barHeight = h * (i + 1) / BAR_COUNT
            val left = i * (barWidth + gap)
            val top = h - barHeight
            val paint = if (level >= 0 && i <= level) activePaint else idlePaint
            canvas.drawRoundRect(left, top, left + barWidth, h, cornerRadius, cornerRadius, paint)
        }
    }

    private fun colorForLevel(lvl: Int): Int {
        return when {
            lvl >= 3 -> ContextCompat.getColor(context, R.color.sig_good)
            lvl == 2 -> ContextCompat.getColor(context, R.color.sig_mid)
            lvl >= 0 -> ContextCompat.getColor(context, R.color.sig_bad)
            else -> ContextCompat.getColor(context, R.color.text_secondary)
        }
    }
}
