#!/usr/bin/env pwsh

Write-Host "Testing build..." -ForegroundColor Cyan

$env:GRADLE_OPTS = "-Xmx16g"

# Just compile, don't package
./gradlew compileReleaseKotlin --no-daemon --stacktrace

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Compilation successful!" -ForegroundColor Green
} else {
    Write-Host "❌ Compilation failed!" -ForegroundColor Red
    exit 1
}
