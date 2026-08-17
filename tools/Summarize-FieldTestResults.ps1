param(
    [string]$CsvPath = "FIELD_TEST_RESULTS_TEMPLATE.csv",
    [switch]$NoThresholdSweep,
    [switch]$NoGate,
    [double]$MinimumAccuracy = 0.80,
    [double]$MinimumGenuineAcceptance = 0.80,
    [int]$MaximumFalseAccepts = 0,
    [int]$MinimumFilledTrials = 30,
    [int]$MinimumTrialsPerRequiredScenario = 3,
    [int]$MinimumDistinctGenuineUsers = 3,
    [string]$ExpectedApkSha256 = "2777F7F1318A652A70A8A77029EAD5D1BA821BC5BD8C71AD0F9A9C710441690F",
    [int]$ExpectedFeatureCount = 170,
    [string[]]$RequiredMeasurementColumns = @(
        "apk_sha256",
        "feature_count",
        "best_user",
        "fuzzy_score",
        "mahalanobis_score",
        "final_score",
        "coverage",
        "margin",
        "liveness_score",
        "liveness_pass",
        "observable_count",
        "face_quality",
        "mesh_symmetry",
        "landmark_topology",
        "identity_consistency",
        "identity_support_count",
        "occlusion_display",
        "failure_reason"
    ),
    [string[]]$RequiredNumericColumns = @(
        "fuzzy_score",
        "mahalanobis_score",
        "final_score",
        "coverage",
        "margin",
        "liveness_score",
        "observable_count",
        "face_quality",
        "mesh_symmetry",
        "landmark_topology",
        "identity_consistency",
        "identity_support_count",
        "feature_count"
    ),
    [string[]]$RequiredScenarios = @(
        "genuine_clean",
        "genuine_mask",
        "genuine_auto_mask",
        "genuine_glasses",
        "genuine_left_patch",
        "genuine_right_patch",
        "excessive_occlusion",
        "impostor_clean",
        "impostor_mask",
        "impostor_auto_mask",
        "static_spoof"
    )
)

$ErrorActionPreference = "Stop"

function Normalize-Result {
    param([string]$Value)

    $text = ""
    if ($null -ne $Value) {
        $text = $Value.Trim().ToUpperInvariant()
    }
    switch ($text) {
        "AUTH SUCCESS" { return "AUTH_SUCCESS" }
        "AUTH_SUCCESS" { return "AUTH_SUCCESS" }
        "SUCCESS" { return "AUTH_SUCCESS" }
        "?몄쬆 ?깃났" { return "AUTH_SUCCESS" }
        "AUTH FAILED" { return "AUTH_FAILED" }
        "AUTH_FAILED" { return "AUTH_FAILED" }
        "FAILED" { return "AUTH_FAILED" }
        "FAIL" { return "AUTH_FAILED" }
        "?몄쬆 ?ㅽ뙣" { return "AUTH_FAILED" }
        default { return $text }
    }
}

function Normalize-User {
    param([string]$Value)

    $text = ""
    if ($null -ne $Value) {
        $text = $Value.Trim().ToUpperInvariant().Replace(" ", "_")
    }
    switch ($text) {
        "USER1" { return "USER_1" }
        "USER_1" { return "USER_1" }
        "?ъ슜??" { return "USER_1" }
        "USER2" { return "USER_2" }
        "USER_2" { return "USER_2" }
        "?ъ슜??" { return "USER_2" }
        "USER3" { return "USER_3" }
        "USER_3" { return "USER_3" }
        "?ъ슜??" { return "USER_3" }
        default { return $text }
    }
}

function Decode-UiText {
    param([string]$Escaped)

    return [System.Text.RegularExpressions.Regex]::Unescape($Escaped)
}

