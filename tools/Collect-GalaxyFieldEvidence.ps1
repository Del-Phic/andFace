param(
    [string]$PackageName = "dev.andface.galaxy",
    [string]$DeviceSerial = "",
    [string]$CsvPath = "FIELD_TEST_RESULTS_TEMPLATE.csv",
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"

function Find-Adb {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $fromPath) {
        return $fromPath.Source
    }

    $candidates = @()
    $scriptDir = Split-Path -Parent $PSCommandPath
    $projectRoot = Split-Path -Parent $scriptDir
    $candidates += Join-Path $projectRoot ".tools/android-sdk/platform-tools/adb.exe"
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidates += Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidates += Join-Path $env:ANDROID_SDK_ROOT "platform-tools/adb.exe"
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    throw "adb was not found. Use the project-local .tools Android SDK, add platform-tools to PATH, or set ANDROID_HOME."
}

function Invoke-AdbText {
    param(
        [string]$Adb,
        [string[]]$Arguments
    )

    $output = & $Adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: adb $($Arguments -join ' ')"
    }
    return @($output)
}

function Select-ReadyDevice {
    param(
        [string]$Adb,
        [string]$RequestedSerial
    )

    Invoke-AdbText -Adb $Adb -Arguments @("start-server") | Out-Null
    $deviceLines = @(Invoke-AdbText -Adb $Adb -Arguments @("devices") | Select-Object -Skip 1 | Where-Object { $_.Trim().Length -gt 0 })
    $devices = @(
        $deviceLines | ForEach-Object {
            $parts = $_.Trim() -split "\s+"
            [pscustomobject]@{
                Serial = $parts[0]
                State = if ($parts.Count -gt 1) { $parts[1] } else { "unknown" }
            }
        }
    )

    $unauthorized = @($devices | Where-Object { $_.State -eq "unauthorized" })
    if ($unauthorized.Count -gt 0) {
        throw "A device is connected but unauthorized. Unlock the Galaxy device and approve USB debugging."
    }

    $ready = @($devices | Where-Object { $_.State -eq "device" })
    if ($ready.Count -eq 0) {
        throw "No ready Android device found. Connect the Galaxy device with USB debugging enabled."
    }

    if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) {
        $selected = @($ready | Where-Object { $_.Serial -eq $RequestedSerial })
        if ($selected.Count -eq 0) {
            $known = ($ready | ForEach-Object { $_.Serial }) -join ", "
            throw "Requested device '$RequestedSerial' is not ready. Ready devices: $known"
        }
        return $RequestedSerial
    }

    if ($ready.Count -gt 1) {
        $known = ($ready | ForEach-Object { $_.Serial }) -join ", "
        throw "Multiple ready devices found: $known. Run again with -DeviceSerial <serial>."
    }

    return $ready[0].Serial
}

function Get-PropValue {
    param(
        [string]$Adb,
        [string[]]$DeviceArgs,
        [string]$Name
    )

    $value = Invoke-AdbText -Adb $Adb -Arguments ($DeviceArgs + @("shell", "getprop", $Name))
    return (($value -join " ").Trim())
}

function Append-CodeBlock {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string[]]$Content
    )

    $Lines.Add('```text')
    foreach ($line in $Content) {
        $Lines.Add($line)
    }
    $Lines.Add('```')
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $scriptDir = Split-Path -Parent $PSCommandPath
    $baseDir = Split-Path -Parent $scriptDir
    $OutputPath = Join-Path $baseDir "FIELD_EVIDENCE_REPORT.md"
}

$adb = Find-Adb
$serial = Select-ReadyDevice -Adb $adb -RequestedSerial $DeviceSerial
$deviceArgs = @("-s", $serial)

$report = New-Object System.Collections.Generic.List[string]
$report.Add("# AndFace Galaxy Field Evidence Report")
$report.Add("")
$report.Add("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')")
$report.Add("")
$report.Add("Privacy note: this report does not collect camera frames, face images, screenshots, landmark coordinates, or enrollment statistics.")
$report.Add("")

$report.Add("## Device")
$report.Add("")
$report.Add("| Field | Value |")
$report.Add("|---|---|")
$report.Add("| ADB serial | $serial |")
$report.Add("| Manufacturer | $(Get-PropValue -Adb $adb -DeviceArgs $deviceArgs -Name 'ro.product.manufacturer') |")
$report.Add("| Model | $(Get-PropValue -Adb $adb -DeviceArgs $deviceArgs -Name 'ro.product.model') |")
$report.Add("| Android release | $(Get-PropValue -Adb $adb -DeviceArgs $deviceArgs -Name 'ro.build.version.release') |")
$report.Add("| Android SDK | $(Get-PropValue -Adb $adb -DeviceArgs $deviceArgs -Name 'ro.build.version.sdk') |")
$report.Add("| Build fingerprint | $(Get-PropValue -Adb $adb -DeviceArgs $deviceArgs -Name 'ro.build.fingerprint') |")
$report.Add("")

$report.Add("## Installed App")
$report.Add("")
$report.Add("Package: ``$PackageName``")
$report.Add("")

$pmPath = Invoke-AdbText -Adb $adb -Arguments ($deviceArgs + @("shell", "pm", "path", $PackageName))
Append-CodeBlock -Lines $report -Content $pmPath
$report.Add("")

$packageDump = Invoke-AdbText -Adb $adb -Arguments ($deviceArgs + @("shell", "dumpsys", "package", $PackageName))
$versionLines = @($packageDump | Select-String -Pattern "versionName|versionCode|firstInstallTime|lastUpdateTime" | ForEach-Object { $_.Line.Trim() })
$report.Add("App version/install lines:")
$report.Add("")
Append-CodeBlock -Lines $report -Content $versionLines
$report.Add("")

$permissionLines = @($packageDump | Select-String -Pattern "android.permission.CAMERA|granted=true|granted=false" | ForEach-Object { $_.Line.Trim() })
$report.Add("Camera permission related lines:")
$report.Add("")
Append-CodeBlock -Lines $report -Content $permissionLines
$report.Add("")

$report.Add("## Field Test CSV Summary")
$report.Add("")
$csvSummaryGateFailed = $false
if (Test-Path -LiteralPath $CsvPath) {
    $summaryScript = Join-Path (Split-Path -Parent $PSCommandPath) "Summarize-FieldTestResults.ps1"
    if (Test-Path -LiteralPath $summaryScript) {
        $summary = & powershell -NoProfile -ExecutionPolicy Bypass -File $summaryScript -CsvPath $CsvPath
        foreach ($line in $summary) {
            $report.Add($line)
        }
        if ($LASTEXITCODE -ne 0) {
            $csvSummaryGateFailed = $true
            $report.Add("")
            $report.Add("CSV summary gate failed for ``$CsvPath``. Do not treat this evidence package as complete until the field CSV passes.")
        }
    } else {
        $report.Add("Summary script not found: ``$summaryScript``.")
    }
} else {
    $report.Add("CSV file not found yet: ``$CsvPath``.")
}
$report.Add("")

$report.Add("## Completion Note")
$report.Add("")
$report.Add("Use this report with `FIELD_TEST_RESULTS_TEMPLATE.csv` and the app screenshots taken manually only if needed. Avoid storing face images unless the evaluator explicitly requires them and participants consent.")

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
$report | Set-Content -LiteralPath $OutputPath -Encoding UTF8

Write-Output "Wrote evidence report: $OutputPath"

if ($csvSummaryGateFailed) {
    throw "CSV summary gate failed. Fix field CSV results before treating evidence as complete."
}
