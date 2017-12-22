package nishkyu.radclock

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

const val MAX_TICKS = 12
const val TICK_DEGREES = 30.0
const val TICK_LENGTH = 20

/**
 * A Radial Clock with two controls. The two controls can represent start and end times. The
 * controls can be moved around the clock like a rotary dial.
 */
class RadClock(context: Context?, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
    val mContext = context
    val mAttrs = attributeSet

    // layout and sizing
    private var mBackgroundDiameter: Float = 0f
    private var mForegroundRadius: Float = 0f
    private var mCircleCenterX: Float = 0f
    private var mCircleCenterY: Float = 0f
    private var mTickStartX: Float = 0f
    private var mTickStartY: Float = 0f
    private var mTickPositions: ArrayList<TickPos> = ArrayList(MAX_TICKS)
    private var mTickNumPositions: ArrayList<TickNumPos> = ArrayList(MAX_TICKS)
    private var mCenterTextY = 0f

    // paint objects
    private val mClockFGPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mClockBGPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mTickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mTickNumPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mClockCenterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // colors
    var mClockBackground: Int = Color.parseColor("#7E7E7E")
        set(value) {
            field = value
            invalidate()
        }
    var mClockForeground = Color.parseColor("#000000")
    var mTickColor = Color.parseColor("#FFFFFF")
    var mTickNumColor = Color.parseColor("#FFFFFF")
    var mCenterTimeColor = Color.parseColor("#FFFFFF")

    // logical properties
    var mStartTime: Calendar = GregorianCalendar.getInstance()
    var mEndTime: Calendar = GregorianCalendar.getInstance()
    var mSelectedTime: Calendar = GregorianCalendar.getInstance()
    var mStartTimeString: String = ""

    init {
        val typedArray = mContext?.theme?.obtainStyledAttributes(mAttrs, R.styleable.RadClock, 0, 0)
        try {
            mClockBackground = typedArray!!.getInt(R.styleable.RadClock_clockBackground, Color.parseColor("#7E7E7E"))
            mClockForeground = typedArray.getInt(R.styleable.RadClock_clockForeground, Color.parseColor("#000000"))
            mTickColor = typedArray.getInt(R.styleable.RadClock_tickColor, Color.parseColor("#FFFFFF"))
            mTickNumColor = typedArray.getInt(R.styleable.RadClock_tickNumColor, Color.parseColor("#FFFFFF"))
            mCenterTimeColor = typedArray.getInt(R.styleable.RadClock_centerTimeColor, Color.parseColor("#FFFFFF"))
        } catch (rte: RuntimeException) {
            typedArray!!.recycle()
        }

        mClockFGPaint.style = Paint.Style.FILL
        mClockFGPaint.color = mClockForeground
        mClockBGPaint.style = Paint.Style.FILL
        mClockBGPaint.color = mClockBackground

        mTickPaint.style = Paint.Style.FILL
        mTickPaint.color = mTickColor
        mTickPaint.strokeWidth = 5f

        mTickNumPaint.style = Paint.Style.FILL
        mTickNumPaint.color = mTickNumColor
        mTickNumPaint.textSize = 16f * resources.displayMetrics.density
        mTickNumPaint.textAlign = Paint.Align.CENTER
        mTickNumPaint.isAntiAlias = true
        mTickNumPaint.typeface = Typeface.DEFAULT

        mClockCenterTextPaint.style = Paint.Style.FILL
        mClockCenterTextPaint.color = mCenterTimeColor
        mClockCenterTextPaint.textSize = 22f * resources.displayMetrics.density
        mClockCenterTextPaint.textAlign = Paint.Align.CENTER
        mClockCenterTextPaint.isAntiAlias = true
        mClockCenterTextPaint.typeface = Typeface.DEFAULT

        val simpleDateFormat = SimpleDateFormat("HH:mm")
        simpleDateFormat.calendar = mStartTime
        mStartTimeString = simpleDateFormat.format(mStartTime.time)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // account for padding
        val xpad = (paddingLeft + paddingRight).toFloat()
        val ypad = (paddingTop + paddingBottom).toFloat()

        val drawWidth = (w - xpad).toFloat()
        val drawHeight = (h - ypad).toFloat()

        // maximum diameter of the clock background, foreground,
        mBackgroundDiameter = Math.min(drawWidth, drawHeight)
        mForegroundRadius = mBackgroundDiameter / 3

        // calculate offset for circle center draw position
        mCircleCenterX = (w / 2).toFloat()
        mCircleCenterY = (h / 2).toFloat()

        // pre-calculate the tick positions for performance
        val TICK_PADDING = 10f
        var radius = mForegroundRadius - TICK_PADDING
        var tick = 0
        while (tick < MAX_TICKS) {
            val tickX = mCircleCenterX + radius * Math.sin(Math.toRadians(TICK_DEGREES) * tick).toFloat()
            val tickY = mCircleCenterY - radius * Math.cos(Math.toRadians(TICK_DEGREES) * tick).toFloat()

            // customize each tick drawing: diagonal, horizontal, vertical
            when (tick) {
                0 -> mTickPositions.add(TickPos(tickX, tickY, tickX, tickY + TICK_LENGTH))
                1 -> mTickPositions.add(TickPos(tickX, tickY, tickX - TICK_LENGTH / 2, tickY + TICK_LENGTH))
                2 -> mTickPositions.add(TickPos(tickX, tickY, tickX - TICK_LENGTH, tickY + TICK_LENGTH / 2))
                3 -> mTickPositions.add(TickPos(tickX, tickY, tickX - TICK_LENGTH, tickY))
                4 -> mTickPositions.add(TickPos(tickX, tickY, tickX - TICK_LENGTH, tickY - TICK_LENGTH / 2))
                5 -> mTickPositions.add(TickPos(tickX, tickY, tickX - TICK_LENGTH / 2, tickY - TICK_LENGTH))
                6 -> mTickPositions.add(TickPos(tickX, tickY, tickX, tickY - TICK_LENGTH))
                7 -> mTickPositions.add(TickPos(tickX, tickY, tickX + TICK_LENGTH / 2, tickY - TICK_LENGTH))
                8 -> mTickPositions.add(TickPos(tickX, tickY, tickX + TICK_LENGTH , tickY - TICK_LENGTH / 2))
                9 -> mTickPositions.add(TickPos(tickX, tickY, tickX + TICK_LENGTH, tickY))
                10 -> mTickPositions.add(TickPos(tickX, tickY, tickX + TICK_LENGTH, tickY + TICK_LENGTH / 2))
                11 -> mTickPositions.add(TickPos(tickX, tickY, tickX + TICK_LENGTH / 2, tickY + TICK_LENGTH))
            }
            ++tick
        }

        // pre-calculate the tick number positions for performance
        tick = 1
        radius = mForegroundRadius - (TICK_PADDING * 2 + TICK_LENGTH * 2) - 10
        while (tick <= MAX_TICKS) {
            val tickX = mCircleCenterX + radius * Math.sin(Math.toRadians(TICK_DEGREES) * tick).toFloat()
            var tickY = mCircleCenterY - radius * Math.cos(Math.toRadians(TICK_DEGREES) * tick).toFloat()

            // text is drawn on the baseline, so numbers 3 - 9 have to be adjusted in height
            val textBounds = Rect()
            mTickNumPaint.getTextBounds(tick.toString(), 0, tick.toString().length, textBounds)
            if (tick == 3 || tick == 9) tickY += textBounds.height() / 2
            if (tick in 4.0..8.0) tickY += textBounds.height()
            mTickNumPositions.add(TickNumPos(tick.toString(), tickX, tickY))
            ++tick
        }

        // y-position of the center time text
        val centerTextRect = Rect()
        mClockCenterTextPaint.getTextBounds(mStartTimeString, 0, mStartTimeString.length, centerTextRect)
        mCenterTextY = mCircleCenterY + centerTextRect.height() / 2
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        // draw the clock background
        canvas!!.drawCircle(mCircleCenterX, mCircleCenterY, mBackgroundDiameter / 2f, mClockBGPaint)

        // draw the clock foreground
        canvas.drawCircle(mCircleCenterX, mCircleCenterY, mForegroundRadius, mClockFGPaint)

        // draw ticks
        for (tickPos in mTickPositions)
            canvas.drawLine(tickPos.startX, tickPos.startY, tickPos.endX, tickPos.endY, mTickPaint)

        for (tickNumPos in mTickNumPositions)
            canvas.drawText(tickNumPos.text, tickNumPos.startX, tickNumPos.startY, mTickNumPaint)

        canvas.drawText(mStartTimeString, mCircleCenterX, mCenterTextY, mClockCenterTextPaint)
    }

    private class TickPos(sx: Float, sy: Float, ex: Float, ey: Float) {
        var startX = sx
        var startY = sy
        var endX = ex
        var endY = ey
    }

    private class TickNumPos(num: String, sx: Float, sy: Float) {
        var text = num
        var startX = sx
        var startY = sy
    }
}