function Normalize-FailureReason {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) { return "NONE" }

    $text = $Value.Trim()
    $upper = $text.ToUpperInvariant().Replace(" ", "_")
    $knownReasons = @(
        "NONE",
        "NO_ENROLLMENT",
        "PROFILE_EXPIRED",
        "NO_FACE",
        "MULTIPLE_FACES",
        "MODEL_NOT_READY",
        "DEVICE_NOT_SECURE",
        "DEVICE_LOCKED",
        "WINDOW_NOT_SECURE",
        "KIOSK_MODE_REQUIRED",
        "RUNTIME_INTEGRITY_RISK",
        "DEPLOYMENT_SIGNING_REQUIRED",
        "SECURE_STORAGE_ERROR",
        "OCCLUDED_DURING_ENROLLMENT",
        "POOR_FACE_QUALITY",
        "TOO_FEW_FEATURES",
        "LOW_COVERAGE",
        "LOW_LIVENESS",
        "LOW_MARGIN",
        "LOW_IDENTITY_COVERAGE",
        "LOW_IDENTITY_SUPPORT",
        "LOW_GLOBAL_CONSISTENCY",
        "EXCESSIVE_OCCLUSION",
        "OCCLUSION_HINT_MISMATCH",
        "TOO_MANY_ATTEMPTS",
        "UNSTABLE_DECISION",
        "SESSION_RESET",
        "LOW_SCORE",
        "UNSTABLE_ENROLLMENT"
    )
    if ($upper -in $knownReasons) { return $upper }

    $koreanReasonMap = @{
        (Decode-UiText "\uC5C6\uC74C") = "NONE"
        (Decode-UiText "\uB4F1\uB85D\uB41C \uC0AC\uC6A9\uC790 \uC5C6\uC74C") = "NO_ENROLLMENT"
        (Decode-UiText "\uB4F1\uB85D \uC815\uBCF4 \uB9CC\uB8CC") = "PROFILE_EXPIRED"
        (Decode-UiText "\uC5BC\uAD74 \uC5C6\uC74C") = "NO_FACE"
        (Decode-UiText "\uC5BC\uAD74 2\uAC1C \uC774\uC0C1") = "MULTIPLE_FACES"
        (Decode-UiText "\uBAA8\uB378 \uC900\uBE44 \uC911") = "MODEL_NOT_READY"
        (Decode-UiText "\uAE30\uAE30 \uBCF4\uC548 \uC870\uAC74 \uBD88\uCDA9\uC871") = "DEVICE_NOT_SECURE"
        (Decode-UiText "\uAE30\uAE30 \uC7A0\uAE40") = "DEVICE_LOCKED"
        (Decode-UiText "\uD654\uBA74 \uBCF4\uC548 \uC870\uAC74 \uBD88\uCDA9\uC871") = "WINDOW_NOT_SECURE"
        (Decode-UiText "\uD0A4\uC624\uC2A4\uD06C \uBAA8\uB4DC \uD544\uC694") = "KIOSK_MODE_REQUIRED"
        (Decode-UiText "\uC2E4\uD589 \uD658\uACBD \uC704\uD5D8") = "RUNTIME_INTEGRITY_RISK"
        (Decode-UiText "\uBC30\uD3EC \uC11C\uBA85 \uD544\uC694") = "DEPLOYMENT_SIGNING_REQUIRED"
        (Decode-UiText "\uC800\uC7A5\uC18C \uC624\uB958") = "SECURE_STORAGE_ERROR"
        (Decode-UiText "\uAE68\uB057\uD55C \uC5BC\uAD74\uC5D0\uC11C\uB9CC \uB4F1\uB85D \uAC00\uB2A5") = "OCCLUDED_DURING_ENROLLMENT"
        (Decode-UiText "\uC5BC\uAD74 \uD488\uC9C8 \uB0AE\uC74C") = "POOR_FACE_QUALITY"
        (Decode-UiText "\uAD00\uCE21 \uD2B9\uC9D5 \uBD80\uC871") = "TOO_FEW_FEATURES"
        (Decode-UiText "\uAD00\uCE21 \uBC94\uC704 \uBD80\uC871") = "LOW_COVERAGE"
        (Decode-UiText "\uC0DD\uB3D9\uC131 \uBD80\uC871") = "LOW_LIVENESS"
        (Decode-UiText "1\uC704\uC640 2\uC704 \uCC28\uC774 \uBD80\uC871") = "LOW_MARGIN"
        (Decode-UiText "\uC2E0\uC6D0 \uD2B9\uC9D5 \uBC94\uC704 \uBD80\uC871") = "LOW_IDENTITY_COVERAGE"
        (Decode-UiText "\uC2E0\uC6D0 \uD2B9\uC9D5 \uC77C\uCE58 \uBD80\uC871") = "LOW_IDENTITY_SUPPORT"
        (Decode-UiText "\uC804\uCCB4 \uC77C\uAD00\uC131 \uBD80\uC871") = "LOW_GLOBAL_CONSISTENCY"
        (Decode-UiText "\uAC00\uB9BC\uC774 \uB108\uBB34 \uB9CE\uC74C") = "EXCESSIVE_OCCLUSION"
        (Decode-UiText "\uAC00\uB9BC \uC120\uD0DD \uBD88\uC77C\uCE58") = "OCCLUSION_HINT_MISMATCH"
        (Decode-UiText "\uC2DC\uB3C4 \uD69F\uC218 \uCD08\uACFC") = "TOO_MANY_ATTEMPTS"
        (Decode-UiText "\uD310\uC815 \uBD88\uC548\uC815") = "UNSTABLE_DECISION"
        (Decode-UiText "\uC138\uC158 \uCD08\uAE30\uD654") = "SESSION_RESET"
        (Decode-UiText "\uC810\uC218 \uBD80\uC871") = "LOW_SCORE"
        (Decode-UiText "\uB4F1\uB85D \uC0D8\uD50C \uBD88\uC548\uC815") = "UNSTABLE_ENROLLMENT"
    }

    if ($koreanReasonMap.ContainsKey($text)) {
        return $koreanReasonMap[$text]
    }
    return $upper
}

function Format-Rate {
    param([int]$Numerator, [int]$Denominator)

    if ($Denominator -le 0) {
        return "N/A"
    }
    $rate = $Numerator / [double]$Denominator
    return ("{0:P1}" -f $rate)
}

function Format-Fraction {
    param([double]$Value)

    return ("{0:P1}" -f $Value)
}

function Try-Number {
    param([object]$Value)

    if ($null -eq $Value) { return $null }
    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    $parsed = 0.0
    if ([double]::TryParse($text.Trim(), [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$parsed)) {
        return $parsed
    }
    return $null
}

function Format-Number {
    param([Nullable[Double]]$Value)

    if ($null -eq $Value) { return "N/A" }
    return ("{0:0.000}" -f [double]$Value)
}

function Average-Nullable {
    param([object[]]$Values)

    $valid = @($Values | Where-Object { $null -ne $_ })
    if ($valid.Count -eq 0) { return $null }
    return ($valid | Measure-Object -Average).Average
}

function Add-GateCheck {
    param(
        [System.Collections.Generic.List[object]]$Rows,
        [System.Collections.Generic.List[string]]$Failures,
        [string]$Criterion,
        [bool]$Passed,
        [string]$Detail
    )

    $status = if ($Passed) { "PASS" } else { "FAIL" }
    $Rows.Add([pscustomobject]@{
        Criterion = $Criterion
        Status = $status
        Detail = $Detail
    })
    if (-not $Passed) {
        $Failures.Add("$($Criterion): $Detail")
    }
}

if (-not (Test-Path -LiteralPath $CsvPath)) {
    throw "CSV file not found: $CsvPath"
}

$rows = Import-Csv -LiteralPath $CsvPath
$filledRows = @(
    $rows | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_.actual_result)
    }
)

