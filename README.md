# AndFace Galaxy

Galaxy Android Native demo for real-time face authentication with Kotlin,
CameraX, MediaPipe Face Landmarker, fuzzy inference, and Mahalanobis auxiliary
scoring.

Use [FIELD_DEPLOYMENT_CHECKLIST.md](FIELD_DEPLOYMENT_CHECKLIST.md) for Galaxy
device validation and presentation testing.

## Core Policy

- This is not template matching.
- Enrollment stores only clean-face statistical baseline data.
- The app does not store mask, glasses, or eye-patch templates.
- The app does not reconstruct hidden face regions.
- Authentication uses observable facial features only.
- Manual occlusion checkboxes explicitly select feature exclusions; they do not prove identity. With no checkbox selected, landmark deviations can automatically infer occlusion after temporal confirmation.
- Inferred occlusion also adjusts liveness evaluation, so automatic mask/glasses/eye-patch handling and liveness use the same observable-feature view.
- The app is a face-authentication demo. It does not emit an external actuator
  signal.

## Android Stack

- Kotlin
- CameraX front-camera preview
- MediaPipe Face Landmarker in `LIVE_STREAM` mode
- XML UI
- Fuzzy Inference System
- Mahalanobis auxiliary consistency check

Key dependencies:

```text
com.google.mediapipe:tasks-vision:0.10.35
androidx.camera:camera-core:1.4.2
androidx.camera:camera-camera2:1.4.2
androidx.camera:camera-lifecycle:1.4.2
androidx.camera:camera-view:1.4.2
compileSdk = 35
minSdk = 26
```

The bundled MediaPipe model is:

```text
app/src/main/assets/face_landmarker.task
```

Expected model SHA-256:

```text
64184E229B263107BC2B804C6625DB1341FF2BB731874B0BCC2FE6544E0BC9FF
```

## Runtime Flow

1. Show live CameraX preview from the Galaxy front camera.
2. Run MediaPipe Face Landmarker in live-stream mode.
3. Reject frames with no face or more than one face.
4. Require at least 468 landmarks for feature extraction.
5. Convert landmarks into 170 normalized feature types and select observable evidence for scoring. Optional iris landmarks are used when MediaPipe returns 478 points; otherwise iris-derived features are marked observable=false and excluded from scoring.
6. Read MediaPipe face blendshapes as current-frame liveness assistance only; blendshapes are not enrolled, stored, or used as identity templates.
7. Analyze visibility and observable feature masks.
8. Apply Gaussian fuzzy membership and FAM Max-Min composition.
9. Compute Mahalanobis consistency using only observable features.
10. Combine scores:

```text
finalScore = 0.72 * fuzzyScore + 0.28 * mahalanobisScore
```

11. Require liveness and consecutive stable decisions before success.
12. Display result, scores, coverage, margin, liveness, observable count,
    occlusion state, and failure reason.

## Features

The app defines 170 normalized feature types; the observable subset depends on occlusion and measurement confidence. The first 13 are the original baseline features, and the rest add finer eye, brow, nose, upper/mid-face, cheek, and optional iris-derived geometry from MediaPipe landmarks. The full feature enum lives in `app/src/main/java/dev/andface/galaxy/feature/FeatureType.kt`.

- `EyeDistance`
- `BrowDistance`
- `NoseWidth`
- `NoseToChin`
- `MouthWidth`
- `JawWidth`
- `LeftEyeOpen`
- `RightEyeOpen`
- `NoseToMouth`
- `FaceAspect`
- `Yaw`
- `Pitch`
- `Roll`
- `InnerEyeDistance`
- `LeftEyeWidth`
- `RightEyeWidth`
- `EyeOpenAsymmetry`
- `BrowWidth`
- `BrowAsymmetry`
- `NoseBridgeLength`
- `EyeNoseLeft`
- `EyeNoseRight`
- `EyeNoseSymmetry`
- `UpperFaceWidth`
- `CheekboneWidth`
- `LeftCheekNose`
- `RightCheekNose`
- `CheekNoseSymmetry`
- `LeftBrowEyeGap`
- `RightBrowEyeGap`
- `BrowEyeGapAsymmetry`
- `LeftUnderEyeCheek`
- `RightUnderEyeCheek`
- `UnderEyeCheekAsymmetry`
- `InterBrowDistance`
- `LeftBrowNoseRoot`
- `RightBrowNoseRoot`
- `BrowNoseRootAsymmetry`
- `LeftInnerEyeNoseRoot`
- `RightInnerEyeNoseRoot`
- `InnerEyeNoseRootAsymmetry`
- `LeftTempleEye`
- `RightTempleEye`
- `TempleEyeAsymmetry`
- `EyeWidthAsymmetry`
- `LeftBrowSlope`
- `RightBrowSlope`
- `BrowSlopeAsymmetry`
- `LeftIrisEyeOffset`
- `RightIrisEyeOffset`
- `IrisOffsetAsymmetry`
- `IrisDistance`
- `LeftIrisNoseRoot`
- `RightIrisNoseRoot`
- `IrisNoseRootAsymmetry`
- `IrisSpanRatio`

