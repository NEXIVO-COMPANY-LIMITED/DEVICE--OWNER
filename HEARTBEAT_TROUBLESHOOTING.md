# Heartbeat Not Sending - Troubleshooting Guide

## Root Cause Analysis

The heartbeat may not be sending due to one of these issues:

### 1. Device ID Not Saved After Registration
**Symptom:** Logs show "device_id missing"
**Check:**
```
adb shell "am shell dumpsys | grep device_id"
adb shell "sqlite3 /data/data/com.example.deviceowner/databases/device_owner_db 'SELECT deviceId FROM complete_device_registrations LIMIT 1;'"
```

### 2. SecurityMonitorService Not Starting
**Symptom:** Logs don't show "SecurityMonitorService started"
**Check:**
```
adb shell "am shell dumpsys activity services | grep SecurityMonitorService"
```

### 3. HeartbeatService Not Scheduling
**Symptom:** Logs show "Heartbeat loop started" but no "Heartbeat #1 sent"
**Check:**
```
adb logcat | grep "Heartbeat"
```

### 4. API Call Failing
**Symptom:** Logs show "Heartbeat send failed"
**Check:**
```
adb logcat | grep "HTTP\|SocketException\|SSL"
```

## Quick Diagnostic Steps

### Step 1: Check Device Registration
```bash
# Verify device_id was saved
adb shell "am shell dumpsys | grep -i device_id"

# Expected output:
# device_data.device_id_for_heartbeat = "DEV-..."
# device_registration.device_id = "DEV-..."
```

### Step 2: Check SecurityMonitorService
```bash
# Verify service is running
adb shell "am shell dumpsys activity services | grep SecurityMonitorService"

# Expected output:
# com.example.deviceowner/.monitoring.SecurityMonitorService
```

### Step 3: Check Heartbeat Logs
```bash
# Clear logs and watch for heartbeat
adb logcat -c
adb logcat | grep -E "Heartbeat|HeartbeatService|SecurityMonitor"

# Expected output:
# ✅ Heartbeat loop started
# ✅ Heartbeat #1 sent successfully
# ✅ Heartbeat #2 sent successfully
```

### Step 4: Check API Connectivity
```bash
# Test API endpoint
curl -X POST https://your-backend.com/api/devices/{device_id}/data/ \
  -H "Content-Type: application/json" \
  -d '{"test": "data"}'

# Check for SSL/TLS errors
adb logcat | grep -i "ssl\|certificate\|tls"
```

## Common Issues & Fixes

### Issue 1: "device_id missing"
**Cause:** Device not registered or device_id not saved
**Fix:**
1. Complete device registration
2. Verify server returns device_id in response
3. Check logs for validation errors

**Logs to check:**
```
adb logcat | grep "device_id"
```

### Issue 2: "HEARTBEAT BLOCKED: device_id is ANDROID-*"
**Cause:** Using locally-generated device ID instead of server-assigned
**Fix:**
1. Check server registration response
2. Verify server returns proper device_id (e.g., "DEV-B5AF7F0BEDEB")
3. Contact backend team if server not returning device_id

**Logs to check:**
```
adb logcat | grep "ANDROID-"
```

### Issue 3: "Heartbeat HTTP 401/403/404"
**Cause:** API endpoint or authentication issue
**Fix:**
1. Verify API endpoint is correct
2. Check device_id format matches server expectations
3. Verify SSL certificate is valid
4. Check server logs for errors

**Logs to check:**
```
adb logcat | grep "HTTP"
```

### Issue 4: "Heartbeat send failed: SocketException"
**Cause:** Network connectivity issue
**Fix:**
1. Verify device has internet connection
2. Check WiFi/mobile data is enabled
3. Verify firewall allows outbound connections
4. Check DNS resolution

**Logs to check:**
```
adb logcat | grep "SocketException\|Network"
```

### Issue 5: SecurityMonitorService Not Starting
**Cause:** Service start failed or permission issue
**Fix:**
1. Check app has FOREGROUND_SERVICE permission
2. Verify AndroidManifest.xml has service declaration
3. Check for crashes in logs

**Logs to check:**
```
adb logcat | grep "SecurityMonitor"
```

## Manual Testing

### Test 1: Force Heartbeat Send
```kotlin
// In adb shell
am start -n com.example.deviceowner/.ui.activities.registration.RegistrationSuccessActivity

// Watch logs
adb logcat | grep "Heartbeat"
```