$tp = 0
$fn = 0
$tn = 0
$fp = 0
$invalidRows = New-Object System.Collections.Generic.List[string]
$classifiedRows = New-Object System.Collections.Generic.List[object]
$scenarioStats = @{}
$failureStats = @{}

foreach ($row in $filledRows) {
    $expected = Normalize-Result $row.expected_result
    $actual = Normalize-Result $row.actual_result
    $enrolledUser = Normalize-User $row.enrolled_user
    $bestUser = Normalize-User $row.best_user
    $scenario = if ([string]::IsNullOrWhiteSpace($row.scenario)) { "unknown" } else { $row.scenario.Trim() }
    $failureReason = Normalize-FailureReason $row.failure_reason
    $occlusionDisplay = if ([string]::IsNullOrWhiteSpace($row.occlusion_display)) { "" } else { $row.occlusion_display.Trim().ToLowerInvariant() }

    if ($expected -notin @("AUTH_SUCCESS", "AUTH_FAILED")) {
        $invalidRows.Add("trial $($row.trial_id): invalid expected_result '$($row.expected_result)'")
        continue
    }
    if ($actual -notin @("AUTH_SUCCESS", "AUTH_FAILED")) {
        $invalidRows.Add("trial $($row.trial_id): invalid actual_result '$($row.actual_result)'")
        continue
    }

    if (-not $scenarioStats.ContainsKey($scenario)) {
        $scenarioStats[$scenario] = [ordered]@{
            Total = 0
            Correct = 0
            FinalScores = New-Object System.Collections.Generic.List[double]
            FuzzyScores = New-Object System.Collections.Generic.List[double]
            MahalanobisScores = New-Object System.Collections.Generic.List[double]
            Coverages = New-Object System.Collections.Generic.List[double]
            Margins = New-Object System.Collections.Generic.List[double]
            LivenessScores = New-Object System.Collections.Generic.List[double]
            FaceQualityScores = New-Object System.Collections.Generic.List[double]
            MeshSymmetryScores = New-Object System.Collections.Generic.List[double]
            LandmarkTopologyScores = New-Object System.Collections.Generic.List[double]
            IdentityConsistencyScores = New-Object System.Collections.Generic.List[double]
        }
    }
    $scenarioStats[$scenario].Total += 1

    $finalScore = Try-Number $row.final_score
    $fuzzyScore = Try-Number $row.fuzzy_score
    $mahalanobisScore = Try-Number $row.mahalanobis_score
    $coverage = Try-Number $row.coverage
    $margin = Try-Number $row.margin
    $livenessScore = Try-Number $row.liveness_score
    $faceQuality = Try-Number $row.face_quality
    $meshSymmetry = Try-Number $row.mesh_symmetry
    $landmarkTopology = Try-Number $row.landmark_topology
    $identityConsistency = Try-Number $row.identity_consistency
    $requiredIdentityConsistency = Try-Number $row.required_identity_consistency
    $identitySupportCount = Try-Number $row.identity_support_count
    $requiredIdentitySupportCount = Try-Number $row.required_identity_support_count

    if ($null -ne $finalScore) { $scenarioStats[$scenario].FinalScores.Add([double]$finalScore) }
    if ($null -ne $fuzzyScore) { $scenarioStats[$scenario].FuzzyScores.Add([double]$fuzzyScore) }
    if ($null -ne $mahalanobisScore) { $scenarioStats[$scenario].MahalanobisScores.Add([double]$mahalanobisScore) }
    if ($null -ne $coverage) { $scenarioStats[$scenario].Coverages.Add([double]$coverage) }
    if ($null -ne $margin) { $scenarioStats[$scenario].Margins.Add([double]$margin) }
    if ($null -ne $livenessScore) { $scenarioStats[$scenario].LivenessScores.Add([double]$livenessScore) }
    if ($null -ne $faceQuality) { $scenarioStats[$scenario].FaceQualityScores.Add([double]$faceQuality) }
    if ($null -ne $meshSymmetry) { $scenarioStats[$scenario].MeshSymmetryScores.Add([double]$meshSymmetry) }
    if ($null -ne $landmarkTopology) { $scenarioStats[$scenario].LandmarkTopologyScores.Add([double]$landmarkTopology) }
    if ($null -ne $identityConsistency) { $scenarioStats[$scenario].IdentityConsistencyScores.Add([double]$identityConsistency) }

    if (-not $failureStats.ContainsKey($failureReason)) {
        $failureStats[$failureReason] = 0
    }
    $failureStats[$failureReason] += 1

    $isCorrect = $false
    $confusion = ""
    if ($expected -eq "AUTH_SUCCESS") {
        $isCorrect = $actual -eq "AUTH_SUCCESS" -and $bestUser -eq $enrolledUser
        if ($isCorrect) {
            $tp += 1
            $scenarioStats[$scenario].Correct += 1
            $confusion = "TP"
        } else {
            $fn += 1
            $confusion = "FN"
        }
    } else {
        $isCorrect = $actual -eq "AUTH_FAILED"
        if ($isCorrect) {
            $tn += 1
            $scenarioStats[$scenario].Correct += 1
            $confusion = "TN"
        } else {
            $fp += 1
            $confusion = "FP"
        }
    }

    $classifiedRows.Add([pscustomobject]@{
        TrialId = $row.trial_id
        Scenario = $scenario
        Expected = $expected
        Actual = $actual
        EnrolledUser = $enrolledUser
        BestUser = $bestUser
        Confusion = $confusion
        Correct = $isCorrect
        FinalScore = $finalScore
        FuzzyScore = $fuzzyScore
        MahalanobisScore = $mahalanobisScore
        Coverage = $coverage
        Margin = $margin
        LivenessScore = $livenessScore
        ObservableCount = Try-Number $row.observable_count
        FaceQuality = $faceQuality
        MeshSymmetry = $meshSymmetry
        LandmarkTopology = $landmarkTopology
        IdentityConsistency = $identityConsistency
        RequiredIdentityConsistency = $requiredIdentityConsistency
        IdentitySupportCount = $identitySupportCount
        RequiredIdentitySupportCount = $requiredIdentitySupportCount
        OcclusionDisplay = $occlusionDisplay
        FailureReason = $failureReason
    })
}

