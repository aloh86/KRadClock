package nishkyu.radclock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

const val MAX_TICKS = 12
const val TICK_DEGREES = 30.0
const val TICK_LENGTH = 20

/**
 * A Radial Clock with two controls. The two controls can represent start and end times. The
 * controls can be moved around the clock like a rotary dial.
 */
class RadClock(context: Context?, attributeSet: AttributeSet? = null) : View(context, attributeSet), ValueAnimator.AnimatorUpdateListener {
    val mContext = context
    val mAttrs = attributeSet

    private lateinit var mBackground: ClockCircle
    private lateinit var mForeground: ClockCircle
    private lateinit var mTicks: Ticks
    private var mNumbers : Numbers
    private var mCenterTime: CenterTime
    private var mStart: Dial
    private var mLetterS: ControlText = ControlText()
    private var mLetterE: ControlText = ControlText()

    // layout and sizing
    private var mCircleCenterX: Float = 0f
    private var mCircleCenterY: Float = 0f
    private var mControlRadius: Float = 0f

    // colors
    var mBackgroundColor = Color.parseColor("#7E7E7E")
    var mForegroundColor = Color.parseColor("#000000")
    var mTickColor = Color.parseColor("#FFFFFF")
    var mNumberColor = Color.parseColor("#FFFFFF")
    var mCenterTimeColor = Color.parseColor("#FFFFFF")
    var mStartColor = Color.parseColor("#1F9ED9")

    // logical properties
    var mStartTime: Calendar = GregorianCalendar.getInstance()
    var mEndTime: Calendar = GregorianCalendar.getInstance()
    var mSelectedTime: Calendar = GregorianCalendar.getInstance()
    var mStartTimeString: String = ""

    // touch
    private var mPreviousX = 0f
    private var mPreviousY = 0f
    private val mDetector = GestureDetector(this.mContext, SimpleGestureListener())

    // animation
    private var mStartAnimator = ValueAnimator()

    private enum class Quadrant {Q1, Q2, Q3, Q4}

