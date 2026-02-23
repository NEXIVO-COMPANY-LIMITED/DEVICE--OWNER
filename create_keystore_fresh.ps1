# PAYO Keystore Generation Script - FRESH START
# Using real name: Abubakari Abushekhe

$desktopPath = "$env:USERPROFILE\Desktop"
$keystorePath = "$desktopPath\payo-release.keystore"
$keytoolPath = "C:\Program Files\Java\jdk-25\bin\keytool.exe"

# Keystore credentials
$storePassword = "Payo2025#SecureKey"
$keyAlias = "payo-release"
$keyPassword = "Payo2025#SecureKey"

# Certificate information - WITH REAL NAME
$dname = "CN=Abubakari Abushekhe,OU=Development,O=PAYO,L=Dar es Salaam,ST=Dar es Salaam Region,C=TZ"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "PAYO KEYSTORE GENERATION - FRESH START" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Name: Abubakari Abushekhe" -ForegroundColor Yellow
Write-Host "Keystore Path: $keystorePath" -ForegroundColor Yellow
Write-Host "Key Alias: $keyAlias" -ForegroundColor Yellow
Write-Host ""

# Check if keystore already exists
if (Test-Path $keystorePath) {
    Write-Host "⚠️  Keystore already exists at: $keystorePath" -ForegroundColor Red
    $response = Read-Host "Do you want to overwrite it? (yes/no)"
    if ($response -ne "yes") {
        Write-Host "Cancelled." -ForegroundColor Yellow
        exit
    }
    Remove-Item -Path $keystorePath -Force
}

# Generate keystore
Write-Host "Generating keystore..." -ForegroundColor Green
& $keytoolPath -genkey -v `
    -keystore $keystorePath `
    -alias $keyAlias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -dname $dname `
    -storepass $storePassword `
    -keypass $keyPassword

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ Keystore created successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "File: $keystorePath" -ForegroundColor Green
    
    # Verify keystore
    Write-Host ""
    Write-Host "Verifying keystore..." -ForegroundColor Cyan
    & $keytoolPath -list -v -keystore $keystorePath -storepass $storePassword
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "SAVE THIS INFORMATION SECURELY:" -ForegroundColor Yellow
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Name: Abubakari Abushekhe" -ForegroundColor Yellow
    Write-Host "Keystore Password: $storePassword" -ForegroundColor Yellow
    Write-Host "Key Alias: $keyAlias" -ForegroundColor Yellow
    Write-Host "Key Password: $keyPassword" -ForegroundColor Yellow
    Write-Host "========================================" -ForegroundColor Cyan
} else {
    Write-Host "❌ Failed to create keystore" -ForegroundColor Red
    exit 1
}
