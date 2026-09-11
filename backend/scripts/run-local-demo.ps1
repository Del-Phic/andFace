param([string]$MavenCommand = 'mvn')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & $MavenCommand -B -ntp test-compile dependency:build-classpath '-Dmdep.outputFile=target/test-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Maven preparation failed' }
    $dependencyPath = (Get-Content -LiteralPath 'target/test-classpath.txt' -Raw).Trim()
    $projectPath = (Get-Location).Path
    $javaCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    & $javaCommand '-cp' "$projectPath/target/test-classes;$projectPath/target/classes;$dependencyPath" 'com.andface.backend.LocalDemoServer'
    if ($LASTEXITCODE -ne 0) { throw 'Local demo server stopped with an error' }
} finally { Pop-Location }
