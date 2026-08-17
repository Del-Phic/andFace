package dev.andface.galaxy.mediapipe

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaPipeCameraIntegrationContractTest {
    @Test
    fun faceLandmarkerUsesLiveStreamModeWithAsyncCallbacks() {
        val source = readSource("app/src/main/java/dev/andface/galaxy/mediapipe/FaceLandmarkerRunner.kt")

        assertTrue(source.contains(".setRunningMode(RunningMode.LIVE_STREAM)"))
        assertTrue(source.contains(".setNumFaces(MAX_FACES_FOR_SECURITY_CHECK)"))
        assertTrue(source.contains(".setOutputFaceBlendshapes(true)"))
        assertTrue(source.contains("MAX_FACES_FOR_SECURITY_CHECK = 2"))
        assertTrue(source.contains(".setResultListener(this::onResult)"))
        assertTrue(source.contains(".setErrorListener(this::onError)"))
        assertTrue(source.contains("runner.detectAsync(mpImage, processingOptions, frameTimeMs)"))
        assertTrue(source.contains("listener.onEmpty()"))
        assertTrue(source.contains("BitmapImageBuilder(bitmap).build()"))
        assertTrue(source.contains("ImageProcessingOptions.builder()"))
        assertTrue(source.contains("imageProxy.imageInfo.rotationDegrees"))
        assertTrue(source.contains("rotateForMediaPipe(frameRotation)"))
        assertTrue(source.contains("val mediaPipeRotation = 0"))
        assertTrue(source.contains(".setRotationDegrees(mediaPipeRotation)"))
        assertTrue(source.contains("imageProxy.toBitmap()"))
    }

    @Test
    fun mainActivityBindsFrontCameraPreviewAndLatestFrameAnalyzer() {
        val source = readSource("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertTrue(source.contains("class MainActivity : ComponentActivity(), FaceLandmarkerRunner.Listener"))
        assertTrue(source.contains("CameraSelector.LENS_FACING_FRONT"))
        assertTrue(source.contains("Preview.Builder()"))
        assertTrue(source.contains("ImageAnalysis.Builder()"))
        assertTrue(source.contains("ResolutionSelector.Builder()"))
        assertTrue(source.contains("AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY"))
        assertTrue(source.contains("ResolutionStrategy("))
        assertTrue(source.contains("Size(1280, 960)"))
        assertTrue(source.contains("Size(640, 480)"))
        assertTrue(source.contains("logAnalyzerFrame(imageProxy)"))
        assertTrue(source.contains(".setResolutionSelector(cameraResolutionSelector)"))
        assertTrue("CameraX should avoid deprecated target aspect ratio APIs", !source.contains("setTargetAspectRatio"))
        assertTrue(source.contains("ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST"))
        assertTrue(
            "CameraX should use the default YUV analysis stream and convert frames to RGBA Bitmap before MediaPipe",
            !source.contains("ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888")
        )
        assertTrue(source.contains("runner.detectLiveStream("))
        assertTrue(source.contains("faceBlendshapes = firstFaceBlendshapes(resultBundle.result)"))
        assertTrue(source.contains("isFrontCamera = true"))
        assertTrue(source.contains("cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)"))
    }

    @Test
    fun mainActivityFailsClosedWhenMultipleFacesAreDetected() {
        val source = readSource("app/src/main/java/dev/andface/galaxy/MainActivity.kt")

        assertTrue(source.contains("val detectedFaces = resultBundle.result.faceLandmarks()"))
        assertTrue(source.contains("if (detectedFaces.size > 1)"))
        assertTrue(source.contains("handleMultipleFaces()"))
        assertTrue(source.contains("FailureReason.MULTIPLE_FACES"))
    }

    @Test
    fun landmarkOverlayRendersOptionalIrisLandmarksWhenPresent() {
        val source = readSource("app/src/main/java/dev/andface/galaxy/ui/LandmarkOverlayView.kt")

        assertTrue(source.contains("this.landmarks = landmarks"))
        assertTrue(source.contains("IRIS_LANDMARK_START_INDEX = 468"))
        assertTrue(source.contains("irisPaint"))
        assertTrue(source.contains("forEachIndexed"))
        assertTrue(source.contains("index >= IRIS_LANDMARK_START_INDEX"))
        assertTrue("overlay must not truncate optional iris landmarks", !source.contains("take(RENDERED_LANDMARK_COUNT)"))
    }

    private fun readSource(path: String): String {
        val file = listOf(
            File(path),
            File("../$path"),
            File("../../$path")
        ).firstOrNull { candidate -> candidate.isFile }

        requireNotNull(file) { "$path was not found from test working directory." }
        return file.readText()
    }
}





