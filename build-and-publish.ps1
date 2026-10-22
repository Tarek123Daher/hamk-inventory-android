param(
    [string]$SiteUrl
)

$ErrorActionPreference = 'Stop'

$projectDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryDirectory = Split-Path -Parent $projectDirectory
$wrapper = Join-Path $projectDirectory 'gradlew.bat'

if (-not (Test-Path $wrapper)) {
    throw 'Gradle is not initialized yet. Generate the Gradle wrapper before building the APK.'
}

Push-Location $projectDirectory
try {
    $gradleArguments = @('--no-daemon', 'assembleDebug')
    if ($SiteUrl) {
        $gradleArguments += "-PHAMK_SITE_URL=$SiteUrl"
    }
    & $wrapper @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Android build failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

$apk = Join-Path $projectDirectory 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) {
    throw "Gradle succeeded but the APK was not found at $apk."
}

$releaseDirectory = Join-Path $projectDirectory 'releases'
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$releaseApk = Join-Path $releaseDirectory 'HAMK-Inventory.apk'
Copy-Item -LiteralPath $apk -Destination $releaseApk -Force

$downloadDirectory = Join-Path $repositoryDirectory 'public\downloads'
New-Item -ItemType Directory -Path $downloadDirectory -Force | Out-Null
$publishedApk = Join-Path $downloadDirectory 'HAMK-Inventory.apk'
Copy-Item -LiteralPath $apk -Destination $publishedApk -Force
$hash = (Get-FileHash -Algorithm SHA256 $releaseApk).Hash.ToLowerInvariant()
Write-Output "Published $releaseApk"
Write-Output "Updated website download $publishedApk"
Write-Output "SHA-256: $hash"