$total = $tp + $fn + $tn + $fp
$correct = $tp + $tn
$accuracyRate = if ($total -gt 0) { $correct / [double]$total } else { 0.0 }
$genuineTotal = $tp + $fn
$negativeTotal = $tn + $fp
$genuineAcceptanceRate = if ($genuineTotal -gt 0) { $tp / [double]$genuineTotal } else { 0.0 }
$impostorRejectionRate = if ($negativeTotal -gt 0) { $tn / [double]$negativeTotal } else { 0.0 }
$metadataHashFailures = New-Object System.Collections.Generic.List[string]
$metadataFeatureFailures = New-Object System.Collections.Generic.List[string]
$metricPresenceFailures = New-Object System.Collections.Generic.List[string]
$numericMetricFailures = New-Object System.Collections.Generic.List[string]
$expectedHash = $ExpectedApkSha256.Trim().ToUpperInvariant()

foreach ($row in $filledRows) {
    $trialId = if ([string]::IsNullOrWhiteSpace($row.trial_id)) { "unknown" } else { $row.trial_id }
    $rowHash = if ($null -eq $row.apk_sha256) { "" } else { $row.apk_sha256.Trim().ToUpperInvariant() }
    if ([string]::IsNullOrWhiteSpace($rowHash) -or $rowHash -ne $expectedHash) {
        $metadataHashFailures.Add("trial ${trialId}: apk_sha256 '$($row.apk_sha256)' does not match expected $expectedHash")
    }

    $featureCount = Try-Number $row.feature_count
    if ($null -eq $featureCount -or [int][double]$featureCount -ne $ExpectedFeatureCount) {
        $metadataFeatureFailures.Add("trial ${trialId}: feature_count '$($row.feature_count)' does not match expected $ExpectedFeatureCount")
    }

    foreach ($column in $RequiredMeasurementColumns) {
        $property = $row.PSObject.Properties[$column]
        $value = if ($null -eq $property) { $null } else { $property.Value }
        if ($null -eq $property -or [string]::IsNullOrWhiteSpace([string]$value)) {
            $metricPresenceFailures.Add("trial ${trialId}: missing required metric '$column'")
        }
    }

    foreach ($column in $RequiredNumericColumns) {
        $property = $row.PSObject.Properties[$column]
        if ($null -eq $property -or [string]::IsNullOrWhiteSpace([string]$property.Value)) {
            continue
        }
        if ($null -eq (Try-Number $property.Value)) {
            $numericMetricFailures.Add("trial ${trialId}: metric '$column' is not numeric ('$($property.Value)')")
        }
    }
}
$gateRows = New-Object System.Collections.Generic.List[object]
$gateFailures = New-Object System.Collections.Generic.List[string]

