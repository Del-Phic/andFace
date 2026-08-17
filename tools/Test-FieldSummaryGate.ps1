param(
    [string]$CsvPath = "FIELD_TEST_RESULTS_TEMPLATE.csv",
    [string]$SummaryScriptPath = "tools/Summarize-FieldTestResults.ps1",
    [string]$TempCsvPath = ""
)

$ErrorActionPreference = "Stop"

function Resolve-RequiredFile {
    param([string]$Path)

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
        throw "Required file not found: $Path"
    }
    return $resolved.Path
}

function Invoke-SummaryGate {
    param(
        [string]$ScriptPath,
        [string]$InputCsvPath,
        [bool]$ExpectSuccess
    )

    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath -CsvPath $InputCsvPath 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).Trim()

    if ($ExpectSuccess) {
        if ($exitCode -ne 0 -or $text -notmatch "FIELD_TEST_GATE_PASSED") {
            Write-Output $text
            throw "Expected summary gate to pass, but it failed with exit code $exitCode."
        }
    } else {
        if ($exitCode -eq 0 -or $text -notmatch "FIELD_TEST_GATE_FAILED") {
            Write-Output $text
            throw "Expected summary gate to fail, but it passed."
        }
    }

    return $text
}

function Decode-UiText {
    param([string]$Escaped)

    return [System.Text.RegularExpressions.Regex]::Unescape($Escaped)
}
function Fill-ExpectedResultRows {
    param([object[]]$Rows)

    foreach ($row in $Rows) {
        $row.actual_result = $row.expected_result
        $row.best_user = if ($row.expected_result -eq "AUTH_SUCCESS") { $row.enrolled_user } else { "--" }
        $row.second_user = "--"
        $row.fuzzy_score = if ($row.expected_result -eq "AUTH_SUCCESS") { "0.930" } else { "0.420" }
        $row.mahalanobis_score = if ($row.expected_result -eq "AUTH_SUCCESS") { "0.910" } else { "0.380" }
        $row.final_score = if ($row.expected_result -eq "AUTH_SUCCESS") { "0.924" } else { "0.408" }
        $row.coverage = if ($row.scenario -like "*mask*") { "0.700" } elseif ($row.scenario -like "*patch*") { "0.760" } else { "1.000" }
        $row.margin = if ($row.expected_result -eq "AUTH_SUCCESS") { "0.260" } else { "0.120" }
        $row.liveness_score = if ($row.scenario -eq "static_spoof") { "0.050" } else { "0.850" }
        $row.liveness_pass = if ($row.scenario -eq "static_spoof") { "FAIL" } else { "PASS" }
        $row.observable_count = if ($row.scenario -like "*mask*") { "62" } elseif ($row.scenario -like "*patch*") { "70" } else { "90" }
        $row.face_quality = if ($row.scenario -eq "static_spoof") { "0.640" } else { "0.940" }
        $row.mesh_symmetry = "0.960"
        $row.landmark_topology = "0.970"
        $row.identity_consistency = if ($row.expected_result -eq "AUTH_SUCCESS") { "0.880" } else { "0.310" }
        $row.required_identity_consistency = "0.720"
        $row.identity_support_count = if ($row.expected_result -eq "AUTH_SUCCESS") { "18" } else { "4" }
        $row.required_identity_support_count = "10"
        $row.occlusion_display = switch -Wildcard ($row.scenario) {
            "*mask*" { "lower"; break }
            "*left_patch*" { "left_eye"; break }
            "*right_patch*" { "right_eye"; break }
            "excessive_occlusion" { "lower+left_eye"; break }
            default { "clean" }
        }
        $row.failure_reason = if ($row.expected_result -eq "AUTH_SUCCESS") {
            Decode-UiText "\uC5C6\uC74C"
        } elseif ($row.scenario -eq "static_spoof") {
            Decode-UiText "\uC0DD\uB3D9\uC131 \uBD80\uC871"
        } elseif ($row.scenario -eq "excessive_occlusion") {
            Decode-UiText "\uAC00\uB9BC\uC774 \uB108\uBB34 \uB9CE\uC74C"
        } else {
            Decode-UiText "\uC810\uC218 \uBD80\uC871"
        }
    }
}

$resolvedCsv = Resolve-RequiredFile $CsvPath
$resolvedSummary = Resolve-RequiredFile $SummaryScriptPath

if ([string]::IsNullOrWhiteSpace($TempCsvPath)) {
    $TempCsvPath = Join-Path ([System.IO.Path]::GetTempPath()) ("andface-field-gate-smoke-{0}.csv" -f ([guid]::NewGuid().ToString("N")))
}

$resolvedTempParent = Split-Path -Parent $TempCsvPath
if (-not [string]::IsNullOrWhiteSpace($resolvedTempParent) -and -not (Test-Path -LiteralPath $resolvedTempParent)) {
    New-Item -ItemType Directory -Path $resolvedTempParent | Out-Null
}

try {
    $rows = @(Import-Csv -LiteralPath $resolvedCsv)
    if ($rows.Count -lt 30) {
        throw "Field CSV template must contain at least 30 rows, but has $($rows.Count)."
    }

    $blankOutput = Invoke-SummaryGate -ScriptPath $resolvedSummary -InputCsvPath $resolvedCsv -ExpectSuccess $false
    Fill-ExpectedResultRows -Rows $rows
    $rows | Export-Csv -LiteralPath $TempCsvPath -NoTypeInformation -Encoding UTF8
    $filledOutput = Invoke-SummaryGate -ScriptPath $resolvedSummary -InputCsvPath $TempCsvPath -ExpectSuccess $true
    if ($filledOutput -notmatch "\| LOW_SCORE \|" -or
        $filledOutput -notmatch "\| LOW_LIVENESS \|" -or
        $filledOutput -notmatch "\| EXCESSIVE_OCCLUSION \|") {
        Write-Output $filledOutput
        throw "Expected Korean copied failure reasons to normalize back to standard failure codes."
    }

    Write-Output "# AndFace Field Summary Gate Smoke Test"
    Write-Output ""
    Write-Output "Template rows: $($rows.Count)"
    Write-Output "Blank template gate: FAILED as expected"
    Write-Output "Filled expected-result gate: PASSED as expected"
    Write-Output "Result: FIELD_SUMMARY_GATE_SMOKE_PASSED"
} finally {
    if (Test-Path -LiteralPath $TempCsvPath) {
        Remove-Item -LiteralPath $TempCsvPath -Force
    }
}
