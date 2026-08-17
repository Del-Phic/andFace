package dev.andface.galaxy

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import dev.andface.galaxy.access.AssetIntegrityPolicy
import dev.andface.galaxy.access.DedicatedTerminalPolicy
import dev.andface.galaxy.audit.SecurityAuditLogger
import dev.andface.galaxy.auth.AccessLockoutStore
import dev.andface.galaxy.auth.AuthDecision
import dev.andface.galaxy.auth.AuthDisplayStabilizer
import dev.andface.galaxy.auth.AuthResult
import dev.andface.galaxy.auth.AuthSuccessWindowController
import dev.andface.galaxy.auth.AuthenticationEngine
import dev.andface.galaxy.auth.EnrollmentCapturePolicy
import dev.andface.galaxy.auth.FailureReason
import dev.andface.galaxy.enrollment.EnrollmentBuilder
import dev.andface.galaxy.enrollment.EnrollmentInventoryStore
import dev.andface.galaxy.enrollment.EnrollmentProfile
import dev.andface.galaxy.enrollment.EnrollmentRepository
import dev.andface.galaxy.enrollment.EnrollmentSecurityPolicy
import dev.andface.galaxy.feature.FaceFeatureExtractor
import dev.andface.galaxy.feature.FeatureType
import dev.andface.galaxy.feature.RawFeatureFrame
import dev.andface.galaxy.mediapipe.FaceLandmarkerRunner
import dev.andface.galaxy.occlusion.OcclusionHint
import dev.andface.galaxy.ui.LandmarkOverlayView
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : ComponentActivity(), FaceLandmarkerRunner.Listener {
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: LandmarkOverlayView
    private lateinit var userLabel: TextView
    private lateinit var resultText: TextView
    private lateinit var failureReasonText: TextView
    private lateinit var modelStateText: TextView
    private lateinit var fuzzyScoreText: TextView
    private lateinit var mahalanobisScoreText: TextView
    private lateinit var finalScoreText: TextView
    private lateinit var matchedUserText: TextView
    private lateinit var registeredUsersText: TextView
    private lateinit var coverageText: TextView
    private lateinit var marginText: TextView
    private lateinit var livenessText: TextView
    private lateinit var observableText: TextView
    private lateinit var occlusionText: TextView
    private lateinit var registerButton: Button
    private lateinit var clearButton: Button
    private lateinit var userSelector: RadioGroup
    private lateinit var lowerFaceHint: CheckBox
    private lateinit var glassesHint: CheckBox
    private lateinit var leftPatchHint: CheckBox
    private lateinit var rightPatchHint: CheckBox

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var analysisExecutor: ExecutorService
    private lateinit var repository: EnrollmentRepository
    private lateinit var profileInventoryStore: EnrollmentInventoryStore
    private lateinit var auditLogger: SecurityAuditLogger
    private lateinit var accessLockoutStore: AccessLockoutStore

    private val featureExtractor = FaceFeatureExtractor()
    private val authEngine = AuthenticationEngine(
        securityClockMs = { SystemClock.elapsedRealtime() },
        onAccessLockout = { untilMs ->
            if (::accessLockoutStore.isInitialized && !accessLockoutStore.save(untilMs)) {
                accessLockoutStorageFailed = true
            }
        },
        onRiskyFailureStateChanged = { state ->
            if (::accessLockoutStore.isInitialized) {
                val saved = if (state == null) {
                    accessLockoutStore.clearRiskyFailureWindow()
                } else {
                    accessLockoutStore.saveRiskyFailureWindow(state)
                }
                if (!saved) accessLockoutStorageFailed = true
            }
        }
    )
    private val authSuccessWindowController = AuthSuccessWindowController(
        clockMs = { SystemClock.elapsedRealtime() },
        reissueCooldownMs = AUTH_SUCCESS_WINDOW_REISSUE_COOLDOWN_MS
    )
    private val authDisplayStabilizer = AuthDisplayStabilizer(
        clockMs = { SystemClock.elapsedRealtime() }
    )
    private val enrollmentSamples = mutableListOf<RawFeatureFrame>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var faceLandmarkerRunner: FaceLandmarkerRunner? = null

    @Volatile
    private var acceptingLiveFrames = false

    private var collectingEnrollment = false
    private var selectedUserId = EnrollmentRepository.SUPPORTED_USER_IDS.first()
    private var lastRenderedResult: AuthResult? = null
    private var storageFailedUserIds: Set<String> = emptySet()
    private var profileInventoryMismatchUserIds: Set<String> = emptySet()
    private var expiredUserIds: Set<String> = emptySet()
    private var profileInventoryFailed = false
    private var accessLockoutStorageFailed = false
    private var auditStorageFailed = false
    private var modelAssetIntegrityFailed = false
    private var detailedMetricsVisibleUntilMs = 0L
    private var enrollmentStartedAtMs = Long.MIN_VALUE
    private var enrollmentNoFaceSinceMs = 0L
    private var lastEnrollmentSampleAcceptedAtMs = Long.MIN_VALUE
    private var lastAnalyzerDebugAtMs = 0L
    private var lastFacePipelineDebugAtMs = 0L
    private var lastAuthDecisionDebugAtMs = 0L

    private val enrollmentTimeoutRunnable = Runnable {
        if (collectingEnrollment) {
            abortEnrollment(FailureReason.UNSTABLE_ENROLLMENT, "\uB4F1\uB85D \uC2DC\uAC04 \uCD08\uACFC")
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraWhenReady() else renderCameraPermissionFailure()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        configureSecureWindow()
        setContentView(R.layout.activity_main)
        bindViews()
        configureObscuredTouchProtection()

        repository = EnrollmentRepository(this)
        profileInventoryStore = EnrollmentInventoryStore(this)
        auditLogger = SecurityAuditLogger(this)
        accessLockoutStore = AccessLockoutStore(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        analysisExecutor = Executors.newSingleThreadExecutor()
        acceptingLiveFrames = true

        modelAssetIntegrityFailed = !verifyModelAssetIntegrity()
        restorePersistedAccessLock()
        if (!reloadProfilesFromStorage()) {
            renderAuthResult(AuthResult.failed(FailureReason.SECURE_STORAGE_ERROR))
        }

        registerButton.setOnClickListener {
            if (collectingEnrollment) cancelEnrollmentByUser() else beginEnrollment(selectedUserId)
        }
        clearButton.setOnClickListener { showClearConfirmation(selectedUserId) }
        modelStateText.setOnLongClickListener {
            revealDetailedMetrics()
            true
        }
        findViewById<View>(R.id.dashboard).setOnLongClickListener {
            copyCurrentFieldCsvRowToClipboard()
            true
        }
        userSelector.setOnCheckedChangeListener { _, checkedId ->
            selectedUserId = when (checkedId) {
                R.id.user2Radio -> "USER_2"
                R.id.user3Radio -> "USER_3"
                else -> "USER_1"
            }
            updateSelectedUserUi()
        }
        updateSelectedUserUi()

        if (modelAssetIntegrityFailed) {
            renderAuthResult(AuthResult.failed(FailureReason.MODEL_NOT_READY))
            failureReasonText.text = "\uBAA8\uB378 \uBB34\uACB0\uC131 \uAC80\uC99D \uC2E4\uD328"
            return
        }

        cameraExecutor.execute {
            faceLandmarkerRunner = FaceLandmarkerRunner(this, this)
        }
        if (hasCameraPermission()) {
            startCameraWhenReady()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::repository.isInitialized) resetTransientAccessState(FailureReason.SESSION_RESET)
    }

    override fun onResume() {
        super.onResume()
        acceptingLiveFrames = true
        enterManagedLockTaskIfPermitted()
        if (::repository.isInitialized) handleGlobalAccessFailureIfNeeded()
    }

    override fun onPause() {
        acceptingLiveFrames = false
        if (::repository.isInitialized) resetTransientAccessState(FailureReason.SESSION_RESET)
        super.onPause()
    }

    override fun onDestroy() {
        acceptingLiveFrames = false
        clearEnrollmentTimers()
        authSuccessWindowController.clear()
        faceLandmarkerRunner?.close()
        faceLandmarkerRunner = null
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        acceptingLiveFrames = hasFocus
        if (!hasFocus && ::repository.isInitialized) {
            authEngine.resetLiveSession()
            overlayView.clear()
        } else if (hasFocus && ::repository.isInitialized) {
            handleGlobalAccessFailureIfNeeded()
        }
    }

    override fun onReady() {
        runOnUiThread { modelStateText.text = appRevisionLabel() }
    }

    override fun onResults(resultBundle: FaceLandmarkerRunner.ResultBundle) {
        if (!acceptingLiveFrames) return
        val detectedFaces = resultBundle.result.faceLandmarks()
        if (detectedFaces.isEmpty()) {
            onEmpty()
            return
        }
        if (detectedFaces.size > 1) {
            runOnUiThread { handleMultipleFaces() }
            return
        }

        val faceBlendshapes = firstFaceBlendshapes(resultBundle.result)
        val candidate = chooseBestLandmarkCandidate(
            resultBundle = resultBundle,
            landmarks = detectedFaces.first(),
            facialTransformMatrix = firstTransformationMatrix(resultBundle.result),
            faceBlendshapes = faceBlendshapes
        )
        logFacePipelineDebug(resultBundle, candidate)
        runOnUiThread {
            if (!acceptingLiveFrames || handleGlobalAccessFailureIfNeeded()) return@runOnUiThread
            renderLandmarkOverlay(
                candidate.landmarks,
                candidate.overlayImageWidth,
                candidate.overlayImageHeight
            )
            val rawFrame = candidate.rawFrame
            if (rawFrame == null) {
                authEngine.resetLiveSession()
                if (collectingEnrollment) {
                    val nowMs = SystemClock.elapsedRealtime()
                    if (!abortEnrollmentIfTimedOut(nowMs)) {
                        renderEnrollmentCollectionProgress(
                            enrollmentRetryGuidance(FailureReason.TOO_FEW_FEATURES),
                            nowMs
                        )
                    }
                } else {
                    renderAuthResult(AuthResult.failed(FailureReason.TOO_FEW_FEATURES))
                }
            } else {
                handleFeatureFrame(rawFrame)
            }
        }
    }

    override fun onEmpty() {
        if (!acceptingLiveFrames) return
        runOnUiThread {
            if (!acceptingLiveFrames) return@runOnUiThread
            overlayView.clear()
            if (handleGlobalAccessFailureIfNeeded()) return@runOnUiThread
            if (collectingEnrollment) {
                val nowMs = SystemClock.elapsedRealtime()
                if (enrollmentNoFaceSinceMs == 0L) enrollmentNoFaceSinceMs = nowMs
                if (!abortEnrollmentIfTimedOut(nowMs)) {
                    renderEnrollmentCollectionProgress(
                        "\uC5BC\uAD74 \uAC80\uCD9C \uB300\uAE30 \uC911 - \uD654\uBA74 \uC911\uC559\uC744 \uBC14\uB77C\uBD10 \uC8FC\uC138\uC694",
                        nowMs
                    )
                }
            } else {
                authEngine.resetLiveSession()
                renderAuthResult(AuthResult.failed(FailureReason.NO_FACE))
            }
        }
    }

    override fun onError(message: String) {
        if (!acceptingLiveFrames) return
        runOnUiThread {
            if (!acceptingLiveFrames) return@runOnUiThread
            authEngine.resetLiveSession()
            modelStateText.text = "ERROR"
            if (collectingEnrollment) {
                abortEnrollment(FailureReason.MODEL_NOT_READY, message)
            } else {
                renderAuthResult(AuthResult.failed(FailureReason.MODEL_NOT_READY))
                failureReasonText.text = message
            }
        }
    }

    private fun handleMultipleFaces() {
        if (!acceptingLiveFrames || handleGlobalAccessFailureIfNeeded()) return
        overlayView.clear()
        authEngine.resetLiveSession()
        if (collectingEnrollment) {
            abortEnrollment(FailureReason.MULTIPLE_FACES, "\uC5BC\uAD74\uC774 \uC5EC\uB7EC \uBA85 \uAC80\uCD9C\uB428")
        } else {
            val result = AuthResult.failed(FailureReason.MULTIPLE_FACES)
            recordAuthenticationAudit(result)
            renderAuthResult(result)
        }
    }

    private fun bindViews() {
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.landmarkOverlay)
        userLabel = findViewById(R.id.userLabel)
        resultText = findViewById(R.id.resultText)
        failureReasonText = findViewById(R.id.failureReasonText)
        modelStateText = findViewById(R.id.modelStateText)
        fuzzyScoreText = findViewById(R.id.fuzzyScoreText)
        mahalanobisScoreText = findViewById(R.id.mahalanobisScoreText)
        finalScoreText = findViewById(R.id.finalScoreText)
        matchedUserText = findViewById(R.id.matchedUserText)
        registeredUsersText = findViewById(R.id.registeredUsersText)
        coverageText = findViewById(R.id.coverageText)
        marginText = findViewById(R.id.marginText)
        livenessText = findViewById(R.id.livenessText)
        observableText = findViewById(R.id.observableText)
        occlusionText = findViewById(R.id.occlusionText)
        registerButton = findViewById(R.id.registerButton)
        clearButton = findViewById(R.id.clearButton)
        userSelector = findViewById(R.id.userSelector)
        lowerFaceHint = findViewById(R.id.lowerFaceHint)
        glassesHint = findViewById(R.id.glassesHint)
        leftPatchHint = findViewById(R.id.leftPatchHint)
        rightPatchHint = findViewById(R.id.rightPatchHint)
    }

    private fun configureObscuredTouchProtection() {
        listOf<View>(
            registerButton,
            clearButton,
            userSelector,
            lowerFaceHint,
            glassesHint,
            leftPatchHint,
            rightPatchHint
        ).forEach { it.filterTouchesWhenObscured = true }
    }

    private fun beginEnrollment(userId: String) {
        selectUser(userId)
        clearOcclusionHintsForEnrollment()
        authDisplayStabilizer.reset()
        enrollmentSamples.clear()
        authEngine.resetLiveSession()
        collectingEnrollment = true
        enrollmentStartedAtMs = SystemClock.elapsedRealtime()
        enrollmentNoFaceSinceMs = 0L
        lastEnrollmentSampleAcceptedAtMs = Long.MIN_VALUE
        mainHandler.postDelayed(enrollmentTimeoutRunnable, ENROLLMENT_SESSION_TIMEOUT_MS)
        registerButton.isEnabled = true
        registerButton.text = "\uB4F1\uB85D \uCDE8\uC18C"
        clearButton.isEnabled = false
        setUserSelectionEnabled(false)
        setOcclusionHintsEnabled(false)
        userLabel.text = "\uB4F1\uB85D ${displayUserId(userId)}"
        renderEnrollmentCollectionProgress(
            "\uB4F1\uB85D \uC900\uBE44 \uC911 - \uC5BC\uAD74\uC744 \uC815\uBA74\uC73C\uB85C \uBC14\uB77C\uBD10 \uC8FC\uC138\uC694",
            enrollmentStartedAtMs
        )
    }

    private fun cancelEnrollmentByUser() {
        if (!collectingEnrollment) return
        enrollmentSamples.clear()
        collectingEnrollment = false
        clearEnrollmentTimers()
        authEngine.resetLiveSession()
        registerButton.isEnabled = true
        setUserSelectionEnabled(true)
        setOcclusionHintsEnabled(true)
        updateSelectedUserUi()
        resultText.text = "\uB4F1\uB85D \uCDE8\uC18C"
        resultText.setTextColor(getColor(R.color.andface_muted))
        failureReasonText.text = "\uB4F1\uB85D\uC744 \uCDE8\uC18C\uD588\uC2B5\uB2C8\uB2E4."
    }

    private fun showEnrollmentConfirmation(userId: String) {
        if (collectingEnrollment) return
        selectUser(userId)
        val userName = displayUserId(userId)
        val exists = repository.loadProfileResult(userId).profile != null
        AlertDialog.Builder(this)
            .setTitle(if (exists) "$userName \uC7AC\uB4F1\uB85D" else "$userName \uB4F1\uB85D")
            .setMessage("\uB9C8\uC2A4\uD06C, \uC548\uACBD, \uC548\uB300\uB97C \uBC97\uACE0 \uAE68\uB057\uD55C \uC5BC\uAD74\uB85C \uB4F1\uB85D\uD558\uC138\uC694.")
            .setNegativeButton("\uCDE8\uC18C", null)
            .setPositiveButton(if (exists) "\uC7AC\uB4F1\uB85D" else "\uB4F1\uB85D") { _, _ -> beginEnrollment(userId) }
            .show()
    }

    private fun showClearConfirmation(userId: String) {
        if (collectingEnrollment) return
        selectUser(userId)
        val profile = repository.loadProfileResult(userId).profile
        if (profile == null && userId !in storageFailedUserIds && userId !in profileInventoryMismatchUserIds) {
            failureReasonText.text = "${displayUserId(userId)} \uB4F1\uB85D \uC815\uBCF4\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("\uB4F1\uB85D \uC0AD\uC81C")
            .setMessage("${displayUserId(userId)} \uB4F1\uB85D \uC815\uBCF4\uB97C \uC0AD\uC81C\uD569\uB2C8\uB2E4.")
            .setNegativeButton("\uCDE8\uC18C", null)
            .setPositiveButton("\uC0AD\uC81C") { _, _ -> clearSelectedUserProfile(userId) }
            .show()
    }

    private fun clearSelectedUserProfile(userId: String) {
        val cleared = repository.clear(userId)
        val audited = cleared && auditLogger.recordProfileCleared(userId)
        val inventorySaved = audited && saveProfileInventoryFromStorage()
        val reloaded = inventorySaved && reloadProfilesFromStorage()
        if (!reloaded) {
            renderAuthResult(AuthResult.failed(FailureReason.SECURE_STORAGE_ERROR))
            return
        }
        enrollmentSamples.clear()
        collectingEnrollment = false
        clearEnrollmentTimers()
        authDisplayStabilizer.reset()
        updateSelectedUserUi()
        renderAuthResult(
            AuthResult.failed(
                if (authEngine.profiles.isEmpty()) FailureReason.NO_ENROLLMENT else FailureReason.LOW_SCORE
            )
        )
    }

    private fun selectUser(userId: String) {
        selectedUserId = userId
        val radioId = when (userId) {
            "USER_2" -> R.id.user2Radio
            "USER_3" -> R.id.user3Radio
            else -> R.id.user1Radio
        }
        if (userSelector.checkedRadioButtonId != radioId) userSelector.check(radioId) else updateSelectedUserUi()
    }

    private fun reloadProfilesFromStorage(): Boolean {
        val result = repository.loadProfilesResult()
        storageFailedUserIds = result.failedUserIds
        val storedProfiles = result.profiles
        if (BuildConfig.DEBUG) {
            storedProfiles.forEach { profile ->
                Log.d(
                    AUTH_DECISION_DEBUG_TAG,
                    String.format(
                        Locale.US,
                        "profile=%s samples=%d calibratedMahalanobis=%.4f calibratedFinal=%.4f policy=%d",
                        profile.userId,
                        profile.sampleCount,
                        profile.calibratedCleanMahalanobisFloor,
                        profile.calibratedCleanFinalScoreFloor,
                        profile.policyVersion
                    )
                )
            }
        }
        val storedProfileUserIds = storedProfiles.map { it.userId }.toSet()
        val inventory = if (storageFailedUserIds.isEmpty()) {
            profileInventoryStore.verifyOrBootstrap(storedProfileUserIds)
        } else {
            EnrollmentInventoryStore.VerificationResult(emptySet(), storageError = true)
        }
        profileInventoryFailed = inventory.storageError
        profileInventoryMismatchUserIds = inventory.mismatchUserIds

        val nowMs = System.currentTimeMillis()
        expiredUserIds = storedProfiles
            .filterNot { EnrollmentSecurityPolicy.isProfileFresh(it.createdAtMs, nowMs) }
            .map { it.userId }
            .toSet()
        val activeProfiles = storedProfiles.filter { it.userId !in expiredUserIds }
        val healthy = storageFailedUserIds.isEmpty() &&
            !profileInventoryFailed &&
            profileInventoryMismatchUserIds.isEmpty()
        authEngine.setProfiles(if (healthy) activeProfiles else emptyList())
        if (expiredUserIds.isNotEmpty()) {
            auditLogger.recordProfilesExpired(expiredUserIds, activeProfiles.size)
        }
        return healthy
    }

    private fun saveProfileInventoryFromStorage(): Boolean {
        val result = repository.loadProfilesResult()
        storageFailedUserIds = result.failedUserIds
        val storedProfileUserIds = result.profiles.map { it.userId }.toSet()
        profileInventoryFailed = result.failedUserIds.isNotEmpty() ||
            !profileInventoryStore.replaceWith(storedProfileUserIds)
        profileInventoryMismatchUserIds = emptySet()
        return !profileInventoryFailed
    }

    private fun handleFeatureFrame(rawFrame: RawFeatureFrame) {
        if (handleGlobalAccessFailureIfNeeded()) return
        val hint = currentHint()
        if (collectingEnrollment) {
            handleEnrollmentFrame(rawFrame, hint)
            return
        }
        val result = if (authEngine.profiles.isEmpty() && expiredUserIds.isNotEmpty()) {
            AuthResult.failed(FailureReason.PROFILE_EXPIRED).copy(registeredUserCount = expiredUserIds.size)
        } else {
            authEngine.authenticate(rawFrame, hint)
        }
        logAuthDecisionDebug(result)
        recordAuthenticationAudit(result)
        renderAuthResult(result)
    }

    private fun handleEnrollmentFrame(rawFrame: RawFeatureFrame, hint: OcclusionHint) {
        val nowMs = SystemClock.elapsedRealtime()
        if (abortEnrollmentIfTimedOut(nowMs)) return
        enrollmentNoFaceSinceMs = 0L
        if (EnrollmentCapturePolicy.isWarmingUp(enrollmentStartedAtMs, nowMs)) {
            renderEnrollmentCollectionProgress(
                "\uB4F1\uB85D \uC900\uBE44 \uC911 - \uC815\uBA74\uC744 \uC720\uC9C0\uD558\uC138\uC694",
                nowMs
            )
            return
        }
        val failure = authEngine.checkEnrollmentSample(rawFrame, hint)
        if (failure != FailureReason.NONE) {
            if (EnrollmentCapturePolicy.isRetryableFrameFailure(failure)) {
                renderEnrollmentCollectionProgress(enrollmentRetryGuidance(failure, rawFrame, hint), nowMs)
            } else {
                abortEnrollment(failure, enrollmentTerminalFailureMessage(failure))
            }
            return
        }
        if (!EnrollmentCapturePolicy.canAcceptSample(lastEnrollmentSampleAcceptedAtMs, nowMs)) {
            renderEnrollmentCollectionProgress("\uCC9C\uCC9C\uD788 \uC218\uC9D1 \uC911 - \uAC19\uC740 \uC790\uC138\uB97C \uC720\uC9C0\uD558\uC138\uC694", nowMs)
            return
        }
        enrollmentSamples += rawFrame
        lastEnrollmentSampleAcceptedAtMs = nowMs
        renderEnrollmentCollectionProgress("\uC88B\uC740 \uD504\uB808\uC784 \uC218\uC9D1 \uC911", nowMs)
        if (EnrollmentCapturePolicy.canComplete(
                enrollmentStartedAtMs,
                enrollmentSamples.size,
                ENROLLMENT_SAMPLE_COUNT,
                nowMs
            )
        ) {
            completeEnrollment()
        }
    }

    private fun completeEnrollment() {
        val samples = enrollmentSamples.takeLast(ENROLLMENT_SAMPLE_COUNT)
        val baselineFailure = authEngine.checkEnrollmentBaseline(samples)
        if (baselineFailure != FailureReason.NONE) {
            if (EnrollmentCapturePolicy.isRetryableBaselineFailure(baselineFailure)) {
                renderEnrollmentCollectionProgress(
                    enrollmentBaselineRetryGuidance(baselineFailure),
                    SystemClock.elapsedRealtime()
                )
            } else {
                abortEnrollment(baselineFailure, enrollmentTerminalFailureMessage(baselineFailure))
            }
            return
        }
        val profile = EnrollmentBuilder.build(selectedUserId, samples)
        val separationFailure = authEngine.checkEnrollmentSeparation(profile)
        if (separationFailure != FailureReason.NONE) {
            abortEnrollment(separationFailure, displayFailureReason(separationFailure))
            return
        }
        if (!repository.saveProfile(profile)) {
            abortEnrollment(FailureReason.SECURE_STORAGE_ERROR, "\uB4F1\uB85D \uC800\uC7A5 \uC2E4\uD328")
            return
        }
        if (!auditLogger.recordEnrollmentCompleted(profile.userId, profile.sampleCount)) {
            repository.clear(profile.userId)
            abortEnrollment(FailureReason.SECURE_STORAGE_ERROR, "\uAC10\uC0AC \uB85C\uADF8 \uC800\uC7A5 \uC2E4\uD328")
            return
        }
        if (!saveProfileInventoryFromStorage() || !reloadProfilesFromStorage()) {
            abortEnrollment(FailureReason.SECURE_STORAGE_ERROR, "\uB4F1\uB85D \uBAA9\uB85D \uAC80\uC99D \uC2E4\uD328")
            return
        }
        enrollmentSamples.clear()
        collectingEnrollment = false
        clearEnrollmentTimers()
        registerButton.isEnabled = true
        setUserSelectionEnabled(true)
        setOcclusionHintsEnabled(true)
        updateSelectedUserUi()
        resultText.text = "\uB4F1\uB85D \uC644\uB8CC"
        resultText.setTextColor(getColor(R.color.andface_green))
        failureReasonText.text = "\uAE68\uB057\uD55C \uC5BC\uAD74 \uB4F1\uB85D \uC644\uB8CC: ${profile.sampleCount}\uAC1C \uC0D8\uD50C"
    }

    private fun renderEnrollmentCollectionProgress(message: String, nowMs: Long) {
        val elapsedSeconds = EnrollmentCapturePolicy.elapsedMs(enrollmentStartedAtMs, nowMs) / 1000.0
        val acceptedCount = enrollmentSamples.size.coerceAtMost(ENROLLMENT_SAMPLE_COUNT)
        resultText.text = "\uB4F1\uB85D \uC911"
        resultText.setTextColor(getColor(R.color.andface_green))
        val savedLabel = String.format(Locale.US, "\uC800\uC7A5 %d/%d", acceptedCount, ENROLLMENT_SAMPLE_COUNT)
        val minimumTimeLabel = "\uCD5C\uC18C\uC2DC\uAC04 %.1f/%.1f\uCD08".format(
            Locale.US,
            elapsedSeconds,
            EnrollmentCapturePolicy.MIN_COLLECTION_MS / 1000.0
        )
        failureReasonText.text = "${displayUserId(selectedUserId)} | $savedLabel | $minimumTimeLabel | $message"
    }

    private fun enrollmentRetryGuidance(
        failure: FailureReason,
        rawFrame: RawFeatureFrame? = null,
        hint: OcclusionHint = currentHint()
    ): String {
        return when (failure) {
            FailureReason.POOR_FACE_QUALITY -> rawFrame?.let {
                "\uD488\uC9C8 \uBD80\uC871: ${authEngine.enrollmentSampleDiagnostic(it, hint)}"
            } ?: "\uC870\uBA85\uACFC \uCD08\uC810\uC744 \uD655\uC778\uD558\uC138\uC694"
            FailureReason.LOW_COVERAGE,
            FailureReason.TOO_FEW_FEATURES -> "\uC5BC\uAD74 \uC804\uCCB4\uB97C \uD654\uBA74 \uC911\uC559\uC5D0 \uB9DE\uCD94\uC138\uC694"
            FailureReason.OCCLUDED_DURING_ENROLLMENT -> "\uB9C8\uC2A4\uD06C, \uC548\uACBD, \uC548\uB300\uB97C \uC81C\uAC70\uD558\uC138\uC694"
            else -> "\uAE68\uB057\uD55C \uC815\uBA74 \uC5BC\uAD74\uC744 \uC720\uC9C0\uD558\uC138\uC694"
        }
    }

    private fun enrollmentBaselineRetryGuidance(failure: FailureReason): String {
        return when (failure) {
            FailureReason.LOW_LIVENESS -> "\uC0DD\uB3D9\uC131 \uD655\uC778 \uC911 - \uB208\uC744 \uAE5C\uBE61\uC774\uACE0 \uACE0\uAC1C\uB97C \uC544\uC8FC \uC870\uAE08 \uC6C0\uC9C1\uC774\uC138\uC694"
            FailureReason.UNSTABLE_ENROLLMENT -> "\uB4F1\uB85D \uC0D8\uD50C \uBD88\uC548\uC815 - \uC815\uBA74\uACFC \uD45C\uC815\uC744 \uC720\uC9C0\uD558\uC138\uC694"
            else -> "\uB4F1\uB85D \uC0D8\uD50C \uD655\uC778 \uC911"
        }
    }

    private fun enrollmentTerminalFailureMessage(failure: FailureReason): String {
        return if (failure == FailureReason.OCCLUDED_DURING_ENROLLMENT) {
            "\uB4F1\uB85D \uC2E4\uD328: \uB9C8\uC2A4\uD06C, \uC548\uACBD, \uC548\uB300\uB97C \uBC97\uACE0 \uB4F1\uB85D\uD558\uC138\uC694"
        } else {
            "\uB4F1\uB85D \uC2E4\uD328: ${displayFailureReason(failure)}"
        }
    }

    private fun abortEnrollmentIfTimedOut(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (!collectingEnrollment) return false
        val sessionTimedOut = enrollmentStartedAtMs != Long.MIN_VALUE &&
            nowMs - enrollmentStartedAtMs >= ENROLLMENT_SESSION_TIMEOUT_MS
        val faceMissingTimedOut = enrollmentNoFaceSinceMs > 0L &&
            nowMs - enrollmentNoFaceSinceMs >= ENROLLMENT_NO_FACE_TIMEOUT_MS
        if (!sessionTimedOut && !faceMissingTimedOut) return false
        val message = if (faceMissingTimedOut) {
            "\uB4F1\uB85D \uC911 \uC5BC\uAD74 \uAC80\uCD9C \uC2DC\uAC04 \uCD08\uACFC"
        } else {
            "\uB4F1\uB85D \uC138\uC158 \uC2DC\uAC04 \uCD08\uACFC"
        }
        return abortEnrollment(FailureReason.UNSTABLE_ENROLLMENT, message)
    }

    private fun abortEnrollment(reason: FailureReason, message: String): Boolean {
        enrollmentSamples.clear()
        collectingEnrollment = false
        clearEnrollmentTimers()
        authEngine.resetLiveSession()
        registerButton.isEnabled = true
        setUserSelectionEnabled(true)
        setOcclusionHintsEnabled(true)
        updateSelectedUserUi()
        recordEnrollmentRejectionAudit(selectedUserId, reason)
        renderAuthResult(AuthResult.failed(reason))
        failureReasonText.text = message
        return true
    }

    private fun clearEnrollmentTimers() {
        mainHandler.removeCallbacks(enrollmentTimeoutRunnable)
        enrollmentStartedAtMs = Long.MIN_VALUE
        enrollmentNoFaceSinceMs = 0L
        lastEnrollmentSampleAcceptedAtMs = Long.MIN_VALUE
    }

    private fun recordEnrollmentRejectionAudit(userId: String, reason: FailureReason): Boolean {
        return !::auditLogger.isInitialized || auditLogger.recordEnrollmentRejected(userId, reason)
    }

    private fun startCameraWhenReady() {
        if (!hasCameraPermission()) {
            renderCameraPermissionFailure()
            return
        }
        previewView.post {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener(
                {
                    runCatching { bindCameraUseCases(cameraProviderFuture.get()) }
                        .onFailure { onError(it.message ?: "CameraX binding failed") }
                },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        if (!hasCameraPermission()) {
            cameraProvider.unbindAll()
            renderCameraPermissionFailure()
            return
        }
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
        val cameraResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 960),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
        val preview = Preview.Builder()
            .setResolutionSelector(cameraResolutionSelector)
            .setTargetRotation(previewView.display.rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolutionSelector)
            .setTargetRotation(previewView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            logAnalyzerFrame(imageProxy)
            val runner = faceLandmarkerRunner
            if (runner == null) {
                imageProxy.close()
            } else {
                runner.detectLiveStream(
                    imageProxy = imageProxy,
                    isFrontCamera = true
                )
            }
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
    }

    private fun renderAuthResult(incomingResult: AuthResult) {
        val result = authDisplayStabilizer.stabilize(incomingResult) ?: return
        if (BuildConfig.DEBUG) {
            Log.d(
                AUTH_DISPLAY_DEBUG_TAG,
                "incoming=${incomingResult.decision}/${incomingResult.failureReason}/" +
                    "${incomingResult.matchedUserId ?: "--"} " +
                    "display=${result.decision}/${result.failureReason}/${result.matchedUserId ?: "--"}"
            )
        }
        val previous = lastRenderedResult
        val sameSuccessfulUser = previous?.decision == AuthDecision.SUCCESS &&
            previous.matchedUserId == result.matchedUserId
        lastRenderedResult = result

        val success = result.decision == AuthDecision.SUCCESS
        val confirming = result.failureReason == FailureReason.UNSTABLE_DECISION &&
            !result.matchedUserId.isNullOrBlank()
        if (success && !sameSuccessfulUser) {
            authSuccessWindowController.show(result.matchedUserId)?.let { window ->
                if (!auditLogger.recordAuthSuccessWindowShown(window)) {
                    auditStorageFailed = true
                }
            }
        } else if (!success) {
            authSuccessWindowController.clear()
        }

        resultText.text = when {
            success -> "\uC778\uC99D \uC644\uB8CC"
            confirming -> "\uC778\uC99D \uD655\uC778 \uC911"
            else -> "\uC778\uC99D \uC2E4\uD328"
        }
        resultText.setTextColor(
            getColor(
                when {
                    success -> R.color.andface_green
                    confirming -> R.color.andface_muted
                    else -> R.color.andface_red
                }
            )
        )

        val detailedMetrics = shouldShowDetailedMetrics()
        userLabel.text = if (detailedMetrics) {
            result.matchedUserId?.let { "\uCD5C\uC6B0\uC120 ${displayUserId(it)}" }
                ?: "\uB4F1\uB85D ${displayUserId(selectedUserId)}"
        } else {
            if (success) "\uC778\uC99D \uC644\uB8CC" else "\uC778\uC99D \uD655\uC778 \uC911"
        }
        failureReasonText.text = buildFailureLine(result)
        matchedUserText.text = buildMatchLine(result)
        registeredUsersText.text = buildString {
            append("\uB4F1\uB85D ${authEngine.profiles.size}/${EnrollmentRepository.SUPPORTED_USER_IDS.size}")
            if (expiredUserIds.isNotEmpty()) append(" \uB9CC\uB8CC ${expiredUserIds.size}")
        }

        if (detailedMetrics) {
            fuzzyScoreText.text = metric("Fuzzy", result.fuzzyScore)
            mahalanobisScoreText.text = metric("\uB9C8\uD560\uB77C\uB178\uBE44\uC2A4", result.mahalanobisScore)
            finalScoreText.text = metric("\uCD5C\uC885", result.finalScore)
            coverageText.text = metric("\uAD00\uCE21\uB960", result.coverage)
            marginText.text = metric("\uB9C8\uC9C4", result.margin)
            livenessText.text = String.format(
                Locale.US,
                "\uC0DD\uB3D9\uC131 %.3f %s %s",
                result.livenessScore,
                if (result.livenessPassed) "\uD1B5\uACFC" else "\uC2E4\uD328",
                livenessChallengeLine(result)
            )
            observableText.text = String.format(
                Locale.US,
                "\uAD00\uCE21 %d/%d \uC9C0\uC9C0 %d/%d \uC77C\uAD00 %.2f/%.2f \uC774\uD0C8 %.2f/%.2f \uD488\uC9C8 %.2f \uB300\uCE6D %.2f \uAD6C\uC870 %.2f",
                result.observableCount,
                FeatureType.COUNT,
                result.identitySupportCount,
                result.requiredSupportCount,
                result.identityConsistencyScore,
                result.requiredIdentityConsistencyScore,
                result.identityOutlierScore,
                result.requiredIdentityOutlierScore,
                result.faceQualityScore,
                result.meshSymmetryScore,
                result.landmarkTopologyScore
            )
        } else {
            fuzzyScoreText.text = "Fuzzy \uBCF4\uD638\uB428"
            mahalanobisScoreText.text = "\uB9C8\uD560\uB77C\uB178\uBE44\uC2A4 \uBCF4\uD638\uB428"
            finalScoreText.text = "\uCD5C\uC885\uC810\uC218 \uBCF4\uD638\uB428"
            coverageText.text = "\uAD00\uCE21\uB960 \uBCF4\uD638\uB428"
            marginText.text = "\uB9C8\uC9C4 \uBCF4\uD638\uB428"
            livenessText.text = "\uC0DD\uB3D9\uC131 ${if (result.livenessPassed) "\uD1B5\uACFC" else "\uC2E4\uD328"}"
            observableText.text = "\uAD00\uCE21 ${result.observableCount}/${FeatureType.COUNT}"
        }
        occlusionText.text = "\uAC00\uB9BC ${displayOcclusion(result.occlusionSummary)}"
    }

    private fun copyCurrentFieldCsvRowToClipboard() {
        val result = lastRenderedResult
        if (result == null) {
            Toast.makeText(this, "\uBCF5\uC0AC\uD560 \uC778\uC99D \uACB0\uACFC\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("AndFace field CSV row", buildFieldCsvRow(result)))
        Toast.makeText(this, "\uD544\uB4DC \uD14C\uC2A4\uD2B8 CSV \uD589\uC744 \uBCF5\uC0AC\uD588\uC2B5\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
    }

    private fun buildFieldCsvRow(result: AuthResult): String {
        val hint = currentHint()
        val values = listOf(
            "",
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            "",
            "${Build.MANUFACTURER} ${Build.MODEL}",
            FINAL_APK_FILE_NAME,
            installedApkSha256ForFieldCsv(),
            selectedUserId,
            "",
            "",
            hint.lowerFaceCovered.toString(),
            hint.glasses.toString(),
            hint.leftEyePatch.toString(),
            hint.rightEyePatch.toString(),
            "",
            authDecisionForFieldCsv(result),
            result.matchedUserId.orEmpty(),
            result.secondBestUserId.orEmpty(),
            formatMetricForFieldCsv(result.fuzzyScore),
            formatMetricForFieldCsv(result.mahalanobisScore),
            formatMetricForFieldCsv(result.finalScore),
            formatMetricForFieldCsv(result.coverage),
            formatMetricForFieldCsv(result.margin),
            formatMetricForFieldCsv(result.livenessScore),
            result.livenessPassed.toString(),
            result.observableCount.toString(),
            formatMetricForFieldCsv(result.faceQualityScore),
            formatMetricForFieldCsv(result.meshSymmetryScore),
            formatMetricForFieldCsv(result.landmarkTopologyScore),
            formatMetricForFieldCsv(result.identityConsistencyScore),
            formatMetricForFieldCsv(result.requiredIdentityConsistencyScore),
            result.identitySupportCount.toString(),
            result.requiredSupportCount.toString(),
            FeatureType.COUNT.toString(),
            result.occlusionSummary,
            result.failureReason.name,
            "",
            ""
        )
        return values.joinToString(",", transform = ::csvValue)
    }

    private fun authDecisionForFieldCsv(result: AuthResult): String {
        return if (result.decision == AuthDecision.SUCCESS) "AUTH_SUCCESS" else "AUTH_FAILED"
    }

    private fun formatMetricForFieldCsv(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun csvValue(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private data class LandmarkFrameCandidate(
        val name: String,
        val landmarks: List<NormalizedLandmark>,
        val rawFrame: RawFeatureFrame?,
        val overlayImageWidth: Int,
        val overlayImageHeight: Int
    )

    private fun chooseBestLandmarkCandidate(
        resultBundle: FaceLandmarkerRunner.ResultBundle,
        landmarks: List<NormalizedLandmark>,
        facialTransformMatrix: FloatArray?,
        faceBlendshapes: List<Category>?
    ): LandmarkFrameCandidate {
        val rotations = listOf(
            normalizedRotation(resultBundle.inputRotationDegrees),
            0,
            90,
            180,
            270
        ).distinct()
        val candidates = rotations.flatMap { rotation ->
            listOf(true, false).map { mirror ->
                val transformed = transformLandmarks(landmarks, rotation, mirror)
                LandmarkFrameCandidate(
                    name = "r${rotation}_m$mirror",
                    landmarks = transformed,
                    rawFrame = featureExtractor.extract(
                        transformed,
                        facialTransformMatrix,
                        resultBundle.result.timestampMs(),
                        faceBlendshapes
                    ),
                    overlayImageWidth = overlayImageWidthForRotation(resultBundle, rotation),
                    overlayImageHeight = overlayImageHeightForRotation(resultBundle, rotation)
                )
            }
        }
        return candidates.maxWithOrNull(
            compareBy<LandmarkFrameCandidate> {
                if (it.rawFrame?.quality?.acceptableForAccess == true) 1 else 0
            }.thenBy { it.rawFrame?.quality?.landmarkTopologyScore ?: -1.0 }
                .thenBy { it.rawFrame?.quality?.meshSymmetryScore ?: -1.0 }
                .thenBy { it.rawFrame?.quality?.inFrameLandmarkRatio ?: -1.0 }
        ) ?: LandmarkFrameCandidate(
            name = "fallback",
            landmarks = transformFrontCameraLandmarks(landmarks, resultBundle.inputRotationDegrees),
            rawFrame = null,
            overlayImageWidth = overlayImageWidthForRotation(resultBundle, resultBundle.inputRotationDegrees),
            overlayImageHeight = overlayImageHeightForRotation(resultBundle, resultBundle.inputRotationDegrees)
        )
    }

    private fun transformFrontCameraLandmarks(
        landmarks: List<NormalizedLandmark>,
        rotationDegrees: Int
    ): List<NormalizedLandmark> = transformLandmarks(landmarks, rotationDegrees, mirrorX = true)

    private fun transformLandmarks(
        landmarks: List<NormalizedLandmark>,
        rotationDegrees: Int,
        mirrorX: Boolean
    ): List<NormalizedLandmark> {
        val rotation = normalizedRotation(rotationDegrees)
        return landmarks.map { landmark ->
            val (rotatedX, rotatedY) = when (rotation) {
                90 -> 1f - landmark.y() to landmark.x()
                180 -> 1f - landmark.x() to 1f - landmark.y()
                270 -> landmark.y() to 1f - landmark.x()
                else -> landmark.x() to landmark.y()
            }
            val finalX = if (mirrorX) 1f - rotatedX else rotatedX
            NormalizedLandmark.create(finalX, rotatedY, landmark.z())
        }
    }

    private fun normalizedRotation(rotationDegrees: Int): Int = ((rotationDegrees % 360) + 360) % 360

    private fun overlayImageWidthForRotation(
        resultBundle: FaceLandmarkerRunner.ResultBundle,
        rotationDegrees: Int
    ): Int = if (normalizedRotation(rotationDegrees) in setOf(90, 270)) {
        resultBundle.inputImageHeight
    } else {
        resultBundle.inputImageWidth
    }

    private fun overlayImageHeightForRotation(
        resultBundle: FaceLandmarkerRunner.ResultBundle,
        rotationDegrees: Int
    ): Int = if (normalizedRotation(rotationDegrees) in setOf(90, 270)) {
        resultBundle.inputImageWidth
    } else {
        resultBundle.inputImageHeight
    }

    private fun renderLandmarkOverlay(
        landmarks: List<NormalizedLandmark>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        if (BuildConfig.DEBUG || collectingEnrollment || shouldShowDetailedMetrics()) {
            overlayView.setLandmarks(landmarks, imageWidth, imageHeight)
        } else {
            overlayView.clear()
        }
    }

    private fun logAnalyzerFrame(imageProxy: ImageProxy) {
        if (!BuildConfig.DEBUG) return
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastAnalyzerDebugAtMs < FACE_PIPELINE_DEBUG_INTERVAL_MS) return
        lastAnalyzerDebugAtMs = nowMs
        Log.d(
            FACE_PIPELINE_DEBUG_TAG,
            "analyzer frame=${imageProxy.width}x${imageProxy.height} " +
                "rotation=${imageProxy.imageInfo.rotationDegrees} runnerReady=${faceLandmarkerRunner != null}"
        )
    }

    private fun logFacePipelineDebug(
        resultBundle: FaceLandmarkerRunner.ResultBundle,
        candidate: LandmarkFrameCandidate
    ) {
        if (!BuildConfig.DEBUG) return
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastFacePipelineDebugAtMs < FACE_PIPELINE_DEBUG_INTERVAL_MS) return
        lastFacePipelineDebugAtMs = nowMs
        Log.d(
            FACE_PIPELINE_DEBUG_TAG,
            "faces=${resultBundle.result.faceLandmarks().size} " +
                "landmarks=${candidate.landmarks.size} candidate=${candidate.name} " +
                "features=${candidate.rawFrame?.values?.size ?: 0} " +
                "quality=${candidate.rawFrame?.quality?.accessScoringConfidence ?: 0.0} " +
                "symmetry=${candidate.rawFrame?.quality?.meshSymmetryScore ?: 0.0} " +
                "topology=${candidate.rawFrame?.quality?.landmarkTopologyScore ?: 0.0}"
        )
    }

    private fun logAuthDecisionDebug(result: AuthResult) {
        if (!BuildConfig.DEBUG) return
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastAuthDecisionDebugAtMs < FACE_PIPELINE_DEBUG_INTERVAL_MS) return
        lastAuthDecisionDebugAtMs = nowMs
        Log.d(
            AUTH_DECISION_DEBUG_TAG,
            String.format(
                Locale.US,
                "decision=%s reason=%s best=%s second=%s fuzzy=%.4f mahal=%.4f final=%.4f " +
                "coverage=%.4f margin=%.4f live=%.4f/%s obs=%d consistency=%.4f/%.4f " +
                    "outlier=%.4f/%.4f support=%d/%d stable=%d/%d mahalReq=%.4f finalReq=%.4f " +
                    "gates=%s/%s/%s micro=%s occ=%s quality=%.4f symmetry=%.4f topology=%.4f",
                result.decision,
                result.failureReason,
                result.matchedUserId ?: "--",
                result.secondBestUserId ?: "--",
                result.fuzzyScore,
                result.mahalanobisScore,
                result.finalScore,
                result.coverage,
                result.margin,
                result.livenessScore,
                result.livenessPassed,
                result.observableCount,
                result.identityConsistencyScore,
                result.requiredIdentityConsistencyScore,
                result.identityOutlierScore,
                result.requiredIdentityOutlierScore,
                result.identitySupportCount,
                result.requiredSupportCount,
                result.stableFrameCount,
                result.requiredStableFrames,
                result.requiredMahalanobisScore,
                result.requiredFinalScore,
                result.regionalInlierBalancePassed,
                result.localStructureAgreementPassed,
                result.microRegionAgreementPassed,
                result.microRegionDiagnostics,
                result.occlusionSummary,
                result.faceQualityScore,
                result.meshSymmetryScore,
                result.landmarkTopologyScore
            )
        )
    }

    private fun updateSelectedUserUi() {
        if (!::registerButton.isInitialized) return
        val selectedProfile = if (::repository.isInitialized) repository.loadProfileResult(selectedUserId) else null
        val hasProfile = selectedProfile?.profile != null
        val storageHealthy = selectedProfile?.storageError != true &&
            selectedUserId !in storageFailedUserIds &&
            selectedUserId !in profileInventoryMismatchUserIds &&
            !profileInventoryFailed
        registerButton.text = when {
            collectingEnrollment -> "\uB4F1\uB85D \uCDE8\uC18C"
            hasProfile -> "\uC7AC\uB4F1\uB85D ${displayUserId(selectedUserId)}"
            else -> "\uB4F1\uB85D ${displayUserId(selectedUserId)}"
        }
        registerButton.isEnabled = collectingEnrollment ||
            (!modelAssetIntegrityFailed && storageHealthy)
        clearButton.isEnabled = !collectingEnrollment &&
            (hasProfile || !storageHealthy)
        setOcclusionHintsEnabled(!collectingEnrollment)
        if (!collectingEnrollment) {
            userLabel.text = "\uB4F1\uB85D ${displayUserId(selectedUserId)}"
        }
    }

    private fun setUserSelectionEnabled(enabled: Boolean) {
        userSelector.isEnabled = enabled
        for (index in 0 until userSelector.childCount) {
            userSelector.getChildAt(index).isEnabled = enabled
        }
        setOcclusionHintsEnabled(enabled)
    }

    private fun setOcclusionHintsEnabled(enabled: Boolean) {
        lowerFaceHint.isEnabled = enabled
        glassesHint.isEnabled = enabled
        leftPatchHint.isEnabled = enabled
        rightPatchHint.isEnabled = enabled
    }

    private fun clearOcclusionHintsForEnrollment() {
        lowerFaceHint.isChecked = false
        glassesHint.isChecked = false
        leftPatchHint.isChecked = false
        rightPatchHint.isChecked = false
        setOcclusionHintsEnabled(false)
    }

    private fun revealDetailedMetrics() {
        detailedMetricsVisibleUntilMs = SystemClock.elapsedRealtime() + DETAILED_METRICS_VISIBLE_MS
        modelStateText.text = "DETAIL 60s"
        lastRenderedResult?.let { renderAuthResult(it) }
    }

    private fun shouldShowDetailedMetrics(): Boolean {
        return BuildConfig.DEBUG || SystemClock.elapsedRealtime() < detailedMetricsVisibleUntilMs
    }

    private fun resetTransientAccessState(reason: FailureReason) {
        lastRenderedResult = null
        detailedMetricsVisibleUntilMs = 0L
        authEngine.resetLiveSession()
        authDisplayStabilizer.reset()
        authSuccessWindowController.clear()
        if (::overlayView.isInitialized) overlayView.clear()
        if (collectingEnrollment) {
            enrollmentSamples.clear()
            collectingEnrollment = false
            clearEnrollmentTimers()
            registerButton.isEnabled = true
            setUserSelectionEnabled(true)
            setOcclusionHintsEnabled(true)
            updateSelectedUserUi()
            recordEnrollmentRejectionAudit(selectedUserId, FailureReason.UNSTABLE_ENROLLMENT)
        }
        renderAuthResult(AuthResult.failed(reason))
    }

    private fun recordAuthenticationAudit(result: AuthResult): Boolean {
        if (!::auditLogger.isInitialized) return true
        val saved = auditLogger.recordAuthentication(result)
        if (!saved) auditStorageFailed = true
        return saved
    }

    private fun buildFailureLine(result: AuthResult): String {
        if (result.decision == AuthDecision.SUCCESS) {
            return "${displayUserId(result.matchedUserId.orEmpty())} | ${displayOcclusion(result.occlusionSummary)}"
        }
        if (result.failureReason == FailureReason.UNSTABLE_DECISION && !result.matchedUserId.isNullOrBlank()) {
            return "${displayUserId(result.matchedUserId)} | 2\uCD08 \uC720\uC9C0 \uD655\uC778 \uC911"
        }
        return displayFailureReason(result.failureReason)
    }

    private fun livenessChallengeLine(result: AuthResult): String {
        val challenge = result.livenessChallenge ?: return ""
        return "$challenge ${if (result.livenessChallengePassed) "\uD1B5\uACFC" else "\uD655\uC778 \uC911"}"
    }

    private fun buildMatchLine(result: AuthResult): String {
        val best = result.matchedUserId?.let(::displayUserId) ?: "--"
        val second = result.secondBestUserId?.let(::displayUserId) ?: "--"
        return "\uCD5C\uC6B0\uC120 $best | 2\uC704 $second"
    }

    private fun displayUserId(userId: String): String = when (userId) {
        "USER_1" -> "\uC0AC\uC6A9\uC7901"
        "USER_2" -> "\uC0AC\uC6A9\uC7902"
        "USER_3" -> "\uC0AC\uC6A9\uC7903"
        else -> userId.ifBlank { "--" }
    }

    private fun displayFailureReason(reason: FailureReason): String = when (reason) {
        FailureReason.NONE -> "\uC815\uC0C1"
        FailureReason.NO_ENROLLMENT -> "\uB4F1\uB85D \uC5C6\uC74C"
        FailureReason.PROFILE_EXPIRED -> "\uB4F1\uB85D \uAE30\uAC04 \uB9CC\uB8CC"
        FailureReason.NO_FACE -> "\uC5BC\uAD74 \uC5C6\uC74C"
        FailureReason.MULTIPLE_FACES -> "\uC5BC\uAD74\uC774 \uC5EC\uB7EC \uBA85 \uAC80\uCD9C\uB428"
        FailureReason.MODEL_NOT_READY -> "\uBAA8\uB378 \uC900\uBE44 \uC548 \uB428"
        FailureReason.DEVICE_NOT_SECURE -> "\uAE30\uAE30 \uBCF4\uC548 \uC124\uC815 \uD544\uC694"
        FailureReason.DEVICE_LOCKED -> "\uAE30\uAE30 \uC7A0\uAE08 \uD574\uC81C \uD544\uC694"
        FailureReason.WINDOW_NOT_SECURE -> "\uC548\uC804\uD55C \uD654\uBA74 \uBAA8\uB4DC \uD544\uC694"
        FailureReason.KIOSK_MODE_REQUIRED -> "\uC804\uC6A9 \uB2E8\uB9D0 \uBAA8\uB4DC \uD544\uC694"
        FailureReason.RUNTIME_INTEGRITY_RISK -> "\uC2E4\uD589 \uD658\uACBD \uBB34\uACB0\uC131 \uC704\uD5D8"
        FailureReason.DEPLOYMENT_SIGNING_REQUIRED -> "\uC6B4\uC601\uC6A9 \uC11C\uBA85 \uD544\uC694"
        FailureReason.SECURE_STORAGE_ERROR -> "\uBCF4\uC548 \uC800\uC7A5\uC18C \uC624\uB958"
        FailureReason.OCCLUDED_DURING_ENROLLMENT -> "\uB4F1\uB85D \uC911 \uAC00\uB9BC \uAC80\uCD9C"
        FailureReason.POOR_FACE_QUALITY -> "\uC5BC\uAD74 \uD488\uC9C8 \uBD80\uC871"
        FailureReason.TOO_FEW_FEATURES -> "\uAD00\uCE21 \uD2B9\uC9D5 \uBD80\uC871"
        FailureReason.LOW_COVERAGE -> "\uAD00\uCE21\uB960 \uBD80\uC871"
        FailureReason.LOW_LIVENESS -> "\uC0DD\uB3D9\uC131 \uBD80\uC871"
        FailureReason.LOW_MARGIN -> "\uD6C4\uBCF4 \uAC04 \uCC28\uC774 \uBD80\uC871"
        FailureReason.LOW_IDENTITY_COVERAGE -> "\uC2E0\uC6D0 \uAD00\uCE21\uB960 \uBD80\uC871"
        FailureReason.LOW_IDENTITY_SUPPORT -> "\uC2E0\uC6D0 \uC9C0\uC9C0 \uD2B9\uC9D5 \uBD80\uC871"
        FailureReason.LOW_GLOBAL_CONSISTENCY -> "\uC804\uC5ED \uC77C\uAD00\uC131 \uBD80\uC871"
        FailureReason.EXCESSIVE_OCCLUSION -> "\uAC00\uB9BC\uC774 \uB108\uBB34 \uB9CE\uC74C"
        FailureReason.OCCLUSION_HINT_MISMATCH -> "\uAC00\uB9BC \uC120\uD0DD\uACFC \uC2E4\uC81C \uC0C1\uD0DC \uBD88\uC77C\uCE58"
        FailureReason.TOO_MANY_ATTEMPTS -> "\uC2DC\uB3C4 \uD69F\uC218 \uCD08\uACFC"
        FailureReason.UNSTABLE_DECISION -> "\uC778\uC99D \uD655\uC778 \uC911"
        FailureReason.SESSION_RESET -> "\uC778\uC99D \uC138\uC158 \uCD08\uAE30\uD654"
        FailureReason.LOW_SCORE -> "\uC810\uC218 \uBD80\uC871"
        FailureReason.UNSTABLE_ENROLLMENT -> "\uB4F1\uB85D \uC0D8\uD50C \uBD88\uC548\uC815"
    }

    private fun displayOcclusion(summary: String): String {
        if (summary.isBlank() || summary == "clean") return "\uC5C6\uC74C"
        val labels = mutableListOf<String>()
        if (summary.contains("lower")) labels += "\uB9C8\uC2A4\uD06C"
        if (summary.contains("mid")) labels += "\uC911\uC548\uBA74"
        if (summary.contains("glasses")) labels += "\uC548\uACBD"
        if (summary.contains("left_eye")) labels += "\uC67C\uC548\uB300"
        if (summary.contains("right_eye")) labels += "\uC624\uB978\uC548\uB300"
        return labels.distinct().joinToString("+").ifBlank { summary }
    }

    private fun currentHint(): OcclusionHint = OcclusionHint(
        lowerFaceCovered = lowerFaceHint.isChecked,
        glasses = glassesHint.isChecked,
        leftEyePatch = leftPatchHint.isChecked,
        rightEyePatch = rightPatchHint.isChecked
    )

    private fun firstFaceBlendshapes(result: FaceLandmarkerResult): List<Category>? {
        val values = result.faceBlendshapes()
        return if (values.isPresent) values.get().firstOrNull() else null
    }

    private fun firstTransformationMatrix(result: FaceLandmarkerResult): FloatArray? {
        val values = result.facialTransformationMatrixes()
        return if (values.isPresent) values.get().firstOrNull() else null
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun renderCameraPermissionFailure() {
        authSuccessWindowController.clear()
        authEngine.resetLiveSession()
        overlayView.clear()
        renderAuthResult(AuthResult.failed(FailureReason.MODEL_NOT_READY))
        failureReasonText.text = "\uCE74\uBA54\uB77C \uAD8C\uD55C\uC774 \uD544\uC694\uD569\uB2C8\uB2E4."
    }

    private fun configureSecureWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
    }

    private fun handleGlobalAccessFailureIfNeeded(): Boolean {
        val failure = activeGlobalAccessFailure() ?: return false
        authEngine.resetLiveSession()
        authDisplayStabilizer.reset()
        authSuccessWindowController.clear()
        if (::overlayView.isInitialized) overlayView.clear()
        renderAuthResult(AuthResult.failed(failure))
        return true
    }

    private fun activeGlobalAccessFailure(): FailureReason? {
        if (!hasCameraPermission()) return FailureReason.MODEL_NOT_READY
        if (modelAssetIntegrityFailed) return FailureReason.MODEL_NOT_READY
        if (storageFailedUserIds.isNotEmpty() || profileInventoryFailed || profileInventoryMismatchUserIds.isNotEmpty()) {
            return FailureReason.SECURE_STORAGE_ERROR
        }
        if (accessLockoutStorageFailed || auditStorageFailed) return FailureReason.SECURE_STORAGE_ERROR
        if (!BuildConfig.DEBUG) {
            if (!isDeviceSecureForAccess()) return FailureReason.DEVICE_NOT_SECURE
            if (!isDeviceUnlockedForAccess()) return FailureReason.DEVICE_LOCKED
            if (!isWindowModeSecureForAccess()) return FailureReason.WINDOW_NOT_SECURE
            if (!isDedicatedTerminalModeForAccess()) return FailureReason.KIOSK_MODE_REQUIRED
            if (!BuildConfig.OPERATOR_RELEASE_SIGNED) return FailureReason.DEPLOYMENT_SIGNING_REQUIRED
        }
        return null
    }

    private fun isDeviceSecureForAccess(): Boolean {
        return getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    private fun isDeviceUnlockedForAccess(): Boolean {
        return getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false
    }

    private fun isWindowModeSecureForAccess(): Boolean {
        return !isInMultiWindowMode && !isInPictureInPictureMode
    }

    private fun isDedicatedTerminalModeForAccess(): Boolean {
        val state = getSystemService(ActivityManager::class.java)?.lockTaskModeState
            ?: ActivityManager.LOCK_TASK_MODE_NONE
        return DedicatedTerminalPolicy.isSatisfied(BuildConfig.REQUIRE_LOCK_TASK_FOR_ACCESS, state)
    }

    private fun enterManagedLockTaskIfPermitted(): Boolean {
        if (!BuildConfig.REQUIRE_LOCK_TASK_FOR_ACCESS) return false
        val activityManager = getSystemService(ActivityManager::class.java)
        val currentState = activityManager?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE
        val permitted = getSystemService(DevicePolicyManager::class.java)?.isLockTaskPermitted(packageName) == true
        if (!DedicatedTerminalPolicy.shouldAttemptManagedLockTask(true, currentState, permitted)) return false
        return runCatching {
            startLockTask()
            true
        }.getOrDefault(false)
    }

    private fun restorePersistedAccessLock() {
        val lock = accessLockoutStore.restoreActive()
        accessLockoutStorageFailed = lock.storageError
        lock.untilElapsedMs?.let(authEngine::restoreAccessLock)
        val risky = accessLockoutStore.restoreRiskyFailureWindow()
        accessLockoutStorageFailed = accessLockoutStorageFailed || risky.storageError
        risky.state?.let(authEngine::restoreRiskyFailureState)
    }

    private fun verifyModelAssetIntegrity(): Boolean {
        return runCatching {
            assets.open(FaceLandmarkerRunner.MODEL_ASSET_PATH).use { input ->
                val actual = AssetIntegrityPolicy.sha256Hex(input)
                AssetIntegrityPolicy.matchesExpectedSha256(actual, BuildConfig.FACE_LANDMARKER_MODEL_SHA256)
            }
        }.getOrDefault(false)
    }

    private fun installedApkSha256ForFieldCsv(): String {
        return runCatching {
            FileInputStream(File(applicationInfo.sourceDir)).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(APK_HASH_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02X".format(it) }
            }
        }.getOrDefault("")
    }

    private fun appRevisionLabel(): String = APP_REVISION

    private fun metric(label: String, value: Double): String = String.format(Locale.US, "%s %.3f", label, value)

    companion object {
        private const val APP_REVISION = "\uAC24\uB7ED\uC2DC \uC5BC\uAD74 \uC778\uC99D S194"
        private const val FINAL_APK_FILE_NAME = "AndFace_Galaxy_face_auth_s194-debug.apk"
        private const val ENROLLMENT_SAMPLE_COUNT = 45
        private const val ENROLLMENT_SESSION_TIMEOUT_MS = 60_000L
        private const val ENROLLMENT_NO_FACE_TIMEOUT_MS = 30_000L
        private const val DETAILED_METRICS_VISIBLE_MS = 60_000L
        private const val AUTH_SUCCESS_WINDOW_REISSUE_COOLDOWN_MS = 5_000L
        private const val FACE_PIPELINE_DEBUG_INTERVAL_MS = 1_000L
        private const val FACE_PIPELINE_DEBUG_TAG = "AndFacePipeline"
        private const val AUTH_DECISION_DEBUG_TAG = "AndFaceAuth"
        private const val AUTH_DISPLAY_DEBUG_TAG = "AndFaceDisplay"
        private const val APK_HASH_BUFFER_SIZE = 65_536
    }
}