### Test 2: Check Device ID Storage
```bash
# Check all SharedPreferences locations
adb shell "sqlite3 /data/data/com.example.deviceowner/shared_prefs/device_data.xml"
adb shell "sqlite3 /data/data/com.example.deviceowner/shared_prefs/device_registration.xml"
adb shell "sqlite3 /data/data/com.example.deviceowner/shared_prefs/device_owner_prefs.xml"
```

### Test 3: Verify API Endpoint
```bash
# Get device_id from logs
DEVICE_ID=$(adb logcat | grep "device_id:" | head -1 | awk '{print $NF}')

# Test API endpoint
curl -X POST "https://your-backend.com/api/devices/$DEVICE_ID/data/" \
  -H "Content-Type: application/json" \
  -d '{"test": "data"}' \
  -v
```

## Log Analysis

### Good Logs (Heartbeat Working)
```
✅ Device ID saved and synced: DEV-B5AF7F0B...
✅ Heartbeat loop started – first heartbeat IMMEDIATE, then every 30s
✅ Heartbeat #1 sent successfully
✅ Heartbeat #2 sent successfully
✅ Heartbeat #3 sent successfully
```

### Bad Logs (Heartbeat Not Working)
```
❌ HEARTBEAT BLOCKED: device_id missing
❌ HEARTBEAT BLOCKED: device_id is ANDROID-*
❌ Heartbeat HTTP 401: Unauthorized
❌ Heartbeat send failed: SocketException
```

## Database Inspection

### Check Registration Record
```bash
adb shell "sqlite3 /data/data/com.example.deviceowner/databases/device_owner_db"

# Inside sqlite3:
SELECT deviceId, loanNumber, registrationStatus FROM complete_device_registrations LIMIT 1;
```

### Check Offline Events (If Heartbeat Failed)
```bash
# Inside sqlite3:
SELECT eventType, jsonData FROM offline_events LIMIT 5;
```

## Network Debugging

### Enable Network Logging
```bash
# In your app, add to ApiClient:
val logging = HttpLoggingInterceptor()
logging.setLevel(HttpLoggingInterceptor.Level.BODY)
httpClient.addInterceptor(logging)
```

### Check SSL Certificate
```bash
# Verify server certificate
openssl s_client -connect your-backend.com:443 -showcerts

# Check certificate validity
adb logcat | grep -i "certificate\|ssl\|tls"
```

## Performance Monitoring

### Check Heartbeat Interval
```bash
# Watch heartbeat timing
adb logcat | grep "Heartbeat" | awk '{print $1, $2, $NF}'

# Should show ~30 second intervals
```

### Check Memory Usage
```bash
# Monitor app memory
adb shell "dumpsys meminfo com.example.deviceowner | grep TOTAL"
```

### Check CPU Usage
```bash
# Monitor app CPU
adb shell "top -n 1 | grep com.example.deviceowner"
```

## Server-Side Debugging

### Check Server Logs
```bash
# Django logs
tail -f /var/log/django/heartbeat.log

# Check for received heartbeats
grep "POST /api/devices/.*/data/" /var/log/nginx/access.log
```

### Verify Device Registration
```bash
# Check if device is registered in database
SELECT * FROM devices WHERE device_id = 'DEV-...';

# Check heartbeat history
SELECT * FROM heartbeat_history WHERE device_id = 'DEV-...' ORDER BY timestamp DESC LIMIT 10;
```

## Final Checklist

- [ ] Device registered successfully
- [ ] Device ID saved to all locations
- [ ] Device ID format is valid (not ANDROID-*)
- [ ] SecurityMonitorService is running
- [ ] HeartbeatService is scheduled
- [ ] API endpoint is correct
- [ ] SSL certificate is valid
- [ ] Network connectivity is working
- [ ] Logs show heartbeat sending
- [ ] Server logs show heartbeat received

## Still Not Working?

If heartbeat still not sending after checking all above:

1. **Collect full logs:**
   ```bash
   adb logcat > heartbeat_logs.txt
   # Wait 2 minutes
   # Ctrl+C
   ```

2. **Check database:**
   ```bash
   adb shell "sqlite3 /data/data/com.example.deviceowner/databases/device_owner_db '.dump'" > db_dump.sql
   ```

3. **Check SharedPreferences:**
   ```bash
   adb shell "cat /data/data/com.example.deviceowner/shared_prefs/*.xml" > prefs_dump.xml
   ```

4. **Contact support with:**
   - heartbeat_logs.txt
   - db_dump.sql
   - prefs_dump.xml
   - Device model and Android version
   - Backend API logs
