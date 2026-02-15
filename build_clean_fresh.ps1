#!/usr/bin/env pwsh

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Clean Build - Removing all caches" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Remove build directories
Write-Host "Removing build directories..." -ForegroundColor Yellow
if (Test-Path "app/build") {
    Remove-Item -Recurse -Force "app/build" -ErrorAction SilentlyContinue
    Write-Host "✓ Removed app/build" -ForegroundColor Green
}

if (Test-Path "build") {
    Remove-Item -Recurse -Force "build" -ErrorAction SilentlyContinue
    Write-Host "✓ Removed build" -ForegroundColor Green
}

# Remove gradle cache
Write-Host "Removing gradle cache..." -ForegroundColor Yellow
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle" -ErrorAction SilentlyContinue
    Write-Host "✓ Removed .gradle" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Release APK" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Set JVM memory
$env:_JAVA_OPTIONS = "-Xmx8g"
Write-Host "Setting JVM memory to 8GB..." -ForegroundColor Yellow

# Build
Write-Host "Building APK..." -ForegroundColor Yellow
& ./gradlew.bat clean assembleRelease --no-daemon

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✓ Build successful!" -ForegroundColor Green
    Write-Host "APK location: app/build/outputs/apk/release/" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "✗ Build failed!" -ForegroundColor Red
    exit 1
}
