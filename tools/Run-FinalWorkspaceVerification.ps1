param(
    [string]$ProjectRoot = ".",
    [string]$PackagePath = "deliverables/final_face_auth_s193",
    [switch]$SkipGradle
)

$ErrorActionPreference = "Stop"

function Resolve-OptionalPath {
    param([string]$Path)

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
        return $null
    }
    return $resolved.Path
}

function Find-Adb {
    param([string]$Root)

    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $fromPath) {
        return $fromPath.Source
    }

    $localAdb = Join-Path $Root ".tools/android-sdk/platform-tools/adb.exe"
    if (Test-Path -LiteralPath $localAdb) {
        return (Resolve-Path -LiteralPath $localAdb).Path
    }

    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidate = Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    return $null
}


function Get-JavaMajorVersion {
    param([string]$JavaHome)

    if ([string]::IsNullOrWhiteSpace($JavaHome)) {
        return $null
    }

    $javaExe = Join-Path $JavaHome "bin/java.exe"
    if (-not (Test-Path -LiteralPath $javaExe)) {
        return $null
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& $javaExe -version 2>&1)
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $line = ($output | Select-Object -First 1)
    if ($line -match '"(\d+)\.(\d+)') {
        $first = [int]$Matches[1]
        $second = [int]$Matches[2]
        if ($first -eq 1) {
            return $second
        }
        return $first
    }

    return $null
}

function Find-JavaHome {
    param([string]$Root)

    $candidates = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add($env:JAVA_HOME)
    }
    $candidates.Add((Join-Path $Root ".tools/jdk17/jdk-17.0.19+10"))
    $candidates.Add("C:\Program Files\Android\Android Studio\jbr")
    $candidates.Add("C:\Program Files\Unity\Hub\Editor\6000.0.60f1\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK")

    foreach ($parent in @("C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java", "C:\Program Files\Microsoft")) {
        if (Test-Path -LiteralPath $parent) {
            Get-ChildItem -LiteralPath $parent -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $candidates.Add($_.FullName)
            }
        }
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        $major = Get-JavaMajorVersion -JavaHome $candidate
        if ($null -ne $major -and $major -ge 17) {
            return $candidate
        }
    }

    return $null
}
function Write-Section {
    param([string]$Title)

    Write-Output ""
    Write-Output "## $Title"
    Write-Output ""
}

$resolvedRoot = Resolve-OptionalPath $ProjectRoot
if ($null -eq $resolvedRoot) {
    throw "Project root not found: $ProjectRoot"
}

$resolvedPackage = Resolve-OptionalPath (Join-Path $resolvedRoot $PackagePath)
if ($null -eq $resolvedPackage) {
    $resolvedPackage = Resolve-OptionalPath $PackagePath
}

Write-Output "# AndFace Galaxy Final Workspace Verification"
Write-Output ""
Write-Output "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
Write-Output "Project root: $resolvedRoot"

Write-Section "Package Integrity"
if ($null -eq $resolvedPackage) {
    Write-Output "Result: SKIPPED"
    Write-Output "Reason: package path not found: $PackagePath"
} else {
    $verifyScript = Join-Path $resolvedRoot "tools/Verify-FinalPackage.ps1"
    if (-not (Test-Path -LiteralPath $verifyScript)) {
        $verifyScript = Join-Path $resolvedPackage "tools/Verify-FinalPackage.ps1"
    }

    if (Test-Path -LiteralPath $verifyScript) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $verifyScript -PackagePath $resolvedPackage
        if ($LASTEXITCODE -ne 0) {
            throw "Final package verification failed."
        }
    } else {
        Write-Output "Result: SKIPPED"
        Write-Output "Reason: Verify-FinalPackage.ps1 not found."
    }
}

Write-Section "Field Summary Gate Smoke"
$smokeScript = Join-Path $resolvedRoot "tools/Test-FieldSummaryGate.ps1"
if (Test-Path -LiteralPath $smokeScript) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $smokeScript
    if ($LASTEXITCODE -ne 0) {
        throw "Field summary gate smoke test failed."
    }
} else {
    Write-Output "Result: SKIPPED"
    Write-Output "Reason: Test-FieldSummaryGate.ps1 not found."
}
Write-Section "Hashes"
$hashTargets = @()
if ($null -ne $resolvedPackage) {
    $packageParent = Split-Path -Parent $resolvedPackage
    $packageName = Split-Path -Leaf $resolvedPackage
    $zipCandidate = Join-Path $packageParent "$packageName.zip"
    if (Test-Path -LiteralPath $zipCandidate) {
        $hashTargets += $zipCandidate
    }
    $apkCandidate = Get-ChildItem -LiteralPath $resolvedPackage -Filter "*.apk" -File -ErrorAction SilentlyContinue |
        Sort-Object Name |
        Select-Object -First 1
    if ($null -ne $apkCandidate) {
        $hashTargets += $apkCandidate.FullName
    }
}

