param(
    [string]$PackagePath = "deliverables/final_face_auth_s193",
    [switch]$AllowExtraFiles
)

$ErrorActionPreference = "Stop"

$resolvedPackage = Resolve-Path -LiteralPath $PackagePath -ErrorAction SilentlyContinue
if ($null -eq $resolvedPackage) {
    throw "Package path not found: $PackagePath"
}

$packageRoot = $resolvedPackage.Path
$manifestPath = Join-Path $packageRoot "SHA256SUMS.txt"
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "SHA256SUMS.txt not found in package: $packageRoot"
}

$expected = [ordered]@{}
$lineNumber = 0
Get-Content -LiteralPath $manifestPath | ForEach-Object {
    $lineNumber += 1
    $line = $_.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#")) {
        return
    }

    $match = [regex]::Match($line, "^([A-Fa-f0-9]{64})\s+(.+)$")
    if (-not $match.Success) {
        throw "Invalid SHA256SUMS line ${lineNumber}: $line"
    }

    $hash = $match.Groups[1].Value.ToUpperInvariant()
    $relativePath = $match.Groups[2].Value.Trim()
    $expected[$relativePath] = $hash
}

$errors = New-Object System.Collections.Generic.List[string]
$checked = 0

foreach ($relativePath in $expected.Keys) {
    $fullPath = Join-Path $packageRoot ($relativePath -replace "/", [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $fullPath)) {
        $errors.Add("MISSING: $relativePath")
        continue
    }

    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $fullPath).Hash.ToUpperInvariant()
    if ($actualHash -ne $expected[$relativePath]) {
        $errors.Add("HASH MISMATCH: $relativePath expected=$($expected[$relativePath]) actual=$actualHash")
        continue
    }

    $checked += 1
}

$manifestNames = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
foreach ($relativePath in $expected.Keys) {
    [void]$manifestNames.Add($relativePath.Replace("\", "/"))
}
[void]$manifestNames.Add("SHA256SUMS.txt")

$extraFiles = @(
    Get-ChildItem -Path $packageRoot -Recurse -File | ForEach-Object {
        $_.FullName.Substring($packageRoot.Length + 1).Replace("\", "/")
    } | Where-Object {
        -not $manifestNames.Contains($_)
    }
)

if ($extraFiles.Count -gt 0 -and -not $AllowExtraFiles) {
    foreach ($extra in $extraFiles) {
        $errors.Add("EXTRA: $extra")
    }
}

Write-Output "# AndFace Galaxy Package Verification"
Write-Output ""
Write-Output "Package: $packageRoot"
Write-Output "Manifest entries: $($expected.Count)"
Write-Output "Verified files: $checked"
Write-Output "Extra files: $($extraFiles.Count)"
Write-Output ""

if ($errors.Count -gt 0) {
    Write-Output "Result: FAILED"
    Write-Output ""
    foreach ($errorItem in $errors) {
        Write-Output "- $errorItem"
    }
    exit 1
}

Write-Output "Result: PASSED"