When `FaceFeatureExtractor` receives only the minimum 468 landmarks, `FaceFrameQuality.irisLandmarksAvailable` is set to `false`. `OcclusionAnalyzer` then marks iris-derived values as observable=false, so fallback eye-center geometry is not used by Fuzzy, Mahalanobis, identity support, or temporal-gate scoring. The optional-iris enrollment policy allows clean-face enrollment without optional iris landmarks by lowering only the optional-iris-dependent clean enrollment count and coverage gate, and by skipping iris-derived geometry/stability validation when those landmarks are not actually returned. Profiles enrolled that way mark optional iris dimensions as unreliable and exclude them later, even if a future frame contains 478 landmarks. The live overlay renders all landmarks returned by MediaPipe and highlights optional iris landmarks in yellow when present.

Before scoring, FeatureGeometryQualityPolicy rejects physically implausible upper/mid-face landmark geometry as POOR_FACE_QUALITY. Lower-face outliers are not used for this quality gate when the mask hint is active, so masked authentication still relies on observable upper/mid-face evidence instead of reconstructing hidden regions.
## Fuzzy Inference

Gaussian membership:

```text
mu = exp(-((x - c)^2) / (2 * sigma^2))
muVisible = mu * visibility
famScore = max(min(muVisible, ruleWeight))
aggregateScore = mean(top activated observable rules)
supportScore = 0.45 * strongFeatureRatio + 0.35 * strongRegionRatio + 0.20 * meanRuleActivation
fuzzyScore = 0.35 * famScore + 0.45 * regionalAggregateScore + 0.20 * supportScore
```

Visibility also includes enrollment-stability confidence: a feature whose clean enrollment sigma is unusually wide is down-weighted instead of being allowed to dominate fuzzy activation. The same confidence is used by identity inlier, regional balance, nose-structure, and micro-region gates, so occluded authentication leans harder on stable clean-baseline features without reconstructing hidden regions.

Rule weights are strengths in `[0, 1]`. They are not normalized to sum to 1.
Features with `observable=false` are excluded before scoring.

The app keeps FAM Max-Min as the primary fuzzy activation, then applies a
multi-feature fuzzy gate so one accidentally matching feature cannot dominate
the final fuzzy score. Authentication also checks observable identity
consistency: each fuzzy activation is normalized by its own rule weight, then
the visible identity-feature group must pass a weighted-average consistency
floor. Rule weights remain independent strengths and are not normalized to sum
to 1.

## Mahalanobis Check

Mahalanobis scoring is auxiliary only. It uses the observable feature subset,
regularizes covariance, applies shrinkage, blends an exponential distance score
with a chi-square survival approximation for the active dimensionality, and falls
back to a diagonal covariance path when matrix solving is not reliable.

Mahalanobis does not replace fuzzy inference. The final score remains:

```text
0.72 * fuzzy + 0.28 * mahalanobis
```

## Occlusion Handling

Mask/lower-face occlusion:

- Excludes hidden lower-face features.
- Can down-weight unstable mid-face evidence if the mask disturbs landmarks.
- Requires enough upper/mid/eye evidence to continue.

Glasses:

- Reduces eye-feature confidence.
- Keeps brow/nose evidence active.

Eye patch:

- Excludes the affected eye feature.
- Requires enough remaining observable evidence.

Excessive compound occlusion, such as mask plus eye patch, fails safely.

