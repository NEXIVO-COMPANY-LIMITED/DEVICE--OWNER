#!/bin/bash

# Simple script to get SSL certificate pin for Android app
# Run this on your server where payoplan.com is hosted

echo "🔍 Getting SSL Certificate Pin for payoplan.com..."
echo ""

# Get the certificate pin
CERT_PIN=$(echo | openssl s_client -servername payoplan.com -connect payoplan.com:443 2>/dev/null | openssl x509 -pubkey -noout | openssl rsa -pubin -outform der 2>/dev/null | openssl dgst -sha256 -binary | openssl enc -base64)

if [ ! -z "$CERT_PIN" ]; then
    echo "✅ SUCCESS! Certificate pin extracted:"
    echo ""
    echo "🔑 CERTIFICATE PIN:"
    echo "=================="
    echo "$CERT_PIN"
    echo "=================="
    echo ""
    
    echo "🔧 COPY THIS TO YOUR ANDROID APP:"
    echo ""
    echo "1. In network_security_config.xml, replace:"
    echo "   REPLACE_WITH_REAL_CERTIFICATE_PIN_HERE="
    echo "   with:"
    echo "   $CERT_PIN"
    echo ""
    echo "2. In ApiClient.kt, replace:"
    echo "   REPLACE_WITH_REAL_CERTIFICATE_PIN_HERE="
    echo "   with:"
    echo "   $CERT_PIN"
    echo ""
    echo "3. Uncomment the certificatePinner line in ApiClient.kt"
    echo ""
    echo "✅ Your Device Owner app will now work without ClassCastException!"
    
else
    echo "❌ Failed to get certificate pin. Possible issues:"
    echo "1. payoplan.com is not accessible from this server"
    echo "2. SSL certificate is not properly configured"
    echo "3. OpenSSL is not installed"
    echo ""
    echo "Try installing OpenSSL: sudo apt update && sudo apt install openssl"
fi