    init {
        val typedArray = mContext?.theme?.obtainStyledAttributes(mAttrs, R.styleable.RadClock, 0, 0)
        try {
            mBackgroundColor = typedArray!!.getInt(R.styleable.RadClock_clockBackground, Color.parseColor("#7E7E7E"))
            mForegroundColor = typedArray.getInt(R.styleable.RadClock_clockForeground, Color.parseColor("#000000"))
            mTickColor = typedArray.getInt(R.styleable.RadClock_tickColor, Color.parseColor("#FFFFFF"))
            mNumberColor = typedArray.getInt(R.styleable.RadClock_tickNumColor, Color.parseColor("#FFFFFF"))
            mCenterTimeColor = typedArray.getInt(R.styleable.RadClock_centerTimeColor, Color.parseColor("#FFFFFF"))
        } catch (rte: RuntimeException) {
            typedArray!!.recycle()
        }


        val centerTimePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        centerTimePaint.style = Paint.Style.FILL
        centerTimePaint.color = mCenterTimeColor
        centerTimePaint.textSize = 22f * resources.displayMetrics.density
        centerTimePaint.textAlign = Paint.Align.CENTER
        centerTimePaint.isAntiAlias = true
        centerTimePaint.typeface = Typeface.DEFAULT
        mCenterTime = CenterTime(centerTimePaint)

        val numbersPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        numbersPaint.style = Paint.Style.FILL
        numbersPaint.color = mNumberColor
        numbersPaint.textSize = 18f * resources.displayMetrics.density
        numbersPaint.textAlign = Paint.Align.CENTER
        numbersPaint.isAntiAlias = true
        numbersPaint.typeface = Typeface.DEFAULT
        mNumbers = Numbers(numbersPaint)

        val simpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        simpleDateFormat.calendar = mStartTime
        mStartTimeString = simpleDateFormat.format(mStartTime.time)

        val startPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        startPaint.style = Paint.Style.FILL
        startPaint.color = mStartColor
        mStart = Dial(startPaint)

        mLetterS.text = "S"
        mLetterS.paint.style = Paint.Style.FILL
        mLetterS.paint.color = Color.WHITE
        mLetterS.paint.textAlign = Paint.Align.CENTER

        mLetterE.text = "E"
        mLetterE.paint.style = Paint.Style.FILL
        mLetterE.paint.color = Color.WHITE
        mLetterE.paint.textAlign = Paint.Align.CENTER

        mStartAnimator = ValueAnimator.ofFloat(0f, TICK_DEGREES.toFloat())
        mStartAnimator.duration = 10
        mStartAnimator.addUpdateListener(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // account for padding
        val xpad = (paddingLeft + paddingRight).toFloat()
        val ypad = (paddingTop + paddingBottom).toFloat()

        val drawWidth = (w - xpad).toFloat()
        val drawHeight = (h - ypad).toFloat()

        // maximum diameter of the clock background, foreground
        mBackground = ClockCircle(Math.min(drawWidth, drawHeight), mBackgroundColor)
        mForeground = ClockCircle((mBackground.diameter / 3) * 2, mForegroundColor)

        // calculate offset for circle center draw position
        mCircleCenterX = (w / 2).toFloat()
        mCircleCenterY = (h / 2).toFloat()

        // pre-calculate the tick positions for performance
        mTicks = Ticks(mTickColor)
        val TICK_PADDING = 10f
        var radius = mForeground.radius - TICK_PADDING
        var tick = 0
        while (tick < MAX_TICKS) {
            val tickX = mCircleCenterX + radius * Math.sin(Math.toRadians(TICK_DEGREES) * tick).toFloat()
            val tickY = mCircleCenterY - radius * Math.cos(Math.toRadians(TICK_DEGREES) * tick).toFloat()

            // customize each tick drawing: diagonal, horizontal, vertical
            when (tick) {
                0 -> mTicks.add(Tick(tickX, tickY, tickX, tickY + TICK_LENGTH))
                1 -> mTicks.add(Tick(tickX, tickY, tickX - TICK_LENGTH / 2, tickY + TICK_LENGTH))
                2 -> mTicks.add(Tick(tickX, tickY, tickX - TICK_LENGTH, tickY + TICK_LENGTH / 2))
                3 -> mTicks.add(Tick(tickX, tickY, tickX - TICK_LENGTH, tickY))
                4 -> mTicks.add(Tick(tickX, tickY, tickX - TICK_LENGTH, tickY - TICK_LENGTH / 2))
                5 -> mTicks.add(Tick(tickX, tickY, tickX - TICK_LENGTH / 2, tickY - TICK_LENGTH))
                6 -> mTicks.add(Tick(tickX, tickY, tickX, tickY - TICK_LENGTH))
                7 -> mTicks.add(Tick(tickX, tickY, tickX + TICK_LENGTH / 2, tickY - TICK_LENGTH))
                8 -> mTicks.add(Tick(tickX, tickY, tickX + TICK_LENGTH , tickY - TICK_LENGTH / 2))
                9 -> mTicks.add(Tick(tickX, tickY, tickX + TICK_LENGTH, tickY))
                10 -> mTicks.add(Tick(tickX, tickY, tickX + TICK_LENGTH, tickY + TICK_LENGTH / 2))
                11 -> mTicks.add(Tick(tickX, tickY, tickX + TICK_LENGTH / 2, tickY + TICK_LENGTH))
            }
            ++tick
        }

        // pre-calculate the tick number positions for performance
        tick = 1
        radius = mForeground.radius - (TICK_PADDING * 2 + TICK_LENGTH * 2) - 20
        while (tick <= MAX_TICKS) {
            val tickX = mCircleCenterX + radius * Math.sin(Math.toRadians(TICK_DEGREES) * tick).toFloat()
            var tickY = mCircleCenterY - radius * Math.cos(Math.toRadians(TICK_DEGREES) * tick).toFloat()

            // text is drawn on the baseline, so numbers 3 - 9 have to be adjusted in height
            val textBounds = Rect()
            mNumbers.paint.getTextBounds(tick.toString(), 0, tick.toString().length, textBounds)
            if (tick == 3 || tick == 9) tickY += textBounds.height() / 2
            if (tick in 4.0..8.0) tickY += textBounds.height()
            mNumbers.add(Number(tick.toString(), tickX, tickY))
            ++tick
        }

        // y-position of the center time text
        val centerTextRect = Rect()
        mCenterTime.paint.getTextBounds(mStartTimeString, 0, mStartTimeString.length, centerTextRect)
        mCenterTime.centerTime = mStartTimeString
        mCenterTime.y = mCircleCenterY + centerTextRect.height() / 2
        mCenterTime.x = mCircleCenterX

        // non-visible control radius. The circle on which the controls turn
        mControlRadius = mForeground.radius + (mBackground.radius - mForeground.radius)  / 2

        // start control calculations
        mStart.diameter = mBackground.radius - mForeground.radius
        mStart.radius = mStart.diameter / 2
        mStart.x = mCircleCenterX + mControlRadius * Math.sin(Math.toRadians(mStart.degree)).toFloat()
        mStart.y = mCircleCenterY - mControlRadius * Math.cos(Math.toRadians(mStart.degree)).toFloat()

        // calculate size and position of start control symbol
        mLetterS.paint.textSize = 22f * resources.displayMetrics.density
        mLetterS.paint.getTextBounds("S", 0, 1, mLetterS.textRect)
        mLetterS.x = mStart.x
        mLetterS.y = mStart.y
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        // draw the clock background
        canvas!!.drawCircle(mCircleCenterX, mCircleCenterY, mBackground.diameter / 2f, mBackground.paint)

        // draw the clock foreground
        canvas.drawCircle(mCircleCenterX, mCircleCenterY, mForeground.radius, mForeground.paint)

        // draw ticks
        for (tick in mTicks)
            canvas.drawLine(tick.sx, tick.sy, tick.ex, tick.ey, mTicks.paint)

        // draw numbers
        for (number in mNumbers)
            canvas.drawText(number.num, number.sx, number.sy, mNumbers.paint)

        // draw center text
        canvas.drawText(mCenterTime.centerTime, mCenterTime.x, mCenterTime.y, mCenterTime.paint)

        // draw start control
        canvas.drawCircle(mStart.x, mStart.y, mStart.radius, mStart.paint)
        canvas.drawText(mLetterS.text, mLetterS.x, mLetterS.y, mLetterS.paint)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val result = mDetector.onTouchEvent(event)
        var x = event!!.getX()
        var y = event.getY()

        if (!result) {
            when (event!!.action) {
                MotionEvent.ACTION_MOVE -> handleMove(x, y, event)
            }
        }
        mPreviousX = x
        mPreviousY = y;
        return true
    }

    private fun handleMove(x: Float, y: Float, event: MotionEvent?) {
        val control = withinControl(x, y)
        if (control != null) {
            val dx = x - mPreviousX
            val dy = y - mPreviousY

            if (dx > 0 || dy > 0) {
                if (!mStartAnimator.isRunning)
                    mStartAnimator.start()
            }
        }
    }

    override fun onAnimationUpdate(animator: ValueAnimator?) {
        val animatedValue = animator!!.getAnimatedValue() as Float
        mStart.degree += animatedValue
        mStart.x = mCircleCenterX + mControlRadius * Math.sin(Math.toRadians(mStart.degree)).toFloat()
        mStart.y = mCircleCenterY - mControlRadius * Math.cos(Math.toRadians(mStart.degree)).toFloat()
        mLetterS.x = mStart.x
        mLetterS.y = mStart.y
        invalidate()
    }

    // returns the control if user is within control using touch
    private fun withinControl(x: Float, y: Float): Dial? {
        val boxStart = RectF()
        boxStart.left = mStart.x - mStart.radius
        boxStart.right = boxStart.left + mStart.diameter
        boxStart.top = mStart.y - mStart.radius
        boxStart.bottom = boxStart.top + mStart.diameter

        if (boxStart.contains(x, y))
            return mStart

        return null
    }

    private fun getQuadrant(dial: Dial): Quadrant {
        if (dial.degree >= 0 && dial.degree < 90)
            return Quadrant.Q1
        else if (dial.degree >= 90 && dial.degree < 180)
            return Quadrant.Q2
        else if (dial.degree >= 180 && dial.degree < 270)
            return Quadrant.Q3
        else
            return Quadrant.Q4
    }

    private class SimpleGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
    }

