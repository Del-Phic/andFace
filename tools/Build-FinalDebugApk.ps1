param(
    [string]$Revision = "s193",
    [string]$OutputDirectory = "deliverables/final_face_auth_s193",
    [string]$OutputFileName = "AndFace_Galaxy_face_auth_s193-debug.apk"
)

$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $PSCommandPath
$projectRoot = Split-Path -Parent $scriptDirectory
$javaHome = Join-Path $projectRoot ".tools/jdk17/jdk-17.0.19+10"
$gradle = Join-Path $projectRoot "gradlew.bat"
$androidBuildToolsRoot = Join-Path $projectRoot ".tools/android-sdk/build-tools"
$debugKeystore = Join-Path $env:USERPROFILE ".android/debug.keystore"
$builtApk = Join-Path $projectRoot "app/build/outputs/apk/debug/app-debug.apk"

if (-not (Test-Path -LiteralPath (Join-Path $javaHome "bin/java.exe"))) {
    throw "Project JDK 17 was not found: $javaHome"
}
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle wrapper was not found: $gradle"
}

$buildTools = Get-ChildItem -LiteralPath $androidBuildToolsRoot -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if ($null -eq $buildTools) {
    throw "Android build tools were not found: $androidBuildToolsRoot"
}

$zipalign = Join-Path $buildTools.FullName "zipalign.exe"
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$resolvedOutputDirectory = Join-Path $projectRoot $OutputDirectory
$finalApk = Join-Path $resolvedOutputDirectory $OutputFileName
$temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("andface-" + [guid]::NewGuid().ToString("N"))
$alignedApk = Join-Path $temporaryDirectory "aligned.apk"
$signedApk = Join-Path $temporaryDirectory "signed.apk"

try {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome/bin;$env:Path"

    Write-Output "Building and verifying AndFace $Revision..."
    & $gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle verification failed."
    }
    if (-not (Test-Path -LiteralPath $builtApk)) {
        throw "Built APK was not found: $builtApk"
    }
    if (-not (Test-Path -LiteralPath $debugKeystore)) {
        throw "Android debug keystore was not found after the build: $debugKeystore"
    }

    New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
    New-Item -ItemType Directory -Path $resolvedOutputDirectory -Force | Out-Null

    & $zipalign -f 4 $builtApk $alignedApk
    if ($LASTEXITCODE -ne 0) {
        throw "zipalign failed."
    }

    & $apksigner sign `
        --ks $debugKeystore `
        --ks-key-alias androiddebugkey `
        --ks-pass pass:android `
        --key-pass pass:android `
        --v1-signing-enabled false `
        --v2-signing-enabled true `
        --v3-signing-enabled true `
        --v4-signing-enabled true `
        --out $signedApk `
        $alignedApk
    if ($LASTEXITCODE -ne 0) {
        throw "APK signing failed."
    }

    Copy-Item -LiteralPath $signedApk -Destination $finalApk -Force
    Copy-Item -LiteralPath "$signedApk.idsig" -Destination "$finalApk.idsig" -Force

    & $zipalign -c 4 $finalApk
    if ($LASTEXITCODE -ne 0) {
        throw "Final APK zipalign verification failed."
    }
    & $apksigner verify --verbose --print-certs $finalApk
    if ($LASTEXITCODE -ne 0) {
        throw "Final APK signature verification failed."
    }

    $hash = (Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash
    $idsigHash = (Get-FileHash -LiteralPath "$finalApk.idsig" -Algorithm SHA256).Hash
    $hashManifest = Join-Path $resolvedOutputDirectory "SHA256SUMS.txt"
    @(
        "$hash  $OutputFileName"
        "$idsigHash  $OutputFileName.idsig"
    ) | Set-Content -LiteralPath $hashManifest -Encoding ASCII
    Write-Output "APK: $finalApk"
    Write-Output "SHA-256: $hash"
    Write-Output "V4 signature: $finalApk.idsig"
    Write-Output "Hash manifest: $hashManifest"
} finally {
    if (Test-Path -LiteralPath $temporaryDirectory) {
        Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
    }
}