if (-not $NoGate) {
    $presentScenarios = @($classifiedRows | ForEach-Object { $_.Scenario } | Sort-Object -Unique)
    $missingScenarios = @($RequiredScenarios | Where-Object { $_ -notin $presentScenarios })
    $scenarioCounts = @{}
    foreach ($scenarioName in $presentScenarios) {
        $scenarioCounts[$scenarioName] = @($classifiedRows | Where-Object { $_.Scenario -eq $scenarioName }).Count
    }
    $underSampledScenarios = New-Object System.Collections.Generic.List[string]
    foreach ($scenarioName in $RequiredScenarios) {
        $count = if ($scenarioCounts.ContainsKey($scenarioName)) { [int]$scenarioCounts[$scenarioName] } else { 0 }
        if ($count -lt $MinimumTrialsPerRequiredScenario) {
            $underSampledScenarios.Add("$scenarioName=$count")
        }
    }
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "APK hash matches expected build" -Passed ($metadataHashFailures.Count -eq 0) -Detail "expected=$expectedHash mismatches=$($metadataHashFailures.Count)"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Feature count matches expected vector" -Passed ($metadataFeatureFailures.Count -eq 0) -Detail "expected=$ExpectedFeatureCount mismatches=$($metadataFeatureFailures.Count)"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Required live metric columns are filled" -Passed ($metricPresenceFailures.Count -eq 0) -Detail "missing=$($metricPresenceFailures.Count) required=$($RequiredMeasurementColumns -join ',')"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Numeric live metric columns parse" -Passed ($numericMetricFailures.Count -eq 0) -Detail "invalid=$($numericMetricFailures.Count) required=$($RequiredNumericColumns -join ',')"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "CSV rows are valid" -Passed ($invalidRows.Count -eq 0) -Detail "invalid_rows=$($invalidRows.Count)"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Minimum filled trials" -Passed ($total -ge $MinimumFilledTrials) -Detail "filled=$total required>=$MinimumFilledTrials"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Required scenarios present" -Passed ($missingScenarios.Count -eq 0) -Detail $(if ($missingScenarios.Count -eq 0) { "all required scenarios present" } else { "missing=$($missingScenarios -join ',')" })
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Minimum trials per required scenario" -Passed ($underSampledScenarios.Count -eq 0) -Detail $(if ($underSampledScenarios.Count -eq 0) { "each required scenario >= $MinimumTrialsPerRequiredScenario" } else { "below=$($underSampledScenarios -join ',') required_each>=$MinimumTrialsPerRequiredScenario" })
    $genuineUsers = @($classifiedRows | Where-Object { $_.Expected -eq "AUTH_SUCCESS" } | ForEach-Object { $_.EnrolledUser } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and $_ -ne "NONE" } | Sort-Object -Unique)
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Minimum distinct enrolled users" -Passed ($genuineUsers.Count -ge $MinimumDistinctGenuineUsers) -Detail "genuine_users=$($genuineUsers.Count) required>=$MinimumDistinctGenuineUsers users=$($genuineUsers -join ',')"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Has genuine trials" -Passed ($genuineTotal -gt 0) -Detail "genuine_trials=$genuineTotal"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Has rejection trials" -Passed ($negativeTotal -gt 0) -Detail "rejection_trials=$negativeTotal"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Accuracy threshold" -Passed ($accuracyRate -ge $MinimumAccuracy) -Detail "accuracy=$(Format-Fraction $accuracyRate) required>=$(('{0:P0}' -f $MinimumAccuracy))"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Genuine acceptance threshold" -Passed ($genuineAcceptanceRate -ge $MinimumGenuineAcceptance) -Detail "gar=$(Format-Fraction $genuineAcceptanceRate) required>=$(('{0:P0}' -f $MinimumGenuineAcceptance))"
    Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "False accept limit" -Passed ($fp -le $MaximumFalseAccepts) -Detail "fp=$fp allowed<=$MaximumFalseAccepts"

    $scenarioAcceptanceRequirements = [ordered]@{
        genuine_clean = $MinimumGenuineAcceptance
        genuine_mask = $MinimumGenuineAcceptance
        genuine_auto_mask = $MinimumGenuineAcceptance
        genuine_glasses = $MinimumGenuineAcceptance
        genuine_left_patch = $MinimumGenuineAcceptance
        genuine_right_patch = $MinimumGenuineAcceptance
    }
    foreach ($scenarioName in $scenarioAcceptanceRequirements.Keys) {
        $scenarioRows = @($classifiedRows | Where-Object { $_.Scenario -eq $scenarioName })
        $scenarioCorrect = @($scenarioRows | Where-Object { $_.Correct }).Count
        $scenarioRate = if ($scenarioRows.Count -gt 0) { $scenarioCorrect / [double]$scenarioRows.Count } else { 0.0 }
        Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Scenario-specific genuine acceptance: $scenarioName" -Passed ($scenarioRate -ge [double]$scenarioAcceptanceRequirements[$scenarioName]) -Detail "rate=$(Format-Fraction $scenarioRate) correct=$scenarioCorrect total=$($scenarioRows.Count) required>=$(('{0:P0}' -f [double]$scenarioAcceptanceRequirements[$scenarioName]))"
    }

    $perUserScenarioRequirements = @(
        "genuine_clean",
        "genuine_mask",
        "genuine_auto_mask",
        "genuine_glasses",
        "genuine_left_patch",
        "genuine_right_patch"
    )
    foreach ($user in $genuineUsers) {
        foreach ($scenarioName in $perUserScenarioRequirements) {
            $userScenarioRows = @($classifiedRows | Where-Object { $_.Expected -eq "AUTH_SUCCESS" -and $_.EnrolledUser -eq $user -and $_.Scenario -eq $scenarioName })
            $userScenarioCorrect = @($userScenarioRows | Where-Object { $_.Correct }).Count
            $userScenarioRate = if ($userScenarioRows.Count -gt 0) { $userScenarioCorrect / [double]$userScenarioRows.Count } else { 0.0 }
            Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Per-user genuine scenario acceptance: $user/$scenarioName" -Passed ($userScenarioRows.Count -gt 0 -and $userScenarioRate -ge $MinimumGenuineAcceptance) -Detail "rate=$(Format-Fraction $userScenarioRate) correct=$userScenarioCorrect total=$($userScenarioRows.Count) required>=$(('{0:P0}' -f $MinimumGenuineAcceptance))"
        }
    }
    foreach ($scenarioName in @("impostor_clean", "impostor_mask", "impostor_auto_mask", "static_spoof", "excessive_occlusion")) {
        $scenarioRows = @($classifiedRows | Where-Object { $_.Scenario -eq $scenarioName })
        $scenarioFalseAccepts = @($scenarioRows | Where-Object { $_.Confusion -eq "FP" }).Count
        Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Scenario false accept limit: $scenarioName" -Passed ($scenarioFalseAccepts -le $MaximumFalseAccepts) -Detail "fp=$scenarioFalseAccepts total=$($scenarioRows.Count) allowed<=$MaximumFalseAccepts"
    }

    $occlusionDisplayRequirements = [ordered]@{
        genuine_mask = "lower"
        genuine_auto_mask = "lower"
        impostor_mask = "lower"
        impostor_auto_mask = "lower"
        genuine_left_patch = "left_eye"
        genuine_right_patch = "right_eye"
        excessive_occlusion = "lower"
    }
    foreach ($scenarioName in $occlusionDisplayRequirements.Keys) {
        $expectedToken = $occlusionDisplayRequirements[$scenarioName]
        $scenarioRows = @($classifiedRows | Where-Object { $_.Scenario -eq $scenarioName })
        $displayMismatchCount = @($scenarioRows | Where-Object { $_.OcclusionDisplay -notlike "*$expectedToken*" }).Count
        Add-GateCheck -Rows $gateRows -Failures $gateFailures -Criterion "Occlusion display check: $scenarioName" -Passed ($displayMismatchCount -eq 0) -Detail "expected_contains=$expectedToken mismatches=$displayMismatchCount total=$($scenarioRows.Count)"
    }
}