if ($hashTargets.Count -eq 0) {
    Write-Output "Result: SKIPPED"
    Write-Output "Reason: no final APK/ZIP found for hashing."
} else {
    foreach ($item in $hashTargets) {
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $item).Hash
        Write-Output "$hash  $item"
    }
}

Write-Section "Gradle Tests"
$gradlew = Join-Path $resolvedRoot "gradlew.bat"
if ($SkipGradle) {
    Write-Output "Result: SKIPPED by -SkipGradle"
} elseif (-not (Test-Path -LiteralPath $gradlew)) {
    Write-Output "Result: SKIPPED"
    Write-Output "Reason: gradlew.bat not found. This is expected if only the final package was copied."
} else {
    $javaHome = Find-JavaHome -Root $resolvedRoot
    $androidHome = Join-Path $resolvedRoot ".tools/android-sdk"
    $gradleHome = Join-Path $resolvedRoot ".gradle-test-home"

    if ($null -eq $javaHome) {
        throw "Java 17 or newer was not found. Install JDK 17 or set JAVA_HOME to a JDK 17+ directory before running Gradle verification."
    }

    $env:JAVA_HOME = $javaHome
    $javaMajor = Get-JavaMajorVersion -JavaHome $javaHome
    Write-Output "Java: $javaHome (major=$javaMajor)"
    if (Test-Path -LiteralPath $androidHome) {
        $env:ANDROID_HOME = $androidHome
    }
    if (Test-Path -LiteralPath $gradleHome) {
        $env:GRADLE_USER_HOME = $gradleHome
    }
    if ($env:JAVA_HOME) {
        if ($env:ANDROID_HOME) {
            $env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
        } else {
            $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        }
    }

    $substDrive = "X:"
    $requiresSubst = $resolvedRoot -match "[^\x00-\x7F]"
    $useSubst = $false
    if ($requiresSubst) {
        if (-not (Test-Path "$substDrive\gradlew.bat")) {
            cmd /c "subst $substDrive `"$resolvedRoot`"" | Out-Null
            $useSubst = Test-Path "$substDrive\gradlew.bat"
        } else {
            $useSubst = $true
        }
    }

    if ($useSubst) {
        Write-Output "Gradle working directory: $substDrive\ (mapped from non-ASCII project path)"
        Push-Location "$substDrive\"
    } else {
        Write-Output "Gradle working directory: $resolvedRoot"
        Push-Location $resolvedRoot
    }
    try {
        & .\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle verification failed."
        }
    } finally {
        Pop-Location
    }

    $resultDir = Join-Path $resolvedRoot "app/build/test-results/testDebugUnitTest"
    $files = @(Get-ChildItem -Path $resultDir -Filter "TEST-*.xml" -ErrorAction SilentlyContinue)
    $tests = 0
    $failures = 0
    $errors = 0
    foreach ($file in $files) {
        [xml]$xml = Get-Content -Raw -LiteralPath $file.FullName
        $tests += [int]$xml.testsuite.tests
        $failures += [int]$xml.testsuite.failures
        $errors += [int]$xml.testsuite.errors
    }
    Write-Output ""
    Write-Output "testDebugUnitTest: tests=$tests failures=$failures errors=$errors files=$($files.Count)"
}

Write-Section "ADB Device State"
$adb = Find-Adb -Root $resolvedRoot
if ($null -eq $adb) {
    Write-Output "Result: SKIPPED"
    Write-Output "Reason: adb not found."
} else {
    Write-Output "ADB: $adb"
    & $adb start-server | Out-Null
    $devices = @(& $adb devices)
    foreach ($line in $devices) {
        Write-Output $line
    }
}

Write-Section "Completion Note"
Write-Output "Local build/package evidence can be verified here. Final completion still requires a connected Galaxy device and filled field-test CSV proving real camera behavior and accuracy."









