#!/usr/bin/env pwsh

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Clean Rebuild" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Stop Gradle daemons
Write-Host "Stopping Gradle daemons..." -ForegroundColor Yellow
./gradlew --stop 2>&1 | Out-Null

# Remove build directories
Write-Host "Removing build directories..." -ForegroundColor Yellow
if (Test-Path "app/build") {
    Remove-Item -Recurse -Force "app/build" -ErrorAction SilentlyContinue
}
if (Test-Path "build") {
    Remove-Item -Recurse -Force "build" -ErrorAction SilentlyContinue
}

# Clean Gradle cache for this project
Write-Host "Cleaning Gradle cache..." -ForegroundColor Yellow
./gradlew clean --no-daemon 2>&1 | Out-Null

# Build
Write-Host "Building APK..." -ForegroundColor Yellow
$env:GRADLE_OPTS = "-Xmx16g"
./gradlew assembleRelease --no-daemon --stacktrace

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Build successful!" -ForegroundColor Green
    Get-ChildItem "app/build/outputs/apk/release/" -Filter "*.apk" | ForEach-Object {
        Write-Host "APK: $($_.FullName)" -ForegroundColor Green
    }
} else {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}