Write-Output "# AndFace Galaxy Field Test Summary"
Write-Output ""
Write-Output "CSV: $CsvPath"
Write-Output "Filled trials: $total"
Write-Output ""
Write-Output "## Confusion Counts"
Write-Output ""
Write-Output "| Metric | Count |"
Write-Output "|---|---:|"
Write-Output "| TP: genuine accepted correctly | $tp |"
Write-Output "| FN: genuine rejected or matched to another user | $fn |"
Write-Output "| TN: impostor/spoof/excessive occlusion rejected | $tn |"
Write-Output "| FP: impostor/spoof/excessive occlusion accepted | $fp |"
Write-Output ""
Write-Output "## Rates"
Write-Output ""
Write-Output "| Metric | Formula | Value |"
Write-Output "|---|---|---:|"
Write-Output "| Accuracy | (TP + TN) / total | $(Format-Rate $correct $total) |"
Write-Output "| Genuine Acceptance Rate | TP / (TP + FN) | $(Format-Rate $tp ($tp + $fn)) |"
Write-Output "| Impostor Rejection Rate | TN / (TN + FP) | $(Format-Rate $tn ($tn + $fp)) |"
Write-Output "| FAR | FP / (FP + TN) | $(Format-Rate $fp ($fp + $tn)) |"
Write-Output "| FRR | FN / (FN + TP) | $(Format-Rate $fn ($fn + $tp)) |"

if (-not $NoGate) {
    Write-Output ""
    Write-Output "## Acceptance Gate"
    Write-Output ""
    Write-Output "| Criterion | Status | Detail |"
    Write-Output "|---|---|---|"
    foreach ($row in $gateRows) {
        Write-Output "| $($row.Criterion) | $($row.Status) | $($row.Detail) |"
    }
    Write-Output ""
    if ($gateFailures.Count -eq 0) {
        Write-Output "Result: FIELD_TEST_GATE_PASSED"
    } else {
        Write-Output "Result: FIELD_TEST_GATE_FAILED"
    }
}

Write-Output ""
Write-Output "## Scenario Breakdown"
Write-Output ""
Write-Output "| Scenario | Correct | Total | Rate | Avg Final | Avg Margin | Avg Liveness | Avg Face Quality | Avg Mesh Symmetry | Avg Landmark Topology | Avg Identity Consistency |"
Write-Output "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"

foreach ($key in ($scenarioStats.Keys | Sort-Object)) {
    $stats = $scenarioStats[$key]
    Write-Output "| $key | $($stats.Correct) | $($stats.Total) | $(Format-Rate $stats.Correct $stats.Total) | $(Format-Number (Average-Nullable $stats.FinalScores.ToArray())) | $(Format-Number (Average-Nullable $stats.Margins.ToArray())) | $(Format-Number (Average-Nullable $stats.LivenessScores.ToArray())) | $(Format-Number (Average-Nullable $stats.FaceQualityScores.ToArray())) | $(Format-Number (Average-Nullable $stats.MeshSymmetryScores.ToArray())) | $(Format-Number (Average-Nullable $stats.LandmarkTopologyScores.ToArray())) | $(Format-Number (Average-Nullable $stats.IdentityConsistencyScores.ToArray())) |"
}

$genuineScenarioRows = @($classifiedRows | Where-Object { $_.Expected -eq "AUTH_SUCCESS" })
if ($genuineScenarioRows.Count -gt 0) {
    Write-Output ""
    Write-Output "## Per-User Scenario Breakdown"
    Write-Output ""
    Write-Output "| User | Scenario | Correct | Total | Rate | Avg Final | Avg Margin | Avg Liveness | Avg Face Quality | Avg Landmark Topology | Main Failure |"
    Write-Output "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|"
    $userScenarioGroups = $genuineScenarioRows | Group-Object EnrolledUser, Scenario | Sort-Object Name
    foreach ($group in $userScenarioGroups) {
        $items = @($group.Group)
        $first = $items[0]
        $correctCount = @($items | Where-Object { $_.Correct }).Count
        $mainFailure = @(
            $items |
                Where-Object { -not $_.Correct } |
                Group-Object FailureReason |
                Sort-Object Count -Descending |
                Select-Object -First 1
        )
        $mainFailureText = if ($mainFailure.Count -gt 0) { $mainFailure[0].Name } else { "NONE" }
        Write-Output "| $($first.EnrolledUser) | $($first.Scenario) | $correctCount | $($items.Count) | $(Format-Rate $correctCount $items.Count) | $(Format-Number (Average-Nullable @($items | ForEach-Object { $_.FinalScore }))) | $(Format-Number (Average-Nullable @($items | ForEach-Object { $_.Margin }))) | $(Format-Number (Average-Nullable @($items | ForEach-Object { $_.LivenessScore }))) | $(Format-Number (Average-Nullable @($items | ForEach-Object { $_.FaceQuality }))) | $(Format-Number (Average-Nullable @($items | ForEach-Object { $_.LandmarkTopology }))) | $mainFailureText |"
    }
}
Write-Output ""
Write-Output "## Failure Reason Breakdown"
Write-Output ""
Write-Output "| Failure reason | Count |"
Write-Output "|---|---:|"
foreach ($key in ($failureStats.Keys | Sort-Object)) {
    Write-Output "| $key | $($failureStats[$key]) |"
}

