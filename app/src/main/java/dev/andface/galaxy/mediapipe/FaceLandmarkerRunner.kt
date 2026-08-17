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
    private val pendingFrames = ConcurrentHashMap<Long, ImageProxy>()
    private val pendingRotations = ConcurrentHashMap<Long, Int>()
    private var lastEmptyDebugAtMs = 0L
    private var lastFrameDebugAtMs = 0L
    private var lastFrameErrorAtMs = 0L
    private var lastResultDebugAtMs = 0L

    init {
        setup()
    }

    fun close() {
        pendingFrames.values.forEach { proxy -> proxy.close() }
        pendingFrames.clear()
        pendingRotations.clear()
        frameInFlight.set(false)
        faceLandmarker?.close()
        faceLandmarker = null
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val runner = faceLandmarker
        if (runner == null) {
            imageProxy.close()
            listener.onError("Face Landmarker is not ready.")
            return
        }
        if (!frameInFlight.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val frameTimeMs = SystemClock.uptimeMillis()
        val frameRotation = imageProxy.imageInfo.rotationDegrees
        val mediaPipeRotation = 0
        pendingFrames[frameTimeMs] = imageProxy
        pendingRotations[frameTimeMs] = mediaPipeRotation

        try {
            val bitmap = imageProxy.toBitmap().rotateForMediaPipe(frameRotation)
            val mpImage = BitmapImageBuilder(bitmap).build()
            val processingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(mediaPipeRotation)
                .build()
            logFrameSubmitted(frameTimeMs, bitmap.width, bitmap.height, frameRotation, mediaPipeRotation, isFrontCamera)
            runner.detectAsync(mpImage, processingOptions, frameTimeMs)
        } catch (error: RuntimeException) {
            closePendingFrame(frameTimeMs)
            frameInFlight.set(false)
            logFrameError("MediaPipe detectAsync failed: ${error.message}")
            listener.onError(error.message ?: "MediaPipe detectAsync failed.")
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
        val imageRotationDegrees = pendingRotations[result.timestampMs()] ?: 0
        closePendingFrame(result.timestampMs())
        frameInFlight.set(false)
        if (result.faceLandmarks().isEmpty()) {
            logEmptyResult(input)
            listener.onEmpty()
            return
        }
        logResult(input, result)
        val inferenceTimeMs = SystemClock.uptimeMillis() - result.timestampMs()
        listener.onResults(
            ResultBundle(
                result = result,
                inferenceTimeMs = inferenceTimeMs,
                inputImageWidth = input.width,
                inputImageHeight = input.height,
                inputRotationDegrees = imageRotationDegrees
            )
        )
    }

    private fun onError(error: RuntimeException) {
        closeAllPendingFrames()
        frameInFlight.set(false)
        listener.onError(error.message ?: "MediaPipe Face Landmarker runtime error.")
    }

    private fun closePendingFrame(timestampMs: Long) {
        pendingRotations.remove(timestampMs)
        pendingFrames.remove(timestampMs)?.close()
    }

    private fun closeAllPendingFrames() {
        pendingFrames.values.forEach { proxy -> proxy.close() }
        pendingFrames.clear()
        pendingRotations.clear()
    }

    private fun Bitmap.rotateForMediaPipe(rotationDegrees: Int): Bitmap {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return this
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
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
        fun onEmpty()
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "AndFaceLandmarker"
        const val MODEL_ASSET_PATH = "face_landmarker.task"
        private const val MAX_FACES_FOR_SECURITY_CHECK = 2
        private const val DEBUG_LOG_INTERVAL_MS = 1_000L
    }
}






