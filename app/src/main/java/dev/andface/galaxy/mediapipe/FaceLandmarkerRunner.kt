package dev.andface.galaxy.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class FaceLandmarkerRunner(
    context: Context,
    private val listener: Listener
) {
    private val appContext = context.applicationContext
    private var faceLandmarker: FaceLandmarker? = null
    private val frameInFlight = AtomicBoolean(false)
    private val pendingFrames = ConcurrentHashMap<Long, MPImage>()
    @Volatile private var closed = false
    private var lastSubmittedTimestampMs = Long.MIN_VALUE
    private val pendingRotations = ConcurrentHashMap<Long, Int>()
    private var lastEmptyDebugAtMs = 0L
    private var lastFrameDebugAtMs = 0L
    private var lastFrameErrorAtMs = 0L
    private var lastResultDebugAtMs = 0L

    init {
        setup()
    }

    fun close() {
        // Serialize detachment with submission; never hold this monitor while
        // native close waits for callback completion.
        val runner = synchronized(this) {
            if (closed) return
            closed = true
            faceLandmarker.also { faceLandmarker = null }
        }
        try {
            runner?.close()
        } finally {
            closeAllPendingFrames()
            frameInFlight.set(false)
        }
    }

    @Synchronized
    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val runner = faceLandmarker
        if (closed || runner == null || !frameInFlight.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val frameTimeMs = maxOf(SystemClock.uptimeMillis(), lastSubmittedTimestampMs + 1L)
        lastSubmittedTimestampMs = frameTimeMs
        var unownedBitmap: Bitmap? = null
        try {
            val frameRotation: Int
            val bitmap: Bitmap
            try {
                frameRotation = imageProxy.imageInfo.rotationDegrees
                bitmap = imageProxy.toBitmap().rotateForMediaPipe(frameRotation)
            } finally {
                // The bitmap owns a copy; release CameraX before async inference.
                imageProxy.close()
            }
            unownedBitmap = bitmap
            val mpImage = BitmapImageBuilder(bitmap).build()
            pendingFrames[frameTimeMs] = mpImage
            unownedBitmap = null
            val mediaPipeRotation = 0
            pendingRotations[frameTimeMs] = mediaPipeRotation
            val processingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(mediaPipeRotation)
                .build()
            logFrameSubmitted(frameTimeMs, bitmap.width, bitmap.height, frameRotation, mediaPipeRotation, isFrontCamera)
            runner.detectAsync(mpImage, processingOptions, frameTimeMs)
        } catch (error: RuntimeException) {
            unownedBitmap?.recycle()
            closePendingFrame(frameTimeMs)
            frameInFlight.set(false)
            logFrameError("MediaPipe detectAsync failed: ${error.message}")
            if (!closed) listener.onError(error.message ?: "MediaPipe detectAsync failed.")
        }
    }

    private fun setup() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(MAX_FACES_FOR_SECURITY_CHECK)
                .setMinFaceDetectionConfidence(0.35f)
                .setMinFacePresenceConfidence(0.35f)
                .setMinTrackingConfidence(0.35f)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .setResultListener(this::onResult)
                .setErrorListener(this::onError)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(appContext, options)
            listener.onReady()
        } catch (error: RuntimeException) {
            listener.onError(error.message ?: "MediaPipe Face Landmarker failed to initialize.")
        }
    }

    private fun onResult(result: FaceLandmarkerResult, input: MPImage) {
        val ownedImage = pendingFrames.remove(result.timestampMs()) ?: return
        val imageRotationDegrees = pendingRotations.remove(result.timestampMs()) ?: 0
        try {
            if (closed) return
            if (result.faceLandmarks().isEmpty()) {
                logEmptyResult(input)
                listener.onEmpty(result.timestampMs())
                return
            }
            logResult(input, result)
            val inferenceTimeMs = (SystemClock.uptimeMillis() - result.timestampMs()).coerceAtLeast(0L)
            listener.onResults(
                ResultBundle(
                    result = result,
                    inferenceTimeMs = inferenceTimeMs,
                    inputImageWidth = input.width,
                    inputImageHeight = input.height,
                    inputRotationDegrees = imageRotationDegrees
                )
            )
        } finally {
            ownedImage.close()
            frameInFlight.set(false)
        }
    }

    private fun onError(error: RuntimeException) {
        closeAllPendingFrames()
        frameInFlight.set(false)
        if (!closed) listener.onError(error.message ?: "MediaPipe Face Landmarker runtime error.")
    }

    private fun closePendingFrame(timestampMs: Long) {
        pendingRotations.remove(timestampMs)
        pendingFrames.remove(timestampMs)?.close()
    }

    private fun closeAllPendingFrames() {
        pendingFrames.keys.toList().forEach(::closePendingFrame)
        pendingRotations.clear()
    }

    private fun Bitmap.rotateForMediaPipe(rotationDegrees: Int): Bitmap {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return this
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return try {
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { rotated ->
                if (rotated !== this) recycle()
            }
        } catch (error: RuntimeException) {
            recycle()
            throw error
        }
    }

    private fun logFrameSubmitted(timestampMs: Long, width: Int, height: Int, frameRotation: Int, mediaPipeRotation: Int, front: Boolean) {
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - lastFrameDebugAtMs < DEBUG_LOG_INTERVAL_MS) return
        lastFrameDebugAtMs = nowMs
        Log.d(TAG, "submit ts=$timestampMs image=${width}x$height frameRotation=$frameRotation mediaPipeRotation=$mediaPipeRotation front=$front")
    }

    private fun logEmptyResult(input: MPImage) {
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - lastEmptyDebugAtMs < DEBUG_LOG_INTERVAL_MS) return
        lastEmptyDebugAtMs = nowMs
        Log.d(TAG, "empty result image=${input.width}x${input.height}")
    }

    private fun logFrameError(message: String) {
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - lastFrameErrorAtMs < DEBUG_LOG_INTERVAL_MS) return
        lastFrameErrorAtMs = nowMs
        Log.w(TAG, message)
    }

    private fun logResult(input: MPImage, result: FaceLandmarkerResult) {
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - lastResultDebugAtMs < DEBUG_LOG_INTERVAL_MS) return
        lastResultDebugAtMs = nowMs
        Log.d(TAG, "result faces=${result.faceLandmarks().size} landmarks=${result.faceLandmarks().first().size} image=${input.width}x${input.height}")
    }

    data class ResultBundle(
        val result: FaceLandmarkerResult,
        val inferenceTimeMs: Long,
        val inputImageWidth: Int,
        val inputImageHeight: Int,
        val inputRotationDegrees: Int
    )

    interface Listener {
        fun onReady()
        fun onResults(resultBundle: ResultBundle)
        fun onEmpty(timestampMs: Long)
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "AndFaceLandmarker"
        const val MODEL_ASSET_PATH = "face_landmarker.task"
        private const val MAX_FACES_FOR_SECURITY_CHECK = 2
        private const val DEBUG_LOG_INTERVAL_MS = 1_000L
    }
}






