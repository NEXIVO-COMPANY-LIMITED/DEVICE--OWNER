#!/usr/bin/env pwsh

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Release APK (Memory Optimized)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Stop any existing Gradle daemons
Write-Host "Stopping any existing Gradle daemons..." -ForegroundColor Yellow
& .\gradlew --stop

# Wait a moment for cleanup
Start-Sleep -Seconds 2

# Set optimized JVM memory settings
$env:GRADLE_OPTS = "-Xmx8g -Xms2g -XX:MaxMetaspaceSize=2g -XX:+UseG1GC -XX:G1HeapRegionSize=16M -XX:+ParallelRefProcEnabled -XX:+UnlockDiagnosticVMOptions -XX:G1SummarizeRSetStatsPeriod=1000000"

Write-Host "Building APK with optimized memory settings..." -ForegroundColor Yellow
Write-Host "JVM Memory: 8GB max, 2GB initial" -ForegroundColor Gray
Write-Host "Metaspace: 2GB max" -ForegroundColor Gray

# Build with reduced parallelism
& .\gradlew.bat `
  -Dorg.gradle.workers.max=2 `
  -Dorg.gradle.parallel=false `
  -Dorg.gradle.caching=true `
  --build-cache `
  -x lint `
  -x lintVitalRelease `
  assembleRelease

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✓ Build successful!" -ForegroundColor Green
    Write-Host "APK location: app/build/outputs/apk/release/" -ForegroundColor Green
} else {
    Write-Host "`n✗ Build failed!" -ForegroundColor Red
    Write-Host "Try: .\build_memory_optimized.ps1 (retry with same settings)" -ForegroundColor Yellow
}