## Enrollment

Supported slots:

```text
USER_1
USER_2
USER_3
```

Enrollment rules:

- Use clean, frontal, unoccluded face samples.
- Do not enroll with mask, glasses, eye patch, or hand occlusion.
- Collect 45 clean live samples.
- Reject too-still, unstable, non-frontal, occluded, low-coverage, or poor
  quality samples.
- Reject persistent one-eye closed or eye-patch-like enrollment baselines, even when no manual occlusion hint is selected.
- Store only encrypted clean-face statistics, not raw images or landmark
  coordinates.

## Authentication

Authentication checks:

- Minimum observable feature count.
- Coverage threshold.
- Identity coverage and fuzzy support.
- Mahalanobis consistency floor.
- Best-vs-second margin.
- Liveness challenge using eye openness, yaw, pitch, and current-frame MediaPipe blendshape variation.
- Stable decision window: 5 consecutive identity-passing frames for clean and occluded modes.
- Completion confirmation: the same successful identity must remain successful for 2 seconds.
- Strict failure reasons.

After the two-second confirmation, the visible `인증 완료` state survives transient
missing/low-quality frames for at most 1.5 seconds since the last real success.
Display refreshes do not renew this deadline. After a gap, even the same user must
qualify again. Multiple faces, model/storage/security errors and session or manual
occlusion changes revoke completion immediately. Identity-score, margin, support,
or global-consistency failures also revoke completion immediately, so a brief
head turn can require authentication again. This state is UI-only.

Real-time processing is bounded: CameraX releases each ImageProxy after copying,
MediaPipe owns at most one submitted bitmap until its callback completes, the UI
mailbox retains one pending event, and a dedicated worker runs at most one
authentication job. Missing-face/multiple-face/error events cannot be overwritten
by normal frames in the mailbox. Results older than 750 ms, duplicates, and results
from before foreground resume are rejected. A 250 ms watchdog revokes the session
after 1.5 seconds without a fresh frame, including when callbacks stop completely.
Worker results from an invalidated session are discarded.

Audit events are encrypted, hash-chain validated, and retained to 200 entries.
Repeated identical authentication states are sampled once per second; identity,
failure and enrollment transitions are recorded. Corrupt history and failed
writes fail closed. Authentication audit work runs on the scoring worker.
Keystore write failures propagate without deleting shared protection keys.

Attempt-count lockout has been removed at the user's request. Failed identities
are rejected for that attempt, and the next frame can be evaluated immediately.
There is no failure counter, timed lock, or restored lock after restart. The old
stored attempt-counter preferences are removed on startup.
Liveness evidence resets on duplicate/out-of-order timestamps
or gaps over 1.5 seconds and expires from the window after four seconds.

An invalid or indefinite covariance uses a positive diagonal fallback, never a
general inverse that could yield a negative distance and a false perfect match.
Non-finite observable measurements fail closed.

These changes harden runtime behavior, but synthetic tests do not establish field
accuracy or resistance to photographs, replayed video, or face masks. The current
RGB landmark/motion method and manually selected occlusion thresholds still need
independent real-device genuine/impostor and spoof testing before deployment.
See [REALTIME_IMPROVEMENT_REPORT_KR.md](REALTIME_IMPROVEMENT_REPORT_KR.md).

## Security And Privacy

- Android Keystore-backed encrypted profile storage.
- Android Keystore-backed encrypted audit log.
- No raw camera frames in storage.
- No raw landmark coordinates in storage.
- No stored feature vectors in audit logs.
- No stored enrollment means, sigmas, or covariance in audit logs.
- Camera permission is required.
- Multiple faces fail closed.
- Model asset SHA-256 is verified.
- App backup and cleartext traffic are disabled.
- Release deployment should use an operator-owned keystore.

## Failure Reasons

The app reports strict failure reasons:

