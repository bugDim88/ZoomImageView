package com.bugdim88.zoomimage

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ScaleGestureDetectorCompat
import kotlin.math.abs

open class ZoomImageView : AppCompatImageView {
    private val kMinScale = 0.6f
    private val kMaxScale = 8f
    private val kResetDuration = 200

    private lateinit var initScaleType: ImageView.ScaleType

    private var initMatrix = Matrix()
    private val mainMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private var initMatrixValues: FloatArray? = null

    private var minScale = kMinScale
    private var maxScale = kMaxScale

    //scale calculated from showing drawable
    private var initMinScale = kMinScale
    private var initMaxScale = kMaxScale

    private val bounds = RectF()

    var scrollable = true
    var zoomable = true
    var doubleTapToZoom = true
    var restrictBounds = true
    var animateOnReset = true
    var autoCenter = true
    var doubleTapScaleFactor = 2f

    private var lastPoint = PointF(0f, 0f)
    private var initScale = 1f
    private var scaleBy = 1f
    var currentScaleFactor = 1f
        private set
    private var prevPointerCount = 1
    private var currPointerCount = 0

    private lateinit var scaleDetector: ScaleGestureDetector

    private lateinit var gestureDetector: GestureDetector
    private var doubleTapDetected = false
    private var singleTapDetected = false

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    private fun init(context: Context?, attrs: AttributeSet?) {
        scaleDetector = ScaleGestureDetector(context, scaleGestureListener)
        gestureDetector = GestureDetector(context, gestureListener)
        ScaleGestureDetectorCompat.setQuickScaleEnabled(scaleDetector, false)
        initScaleType = scaleType

        val values = context?.obtainStyledAttributes(attrs, R.styleable.ZoomImageView)
        values ?: return

        zoomable = values.getBoolean(R.styleable.ZoomImageView_zoomable, true)
        scrollable = values.getBoolean(R.styleable.ZoomImageView_scrollable, true)
        animateOnReset = values.getBoolean(R.styleable.ZoomImageView_animateOnReset, true)
        autoCenter = values.getBoolean(R.styleable.ZoomImageView_autoCenter, true)
        restrictBounds = values.getBoolean(R.styleable.ZoomImageView_restrictBounds, false)
        doubleTapToZoom = values.getBoolean(R.styleable.ZoomImageView_doubleTapZoom, true)
        minScale = values.getFloat(R.styleable.ZoomImageView_minScale, kMinScale)
        maxScale = values.getFloat(R.styleable.ZoomImageView_maxScale, kMaxScale)
        doubleTapScaleFactor = values.getFloat(R.styleable.ZoomImageView_doubleTapScaleFactor, 3f)

        verifyScaleRange()

        values.recycle()
    }

    private fun verifyScaleRange() {
        if (minScale >= maxScale) {
            throw IllegalStateException("minScale must be less than maxScale")
        }

        if (minScale < 0) {
            throw IllegalStateException("minScale must be greater than 0")
        }

        if (maxScale < 0) {
            throw IllegalStateException("maxScale must be greater than 0")
        }

        if (doubleTapScaleFactor > maxScale) {
            doubleTapScaleFactor = maxScale
        }

        if (doubleTapScaleFactor < minScale) {
            doubleTapScaleFactor = minScale
        }
    }

    private fun isInitScale(): Boolean {
        return matrixValues[Matrix.MSCALE_X] == initMatrixValues?.get(Matrix.MSCALE_X)
    }

    fun setScaleRange(minScale: Float, maxScale: Float) {
        this.minScale = minScale
        this.maxScale = maxScale

        initMatrixValues = null

        verifyScaleRange()
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        actualizeMainMatrix()
        val x = matrixValues[Matrix.MTRANS_X]
        if (isInitScale()) return false
        else if (x >= -1 && direction < 0) return false
        else if (abs(direction) + width + 1 >= currentDisplayedWidth && direction > 0) return false

        return true
        //return !isInitScale()
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return !isInitScale()
    }

