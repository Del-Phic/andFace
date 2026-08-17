package dev.andface.galaxy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import dev.andface.galaxy.R
import kotlin.math.max

class LandmarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.andface_green)
        alpha = 185
        strokeWidth = 2.2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.andface_green)
        strokeWidth = 2.5f
        style = Paint.Style.FILL
    }
    private val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.andface_yellow)
        strokeWidth = 3.0f
        style = Paint.Style.FILL
    }

    private var landmarks: List<NormalizedLandmark> = emptyList()
    private var sourceImageWidth: Int = 0
    private var sourceImageHeight: Int = 0

    fun setLandmarks(landmarks: List<NormalizedLandmark>, imageWidth: Int = 0, imageHeight: Int = 0) {
        this.landmarks = landmarks
        sourceImageWidth = imageWidth
        sourceImageHeight = imageHeight
        invalidate()
    }

    fun clear() {
        landmarks = emptyList()
        sourceImageWidth = 0
        sourceImageHeight = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val transform = imageToViewTransform(width.toFloat(), height.toFloat())
        drawContours(canvas, transform)
        landmarks.forEachIndexed { index, landmark ->
            canvas.drawCircle(
                mappedX(landmark, transform),
                mappedY(landmark, transform),
                if (index >= IRIS_LANDMARK_START_INDEX) IRIS_LANDMARK_RADIUS else LANDMARK_RADIUS,
                if (index >= IRIS_LANDMARK_START_INDEX) irisPaint else landmarkPaint
            )
        }
    }

    private fun drawContours(canvas: Canvas, transform: ImageToViewTransform) {
        CONTOUR_PATHS.forEach { indices ->
            for (index in 0 until indices.lastIndex) {
                drawSegment(canvas, indices[index], indices[index + 1], transform)
            }
        }
    }

    private fun drawSegment(
        canvas: Canvas,
        firstIndex: Int,
        secondIndex: Int,
        transform: ImageToViewTransform
    ) {
        if (firstIndex !in landmarks.indices || secondIndex !in landmarks.indices) return
        val first = landmarks[firstIndex]
        val second = landmarks[secondIndex]
        canvas.drawLine(
            mappedX(first, transform),
            mappedY(first, transform),
            mappedX(second, transform),
            mappedY(second, transform),
            contourPaint
        )
    }

    private fun mappedX(landmark: NormalizedLandmark, transform: ImageToViewTransform): Float {
        return transform.offsetX + landmark.x() * transform.drawnImageWidth
    }

    private fun mappedY(landmark: NormalizedLandmark, transform: ImageToViewTransform): Float {
        return transform.offsetY + landmark.y() * transform.drawnImageHeight
    }

    private fun imageToViewTransform(viewWidth: Float, viewHeight: Float): ImageToViewTransform {
        val imageWidth = sourceImageWidth.takeIf { it > 0 }?.toFloat() ?: viewWidth
        val imageHeight = sourceImageHeight.takeIf { it > 0 }?.toFloat() ?: viewHeight
        val scale = max(viewWidth / imageWidth, viewHeight / imageHeight)
        val drawnImageWidth = imageWidth * scale
        val drawnImageHeight = imageHeight * scale
        return ImageToViewTransform(
            drawnImageWidth = drawnImageWidth,
            drawnImageHeight = drawnImageHeight,
            offsetX = (viewWidth - drawnImageWidth) / 2f,
            offsetY = (viewHeight - drawnImageHeight) / 2f
        )
    }

    private data class ImageToViewTransform(
        val drawnImageWidth: Float,
        val drawnImageHeight: Float,
        val offsetX: Float,
        val offsetY: Float
    )

    companion object {
        private const val IRIS_LANDMARK_START_INDEX = 468
        private const val LANDMARK_RADIUS = 2.3f
        private const val IRIS_LANDMARK_RADIUS = 3.4f

        private val FACE_OVAL = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397,
            365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58,
            132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10
        )
        private val LEFT_EYE = intArrayOf(
            33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159,
            160, 161, 246, 33
        )
        private val RIGHT_EYE = intArrayOf(
            362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387,
            386, 385, 384, 398, 362
        )
        private val LEFT_BROW = intArrayOf(70, 63, 105, 66, 107)
        private val RIGHT_BROW = intArrayOf(336, 296, 334, 293, 300)
        private val NOSE_BRIDGE = intArrayOf(168, 6, 197, 195, 5, 4, 1, 19, 94, 2)
        private val NOSE_BASE = intArrayOf(98, 97, 2, 326, 327)
        private val OUTER_LIP = intArrayOf(
            61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270,
            269, 267, 0, 37, 39, 40, 185, 61
        )
        private val LEFT_IRIS = intArrayOf(468, 469, 470, 471, 472, 468)
        private val RIGHT_IRIS = intArrayOf(473, 474, 475, 476, 477, 473)

        private val CONTOUR_PATHS = listOf(
            FACE_OVAL,
            LEFT_EYE,
            RIGHT_EYE,
            LEFT_BROW,
            RIGHT_BROW,
            NOSE_BRIDGE,
            NOSE_BASE,
            OUTER_LIP,
            LEFT_IRIS,
            RIGHT_IRIS
        )
    }
}
