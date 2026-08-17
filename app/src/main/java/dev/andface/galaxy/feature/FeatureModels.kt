package dev.andface.galaxy.feature

data class RawFeatureFrame(
    val values: DoubleArray,
    val timestampMs: Long,
    val quality: FaceFrameQuality = FaceFrameQuality.TRUSTED
) {
    fun value(type: FeatureType): Double = values[type.ordinal]

    fun copyValues(): DoubleArray = values.copyOf()
}

data class FaceFrameQuality(
    val faceWidthRatio: Double,
    val faceHeightRatio: Double,
    val centerX: Double,
    val centerY: Double,
    val inFrameLandmarkRatio: Double,
    val irisLandmarksAvailable: Boolean = true,
    val meshSymmetryScore: Double = 1.0,
    val landmarkTopologyScore: Double = 1.0,
    val landmarkTopologyHint: String = "",
    val blendshapesAvailable: Boolean = false,
    val blendshapeActivityScore: Double = 0.0,
    val blendshapeLeftEyeActivityScore: Double = 0.0,
    val blendshapeRightEyeActivityScore: Double = 0.0,
    val blendshapeBrowActivityScore: Double = 0.0,
    val blendshapeMouthActivityScore: Double = 0.0
) {
    val acceptableForAccess: Boolean
        get() = faceWidthRatio in MIN_FACE_WIDTH_RATIO..MAX_FACE_WIDTH_RATIO &&
            faceHeightRatio in MIN_FACE_HEIGHT_RATIO..MAX_FACE_HEIGHT_RATIO &&
            kotlin.math.abs(centerX - FRAME_CENTER) <= MAX_CENTER_OFFSET_X &&
            kotlin.math.abs(centerY - FRAME_CENTER) <= MAX_CENTER_OFFSET_Y &&
            inFrameLandmarkRatio >= MIN_IN_FRAME_LANDMARK_RATIO &&
            meshSymmetryScore >= MIN_MESH_SYMMETRY_SCORE &&
            landmarkTopologyScore >= MIN_LANDMARK_TOPOLOGY_SCORE

    val acceptableForCleanEnrollment: Boolean
        get() = acceptableForAccess &&
            faceWidthRatio in MIN_CLEAN_ENROLLMENT_FACE_WIDTH_RATIO..MAX_CLEAN_ENROLLMENT_FACE_WIDTH_RATIO &&
            faceHeightRatio in MIN_CLEAN_ENROLLMENT_FACE_HEIGHT_RATIO..MAX_CLEAN_ENROLLMENT_FACE_HEIGHT_RATIO &&
            kotlin.math.abs(centerX - FRAME_CENTER) <= MAX_CLEAN_ENROLLMENT_CENTER_OFFSET_X &&
            kotlin.math.abs(centerY - FRAME_CENTER) <= MAX_CLEAN_ENROLLMENT_CENTER_OFFSET_Y &&
            inFrameLandmarkRatio >= MIN_CLEAN_ENROLLMENT_IN_FRAME_LANDMARK_RATIO &&
            meshSymmetryScore >= MIN_CLEAN_ENROLLMENT_MESH_SYMMETRY_SCORE &&
            landmarkTopologyScore >= MIN_CLEAN_ENROLLMENT_LANDMARK_TOPOLOGY_SCORE

    val acceptableForEnrollmentCapture: Boolean
        get() = faceWidthRatio in MIN_ENROLLMENT_CAPTURE_FACE_WIDTH_RATIO..MAX_ENROLLMENT_CAPTURE_FACE_WIDTH_RATIO &&
            faceHeightRatio in MIN_ENROLLMENT_CAPTURE_FACE_HEIGHT_RATIO..MAX_ENROLLMENT_CAPTURE_FACE_HEIGHT_RATIO &&
            kotlin.math.abs(centerX - FRAME_CENTER) <= MAX_ENROLLMENT_CAPTURE_CENTER_OFFSET_X &&
            kotlin.math.abs(centerY - FRAME_CENTER) <= MAX_ENROLLMENT_CAPTURE_CENTER_OFFSET_Y &&
            inFrameLandmarkRatio >= MIN_ENROLLMENT_CAPTURE_IN_FRAME_LANDMARK_RATIO &&
            meshSymmetryScore >= MIN_ENROLLMENT_CAPTURE_MESH_SYMMETRY_SCORE &&
            landmarkTopologyScore >= MIN_ENROLLMENT_CAPTURE_LANDMARK_TOPOLOGY_SCORE

    val accessScoringConfidence: Double
        get() {
            if (!acceptableForEnrollmentCapture) return 0.0

            val qualityScore = listOf(
                bandConfidence(
                    faceWidthRatio,
                    MIN_FACE_WIDTH_RATIO,
                    COMFORT_FACE_WIDTH_MIN,
                    COMFORT_FACE_WIDTH_MAX,
                    MAX_FACE_WIDTH_RATIO
                ),
                bandConfidence(
                    faceHeightRatio,
                    MIN_FACE_HEIGHT_RATIO,
                    COMFORT_FACE_HEIGHT_MIN,
                    COMFORT_FACE_HEIGHT_MAX,
                    MAX_FACE_HEIGHT_RATIO
                ),
                centerConfidence(kotlin.math.abs(centerX - FRAME_CENTER), COMFORT_CENTER_OFFSET_X, MAX_CENTER_OFFSET_X),
                centerConfidence(kotlin.math.abs(centerY - FRAME_CENTER), COMFORT_CENTER_OFFSET_Y, MAX_CENTER_OFFSET_Y),
                lowerBoundConfidence(
                    inFrameLandmarkRatio,
                    MIN_IN_FRAME_LANDMARK_RATIO,
                    COMFORT_IN_FRAME_LANDMARK_RATIO
                ),
                lowerBoundConfidence(meshSymmetryScore, MIN_MESH_SYMMETRY_SCORE, COMFORT_MESH_SYMMETRY_SCORE),
                lowerBoundConfidence(
                    landmarkTopologyScore,
                    MIN_LANDMARK_TOPOLOGY_SCORE,
                    COMFORT_LANDMARK_TOPOLOGY_SCORE
                )
            ).minOrNull() ?: 0.0

            return (MIN_ACCESS_SCORING_CONFIDENCE +
                (1.0 - MIN_ACCESS_SCORING_CONFIDENCE) * qualityScore).coerceIn(0.0, 1.0)
        }

    private fun bandConfidence(
        value: Double,
        minValue: Double,
        comfortableMin: Double,
        comfortableMax: Double,
        maxValue: Double
    ): Double {
        val lower = if (value >= comfortableMin) {
            1.0
        } else {
            ((value - minValue) / kotlin.math.max(comfortableMin - minValue, EPSILON)).coerceIn(0.0, 1.0)
        }
        val upper = if (value <= comfortableMax) {
            1.0
        } else {
            ((maxValue - value) / kotlin.math.max(maxValue - comfortableMax, EPSILON)).coerceIn(0.0, 1.0)
        }
        return kotlin.math.min(lower, upper)
    }

    private fun centerConfidence(offset: Double, comfortableOffset: Double, maxOffset: Double): Double {
        return if (offset <= comfortableOffset) {
            1.0
        } else {
            ((maxOffset - offset) / kotlin.math.max(maxOffset - comfortableOffset, EPSILON)).coerceIn(0.0, 1.0)
        }
    }

    private fun lowerBoundConfidence(value: Double, minValue: Double, comfortableValue: Double): Double {
        return ((value - minValue) / kotlin.math.max(comfortableValue - minValue, EPSILON)).coerceIn(0.0, 1.0)
    }
    companion object {
        val TRUSTED = FaceFrameQuality(
            faceWidthRatio = 0.45,
            faceHeightRatio = 0.55,
            centerX = FRAME_CENTER,
            centerY = FRAME_CENTER,
            inFrameLandmarkRatio = 1.0
        )

        private const val FRAME_CENTER = 0.5
        private const val EPSILON = 1.0e-6
        private const val MIN_ACCESS_SCORING_CONFIDENCE = 0.55
        private const val COMFORT_FACE_WIDTH_MIN = 0.28
        private const val COMFORT_FACE_WIDTH_MAX = 0.82
        private const val COMFORT_FACE_HEIGHT_MIN = 0.32
        private const val COMFORT_FACE_HEIGHT_MAX = 0.88
        private const val COMFORT_CENTER_OFFSET_X = 0.22
        private const val COMFORT_CENTER_OFFSET_Y = 0.26
        private const val COMFORT_IN_FRAME_LANDMARK_RATIO = 0.99
        private const val COMFORT_MESH_SYMMETRY_SCORE = 0.72
        private const val COMFORT_LANDMARK_TOPOLOGY_SCORE = 0.92
        private const val MIN_FACE_WIDTH_RATIO = 0.12
        private const val MIN_FACE_HEIGHT_RATIO = 0.16
        private const val MAX_FACE_WIDTH_RATIO = 0.96
        private const val MAX_FACE_HEIGHT_RATIO = 0.96
        private const val MAX_CENTER_OFFSET_X = 0.34
        private const val MAX_CENTER_OFFSET_Y = 0.38
        private const val MIN_IN_FRAME_LANDMARK_RATIO = 0.93
        private const val MIN_MESH_SYMMETRY_SCORE = 0.40
        private const val MIN_LANDMARK_TOPOLOGY_SCORE = 0.70
        private const val MIN_CLEAN_ENROLLMENT_FACE_WIDTH_RATIO = 0.14
        private const val MIN_CLEAN_ENROLLMENT_FACE_HEIGHT_RATIO = 0.18
        private const val MAX_CLEAN_ENROLLMENT_FACE_WIDTH_RATIO = 0.88
        private const val MAX_CLEAN_ENROLLMENT_FACE_HEIGHT_RATIO = 0.92
        private const val MAX_CLEAN_ENROLLMENT_CENTER_OFFSET_X = 0.30
        private const val MAX_CLEAN_ENROLLMENT_CENTER_OFFSET_Y = 0.34
        private const val MIN_CLEAN_ENROLLMENT_IN_FRAME_LANDMARK_RATIO = 0.94
        private const val MIN_CLEAN_ENROLLMENT_MESH_SYMMETRY_SCORE = 0.45
        private const val MIN_CLEAN_ENROLLMENT_LANDMARK_TOPOLOGY_SCORE = 0.72
        private const val MIN_ENROLLMENT_CAPTURE_FACE_WIDTH_RATIO = 0.12
        private const val MIN_ENROLLMENT_CAPTURE_FACE_HEIGHT_RATIO = 0.16
        private const val MAX_ENROLLMENT_CAPTURE_FACE_WIDTH_RATIO = 0.92
        private const val MAX_ENROLLMENT_CAPTURE_FACE_HEIGHT_RATIO = 0.95
        private const val MAX_ENROLLMENT_CAPTURE_CENTER_OFFSET_X = 0.32
        private const val MAX_ENROLLMENT_CAPTURE_CENTER_OFFSET_Y = 0.38
        private const val MIN_ENROLLMENT_CAPTURE_IN_FRAME_LANDMARK_RATIO = 0.90
        private const val MIN_ENROLLMENT_CAPTURE_MESH_SYMMETRY_SCORE = 0.30
        private const val MIN_ENROLLMENT_CAPTURE_LANDMARK_TOPOLOGY_SCORE = 0.50
    }
}

data class FeatureEvidence(
    val type: FeatureType,
    val value: Double,
    val visibility: Double,
    val observable: Boolean,
    val reason: String = "visible"
)

data class ObservableFeatureFrame(
    val evidence: List<FeatureEvidence>,
    val coverage: Double,
    val observableCount: Int,
    val occlusionSummary: String
) {
    val observableEvidence: List<FeatureEvidence>
        get() = evidence.filter { it.observable }

    val effectiveObservableCount: Double
        get() = observableEvidence.sumOf { item -> item.visibility.coerceIn(0.0, 1.0) }
}

data class Point3(
    val x: Double,
    val y: Double,
    val z: Double
)