Write-Output ""
Write-Output "## Actionable Calibration Guidance"
Write-Output ""
if ($total -eq 0) {
    Write-Output "- No filled trials yet. Fill actual_result and copied live metrics from the Galaxy app before judging performance."
} else {
    if ($fp -gt 0) {
        Write-Output "- CRITICAL: false accepts were observed ($fp). Do not lower thresholds. Inspect the false-accept table first, then tighten margin, identity support, or occlusion-specific gates before any usability tuning."
    } else {
        Write-Output "- False accepts: 0. This keeps the current safety requirement intact for the filled CSV."
    }

    if ($fn -gt 0) {
        Write-Output "- False rejects: $fn. Use the failure reason groups below to decide whether to re-enroll, improve camera framing, or review liveness/identity gates."
    } else {
        Write-Output "- False rejects: 0 in the filled CSV."
    }

    $lowQualityCount = @($classifiedRows | Where-Object {
        ($null -ne $_.FaceQuality -and [double]$_.FaceQuality -lt 0.70) -or
            ($null -ne $_.MeshSymmetry -and [double]$_.MeshSymmetry -lt 0.58) -or
            ($null -ne $_.LandmarkTopology -and [double]$_.LandmarkTopology -lt 0.82)
    }).Count
    $lowLivenessCount = @($classifiedRows | Where-Object { $null -ne $_.LivenessScore -and [double]$_.LivenessScore -lt 0.35 }).Count
    if ($lowQualityCount -gt 0) {
        Write-Output "- Camera/MediaPipe quality warning: $lowQualityCount rows have low face quality, mesh symmetry, or landmark topology. Retest with centered face, stable distance, brighter light, and less glare before changing authentication thresholds."
    }
    if ($lowLivenessCount -gt 0) {
        Write-Output "- Liveness warning: $lowLivenessCount rows have low liveness. Retest with small natural eye/pose movement; repeated LOW_LIVENESS indicates liveness tuning should be reviewed separately from identity scoring."
    }

    $identityFailureCount = @($classifiedRows | Where-Object {
        $_.Confusion -eq "FN" -and $_.FailureReason -in @("LOW_IDENTITY_SUPPORT", "LOW_GLOBAL_CONSISTENCY", "LOW_MARGIN", "LOW_SCORE")
    }).Count
    if ($identityFailureCount -gt 0) {
        Write-Output "- Identity scoring warning: $identityFailureCount genuine failures are score/support/consistency related. Prefer clean re-enrollment with stable frontal samples and compare per-user scenario rates before loosening gates."
    }
}
$qualityFlagRows = @(
    $classifiedRows | Where-Object {
        ($null -ne $_.FaceQuality -and [double]$_.FaceQuality -lt 0.70) -or
            ($null -ne $_.MeshSymmetry -and [double]$_.MeshSymmetry -lt 0.58) -or
            ($null -ne $_.LandmarkTopology -and [double]$_.LandmarkTopology -lt 0.82) -or
            ($null -ne $_.LivenessScore -and [double]$_.LivenessScore -lt 0.35)
    } | Sort-Object Scenario, TrialId
)
if ($qualityFlagRows.Count -gt 0) {
    Write-Output ""
    Write-Output "## Quality Diagnostic Flags"
    Write-Output ""
    Write-Output "These rows are not an automatic failure by themselves. They show where camera framing, MediaPipe mesh quality, or liveness motion likely affected authentication stability."
    Write-Output ""
    Write-Output "| Trial | Scenario | Actual | Reason | Face Quality | Mesh Symmetry | Landmark Topology | Liveness |"
    Write-Output "|---:|---|---|---|---:|---:|---:|---:|"
    foreach ($item in $qualityFlagRows | Select-Object -First 20) {
        Write-Output "| $($item.TrialId) | $($item.Scenario) | $($item.Actual) | $($item.FailureReason) | $(Format-Number $item.FaceQuality) | $(Format-Number $item.MeshSymmetry) | $(Format-Number $item.LandmarkTopology) | $(Format-Number $item.LivenessScore) |"
    }
}
$mistakes = @($classifiedRows | Where-Object { -not $_.Correct })
if ($mistakes.Count -gt 0) {
    Write-Output ""
    Write-Output "## Incorrect Trials"
    Write-Output ""
    Write-Output "| Trial | Type | Scenario | Expected | Actual | Best | Final | Margin | Liveness | Face Quality | Landmark Topology | Identity Consistency | Reason |"
    Write-Output "|---:|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|"
    foreach ($item in $mistakes | Sort-Object Confusion, TrialId) {
        Write-Output "| $($item.TrialId) | $($item.Confusion) | $($item.Scenario) | $($item.Expected) | $($item.Actual) | $($item.BestUser) | $(Format-Number $item.FinalScore) | $(Format-Number $item.Margin) | $(Format-Number $item.LivenessScore) | $(Format-Number $item.FaceQuality) | $(Format-Number $item.LandmarkTopology) | $(Format-Number $item.IdentityConsistency) | $($item.FailureReason) |"
    }
}

