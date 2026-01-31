#!/bin/bash

# Server Commands to Get SSL Certificate Pins for Android App
# Run these commands on your server where payoplan.com is hosted

echo "🔍 Getting SSL Certificate Pins for Android Device Owner App..."
echo ""

# Method 1: Get certificate pin from your own server (RECOMMENDED)
echo "📜 Method 1: Extract certificate from your server..."
echo ""

# Get the certificate from your server
echo "Getting certificate from payoplan.com..."
openssl s_client -servername payoplan.com -connect payoplan.com:443 -showcerts </dev/null 2>/dev/null | openssl x509 -outform PEM > payoplan_cert.pem

if [ -f "payoplan_cert.pem" ]; then
    echo "✅ Certificate downloaded successfully!"
    
    # Calculate SHA-256 pin for Android certificate pinning
    echo ""
    echo "🔑 Calculating SHA-256 certificate pin..."
    CERT_PIN=$(openssl x509 -in payoplan_cert.pem -pubkey -noout | openssl rsa -pubin -outform der 2>/dev/null | openssl dgst -sha256 -binary | openssl enc -base64)
    
    echo ""
    echo "🎯 CERTIFICATE PIN FOR ANDROID APP:"
    echo "=================================="
    echo "$CERT_PIN"
    echo "=================================="
    echo ""
    
    # Show certificate details
    echo "📋 Certificate Details:"
    echo "----------------------"
    openssl x509 -in payoplan_cert.pem -text -noout | grep -E "(Subject:|Issuer:|Not Before|Not After)"
    echo ""
    
    # Generate Android configuration code
    echo "🔧 ANDROID CONFIGURATION CODE:"
    echo "=============================="
    echo ""
    echo "1. For network_security_config.xml:"
    echo "<pin digest=\"SHA-256\">$CERT_PIN</pin>"
    echo ""
    echo "2. For ApiClient.kt:"
    echo ".add(\"payoplan.com\", \"sha256/$CERT_PIN\")"
    echo ".add(\"api.payoplan.com\", \"sha256/$CERT_PIN\")"
    echo ""
    
else
    echo "❌ Failed to download certificate. Trying alternative method..."
fi

echo ""
echo "📜 Method 2: Direct certificate extraction..."
echo ""

# Alternative method - direct extraction
DIRECT_PIN=$(echo | openssl s_client -servername payoplan.com -connect payoplan.com:443 2>/dev/null | openssl x509 -pubkey -noout | openssl rsa -pubin -outform der 2>/dev/null | openssl dgst -sha256 -binary | openssl enc -base64)

if [ ! -z "$DIRECT_PIN" ]; then
    echo "✅ Direct extraction successful!"
    echo ""
    echo "🔑 Certificate Pin: $DIRECT_PIN"
    echo ""
else
    echo "❌ Direct extraction failed"
fi

echo ""
echo "📜 Method 3: Check if certificate files exist on server..."
echo ""

# Check common certificate locations on server
CERT_LOCATIONS=(
    "/etc/ssl/certs/payoplan.com.crt"
    "/etc/ssl/certs/payoplan.com.pem"
    "/etc/letsencrypt/live/payoplan.com/cert.pem"
    "/etc/letsencrypt/live/payoplan.com/fullchain.pem"
    "/etc/nginx/ssl/payoplan.com.crt"
    "/etc/apache2/ssl/payoplan.com.crt"
    "/var/www/ssl/payoplan.com.crt"
)

echo "Checking common certificate locations..."
for cert_path in "${CERT_LOCATIONS[@]}"; do
    if [ -f "$cert_path" ]; then
        echo "✅ Found certificate: $cert_path"
        
        # Calculate pin from local certificate file
        LOCAL_PIN=$(openssl x509 -in "$cert_path" -pubkey -noout | openssl rsa -pubin -outform der 2>/dev/null | openssl dgst -sha256 -binary | openssl enc -base64)
        
        if [ ! -z "$LOCAL_PIN" ]; then
            echo "🔑 Certificate Pin from $cert_path: $LOCAL_PIN"
            echo ""
            echo "🔧 Android Configuration:"
            echo "<pin digest=\"SHA-256\">$LOCAL_PIN</pin>"
            echo ""
        fi
        break
    fi
done

echo ""
echo "📜 Method 4: Generate self-signed certificate (if needed)..."
echo ""

# If no certificate exists, show how to generate one
if [ ! -f "/etc/letsencrypt/live/payoplan.com/cert.pem" ] && [ ! -f "/etc/ssl/certs/payoplan.com.crt" ]; then
    echo "⚠️ No SSL certificate found. Here's how to get one:"
    echo ""
    echo "Option A: Let's Encrypt (FREE):"
    echo "sudo apt update"
    echo "sudo apt install certbot"
    echo "sudo certbot certonly --standalone -d payoplan.com"
    echo ""
    echo "Option B: Self-signed certificate (for testing):"
    echo "sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /etc/ssl/private/payoplan.com.key -out /etc/ssl/certs/payoplan.com.crt -subj \"/C=US/ST=State/L=City/O=Organization/CN=payoplan.com\""
    echo ""
fi

echo ""
echo "🎯 SUMMARY:"
echo "==========="
echo "1. Copy the certificate pin from above"
echo "2. Replace 'REPLACE_WITH_REAL_CERTIFICATE_PIN_HERE=' in your Android app"
echo "3. Update both network_security_config.xml and ApiClient.kt"
echo "4. Build and test your Android app"
echo ""
echo "The certificate pin ensures secure communication and prevents ClassCastException in Device Owner mode!"