- `NO_ENROLLMENT`
- `PROFILE_EXPIRED`
- `NO_FACE`
- `MULTIPLE_FACES`
- `MODEL_NOT_READY`
- `DEVICE_NOT_SECURE`
- `DEVICE_LOCKED`
- `WINDOW_NOT_SECURE`
- `KIOSK_MODE_REQUIRED`
- `RUNTIME_INTEGRITY_RISK`
- `DEPLOYMENT_SIGNING_REQUIRED`
- `SECURE_STORAGE_ERROR`
- `OCCLUDED_DURING_ENROLLMENT`
- `POOR_FACE_QUALITY`
- `TOO_FEW_FEATURES`
- `LOW_COVERAGE`
- `LOW_LIVENESS`
- `LOW_MARGIN`
- `LOW_IDENTITY_COVERAGE`
- `LOW_IDENTITY_SUPPORT`
- `LOW_GLOBAL_CONSISTENCY`
- `EXCESSIVE_OCCLUSION`
- `OCCLUSION_HINT_MISMATCH`
- `UNSTABLE_DECISION`
- `SESSION_RESET`
- `LOW_SCORE`
- `UNSTABLE_ENROLLMENT`

## Build

Recommended workspace verification command. This script auto-detects a JDK 17+ runtime before running Gradle, which avoids failures on Windows machines whose default `JAVA_HOME` still points to Java 8.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\Run-FinalWorkspaceVerification.ps1
```

To build a debug APK with the test suite, Android lint,
zipalign, v2/v3 signing, and the incremental-install `.idsig` file:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\Build-FinalDebugApk.ps1
```

Direct Gradle commands require JDK 17 or newer. If direct Gradle fails with a Java 8 message, either use the verification script above or set `JAVA_HOME` to a JDK 17+ directory first.

Debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Direct Gradle verification command:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

Local debug APK after a successful build:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Gradle intermediate APKs are build artifacts only and are not kept as final deliverables.

Debug demo APKs show detailed field metrics by default so testers can record Fuzzy, Mahalanobis, final score, coverage, margin, liveness, and identity consistency values. Release-style builds still reveal detailed metrics temporarily through the model-state long press.

The authentication gate also checks regional inlier balance across the currently visible upper-face, mid-face, and lower-face evidence so a high aggregate score from one region cannot dominate a masked or partially occluded decision.

Generated APKs, device captures, biometric data, and submission packages are
kept outside this source repository. The text-only improvement report is included.

## Current Verification

Workspace verification on 2026-09-10 (KST), including the mask contour exclusions and screenshot-unlock change:

```text
testDebugUnitTest: 297 tests, 0 failures, 0 errors
lintDebug: passed, 0 errors, 59 warnings
assembleDebug: passed
assembleRelease: passed (unsigned unless operator release credentials are supplied)
MainActivity: built from app/src/main/java/dev/andface/galaxy/MainActivity.kt
recovered MainActivity JAR dependency: removed
```

Synthetic authentication benchmark:

```text
test: dev.andface.galaxy.auth.SyntheticAccuracyBenchmarkTest
standard accuracy: 24/24 = 100.0%
standard genuine acceptance: 15/15 = 100.0%
standard impostor rejection: 9/9 = 100.0%
468-only enrollment accuracy: 39/39 = 100.0%
468-only genuine acceptance: 30/30 = 100.0%
468-only impostor rejection: 9/9 = 100.0%
false accepts: 0
scenarios: clean, mask, glasses, left eye patch, right eye patch, mask+eye stress
```

Enrollment separation: a new USER profile is rejected with LOW_MARGIN when its clean statistical baseline, or the visible subset needed for mask/glasses/patch authentication, is too close to an existing different USER profile.

Galaxy S23+ / SM-S916N / Android 16 field checks confirmed a registered user's
clean-face and glasses authentication. A mask-only trial with the mask checkbox
enabled and gaze directed at the front-camera lens also reached the completed UI
state, including after leaving and returning to view. This is a limited functional
check, not a measured acceptance rate or latency benchmark.

Known limitations remain: gaze and pose sensitivity, automatic lower-face
occlusion inferred on an unmasked face, and immediate completion revocation on
identity-score failures. Actual unregistered-person rejection, mask-plus-glasses,
eye-patch conditions, and photo/video spoof resistance still need field testing.
Screenshots are enabled for report capture; the activity no longer sets FLAG_SECURE.
Debug builds emit numerical score/geometry diagnostics; release builds do not run
that debug logger. Do not publish device logs or enrolled biometric data.

The field CSV includes identity consistency columns so field tests can separate score failures from visible-feature group consistency failures during mask, glasses, and eye-patch trials.

Field validation helpers:

