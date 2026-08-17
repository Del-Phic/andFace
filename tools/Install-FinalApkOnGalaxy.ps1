param(
    [string]$ApkPath = "deliverables/final_face_auth_s193/AndFace_Galaxy_face_auth_s193-debug.apk",
    [string]$PackageName = "dev.andface.galaxy",
    [string]$DeviceSerial = "",
    [switch]$Launch
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

function Invoke-Adb {
    param(
        [string]$Adb,
        [string[]]$Arguments
    )

    & $Adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: adb $($Arguments -join ' ')"
    }
}

$resolvedApk = Resolve-Path -LiteralPath $ApkPath -ErrorAction SilentlyContinue
if ($null -eq $resolvedApk) {
    throw "APK not found: $ApkPath"
}

$adb = Find-Adb
Write-Output "ADB: $adb"
Write-Output "APK: $($resolvedApk.Path)"
Write-Output "Package: $PackageName"

Invoke-Adb -Adb $adb -Arguments @("start-server")

$deviceLines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim().Length -gt 0 })
$devices = @(
    $deviceLines | ForEach-Object {
        $parts = $_.Trim() -split "\s+"
        [pscustomobject]@{
            Serial = $parts[0]
            State = if ($parts.Count -gt 1) { $parts[1] } else { "unknown" }
            Raw = $_
        }
    }
)
$readyDevices = @($devices | Where-Object { $_.State -eq "device" })
$unauthorizedDevices = @($devices | Where-Object { $_.State -eq "unauthorized" })

if ($unauthorizedDevices.Count -gt 0) {
    throw "A device is connected but unauthorized. Unlock the Galaxy device and approve USB debugging."
}
if ($readyDevices.Count -eq 0) {
    throw "No ready Android device found. Connect the Galaxy device with USB debugging enabled."
}

if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $selected = @($readyDevices | Where-Object { $_.Serial -eq $DeviceSerial })
    if ($selected.Count -eq 0) {
        $known = ($readyDevices | ForEach-Object { $_.Serial }) -join ", "
        throw "Requested device '$DeviceSerial' is not ready. Ready devices: $known"
    }
} elseif ($readyDevices.Count -gt 1) {
    Write-Output "Multiple ready devices found:"
    $readyDevices | ForEach-Object { Write-Output "  $($_.Serial)" }
    throw "Run again with -DeviceSerial <serial> to avoid installing on the wrong device."
} else {
    $DeviceSerial = $readyDevices[0].Serial
}

Write-Output "Device: $DeviceSerial"
$deviceArgs = @("-s", $DeviceSerial)

Write-Output "Installing debug APK..."
Invoke-Adb -Adb $adb -Arguments ($deviceArgs + @("install", "-r", "-d", $resolvedApk.Path))

Write-Output "Installed package:"
Invoke-Adb -Adb $adb -Arguments ($deviceArgs + @("shell", "pm", "path", $PackageName))

Write-Output "App version:"
$packageDump = & $adb @($deviceArgs + @("shell", "dumpsys", "package", $PackageName))
if ($LASTEXITCODE -ne 0) {
    throw "adb command failed: adb shell dumpsys package $PackageName"
}
$packageDump | Select-String -Pattern "versionName|versionCode" | ForEach-Object {
    Write-Output $_.Line.Trim()
}

if ($Launch) {
    Write-Output "Launching app..."
    Invoke-Adb -Adb $adb -Arguments ($deviceArgs + @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1"))
}

Write-Output "Done."