    private class ControlText {
        var x = 0f
        var y = 0f
        set(value) {
            field = value
            field += textRect.height() / 2
        }
        var text = ""
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textRect = Rect()
    }

    private class Dial(paint: Paint) {
        var x = 0f
        var y = 0f
        var diameter = 0f
        var radius = 0f
        var degree = 0.0
        set(value) {
            if (value >= 720)
                field = 0.0
            else
                field = value
        }
        val paint = paint
    }

    private class CenterTime(paint: Paint) {
        var x = 0f
        var y = 0f
        var centerTime = ""
        val paint = paint
    }

    data class Tick(val sx: Float, val sy: Float, val ex: Float, val ey: Float)
    private class Ticks : Iterable<Tick> {
        private val mTicks = ArrayList<Tick>(MAX_TICKS)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 5f
        }

        constructor(color: Int) {
            paint.color = color
        }

        override fun iterator(): Iterator<Tick> {
            return mTicks.iterator()
        }

        fun add(tick: Tick) {
            mTicks.add(tick)
        }

        operator fun get(index: Int): Tick {
            return mTicks[index]
        }
    }

    data class Number(val num: String, val sx: Float, val sy: Float)
    private class Numbers(paint: Paint) : Iterable<Number> {
        private val mNumbers = ArrayList<Number>(MAX_TICKS)
        val paint = paint

        fun add(number: Number) {
            mNumbers.add(number)
        }

        override fun iterator(): Iterator<Number> {
            return mNumbers.iterator()
        }

        operator fun get(index: Int): Number {
            return mNumbers[index]
        }
    }

    private class ClockCircle {
        var diameter: Float = 0f
        var radius: Float = 0f
        val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            paint.style = Paint.Style.FILL
        }

        constructor(diameter: Float, color: Int) {
            this.diameter = diameter
            radius = diameter / 2
            paint.color = color
        }
    }
}