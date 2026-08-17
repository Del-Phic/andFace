package dev.andface.galaxy.feature

import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class FaceFeatureExtractor {
    fun extract(
        landmarks: List<NormalizedLandmark>,
        facialTransformMatrix: FloatArray?,
        timestampMs: Long,
        faceBlendshapes: List<Category>? = null
    ): RawFeatureFrame? {
        if (landmarks.size < REQUIRED_LANDMARKS) return null

        val faceWidth = max(distance(landmarks, JAW_LEFT, JAW_RIGHT), EPSILON)
        val faceHeight = max(distance(landmarks, FOREHEAD, CHIN), EPSILON)
        val leftEyeWidth = max(distance(landmarks, LEFT_EYE_OUTER, LEFT_EYE_INNER), EPSILON)
        val rightEyeWidth = max(distance(landmarks, RIGHT_EYE_INNER, RIGHT_EYE_OUTER), EPSILON)

        val leftEyeCenter = midpoint(point(landmarks, LEFT_EYE_OUTER), point(landmarks, LEFT_EYE_INNER))
        val rightEyeCenter = midpoint(point(landmarks, RIGHT_EYE_INNER), point(landmarks, RIGHT_EYE_OUTER))
        val leftIrisCenter = irisCenterOrFallback(landmarks, LEFT_IRIS_INDICES, leftEyeCenter)
        val rightIrisCenter = irisCenterOrFallback(landmarks, RIGHT_IRIS_INDICES, rightEyeCenter)
        val mouthCenter = midpoint(point(landmarks, MOUTH_TOP), point(landmarks, MOUTH_BOTTOM))
        val noseTip = point(landmarks, NOSE_TIP)
        val forehead = point(landmarks, FOREHEAD)
        val jawCenter = midpoint(point(landmarks, JAW_LEFT), point(landmarks, JAW_RIGHT))
        val faceCenter = midpoint(forehead, point(landmarks, CHIN))
        val blendshapeActivity = blendshapeActivity(faceBlendshapes)
        val noseBridgeTop = point(landmarks, NOSE_BRIDGE_TOP)
        val leftTemple = point(landmarks, LEFT_TEMPLE)
        val rightTemple = point(landmarks, RIGHT_TEMPLE)
        val leftCheek = point(landmarks, LEFT_CHEEK)
        val rightCheek = point(landmarks, RIGHT_CHEEK)

        val pose = estimatePose(
            landmarks = landmarks,
            facialTransformMatrix = facialTransformMatrix,
            leftEyeCenter = leftEyeCenter,
            rightEyeCenter = rightEyeCenter,
            noseTip = noseTip,
            faceCenter = faceCenter,
            faceWidth = faceWidth,
            faceHeight = faceHeight
        )

        val leftEyeHeight = distance(landmarks, LEFT_EYE_TOP, LEFT_EYE_BOTTOM) / faceHeight
        val rightEyeHeight = distance(landmarks, RIGHT_EYE_TOP, RIGHT_EYE_BOTTOM) / faceHeight
        val leftEyeOpen = distance(landmarks, LEFT_EYE_TOP, LEFT_EYE_BOTTOM) / leftEyeWidth
        val rightEyeOpen = distance(landmarks, RIGHT_EYE_TOP, RIGHT_EYE_BOTTOM) / rightEyeWidth
        val leftBrowWidth = distance(landmarks, LEFT_BROW_A, LEFT_BROW_B) / faceWidth
        val rightBrowWidth = distance(landmarks, RIGHT_BROW_A, RIGHT_BROW_B) / faceWidth
        val leftEyeToNose = distance(leftEyeCenter, noseTip) / faceHeight
        val rightEyeToNose = distance(rightEyeCenter, noseTip) / faceHeight
        val leftCheekToNose = distance(leftCheek, noseTip) / faceWidth
        val rightCheekToNose = distance(rightCheek, noseTip) / faceWidth
        val leftBrowA = point(landmarks, LEFT_BROW_A)
        val leftBrowB = point(landmarks, LEFT_BROW_B)
        val leftBrowMid = point(landmarks, LEFT_BROW_MID)
        val rightBrowA = point(landmarks, RIGHT_BROW_A)
        val rightBrowB = point(landmarks, RIGHT_BROW_B)
        val rightBrowMid = point(landmarks, RIGHT_BROW_MID)
        val leftBrowCenter = average(leftBrowA, leftBrowB)
        val rightBrowCenter = average(rightBrowA, rightBrowB)
        val browLineCenter = midpoint(leftBrowCenter, rightBrowCenter)
        val eyeLineCenter = midpoint(leftEyeCenter, rightEyeCenter)
        val leftBrowArchHeight = pointLineDistance(leftBrowMid, leftBrowA, leftBrowB) / faceHeight
        val rightBrowArchHeight = pointLineDistance(rightBrowMid, rightBrowA, rightBrowB) / faceHeight
        val browLineTilt = abs(leftBrowCenter.y - rightBrowCenter.y) /
            max(distance(leftBrowCenter, rightBrowCenter), EPSILON)
        val leftEyeInner = point(landmarks, LEFT_EYE_INNER)
        val rightEyeInner = point(landmarks, RIGHT_EYE_INNER)
        val leftEyeOuter = point(landmarks, LEFT_EYE_OUTER)
        val rightEyeOuter = point(landmarks, RIGHT_EYE_OUTER)
        val leftEyeTopPoint = point(landmarks, LEFT_EYE_TOP)
        val rightEyeTopPoint = point(landmarks, RIGHT_EYE_TOP)
        val leftEyeBottomPoint = point(landmarks, LEFT_EYE_BOTTOM)
        val rightEyeBottomPoint = point(landmarks, RIGHT_EYE_BOTTOM)
        val leftEyeCornerTilt = abs(leftEyeOuter.y - leftEyeInner.y) / leftEyeWidth
        val rightEyeCornerTilt = abs(rightEyeOuter.y - rightEyeInner.y) / rightEyeWidth
        val leftUpperEyelidArch = pointLineDistance(leftEyeTopPoint, leftEyeOuter, leftEyeInner) / faceHeight
        val rightUpperEyelidArch = pointLineDistance(rightEyeTopPoint, rightEyeInner, rightEyeOuter) / faceHeight
        val leftLowerEyelidArch = pointLineDistance(leftEyeBottomPoint, leftEyeOuter, leftEyeInner) / faceHeight
        val rightLowerEyelidArch = pointLineDistance(rightEyeBottomPoint, rightEyeInner, rightEyeOuter) / faceHeight
        val leftBrowEyeGap = abs(point(landmarks, LEFT_EYE_TOP).y - leftBrowCenter.y) / faceHeight
        val rightBrowEyeGap = abs(point(landmarks, RIGHT_EYE_TOP).y - rightBrowCenter.y) / faceHeight
        val leftBrowOuterEyeGap = abs(leftEyeOuter.y - leftBrowA.y) / faceHeight
        val rightBrowOuterEyeGap = abs(rightEyeOuter.y - rightBrowB.y) / faceHeight
        val leftBrowInnerEyeGap = abs(leftEyeInner.y - leftBrowB.y) / faceHeight
        val rightBrowInnerEyeGap = abs(rightEyeInner.y - rightBrowA.y) / faceHeight
        val leftUnderEyeCheek = distance(leftEyeCenter, leftCheek) / faceHeight
        val rightUnderEyeCheek = distance(rightEyeCenter, rightCheek) / faceHeight
        val leftBrowNoseRoot = distance(leftBrowCenter, noseBridgeTop) / faceHeight
        val rightBrowNoseRoot = distance(rightBrowCenter, noseBridgeTop) / faceHeight
        val leftInnerEyeNoseRoot = distance(leftEyeInner, noseBridgeTop) / faceHeight
        val rightInnerEyeNoseRoot = distance(rightEyeInner, noseBridgeTop) / faceHeight
        val leftTempleEye = distance(leftTemple, leftEyeOuter) / faceWidth
        val rightTempleEye = distance(rightTemple, rightEyeOuter) / faceWidth
        val eyeWidthAsymmetry = abs(leftEyeWidth - rightEyeWidth) / max((leftEyeWidth + rightEyeWidth) * 0.5, EPSILON)
        val leftIrisEyeOffset = distance(leftIrisCenter, leftEyeCenter) / leftEyeWidth
        val rightIrisEyeOffset = distance(rightIrisCenter, rightEyeCenter) / rightEyeWidth
        val leftIrisNoseRoot = distance(leftIrisCenter, noseBridgeTop) / faceHeight
        val rightIrisNoseRoot = distance(rightIrisCenter, noseBridgeTop) / faceHeight
        val irisDistance = distance(leftIrisCenter, rightIrisCenter) / faceWidth
        val irisSpanRatio = distance(leftIrisCenter, rightIrisCenter) /
            max(distance(leftEyeCenter, rightEyeCenter), EPSILON)
        val leftBrowSlope = abs(leftBrowA.y - leftBrowB.y) / max(distance(leftBrowA, leftBrowB), EPSILON)
        val rightBrowSlope = abs(rightBrowA.y - rightBrowB.y) / max(distance(rightBrowA, rightBrowB), EPSILON)
        val leftBrowEyeDistance = distance(leftBrowCenter, leftEyeCenter) / faceHeight
        val rightBrowEyeDistance = distance(rightBrowCenter, rightEyeCenter) / faceHeight
        val noseRootEyeLineDistance = distance(noseBridgeTop, eyeLineCenter) / faceHeight
        val noseTipEyeLineDistance = distance(noseTip, eyeLineCenter) / faceHeight
        val noseLateralOffset = abs(noseTip.x - eyeLineCenter.x) / faceWidth
        val leftPeriocularArea = triangleArea(leftEyeOuter, leftEyeInner, leftBrowCenter) / (faceWidth * faceHeight)
        val rightPeriocularArea = triangleArea(rightEyeInner, rightEyeOuter, rightBrowCenter) / (faceWidth * faceHeight)
        val leftMidFaceTriangle = triangleArea(leftEyeCenter, noseTip, leftCheek) / (faceWidth * faceHeight)
        val rightMidFaceTriangle = triangleArea(rightEyeCenter, noseTip, rightCheek) / (faceWidth * faceHeight)
        val leftUpperMidfaceArea = triangleArea(leftEyeOuter, noseBridgeTop, leftCheek) / (faceWidth * faceHeight)
        val rightUpperMidfaceArea = triangleArea(rightEyeOuter, noseBridgeTop, rightCheek) / (faceWidth * faceHeight)
        val leftNoseCheekArea = triangleArea(noseBridgeTop, noseTip, leftCheek) / (faceWidth * faceHeight)
        val rightNoseCheekArea = triangleArea(noseBridgeTop, noseTip, rightCheek) / (faceWidth * faceHeight)
        val leftTempleCheekSlope = verticalSlope(leftTemple, leftCheek)
        val rightTempleCheekSlope = verticalSlope(rightTemple, rightCheek)
        val midfaceWidthRatio = distance(leftCheek, rightCheek) / max(distance(leftTemple, rightTemple), EPSILON)
        val innerEyeNoseTipTriangle = triangleArea(leftEyeInner, rightEyeInner, noseTip) / (faceWidth * faceHeight)
        val browNoseRootTriangle = triangleArea(leftBrowCenter, rightBrowCenter, noseBridgeTop) / (faceWidth * faceHeight)
        val noseRootLateralOffset = abs(noseBridgeTop.x - browLineCenter.x) / faceWidth
        val noseBridgeBrowLineOffset = pointLineDistance(noseBridgeTop, leftBrowCenter, rightBrowCenter) / faceHeight
        val noseBridgeEyeLineOffset = pointLineDistance(noseBridgeTop, leftEyeCenter, rightEyeCenter) / faceHeight
        val leftTempleBrowDistance = distance(leftTemple, leftBrowCenter) / faceWidth
        val rightTempleBrowDistance = distance(rightTemple, rightBrowCenter) / faceWidth
        val foreheadBrowTriangle = triangleArea(forehead, leftBrowCenter, rightBrowCenter) / (faceWidth * faceHeight)
        val foreheadEyeTriangle = triangleArea(forehead, leftEyeCenter, rightEyeCenter) / (faceWidth * faceHeight)
        val upperFaceTaper = distance(leftBrowCenter, rightBrowCenter) / max(distance(leftTemple, rightTemple), EPSILON)
        val leftBrowTempleSlope = verticalSlope(leftTemple, leftBrowCenter)
        val rightBrowTempleSlope = verticalSlope(rightTemple, rightBrowCenter)
        val eyeLineBrowLineGap = distance(eyeLineCenter, browLineCenter) / faceHeight
        val leftOuterEyeNoseRoot = distance(leftEyeOuter, noseBridgeTop) / faceHeight
        val rightOuterEyeNoseRoot = distance(rightEyeOuter, noseBridgeTop) / faceHeight
        val leftEyeForeheadDistance = distance(leftEyeCenter, forehead) / faceHeight
        val rightEyeForeheadDistance = distance(rightEyeCenter, forehead) / faceHeight
        val noseRootForeheadDistance = distance(noseBridgeTop, forehead) / faceHeight
        val upperFaceDiagonalRatio = (
            distance(leftTemple, rightBrowCenter) + distance(rightTemple, leftBrowCenter)
            ) * 0.5 / faceWidth
        val upperFacePerimeterRatio = (
            distance(leftTemple, forehead) + distance(forehead, rightTemple) +
                distance(rightTemple, rightBrowCenter) + distance(rightBrowCenter, leftBrowCenter) +
                distance(leftBrowCenter, leftTemple)
            ) / max(faceWidth + faceHeight, EPSILON)
        val leftOrbitalTriangle = triangleArea(leftTemple, leftEyeOuter, leftBrowCenter) / (faceWidth * faceHeight)
        val rightOrbitalTriangle = triangleArea(rightTemple, rightBrowCenter, rightEyeOuter) / (faceWidth * faceHeight)
        val leftInnerBrowNoseArea = triangleArea(leftBrowCenter, leftEyeInner, noseBridgeTop) / (faceWidth * faceHeight)
        val rightInnerBrowNoseArea = triangleArea(rightBrowCenter, noseBridgeTop, rightEyeInner) / (faceWidth * faceHeight)
        val browSpanEyeSpanRatio = distance(leftBrowCenter, rightBrowCenter) /
            max(distance(leftEyeCenter, rightEyeCenter), EPSILON)
        val noseRootToEyeSpanRatio = distance(noseBridgeTop, eyeLineCenter) /
            max(distance(leftEyeCenter, rightEyeCenter), EPSILON)
        val noseTipDepth = abs(noseTip.z - noseBridgeTop.z) / faceWidth
        val leftEyeNoseDepth = abs(noseTip.z - leftEyeCenter.z) / faceWidth
        val rightEyeNoseDepth = abs(noseTip.z - rightEyeCenter.z) / faceWidth
        val leftCheekNoseDepth = abs(noseTip.z - leftCheek.z) / faceWidth
        val rightCheekNoseDepth = abs(noseTip.z - rightCheek.z) / faceWidth
        val leftForeheadTempleArea = triangleArea(forehead, leftTemple, leftBrowCenter) / (faceWidth * faceHeight)
        val rightForeheadTempleArea = triangleArea(forehead, rightBrowCenter, rightTemple) / (faceWidth * faceHeight)
        val leftEyeBrowNoseTriangle = triangleArea(leftEyeCenter, leftBrowCenter, noseBridgeTop) / (faceWidth * faceHeight)
        val rightEyeBrowNoseTriangle = triangleArea(rightEyeCenter, noseBridgeTop, rightBrowCenter) / (faceWidth * faceHeight)
        val noseBridgeEyeTriangle = triangleArea(leftEyeCenter, rightEyeCenter, noseBridgeTop) / (faceWidth * faceHeight)
        val noseBridgeToEyeSpanRatio = distance(noseBridgeTop, noseTip) /
            max(distance(leftEyeCenter, rightEyeCenter), EPSILON)
        val leftInnerEyeNoseBridgeTriangle = triangleArea(leftEyeInner, noseBridgeTop, noseTip) / (faceWidth * faceHeight)
        val rightInnerEyeNoseBridgeTriangle = triangleArea(rightEyeInner, noseTip, noseBridgeTop) / (faceWidth * faceHeight)
        val noseTipInnerEyeLineDistance = pointLineDistance(noseTip, leftEyeInner, rightEyeInner) / faceHeight
        val noseBridgeInnerEyeSpanRatio = distance(noseBridgeTop, noseTip) /
            max(distance(leftEyeInner, rightEyeInner), EPSILON)
        val noseTipDepthToEyeSpanRatio = abs(noseTip.z - noseBridgeTop.z) /
            max(distance(leftEyeInner, rightEyeInner), EPSILON)
        val leftJawCheekDistance = distance(leftCheek, point(landmarks, JAW_LEFT)) / faceHeight
        val rightJawCheekDistance = distance(rightCheek, point(landmarks, JAW_RIGHT)) / faceHeight
        val chinLateralOffset = abs(point(landmarks, CHIN).x - jawCenter.x) / faceWidth
        val meshSymmetryScore = listOf(
            symmetryScore(leftEyeWidth, rightEyeWidth),
            symmetryScore(leftEyeToNose, rightEyeToNose),
            symmetryScore(leftBrowWidth, rightBrowWidth),
            symmetryScore(leftUnderEyeCheek, rightUnderEyeCheek),
            symmetryScore(leftTempleEye, rightTempleEye),
            symmetryScore(leftMidFaceTriangle, rightMidFaceTriangle),
            symmetryScore(leftUpperMidfaceArea, rightUpperMidfaceArea),
            symmetryScore(leftNoseCheekArea, rightNoseCheekArea),
            symmetryScore(leftBrowArchHeight, rightBrowArchHeight),
            symmetryScore(leftEyeCornerTilt, rightEyeCornerTilt),
            symmetryScore(leftTempleBrowDistance, rightTempleBrowDistance),
            symmetryScore(leftBrowTempleSlope, rightBrowTempleSlope),
            symmetryScore(leftOuterEyeNoseRoot, rightOuterEyeNoseRoot),
            symmetryScore(leftEyeForeheadDistance, rightEyeForeheadDistance),
            symmetryScore(leftOrbitalTriangle, rightOrbitalTriangle),
            symmetryScore(leftInnerBrowNoseArea, rightInnerBrowNoseArea),
            symmetryScore(leftForeheadTempleArea, rightForeheadTempleArea),
            symmetryScore(leftEyeBrowNoseTriangle, rightEyeBrowNoseTriangle),
            symmetryScore(leftInnerEyeNoseBridgeTriangle, rightInnerEyeNoseBridgeTriangle),
            symmetryScore(leftUpperEyelidArch, rightUpperEyelidArch),
            symmetryScore(leftLowerEyelidArch, rightLowerEyelidArch),
            symmetryScore(leftJawCheekDistance, rightJawCheekDistance)
        ).average().coerceIn(0.0, 1.0)
        val leftNoseWing = distance(point(landmarks, NOSE_LEFT), noseTip) / faceWidth
        val rightNoseWing = distance(point(landmarks, NOSE_RIGHT), noseTip) / faceWidth
        val noseBaseTriangle = triangleArea(point(landmarks, NOSE_LEFT), noseTip, point(landmarks, NOSE_RIGHT)) /
            (faceWidth * faceHeight)
        val mouthWidth = distance(landmarks, MOUTH_LEFT, MOUTH_RIGHT) / faceWidth
        val mouthHeight = distance(landmarks, MOUTH_TOP, MOUTH_BOTTOM) / faceHeight
        val mouthAspect = mouthHeight / max(mouthWidth, EPSILON)
        val chinMouthDistance = distance(mouthCenter, point(landmarks, CHIN)) / faceHeight
        val chinJawOffset = distance(point(landmarks, CHIN), jawCenter) / faceHeight

        val values = DoubleArray(FeatureType.COUNT)
        values[FeatureType.EyeDistance.ordinal] = distance(leftEyeCenter, rightEyeCenter) / faceWidth
        values[FeatureType.BrowDistance.ordinal] = browDistance(landmarks, faceHeight)
        values[FeatureType.NoseWidth.ordinal] = distance(landmarks, NOSE_LEFT, NOSE_RIGHT) / faceWidth
        values[FeatureType.NoseToChin.ordinal] = distance(noseTip, point(landmarks, CHIN)) / faceHeight
        values[FeatureType.MouthWidth.ordinal] = mouthWidth
        values[FeatureType.JawWidth.ordinal] = faceWidth / max(distance(leftEyeCenter, rightEyeCenter), EPSILON)
        values[FeatureType.LeftEyeOpen.ordinal] = leftEyeOpen
        values[FeatureType.RightEyeOpen.ordinal] = rightEyeOpen
        values[FeatureType.NoseToMouth.ordinal] = distance(noseTip, mouthCenter) / faceHeight
        values[FeatureType.FaceAspect.ordinal] = distance(point(landmarks, FOREHEAD), point(landmarks, CHIN)) /
            max(distance(point(landmarks, JAW_LEFT), point(landmarks, JAW_RIGHT)), EPSILON)
        values[FeatureType.Yaw.ordinal] = pose.yaw
        values[FeatureType.Pitch.ordinal] = pose.pitch
        values[FeatureType.Roll.ordinal] = pose.roll
        values[FeatureType.InnerEyeDistance.ordinal] = distance(landmarks, LEFT_EYE_INNER, RIGHT_EYE_INNER) / faceWidth
        values[FeatureType.LeftEyeWidth.ordinal] = leftEyeWidth / faceWidth
        values[FeatureType.RightEyeWidth.ordinal] = rightEyeWidth / faceWidth
        values[FeatureType.EyeOpenAsymmetry.ordinal] = abs(leftEyeOpen - rightEyeOpen) /
            max((leftEyeOpen + rightEyeOpen) * 0.5, EPSILON)
        values[FeatureType.BrowWidth.ordinal] = (leftBrowWidth + rightBrowWidth) * 0.5
        values[FeatureType.BrowAsymmetry.ordinal] = abs(leftBrowWidth - rightBrowWidth)
        values[FeatureType.NoseBridgeLength.ordinal] = distance(noseBridgeTop, noseTip) / faceHeight
        values[FeatureType.EyeNoseLeft.ordinal] = leftEyeToNose
        values[FeatureType.EyeNoseRight.ordinal] = rightEyeToNose
        values[FeatureType.EyeNoseSymmetry.ordinal] = abs(leftEyeToNose - rightEyeToNose) / max(faceWidth, EPSILON)
        values[FeatureType.UpperFaceWidth.ordinal] = distance(leftTemple, rightTemple) / faceWidth
        values[FeatureType.CheekboneWidth.ordinal] = distance(leftCheek, rightCheek) / faceWidth
        values[FeatureType.LeftCheekNose.ordinal] = leftCheekToNose
        values[FeatureType.RightCheekNose.ordinal] = rightCheekToNose
        values[FeatureType.CheekNoseSymmetry.ordinal] = abs(leftCheekToNose - rightCheekToNose)
        values[FeatureType.LeftBrowEyeGap.ordinal] = leftBrowEyeGap
        values[FeatureType.RightBrowEyeGap.ordinal] = rightBrowEyeGap
        values[FeatureType.BrowEyeGapAsymmetry.ordinal] = abs(leftBrowEyeGap - rightBrowEyeGap)
        values[FeatureType.LeftUnderEyeCheek.ordinal] = leftUnderEyeCheek
        values[FeatureType.RightUnderEyeCheek.ordinal] = rightUnderEyeCheek
        values[FeatureType.UnderEyeCheekAsymmetry.ordinal] = abs(leftUnderEyeCheek - rightUnderEyeCheek)
        values[FeatureType.InterBrowDistance.ordinal] = distance(leftBrowCenter, rightBrowCenter) / faceWidth
        values[FeatureType.LeftBrowNoseRoot.ordinal] = leftBrowNoseRoot
        values[FeatureType.RightBrowNoseRoot.ordinal] = rightBrowNoseRoot
        values[FeatureType.BrowNoseRootAsymmetry.ordinal] = abs(leftBrowNoseRoot - rightBrowNoseRoot)
        values[FeatureType.LeftInnerEyeNoseRoot.ordinal] = leftInnerEyeNoseRoot
        values[FeatureType.RightInnerEyeNoseRoot.ordinal] = rightInnerEyeNoseRoot
        values[FeatureType.InnerEyeNoseRootAsymmetry.ordinal] = abs(leftInnerEyeNoseRoot - rightInnerEyeNoseRoot)
        values[FeatureType.LeftTempleEye.ordinal] = leftTempleEye
        values[FeatureType.RightTempleEye.ordinal] = rightTempleEye
        values[FeatureType.TempleEyeAsymmetry.ordinal] = abs(leftTempleEye - rightTempleEye)
        values[FeatureType.EyeWidthAsymmetry.ordinal] = eyeWidthAsymmetry
        values[FeatureType.LeftBrowSlope.ordinal] = leftBrowSlope
        values[FeatureType.RightBrowSlope.ordinal] = rightBrowSlope
        values[FeatureType.BrowSlopeAsymmetry.ordinal] = abs(leftBrowSlope - rightBrowSlope)
        values[FeatureType.LeftEyeHeight.ordinal] = leftEyeHeight
        values[FeatureType.RightEyeHeight.ordinal] = rightEyeHeight
        values[FeatureType.EyeHeightAsymmetry.ordinal] = abs(leftEyeHeight - rightEyeHeight) /
            max((leftEyeHeight + rightEyeHeight) * 0.5, EPSILON)
        values[FeatureType.LeftBrowWidth.ordinal] = leftBrowWidth
        values[FeatureType.RightBrowWidth.ordinal] = rightBrowWidth
        values[FeatureType.LeftBrowOuterEyeGap.ordinal] = leftBrowOuterEyeGap
        values[FeatureType.RightBrowOuterEyeGap.ordinal] = rightBrowOuterEyeGap
        values[FeatureType.BrowOuterGapAsymmetry.ordinal] = abs(leftBrowOuterEyeGap - rightBrowOuterEyeGap)
        values[FeatureType.LeftBrowInnerEyeGap.ordinal] = leftBrowInnerEyeGap
        values[FeatureType.RightBrowInnerEyeGap.ordinal] = rightBrowInnerEyeGap
        values[FeatureType.BrowInnerGapAsymmetry.ordinal] = abs(leftBrowInnerEyeGap - rightBrowInnerEyeGap)
        values[FeatureType.NoseRootToBrowLine.ordinal] = distance(noseBridgeTop, browLineCenter) / faceHeight
        values[FeatureType.ForeheadToEyeLine.ordinal] = distance(forehead, eyeLineCenter) / faceHeight
        values[FeatureType.UpperFaceAspect.ordinal] = distance(forehead, noseBridgeTop) /
            max(distance(leftTemple, rightTemple), EPSILON)
        values[FeatureType.LeftBrowEyeDistance.ordinal] = leftBrowEyeDistance
        values[FeatureType.RightBrowEyeDistance.ordinal] = rightBrowEyeDistance
        values[FeatureType.BrowEyeDistanceAsymmetry.ordinal] = abs(leftBrowEyeDistance - rightBrowEyeDistance)
        values[FeatureType.NoseRootEyeLineDistance.ordinal] = noseRootEyeLineDistance
        values[FeatureType.NoseTipEyeLineDistance.ordinal] = noseTipEyeLineDistance
        values[FeatureType.NoseLateralOffset.ordinal] = noseLateralOffset
        values[FeatureType.LeftPeriocularArea.ordinal] = leftPeriocularArea
        values[FeatureType.RightPeriocularArea.ordinal] = rightPeriocularArea
        values[FeatureType.PeriocularAreaAsymmetry.ordinal] = abs(leftPeriocularArea - rightPeriocularArea)
        values[FeatureType.LeftMidFaceTriangle.ordinal] = leftMidFaceTriangle
        values[FeatureType.RightMidFaceTriangle.ordinal] = rightMidFaceTriangle
        values[FeatureType.MidFaceTriangleAsymmetry.ordinal] = abs(leftMidFaceTriangle - rightMidFaceTriangle)
        values[FeatureType.LeftUpperMidfaceArea.ordinal] = leftUpperMidfaceArea
        values[FeatureType.RightUpperMidfaceArea.ordinal] = rightUpperMidfaceArea
        values[FeatureType.UpperMidfaceAreaAsymmetry.ordinal] = abs(leftUpperMidfaceArea - rightUpperMidfaceArea)
        values[FeatureType.LeftNoseCheekArea.ordinal] = leftNoseCheekArea
        values[FeatureType.RightNoseCheekArea.ordinal] = rightNoseCheekArea
        values[FeatureType.NoseCheekAreaAsymmetry.ordinal] = abs(leftNoseCheekArea - rightNoseCheekArea)
        values[FeatureType.LeftTempleCheekSlope.ordinal] = leftTempleCheekSlope
        values[FeatureType.RightTempleCheekSlope.ordinal] = rightTempleCheekSlope
        values[FeatureType.TempleCheekSlopeAsymmetry.ordinal] = abs(leftTempleCheekSlope - rightTempleCheekSlope)
        values[FeatureType.MidfaceWidthRatio.ordinal] = midfaceWidthRatio
        values[FeatureType.LeftBrowArchHeight.ordinal] = leftBrowArchHeight
        values[FeatureType.RightBrowArchHeight.ordinal] = rightBrowArchHeight
        values[FeatureType.BrowArchAsymmetry.ordinal] = abs(leftBrowArchHeight - rightBrowArchHeight)
        values[FeatureType.BrowLineTilt.ordinal] = browLineTilt
        values[FeatureType.LeftEyeCornerTilt.ordinal] = leftEyeCornerTilt
        values[FeatureType.RightEyeCornerTilt.ordinal] = rightEyeCornerTilt
        values[FeatureType.EyeCornerTiltAsymmetry.ordinal] = abs(leftEyeCornerTilt - rightEyeCornerTilt)
        values[FeatureType.InnerEyeNoseTipTriangle.ordinal] = innerEyeNoseTipTriangle
        values[FeatureType.BrowNoseRootTriangle.ordinal] = browNoseRootTriangle
        values[FeatureType.NoseRootLateralOffset.ordinal] = noseRootLateralOffset
        values[FeatureType.LeftTempleBrowDistance.ordinal] = leftTempleBrowDistance
        values[FeatureType.RightTempleBrowDistance.ordinal] = rightTempleBrowDistance
        values[FeatureType.TempleBrowDistanceAsymmetry.ordinal] = abs(leftTempleBrowDistance - rightTempleBrowDistance)
        values[FeatureType.ForeheadBrowTriangle.ordinal] = foreheadBrowTriangle
        values[FeatureType.ForeheadEyeTriangle.ordinal] = foreheadEyeTriangle
        values[FeatureType.UpperFaceTaper.ordinal] = upperFaceTaper
        values[FeatureType.LeftBrowTempleSlope.ordinal] = leftBrowTempleSlope
        values[FeatureType.RightBrowTempleSlope.ordinal] = rightBrowTempleSlope
        values[FeatureType.BrowTempleSlopeAsymmetry.ordinal] = abs(leftBrowTempleSlope - rightBrowTempleSlope)
        values[FeatureType.LeftNoseWing.ordinal] = leftNoseWing
        values[FeatureType.RightNoseWing.ordinal] = rightNoseWing
        values[FeatureType.NoseWingAsymmetry.ordinal] = abs(leftNoseWing - rightNoseWing)
        values[FeatureType.NoseBaseTriangle.ordinal] = noseBaseTriangle
        values[FeatureType.MouthHeight.ordinal] = mouthHeight
        values[FeatureType.MouthAspect.ordinal] = mouthAspect
        values[FeatureType.ChinMouthDistance.ordinal] = chinMouthDistance
        values[FeatureType.ChinJawOffset.ordinal] = chinJawOffset
        values[FeatureType.LeftIrisEyeOffset.ordinal] = leftIrisEyeOffset
        values[FeatureType.RightIrisEyeOffset.ordinal] = rightIrisEyeOffset
        values[FeatureType.IrisOffsetAsymmetry.ordinal] = abs(leftIrisEyeOffset - rightIrisEyeOffset)
        values[FeatureType.IrisDistance.ordinal] = irisDistance
        values[FeatureType.LeftIrisNoseRoot.ordinal] = leftIrisNoseRoot
        values[FeatureType.RightIrisNoseRoot.ordinal] = rightIrisNoseRoot
        values[FeatureType.IrisNoseRootAsymmetry.ordinal] = abs(leftIrisNoseRoot - rightIrisNoseRoot)
        values[FeatureType.IrisSpanRatio.ordinal] = irisSpanRatio
        values[FeatureType.EyeLineBrowLineGap.ordinal] = eyeLineBrowLineGap
        values[FeatureType.LeftOuterEyeNoseRoot.ordinal] = leftOuterEyeNoseRoot
        values[FeatureType.RightOuterEyeNoseRoot.ordinal] = rightOuterEyeNoseRoot
        values[FeatureType.OuterEyeNoseRootAsymmetry.ordinal] = abs(leftOuterEyeNoseRoot - rightOuterEyeNoseRoot)
        values[FeatureType.LeftEyeForeheadDistance.ordinal] = leftEyeForeheadDistance
        values[FeatureType.RightEyeForeheadDistance.ordinal] = rightEyeForeheadDistance
        values[FeatureType.EyeForeheadAsymmetry.ordinal] = abs(leftEyeForeheadDistance - rightEyeForeheadDistance)
        values[FeatureType.NoseRootForeheadDistance.ordinal] = noseRootForeheadDistance
        values[FeatureType.UpperFaceDiagonalRatio.ordinal] = upperFaceDiagonalRatio
        values[FeatureType.UpperFacePerimeterRatio.ordinal] = upperFacePerimeterRatio
        values[FeatureType.LeftJawCheekDistance.ordinal] = leftJawCheekDistance
        values[FeatureType.RightJawCheekDistance.ordinal] = rightJawCheekDistance
        values[FeatureType.JawCheekAsymmetry.ordinal] = abs(leftJawCheekDistance - rightJawCheekDistance)
        values[FeatureType.ChinLateralOffset.ordinal] = chinLateralOffset
        values[FeatureType.LeftOrbitalTriangle.ordinal] = leftOrbitalTriangle
        values[FeatureType.RightOrbitalTriangle.ordinal] = rightOrbitalTriangle
        values[FeatureType.OrbitalTriangleAsymmetry.ordinal] = abs(leftOrbitalTriangle - rightOrbitalTriangle)
        values[FeatureType.LeftInnerBrowNoseArea.ordinal] = leftInnerBrowNoseArea
        values[FeatureType.RightInnerBrowNoseArea.ordinal] = rightInnerBrowNoseArea
        values[FeatureType.InnerBrowNoseAreaAsymmetry.ordinal] = abs(leftInnerBrowNoseArea - rightInnerBrowNoseArea)
        values[FeatureType.BrowSpanEyeSpanRatio.ordinal] = browSpanEyeSpanRatio
        values[FeatureType.NoseRootToEyeSpanRatio.ordinal] = noseRootToEyeSpanRatio
        values[FeatureType.NoseTipDepth.ordinal] = noseTipDepth
        values[FeatureType.LeftEyeNoseDepth.ordinal] = leftEyeNoseDepth
        values[FeatureType.RightEyeNoseDepth.ordinal] = rightEyeNoseDepth
        values[FeatureType.EyeNoseDepthAsymmetry.ordinal] = abs(leftEyeNoseDepth - rightEyeNoseDepth)
        values[FeatureType.LeftCheekNoseDepth.ordinal] = leftCheekNoseDepth
        values[FeatureType.RightCheekNoseDepth.ordinal] = rightCheekNoseDepth
        values[FeatureType.CheekNoseDepthAsymmetry.ordinal] = abs(leftCheekNoseDepth - rightCheekNoseDepth)
        values[FeatureType.LeftForeheadTempleArea.ordinal] = leftForeheadTempleArea
        values[FeatureType.RightForeheadTempleArea.ordinal] = rightForeheadTempleArea
        values[FeatureType.ForeheadTempleAreaAsymmetry.ordinal] = abs(leftForeheadTempleArea - rightForeheadTempleArea)
        values[FeatureType.LeftEyeBrowNoseTriangle.ordinal] = leftEyeBrowNoseTriangle
        values[FeatureType.RightEyeBrowNoseTriangle.ordinal] = rightEyeBrowNoseTriangle
        values[FeatureType.EyeBrowNoseTriangleAsymmetry.ordinal] = abs(leftEyeBrowNoseTriangle - rightEyeBrowNoseTriangle)
        values[FeatureType.NoseBridgeEyeTriangle.ordinal] = noseBridgeEyeTriangle
        values[FeatureType.NoseBridgeToEyeSpanRatio.ordinal] = noseBridgeToEyeSpanRatio
        values[FeatureType.LeftInnerEyeNoseBridgeTriangle.ordinal] = leftInnerEyeNoseBridgeTriangle
        values[FeatureType.RightInnerEyeNoseBridgeTriangle.ordinal] = rightInnerEyeNoseBridgeTriangle
        values[FeatureType.InnerEyeNoseBridgeTriangleAsymmetry.ordinal] = abs(leftInnerEyeNoseBridgeTriangle - rightInnerEyeNoseBridgeTriangle)
        values[FeatureType.NoseTipInnerEyeLineDistance.ordinal] = noseTipInnerEyeLineDistance
        values[FeatureType.NoseBridgeInnerEyeSpanRatio.ordinal] = noseBridgeInnerEyeSpanRatio
        values[FeatureType.NoseTipDepthToEyeSpanRatio.ordinal] = noseTipDepthToEyeSpanRatio
        values[FeatureType.LeftUpperEyelidArch.ordinal] = leftUpperEyelidArch
        values[FeatureType.RightUpperEyelidArch.ordinal] = rightUpperEyelidArch
        values[FeatureType.UpperEyelidArchAsymmetry.ordinal] = abs(leftUpperEyelidArch - rightUpperEyelidArch)
        values[FeatureType.LeftLowerEyelidArch.ordinal] = leftLowerEyelidArch
        values[FeatureType.RightLowerEyelidArch.ordinal] = rightLowerEyelidArch
        values[FeatureType.LowerEyelidArchAsymmetry.ordinal] = abs(leftLowerEyelidArch - rightLowerEyelidArch)
        values[FeatureType.NoseBridgeBrowLineOffset.ordinal] = noseBridgeBrowLineOffset
        values[FeatureType.NoseBridgeEyeLineOffset.ordinal] = noseBridgeEyeLineOffset

        return RawFeatureFrame(
            values = values,
            timestampMs = timestampMs,
            quality = frameQuality(
                landmarks = landmarks,
                meshSymmetryScore = meshSymmetryScore,
                blendshapesAvailable = !faceBlendshapes.isNullOrEmpty(),
                blendshapeActivityScore = blendshapeActivity.overallActivityScore,
                blendshapeLeftEyeActivityScore = blendshapeActivity.leftEyeActivityScore,
                blendshapeRightEyeActivityScore = blendshapeActivity.rightEyeActivityScore,
                blendshapeBrowActivityScore = blendshapeActivity.browActivityScore,
                blendshapeMouthActivityScore = blendshapeActivity.mouthActivityScore
            )
        )
    }

    private fun frameQuality(
        landmarks: List<NormalizedLandmark>,
        meshSymmetryScore: Double,
        blendshapesAvailable: Boolean,
        blendshapeActivityScore: Double,
        blendshapeLeftEyeActivityScore: Double,
        blendshapeRightEyeActivityScore: Double,
        blendshapeBrowActivityScore: Double,
        blendshapeMouthActivityScore: Double
    ): FaceFrameQuality {
        val minX = landmarks.minOf { it.x().toDouble() }
        val maxX = landmarks.maxOf { it.x().toDouble() }
        val minY = landmarks.minOf { it.y().toDouble() }
        val maxY = landmarks.maxOf { it.y().toDouble() }
        val inFrameCount = landmarks.count { landmark ->
            landmark.x() in 0.0f..1.0f && landmark.y() in 0.0f..1.0f
        }

        val topology = landmarkTopologyAssessment(landmarks)
        return FaceFrameQuality(
            faceWidthRatio = maxX - minX,
            faceHeightRatio = maxY - minY,
            centerX = (minX + maxX) * 0.5,
            centerY = (minY + maxY) * 0.5,
            inFrameLandmarkRatio = inFrameCount.toDouble() / landmarks.size.toDouble(),
            irisLandmarksAvailable = landmarks.size >= IRIS_LANDMARK_COUNT,
            meshSymmetryScore = meshSymmetryScore,
            landmarkTopologyScore = topology.score,
            landmarkTopologyHint = topology.worstCheck,
            blendshapesAvailable = blendshapesAvailable,
            blendshapeActivityScore = blendshapeActivityScore.coerceIn(0.0, 1.0),
            blendshapeLeftEyeActivityScore = blendshapeLeftEyeActivityScore.coerceIn(0.0, 1.0),
            blendshapeRightEyeActivityScore = blendshapeRightEyeActivityScore.coerceIn(0.0, 1.0),
            blendshapeBrowActivityScore = blendshapeBrowActivityScore.coerceIn(0.0, 1.0),
            blendshapeMouthActivityScore = blendshapeMouthActivityScore.coerceIn(0.0, 1.0)
        )
    }


    private fun landmarkTopologyAssessment(landmarks: List<NormalizedLandmark>): TopologyAssessment {
        val leftEyeCenter = midpoint(point(landmarks, LEFT_EYE_OUTER), point(landmarks, LEFT_EYE_INNER))
        val rightEyeCenter = midpoint(point(landmarks, RIGHT_EYE_INNER), point(landmarks, RIGHT_EYE_OUTER))
        val eyeLineCenter = midpoint(leftEyeCenter, rightEyeCenter)
        val leftBrowCenter = midpoint(point(landmarks, LEFT_BROW_A), point(landmarks, LEFT_BROW_B))
        val rightBrowCenter = midpoint(point(landmarks, RIGHT_BROW_A), point(landmarks, RIGHT_BROW_B))
        val browLineCenter = midpoint(leftBrowCenter, rightBrowCenter)
        val mouthCenter = midpoint(point(landmarks, MOUTH_TOP), point(landmarks, MOUTH_BOTTOM))

        val rigidTopologyScores = listOf(
            topologyCheck("jawWidth", separatedAxisScore(point(landmarks, JAW_LEFT).x, point(landmarks, JAW_RIGHT).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("templeWidth", separatedAxisScore(point(landmarks, LEFT_TEMPLE).x, point(landmarks, RIGHT_TEMPLE).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("leftEyeWidth", separatedAxisScore(point(landmarks, LEFT_EYE_OUTER).x, point(landmarks, LEFT_EYE_INNER).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("innerEyeGap", separatedAxisScore(point(landmarks, LEFT_EYE_INNER).x, point(landmarks, RIGHT_EYE_INNER).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("rightEyeWidth", separatedAxisScore(point(landmarks, RIGHT_EYE_INNER).x, point(landmarks, RIGHT_EYE_OUTER).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("noseWidth", separatedAxisScore(point(landmarks, NOSE_LEFT).x, point(landmarks, NOSE_RIGHT).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("mouthWidth", separatedAxisScore(point(landmarks, MOUTH_LEFT).x, point(landmarks, MOUTH_RIGHT).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("foreheadBrow", orderedAxisScore(point(landmarks, FOREHEAD).y, browLineCenter.y, TOPOLOGY_MIN_VERTICAL_GAP)),
            topologyCheck("browEye", orderedAxisScore(browLineCenter.y, eyeLineCenter.y, TOPOLOGY_MIN_VERTICAL_GAP)),
            topologyCheck("eyeNose", orderedAxisScore(eyeLineCenter.y, point(landmarks, NOSE_TIP).y, TOPOLOGY_MIN_VERTICAL_GAP)),
            topologyCheck("noseMouth", orderedAxisScore(point(landmarks, NOSE_TIP).y, mouthCenter.y, TOPOLOGY_MIN_VERTICAL_GAP)),
            topologyCheck("mouthChin", orderedAxisScore(mouthCenter.y, point(landmarks, CHIN).y, TOPOLOGY_MIN_VERTICAL_GAP)),
            topologyCheck("noseBridge", orderedAxisScore(point(landmarks, NOSE_BRIDGE_TOP).y, point(landmarks, NOSE_TIP).y, TOPOLOGY_MIN_VERTICAL_GAP))
        )
        val flexibleApertureScores = listOf(
            topologyCheck("leftEyeOpen", orderedAxisScore(point(landmarks, LEFT_EYE_TOP).y, point(landmarks, LEFT_EYE_BOTTOM).y, TOPOLOGY_MIN_VERTICAL_GAP)),
            topologyCheck("rightEyeOpen", orderedAxisScore(point(landmarks, RIGHT_EYE_TOP).y, point(landmarks, RIGHT_EYE_BOTTOM).y, TOPOLOGY_MIN_VERTICAL_GAP)),
            topologyCheck("mouthOpen", orderedAxisScore(point(landmarks, MOUTH_TOP).y, point(landmarks, MOUTH_BOTTOM).y, TOPOLOGY_MIN_VERTICAL_GAP))
        )
        val supportingScores = listOf(
            topologyCheck("browOuterWidth", separatedAxisScore(point(landmarks, LEFT_BROW_A).x, point(landmarks, RIGHT_BROW_A).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("browInnerWidth", separatedAxisScore(point(landmarks, LEFT_BROW_B).x, point(landmarks, RIGHT_BROW_B).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("cheekWidth", separatedAxisScore(point(landmarks, LEFT_CHEEK).x, point(landmarks, RIGHT_CHEEK).x, TOPOLOGY_MIN_HORIZONTAL_GAP)),
            topologyCheck("foreheadEye", orderedAxisScore(point(landmarks, FOREHEAD).y, eyeLineCenter.y, TOPOLOGY_MIN_VERTICAL_GAP)),
            topologyCheck("noseBridgeMouth", orderedAxisScore(point(landmarks, NOSE_BRIDGE_TOP).y, mouthCenter.y, TOPOLOGY_MIN_VERTICAL_GAP))
        )
        val allScores = rigidTopologyScores + supportingScores + flexibleApertureScores
        val worstCritical = rigidTopologyScores.minByOrNull { it.score }
        val criticalScore = worstCritical?.score ?: 0.0
        val meanScore = allScores.map { it.score }.average()
        val topologyScore = min(criticalScore, meanScore).coerceIn(0.0, 1.0)
        val worstCheck = allScores.minByOrNull { it.score }?.label.orEmpty()
        return TopologyAssessment(topologyScore, worstCheck)
    }

    private fun topologyCheck(label: String, score: Double): TopologyCheck {
        return TopologyCheck(label = label, score = score.coerceIn(0.0, 1.0))
    }

    private data class TopologyAssessment(
        val score: Double,
        val worstCheck: String
    )

    private data class TopologyCheck(
        val label: String,
        val score: Double
    )

    private fun orderedAxisScore(first: Double, second: Double, expectedGap: Double): Double {
        return ((second - first) / max(expectedGap, EPSILON)).coerceIn(0.0, 1.0)
    }

    private fun separatedAxisScore(first: Double, second: Double, expectedGap: Double): Double {
        return (abs(second - first) / max(expectedGap, EPSILON)).coerceIn(0.0, 1.0)
    }
    private data class BlendshapeActivity(
        val overallActivityScore: Double = 0.0,
        val leftEyeActivityScore: Double = 0.0,
        val rightEyeActivityScore: Double = 0.0,
        val browActivityScore: Double = 0.0,
        val mouthActivityScore: Double = 0.0
    )

    private fun blendshapeActivity(faceBlendshapes: List<Category>?): BlendshapeActivity {
        if (faceBlendshapes.isNullOrEmpty()) return BlendshapeActivity()

        val scoreByName = faceBlendshapes.associate { category ->
            category.categoryName().lowercase(Locale.US) to category.score().toDouble().coerceIn(0.0, 1.0)
        }
        val leftEye = maxBlendshapeScore(scoreByName, BLENDSHAPE_LEFT_EYE_LIVENESS_NAMES)
        val rightEye = maxBlendshapeScore(scoreByName, BLENDSHAPE_RIGHT_EYE_LIVENESS_NAMES)
        val brow = maxBlendshapeScore(scoreByName, BLENDSHAPE_BROW_LIVENESS_NAMES)
        val mouth = maxBlendshapeScore(scoreByName, BLENDSHAPE_MOUTH_LIVENESS_NAMES)
        return BlendshapeActivity(
            overallActivityScore = max(max(leftEye, rightEye), max(brow, mouth)),
            leftEyeActivityScore = leftEye,
            rightEyeActivityScore = rightEye,
            browActivityScore = brow,
            mouthActivityScore = mouth
        )
    }

    private fun maxBlendshapeScore(scoreByName: Map<String, Double>, names: Set<String>): Double {
        return names.maxOfOrNull { name -> scoreByName[name] ?: 0.0 } ?: 0.0
    }
    private fun browDistance(landmarks: List<NormalizedLandmark>, faceHeight: Double): Double {
        val leftBrow = average(point(landmarks, LEFT_BROW_A), point(landmarks, LEFT_BROW_B))
        val rightBrow = average(point(landmarks, RIGHT_BROW_A), point(landmarks, RIGHT_BROW_B))
        val leftEyeTop = point(landmarks, LEFT_EYE_TOP)
        val rightEyeTop = point(landmarks, RIGHT_EYE_TOP)
        val leftGap = abs(leftEyeTop.y - leftBrow.y)
        val rightGap = abs(rightEyeTop.y - rightBrow.y)
        return ((leftGap + rightGap) * 0.5) / max(faceHeight, EPSILON)
    }

    private fun estimatePose(
        landmarks: List<NormalizedLandmark>,
        facialTransformMatrix: FloatArray?,
        leftEyeCenter: Point3,
        rightEyeCenter: Point3,
        noseTip: Point3,
        faceCenter: Point3,
        faceWidth: Double,
        faceHeight: Double
    ): PoseValues {
        val fromMatrix = facialTransformMatrix?.takeIf { it.size >= 16 }?.let { matrixToPose(it) }
        if (fromMatrix != null) return fromMatrix

        val rollRadians = atan2(rightEyeCenter.y - leftEyeCenter.y, rightEyeCenter.x - leftEyeCenter.x)
        val yaw = ((noseTip.x - faceCenter.x) / max(faceWidth * 0.5, EPSILON)).coerceIn(-1.0, 1.0)
        val pitch = ((noseTip.y - faceCenter.y) / max(faceHeight * 0.5, EPSILON)).coerceIn(-1.0, 1.0)
        val roll = (Math.toDegrees(rollRadians) / 45.0).coerceIn(-1.0, 1.0)

        val leftJaw = point(landmarks, JAW_LEFT)
        val rightJaw = point(landmarks, JAW_RIGHT)
        val jawYawCorrection = ((distance(noseTip, leftJaw) - distance(noseTip, rightJaw)) / faceWidth)
            .coerceIn(-0.5, 0.5)
        return PoseValues(
            yaw = (yaw + jawYawCorrection).coerceIn(-1.0, 1.0),
            pitch = pitch,
            roll = roll
        )
    }

    private fun matrixToPose(matrix: FloatArray): PoseValues {
        val r00 = matrix[0].toDouble()
        val r01 = matrix[1].toDouble()
        val r02 = matrix[2].toDouble()
        val r10 = matrix[4].toDouble()
        val r11 = matrix[5].toDouble()
        val r12 = matrix[6].toDouble()
        val r20 = matrix[8].toDouble()
        val r21 = matrix[9].toDouble()
        val r22 = matrix[10].toDouble()

        val sy = sqrt(r00 * r00 + r10 * r10)
        val singular = sy < 1e-6

        val pitchRadians: Double
        val yawRadians: Double
        val rollRadians: Double
        if (!singular) {
            pitchRadians = atan2(r21, r22)
            yawRadians = atan2(-r20, sy)
            rollRadians = atan2(r10, r00)
        } else {
            pitchRadians = atan2(-r12, r11)
            yawRadians = atan2(-r20, sy)
            rollRadians = 0.0
        }

        return PoseValues(
            yaw = (Math.toDegrees(yawRadians) / 45.0).coerceIn(-1.0, 1.0),
            pitch = (Math.toDegrees(pitchRadians) / 45.0).coerceIn(-1.0, 1.0),
            roll = (Math.toDegrees(rollRadians) / 45.0).coerceIn(-1.0, 1.0)
        )
    }

    private data class PoseValues(
        val yaw: Double,
        val pitch: Double,
        val roll: Double
    )

    companion object {
        private const val REQUIRED_LANDMARKS = 468
        private const val IRIS_LANDMARK_COUNT = 478
        private const val EPSILON = 1e-6
        private const val TOPOLOGY_MIN_HORIZONTAL_GAP = 0.012
        private const val TOPOLOGY_MIN_VERTICAL_GAP = 0.012
        private val BLENDSHAPE_LEFT_EYE_LIVENESS_NAMES = setOf(
            "eyeblinkleft",
            "eyesquintleft",
            "eyewideleft"
        )
        private val BLENDSHAPE_RIGHT_EYE_LIVENESS_NAMES = setOf(
            "eyeblinkright",
            "eyesquintright",
            "eyewideright"
        )
        private val BLENDSHAPE_BROW_LIVENESS_NAMES = setOf(
            "browdownleft",
            "browdownright",
            "browinnerup",
            "browouterupleft",
            "browouterupright"
        )
        private val BLENDSHAPE_MOUTH_LIVENESS_NAMES = setOf(
            "jawopen",
            "mouthclose",
            "mouthdimpleleft",
            "mouthdimpleright",
            "mouthfrownleft",
            "mouthfrownright",
            "mouthfunnelleft",
            "mouthfunnelright",
            "mouthleft",
            "mouthlowerdownleft",
            "mouthlowerdownright",
            "mouthpressleft",
            "mouthpressright",
            "mouthpucker",
            "mouthright",
            "mouthrolllower",
            "mouthrollupper",
            "mouthshruglower",
            "mouthshrugupper",
            "mouthsmileleft",
            "mouthsmileright",
            "mouthstretchleft",
            "mouthstretchright",
            "mouthupperupleft",
            "mouthupperupright"
        )
        private const val FOREHEAD = 10
        private const val CHIN = 152
        private const val JAW_LEFT = 234
        private const val JAW_RIGHT = 454
        private const val LEFT_TEMPLE = 127
        private const val RIGHT_TEMPLE = 356
        private const val LEFT_CHEEK = 93
        private const val RIGHT_CHEEK = 323

        private const val LEFT_EYE_OUTER = 33
        private const val LEFT_EYE_INNER = 133
        private const val LEFT_EYE_TOP = 159
        private const val LEFT_EYE_BOTTOM = 145

        private const val RIGHT_EYE_INNER = 362
        private const val RIGHT_EYE_OUTER = 263
        private const val RIGHT_EYE_TOP = 386
        private const val RIGHT_EYE_BOTTOM = 374
        private val LEFT_IRIS_INDICES = intArrayOf(474, 475, 476, 477)
        private val RIGHT_IRIS_INDICES = intArrayOf(469, 470, 471, 472)

        private const val LEFT_BROW_A = 70
        private const val LEFT_BROW_B = 63
        private const val LEFT_BROW_MID = 105
        private const val RIGHT_BROW_A = 300
        private const val RIGHT_BROW_B = 293
        private const val RIGHT_BROW_MID = 334

        private const val NOSE_TIP = 1
        private const val NOSE_BRIDGE_TOP = 168
        private const val NOSE_LEFT = 129
        private const val NOSE_RIGHT = 358

        private const val MOUTH_LEFT = 61
        private const val MOUTH_RIGHT = 291
        private const val MOUTH_TOP = 13
        private const val MOUTH_BOTTOM = 14

        fun point(landmarks: List<NormalizedLandmark>, index: Int): Point3 {
            val landmark = landmarks[index]
            return Point3(
                x = landmark.x().toDouble(),
                y = landmark.y().toDouble(),
                z = landmark.z().toDouble()
            )
        }

        fun distance(landmarks: List<NormalizedLandmark>, first: Int, second: Int): Double {
            return distance(point(landmarks, first), point(landmarks, second))
        }

        fun distance(first: Point3, second: Point3): Double {
            val dx = first.x - second.x
            val dy = first.y - second.y
            val dz = first.z - second.z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        fun midpoint(first: Point3, second: Point3): Point3 {
            return Point3(
                x = (first.x + second.x) * 0.5,
                y = (first.y + second.y) * 0.5,
                z = (first.z + second.z) * 0.5
            )
        }

        fun triangleArea(first: Point3, second: Point3, third: Point3): Double {
            return abs(
                first.x * (second.y - third.y) +
                    second.x * (third.y - first.y) +
                    third.x * (first.y - second.y)
            ) * 0.5
        }

        fun verticalSlope(first: Point3, second: Point3): Double {
            return (abs(first.y - second.y) / max(distance(first, second), EPSILON)).coerceIn(0.0, 1.0)
        }

        fun pointLineDistance(point: Point3, lineStart: Point3, lineEnd: Point3): Double {
            val numerator = abs(
                (lineEnd.y - lineStart.y) * point.x -
                    (lineEnd.x - lineStart.x) * point.y +
                    lineEnd.x * lineStart.y -
                    lineEnd.y * lineStart.x
            )
            return numerator / max(distance(lineStart, lineEnd), EPSILON)
        }

        fun symmetryScore(first: Double, second: Double): Double {
            if (abs(first) < EPSILON && abs(second) < EPSILON) return 1.0
            val larger = max(max(abs(first), abs(second)), EPSILON)
            return (min(abs(first), abs(second)) / larger).coerceIn(0.0, 1.0)
        }

        private fun average(first: Point3, second: Point3): Point3 = midpoint(first, second)

        private fun irisCenterOrFallback(
            landmarks: List<NormalizedLandmark>,
            indices: IntArray,
            fallback: Point3
        ): Point3 {
            if (indices.any { it >= landmarks.size }) return fallback

            var x = 0.0
            var y = 0.0
            var z = 0.0
            indices.forEach { index ->
                val landmark = landmarks[index]
                x += landmark.x().toDouble()
                y += landmark.y().toDouble()
                z += landmark.z().toDouble()
            }
            val size = indices.size.toDouble()
            return Point3(x / size, y / size, z / size)
        }
    }
}