    override fun setScaleType(scaleType: ImageView.ScaleType?) {
        super.setScaleType(scaleType)
        scaleType ?: return
        initScaleType = scaleType
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        if (!enabled) {
            scaleType = initScaleType
        }
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        scaleType = initScaleType
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        scaleType = initScaleType
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        scaleType = initScaleType
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        scaleType = initScaleType
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return false
        val enabled = !isClickable && isEnabled && (zoomable || scrollable)
        if (enabled) {
            if (scaleType != ImageView.ScaleType.MATRIX)
                super.setScaleType(ImageView.ScaleType.MATRIX)

            if (initMatrixValues == null) setStartValues()

            currPointerCount = event.pointerCount

            actualizeMainMatrix()

            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            if (doubleTapToZoom && doubleTapDetected) {
                doubleTapDetected = false
                singleTapDetected = false
                if (matrixValues[Matrix.MSCALE_X] != initMatrixValues?.get(Matrix.MSCALE_X)) {
                    reset()
                } else {
                    val zoomMatrix = Matrix(mainMatrix)
                    zoomMatrix.postScale(
                        doubleTapScaleFactor, doubleTapScaleFactor,
                        scaleDetector.focusX, scaleDetector.focusY
                    )
                    animateScaleAndTranslationToMatrix(zoomMatrix, kResetDuration)
                }
                return true
            } else if (!singleTapDetected) {
                /* if the event is a down touch, or if the number of touch points changed,
                     * we should reset our start point, as event origins have likely shifted to a
                     * different part of the screen*/
                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    currPointerCount != prevPointerCount
                ) {
                    lastPoint.set(scaleDetector.focusX, scaleDetector.focusY)
                } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    if (scrollable) {
                        val xdistance = getXDistance(scaleDetector.focusX, lastPoint.x)
                        val ydistance = getYDistance(scaleDetector.focusY, lastPoint.y)
                        mainMatrix.postTranslate(xdistance, ydistance)
                    }

                    if (zoomable) {
                        mainMatrix.postScale(scaleBy, scaleBy, scaleDetector.focusX, scaleDetector.focusY)
                        currentScaleFactor = matrixValues[Matrix.MSCALE_X] / (initMatrixValues?.get(Matrix.MSCALE_X)
                            ?: 1f)
                    }

                    imageMatrix = mainMatrix
                    lastPoint.set(scaleDetector.focusX, scaleDetector.focusY)
                }


                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    scaleBy = 1f
                    resetImage()
                }
            }

            parent.requestDisallowInterceptTouchEvent(
                (scrollable && currPointerCount > 0 && !isInitScale())
                        || (zoomable && currPointerCount > 1)
            )

            //this tracks whether they have changed the number of fingers down
            prevPointerCount = currPointerCount

            return true
        }
        return false
    }

    /**
     * Get the current state of the image matrix, its values, and the bounds of the drawn bitmap
     */
    private fun actualizeMainMatrix() {
        mainMatrix.set(imageMatrix)
        mainMatrix.getValues(matrixValues)
        updateBounds(matrixValues)
    }

    private fun resetImage() {
        if (matrixValues[Matrix.MSCALE_X] <= (initMatrixValues?.get(Matrix.MSCALE_X) ?: 1f)) {
            reset()
        } else {
            center()
        }
    }


    /**
     * Update the bounds of the displayed image based on the current matrix.
     *
     * @param values the image's current matrix values.
     */
    private fun updateBounds(values: FloatArray) {
        drawable?.let {
            bounds.set(
                values[Matrix.MTRANS_X],
                values[Matrix.MTRANS_Y],
                it.intrinsicWidth * values[Matrix.MSCALE_X] + values[Matrix.MTRANS_X],
                it.intrinsicHeight * values[Matrix.MSCALE_Y] + values[Matrix.MTRANS_Y]
            )
        }
    }

    /**
     * Get the width of the displayed image.
     *
     * @return the current width of the image as displayed (not the width of the {@link ImageView} itself.
     */
    private val currentDisplayedWidth: Float
        get() =
            drawable?.let {
                it.intrinsicWidth * matrixValues[Matrix.MSCALE_X]
            } ?: 0f

    /**
     * Get the height of the displayed image.
     *
     * @return the current height of the image as displayed (not the height of the {@link ImageView} itself.
     */
    private val currentDisplayedHeight: Float
        get() =
            drawable?.let {
                it.intrinsicHeight * matrixValues[Matrix.MSCALE_Y]
            } ?: 0f

    private fun setStartValues() {
        initMatrixValues = FloatArray(9)
        initMatrix = Matrix(imageMatrix)
        initMatrix.getValues(initMatrixValues)
        initMinScale = minScale * (initMatrixValues?.get(Matrix.MSCALE_X) ?: 1f)
        initMaxScale = maxScale * (initMatrixValues?.get(Matrix.MSCALE_X) ?: 1f)
    }


    /**
     * This helps to keep the image on-screen by animating the translation to the nearest
     * edge, both vertically and horizontally.
     */
    private fun center() {
        if (autoCenter) {
            animateTranslationX()
            animateTranslationY()
        }
    }

    /**
     * Reset image back to its original size. Will snap back to original size
     * if animation on reset is disabled via [.setAnimateOnReset].
     */
    fun reset() = reset(animateOnReset)


    /**
     * Reset image back to its starting size. If `animate` is false, image
     * will snap back to its original size.
     *
     * @param animate animate the image back to its starting size
     */
    fun reset(animate: Boolean) {
        if (animate) {
            animateToStartMatrix()
        } else {
            imageMatrix = initMatrix
        }
    }

    /**
     * Animate the matrix back to its original position after the user stopped interacting with it.
     */
    private fun animateToStartMatrix() = animateScaleAndTranslationToMatrix(initMatrix, kResetDuration)


    private fun animateScaleAndTranslationToMatrix(targetMatrix: Matrix, duration: Int) {
        val targetValues = FloatArray(9)
        targetMatrix.getValues(targetValues)

        val beginMatrix = Matrix(imageMatrix)
        beginMatrix.getValues(matrixValues)

        //difference in current and original values
        val xsdiff = targetValues[Matrix.MSCALE_X] - matrixValues[Matrix.MSCALE_X]
        val ysdiff = targetValues[Matrix.MSCALE_Y] - matrixValues[Matrix.MSCALE_Y]
        val xtdiff = targetValues[Matrix.MTRANS_X] - matrixValues[Matrix.MTRANS_X]
        val ytdiff = targetValues[Matrix.MTRANS_Y] - matrixValues[Matrix.MTRANS_Y]

        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {

            val activeMatrix = Matrix(imageMatrix)
            val values = FloatArray(9)

            override fun onAnimationUpdate(animation: ValueAnimator) {
                val value = animation.animatedValue as Float
                activeMatrix.set(beginMatrix)
                activeMatrix.getValues(values)
                values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X] + xtdiff * value
                values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y] + ytdiff * value
                values[Matrix.MSCALE_X] = values[Matrix.MSCALE_X] + xsdiff * value
                values[Matrix.MSCALE_Y] = values[Matrix.MSCALE_Y] + ysdiff * value
                activeMatrix.setValues(values)
                imageMatrix = activeMatrix
            }
        })

        anim.addListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {}

            override fun onAnimationCancel(animation: Animator?) {}

            override fun onAnimationStart(animation: Animator?) {}

            override fun onAnimationEnd(animation: Animator) {
                imageMatrix = targetMatrix
            }
        })

        anim.duration = duration.toLong()
        anim.start()
    }

    private fun animateTranslationX() {
        if (currentDisplayedWidth > width) {
            //the left edge is too far to the interior
            if (bounds.left > 0) {
                animateMatrixIndex(Matrix.MTRANS_X, 0f)
            }
            //the right edge is too far to the interior
            else if (bounds.right < width) {
                animateMatrixIndex(Matrix.MTRANS_X, bounds.left + width - bounds.right)
            }
        } else {
            //left edge needs to be pulled in, and should be considered before the right edge
            if (bounds.left < 0) {
                animateMatrixIndex(Matrix.MTRANS_X, 0f)
            }
            //right edge needs to be pulled in
            else if (bounds.right > width) {
                animateMatrixIndex(Matrix.MTRANS_X, bounds.left + width - bounds.right)
            }
        }
    }

    private fun animateTranslationY() {
        if (currentDisplayedHeight > height) {
            //the top edge is too far to the interior
            if (bounds.top > 0) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0f)
            }
            //the bottom edge is too far to the interior
            else if (bounds.bottom < height) {
                animateMatrixIndex(Matrix.MTRANS_Y, bounds.top + height - bounds.bottom)
            }
        } else {
            //top needs to be pulled in, and needs to be considered before the bottom edge
            if (bounds.top < 0) {
                animateMatrixIndex(Matrix.MTRANS_Y, 0f)
            }
            //bottom edge needs to be pulled in
            else if (bounds.bottom > height) {
                animateMatrixIndex(Matrix.MTRANS_Y, bounds.top + height - bounds.bottom)
            }
        }
    }

    private fun animateMatrixIndex(index: Int, to: Float) {
        val animator = ValueAnimator.ofFloat(matrixValues[index], to)
        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {

            val values = FloatArray(9)
            var current = Matrix()

            override fun onAnimationUpdate(animation: ValueAnimator) {
                current.set(imageMatrix)
                current.getValues(values)
                values[index] = animation.animatedValue as Float
                current.setValues(values)
                imageMatrix = current
            }
        })
        animator.duration = kResetDuration.toLong()
        animator.start()
    }

    /**
     * Get the x distance to translate the current image.
     *
     * @param toX   the current x location of touch focus
     * @param fromX the last x location of touch focus
     * @return the distance to move the image,
     * will restrict the translation to keep the image on screen.
     */
    private fun getXDistance(toX: Float, fromX: Float): Float {
        var xdistance = toX - fromX

        if (restrictBounds) {
            xdistance = getRestrictedXDistance(xdistance)
        }

        //prevents image from translating an infinite distance offscreen
        if (bounds.right + xdistance < 0) {
            xdistance = -bounds.right
        } else if (bounds.left + xdistance > width) {
            xdistance = width - bounds.left
        }

        return xdistance
    }

    /**
     * Get the horizontal distance to translate the current image, but restrict
     * it to the outer bounds of the [ImageView]. If the current
     * image is smaller than the bounds, keep it within the current bounds.
     * If it is larger, prevent its edges from translating farther inward
     * from the outer edge.
     *
     * @param xdistance the current desired horizontal distance to translate
     * @return the actual horizontal distance to translate with bounds restrictions
     */
    private fun getRestrictedXDistance(xdistance: Float): Float {
        var restrictedXDistance = xdistance

        if (currentDisplayedWidth >= width) {
            if (bounds.left <= 0 && bounds.left + xdistance > 0 && !scaleDetector.isInProgress) {
                restrictedXDistance = -bounds.left
            } else if (bounds.right >= width && bounds.right + xdistance < width && !scaleDetector.isInProgress) {
                restrictedXDistance = width - bounds.right
            }
        } else if (!scaleDetector.isInProgress) {
            if (bounds.left >= 0 && bounds.left + xdistance < 0) {
                restrictedXDistance = -bounds.left
            } else if (bounds.right <= width && bounds.right + xdistance > width) {
                restrictedXDistance = width - bounds.right
            }
        }

        return restrictedXDistance
    }

    /**
     * Get the y distance to translate the current image.
     *
     * @param toY   the current y location of touch focus
     * @param fromY the last y location of touch focus
     * @return the distance to move the image,
     * will restrict the translation to keep the image on screen.
     */
    private fun getYDistance(toY: Float, fromY: Float): Float {
        var ydistance = toY - fromY

        if (restrictBounds) {
            ydistance = getRestrictedYDistance(ydistance)
        }

        //prevents image from translating an infinite distance offscreen
        if (bounds.bottom + ydistance < 0) {
            ydistance = -bounds.bottom
        } else if (bounds.top + ydistance > height) {
            ydistance = height - bounds.top
        }

        return ydistance
    }

    /**
     * Get the vertical distance to translate the current image, but restrict
     * it to the outer bounds of the [ImageView]. If the current
     * image is smaller than the bounds, keep it within the current bounds.
     * If it is larger, prevent its edges from translating farther inward
     * from the outer edge.
     *
     * @param ydistance the current desired vertical distance to translate
     * @return the actual vertical distance to translate with bounds restrictions
     */
    private fun getRestrictedYDistance(ydistance: Float): Float {
        var restrictedYDistance = ydistance

        if (currentDisplayedHeight >= height) {
            if (bounds.top <= 0 && bounds.top + ydistance > 0 && !scaleDetector.isInProgress) {
                restrictedYDistance = -bounds.top
            } else if (bounds.bottom >= height && bounds.bottom + ydistance < height && !scaleDetector.isInProgress) {
                restrictedYDistance = height - bounds.bottom
            }
        } else if (!scaleDetector.isInProgress) {
            if (bounds.top >= 0 && bounds.top + ydistance < 0) {
                restrictedYDistance = -bounds.top
            } else if (bounds.bottom <= height && bounds.bottom + ydistance > height) {
                restrictedYDistance = height - bounds.bottom
            }
        }

        return restrictedYDistance
    }

    private val scaleGestureListener = object : ScaleGestureDetector.OnScaleGestureListener {
        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            initScale = matrixValues[Matrix.MSCALE_X]
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector?) {
            scaleBy = 1f
        }

        override fun onScale(detector: ScaleGestureDetector?): Boolean {
            detector ?: return false
            scaleBy = (initScale * detector.scaleFactor) / matrixValues[Matrix.MSCALE_X]
            val projectedScale = scaleBy * matrixValues[Matrix.MSCALE_X]

            if (projectedScale < initMinScale) {
                scaleBy = initMinScale / matrixValues[Matrix.MSCALE_X]
            }
            if (projectedScale > initMaxScale) {
                scaleBy = initMaxScale / matrixValues[Matrix.MSCALE_X]
            }
            return false
        }

    }

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
            if (e?.action == MotionEvent.ACTION_UP) {
                doubleTapDetected = true
            }
            return false
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            singleTapDetected = true
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            singleTapDetected = false
            return false
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
    }


}