```text
FIELD_DEPLOYMENT_CHECKLIST.md
FIELD_TEST_PROTOCOL.md
FIELD_TEST_RESULTS_TEMPLATE.csv
tools/Summarize-FieldTestResults.ps1
tools/Install-FinalApkOnGalaxy.ps1
tools/Collect-GalaxyFieldEvidence.ps1
tools/Verify-FinalPackage.ps1
tools/Run-FinalWorkspaceVerification.ps1
```



### Historical S114 Upper-Face Contour Landmark Note

These historical notes describe earlier stages. Current mask scoring excludes
hidden nose/cheek dependencies and unreliable contour/pose features; see the
improvement report for the current behavior.

S114 kept the no-reconstruction rule and expanded the observable MediaPipe-derived feature vector from 110 to 119 values. Those features compare temple-brow distance, temple-brow asymmetry, forehead-brow triangle area, forehead-eye triangle area, upper-face taper, and brow-temple slopes. They remain part of the clean-face baseline and mask-visible identity evidence without storing mask/glasses/eye-patch templates or reconstructing the hidden lower face.
### Historical S147 Stability Note
S147 uses a 170-value MediaPipe-derived feature vector with visible upper-face and nose-root geometry: left/right orbital triangles, orbital asymmetry, left/right inner-brow-to-nose-root areas, inner-brow area asymmetry, brow-span/eye-span ratio, nose-root/eye-span ratio, forehead-temple areas, eye-brow-nose triangles, nose-bridge/eye triangle, nose-bridge/eye-span ratio, and nose-tip/eye/cheek depth-balance features from MediaPipe z coordinates. These values are computed only from live MediaPipe landmarks; hidden lower-face or covered-eye regions are still never reconstructed. MediaPipe face blendshapes are enabled only as a current-frame liveness aid for expression/eye-motion activity and are not enrolled or scored as identity templates; left-eye, right-eye, brow, and mouth activity are tracked separately so masked liveness ignores hidden-mouth activity and relies on visible upper-face motion. Borderline but still acceptable MediaPipe frame quality now reduces observable-feature visibility using face size, frame centering, in-frame landmark ratio, and mesh symmetry, so unstable camera frames need stronger temporal evidence instead of producing abrupt success/failure flicker. The new features are wired into quality checks, eye-patch observability exclusion, frame-stabilizer reset thresholds, clean-enrollment consensus filtering, identity support gates, micro-region consistency gates, nose-structure checks, and mask-visible separation gates. Occlusion-aware passive liveness remains active, so masked or eye-patch authentication can pass with low-amplitude natural eye/pose/expression micro-motion while static masked faces still fail liveness. The field summary gate checks mask and left/right eye-patch genuine acceptance separately at >=80%, adds per-user genuine scenario acceptance checks for USER_1/USER_2/USER_3, and keeps impostor/spoof/excessive-occlusion false accepts at 0. S147 additionally hardens the primary Fuzzy engine so observable=false or zero-visibility evidence is ignored inside the model before Gaussian membership and FAM Max-Min scoring. S147 adds FAM+regional+support fuzzy blending: FAM Max-Min remains exposed as famScore, while the final fuzzy score blends FAM activation, per-region aggregate evidence, and multi-feature support so a single high rule cannot dominate. The older top-rule aggregate remains exposed for diagnostics but no longer drives the final fuzzy score. S147 adds effective observable count gating so authentication and clean enrollment require enough summed feature visibility, not just a raw observable feature count. S147 adds per-region effective visibility gates so required upper, mid, and lower identity regions each need enough visible evidence for clean authentication, while masked or eye-patch modes require enough visible evidence in their remaining required regions. S147 adds quality-weighted temporal stabilization so borderline but acceptable MediaPipe frames have less influence on the robust feature window before Fuzzy and Mahalanobis scoring. S147 expands the visible MediaPipe-derived vector to 170 by adding inner-eye/nose-bridge triangle, distance, ratio, and depth features for stronger masked-face separation without reconstructing hidden regions. S147 strengthens enrollment capture with a 1s warm-up, 180ms accepted-sample spacing, 8.5s minimum collection duration, and retryable low-quality frame skipping so registration is based on steadier clean-face evidence instead of a burst of adjacent frames.