$scoredRows = @($classifiedRows | Where-Object { $null -ne $_.FinalScore })
if (-not $NoThresholdSweep -and $scoredRows.Count -gt 0) {
    Write-Output ""
    Write-Output "## Final Score Threshold Diagnostic"
    Write-Output ""
    Write-Output 'This diagnostic treats only `final_score >= threshold` as success. It is for field calibration review only; the app also uses liveness, coverage, margin, identity support, identity consistency, and global consistency gates.'
    Write-Output ""
    Write-Output "| Threshold | Accuracy | FAR | FRR | TP | FN | TN | FP |"
    Write-Output "|---:|---:|---:|---:|---:|---:|---:|---:|"

    $best = $null
    for ($threshold = 0.50; $threshold -le 0.951; $threshold += 0.05) {
        $sweepTp = 0; $sweepFn = 0; $sweepTn = 0; $sweepFp = 0
        foreach ($item in $scoredRows) {
            $predictedSuccess = [double]$item.FinalScore -ge $threshold
            if ($item.Expected -eq "AUTH_SUCCESS") {
                if ($predictedSuccess -and $item.BestUser -eq $item.EnrolledUser) { $sweepTp += 1 } else { $sweepFn += 1 }
            } else {
                if ($predictedSuccess) { $sweepFp += 1 } else { $sweepTn += 1 }
            }
        }
        $sweepTotal = $sweepTp + $sweepFn + $sweepTn + $sweepFp
        $sweepCorrect = $sweepTp + $sweepTn
        $accuracy = if ($sweepTotal -gt 0) { $sweepCorrect / [double]$sweepTotal } else { 0.0 }
        $farText = Format-Rate $sweepFp ($sweepFp + $sweepTn)
        $frrText = Format-Rate $sweepFn ($sweepFn + $sweepTp)
        Write-Output "| $(('{0:0.00}' -f $threshold)) | $(Format-Rate $sweepCorrect $sweepTotal) | $farText | $frrText | $sweepTp | $sweepFn | $sweepTn | $sweepFp |"
        if ($null -eq $best -or $accuracy -gt $best.Accuracy -or ($accuracy -eq $best.Accuracy -and $sweepFp -lt $best.Fp)) {
            $best = [pscustomobject]@{ Threshold = $threshold; Accuracy = $accuracy; Tp = $sweepTp; Fn = $sweepFn; Tn = $sweepTn; Fp = $sweepFp }
        }
    }

    if ($null -ne $best) {
        Write-Output ""
        Write-Output "Recommended review point: threshold $(('{0:0.00}' -f $best.Threshold)) gave $(Format-Rate ($best.Tp + $best.Tn) ($best.Tp + $best.Fn + $best.Tn + $best.Fp)) accuracy in this CSV. Treat this as evidence for tuning discussion, not an automatic code change."
    }
}

$nearMisses = @($classifiedRows | Where-Object { $_.Confusion -eq "FN" -and $null -ne $_.FinalScore } | Sort-Object FinalScore -Descending | Select-Object -First 5)
if ($nearMisses.Count -gt 0) {
    Write-Output ""
    Write-Output "## Highest-Score False Rejects"
    Write-Output ""
    Write-Output "| Trial | Scenario | Final | Margin | Liveness | Face Quality | Landmark Topology | Identity Consistency | Reason |"
    Write-Output "|---:|---|---:|---:|---:|---:|---:|---:|---|"
    foreach ($item in $nearMisses) {
        Write-Output "| $($item.TrialId) | $($item.Scenario) | $(Format-Number $item.FinalScore) | $(Format-Number $item.Margin) | $(Format-Number $item.LivenessScore) | $(Format-Number $item.FaceQuality) | $(Format-Number $item.LandmarkTopology) | $(Format-Number $item.IdentityConsistency) | $($item.FailureReason) |"
    }
}

$falseAccepts = @($classifiedRows | Where-Object { $_.Confusion -eq "FP" } | Sort-Object FinalScore -Descending)
if ($falseAccepts.Count -gt 0) {
    Write-Output ""
    Write-Output "## False Accepts To Investigate First"
    Write-Output ""
    Write-Output "| Trial | Scenario | Best | Final | Margin | Liveness | Face Quality | Landmark Topology | Identity Consistency | Reason |"
    Write-Output "|---:|---|---|---:|---:|---:|---:|---:|---:|---|"
    foreach ($item in $falseAccepts | Select-Object -First 10) {
        Write-Output "| $($item.TrialId) | $($item.Scenario) | $($item.BestUser) | $(Format-Number $item.FinalScore) | $(Format-Number $item.Margin) | $(Format-Number $item.LivenessScore) | $(Format-Number $item.FaceQuality) | $(Format-Number $item.LandmarkTopology) | $(Format-Number $item.IdentityConsistency) | $($item.FailureReason) |"
    }
}

if ($invalidRows.Count -gt 0) {
    Write-Output ""
    Write-Output "## Rows Needing Fix"
    Write-Output ""
    foreach ($item in $invalidRows) {
        Write-Output "- $item"
    }
}

if (-not $NoGate -and $gateFailures.Count -gt 0) {
    Write-Output ""
    Write-Output "Gate failures:"
    foreach ($item in $gateFailures) {
        Write-Output "- $item"
    }
    exit 1
}




