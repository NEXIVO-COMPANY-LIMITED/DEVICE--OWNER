#!/usr/bin/env pwsh

# PowerShell script to get SSL certificate pin for payoplan.com
# This script extracts the certificate and calculates the SHA-256 pin

Write-Host "🔍 Getting SSL Certificate Pin for payoplan.com..." -ForegroundColor Green
Write-Host ""

# Method 1: Using OpenSSL (if available)
if (Get-Command openssl -ErrorAction SilentlyContinue) {
    Write-Host "📜 Using OpenSSL method..." -ForegroundColor Yellow
    
    try {
        # Get certificate from server
        $cert = echo "" | openssl s_client -servername payoplan.com -connect payoplan.com:443 2>$null | openssl x509 -outform DER 2>$null
        
        # Calculate SHA-256 pin
        $pin = echo $cert | openssl x509 -pubkey -noout -outform DER 2>$null | openssl dgst -sha256 -binary 2>$null | openssl enc -base64 2>$null
        
        if ($pin) {
            Write-Host "✅ Certificate Pin Found!" -ForegroundColor Green
            Write-Host ""
            Write-Host "🔑 SHA-256 Pin: $pin" -ForegroundColor Cyan
            Write-Host ""
            Write-Host "📋 Configuration Code:" -ForegroundColor Yellow
            Write-Host ""
            Write-Host "For network_security_config.xml:" -ForegroundColor White
            Write-Host "<pin digest=`"SHA-256`">$pin</pin>" -ForegroundColor Green
            Write-Host ""
            Write-Host "For ApiClient.kt:" -ForegroundColor White
            Write-Host ".add(`"payoplan.com`", `"sha256/$pin`")" -ForegroundColor Green
            Write-Host ""
        } else {
            Write-Host "❌ Failed to extract certificate pin using OpenSSL" -ForegroundColor Red
        }
    } catch {
        Write-Host "❌ OpenSSL method failed: $_" -ForegroundColor Red
    }
} else {
    Write-Host "⚠️ OpenSSL not found. Install OpenSSL or use the Android app method." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "📱 Alternative Method: Use Android App" -ForegroundColor Cyan
Write-Host "1. Add CertificateExtractorTestActivity to your app" -ForegroundColor White
Write-Host "2. Run the activity and click 'Extract Certificate'" -ForegroundColor White
Write-Host "3. Copy the certificate pin from the results" -ForegroundColor White
Write-Host ""

# Method 2: Using PowerShell (Windows)
if ($IsWindows -or $env:OS -eq "Windows_NT") {
    Write-Host "🔍 Trying PowerShell method..." -ForegroundColor Yellow
    
    try {
        # Create web request to get certificate
        $request = [System.Net.WebRequest]::Create("https://payoplan.com")
        $request.Timeout = 10000
        
        # Get response (this will establish SSL connection)
        $response = $request.GetResponse()
        
        # Get certificate from the connection
        if ($request -is [System.Net.HttpWebRequest]) {
            $cert = $request.ServicePoint.Certificate
            
            if ($cert) {
                Write-Host "✅ Certificate found via PowerShell!" -ForegroundColor Green
                Write-Host "Subject: $($cert.Subject)" -ForegroundColor White
                Write-Host "Issuer: $($cert.Issuer)" -ForegroundColor White
                Write-Host "Valid From: $($cert.GetEffectiveDateString())" -ForegroundColor White
                Write-Host "Valid Until: $($cert.GetExpirationDateString())" -ForegroundColor White
                Write-Host ""
                Write-Host "⚠️ Note: PowerShell method may not give exact SHA-256 pin." -ForegroundColor Yellow
                Write-Host "Use the Android app method for accurate certificate pin." -ForegroundColor Yellow
            }
        }
        
        $response.Close()
    } catch {
        Write-Host "❌ PowerShell method failed: $_" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "🎯 Recommended Steps:" -ForegroundColor Green
Write-Host "1. Use the CertificateExtractorTestActivity in your Android app" -ForegroundColor White
Write-Host "2. Run it on a device/emulator with internet connection" -ForegroundColor White
Write-Host "3. Copy the certificate pin from the app" -ForegroundColor White
Write-Host "4. Update your configuration files with the real pin" -ForegroundColor White
Write-Host ""
Write-Host "This ensures you get the exact certificate pin that Android will use!" -ForegroundColor Cyan