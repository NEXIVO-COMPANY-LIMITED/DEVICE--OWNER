# Heartbeat Quick Start Guide

## What Was Fixed

Your device now sends heartbeat **reliably and correctly** with the server-assigned device ID.

## How It Works Now

### 1. Registration Flow
```
Device Registration Request
    ↓
Server Returns device_id (e.g., "DEV-B5AF7F0BEDEB")
    ↓
Device validates device_id (not ANDROID-*, not empty)
    ↓
Device saves to PRIMARY location: device_data.device_id_for_heartbeat
    ↓
Device SYNCS to backup locations automatically
    ↓
Device ID stored in database for recovery
    ↓
Heartbeat starts immediately with correct device_id
```

### 2. Heartbeat Flow
```
Every 30 seconds:
    ↓
Retrieve device_id from DeviceIdProvider (with validation)
    ↓
Collect device data
    ↓
Send POST /api/devices/{device_id}/data/ with heartbeat
    ↓
Process server response (lock/unlock/deactivate)
    ↓
If offline, queue for sync when online
```

### 3. Consistency Checks
```
Periodically:
    ↓
Verify device_id in all storage locations
    ↓
If mismatch detected, repair from primary location
    ↓
Log results for debugging
```

## Key Improvements

| Issue | Before | After |
|-------|--------|-------|
| **Device ID Storage** | 4 locations, no sync | Primary + auto-sync to backups |
| **Validation** | Only on retrieval | Before saving + on retrieval |
| **Cache** | 60s TTL, no invalidation | 30s TTL + explicit invalidation |
| **Error Handling** | Silent failures | Detailed logging + server reporting |
| **Database** | deviceId as primary key | Auto-increment ID, unique constraints |
| **Recovery** | No backup | Database backup + consistency repair |

## Testing Your Setup

### 1. Verify Registration
```
After device registers:
- Check logs for: "✅ Device ID saved and synced: DEV-..."
- Check database: SELECT * FROM complete_device_registrations
- Check SharedPreferences: device_data.device_id_for_heartbeat
```

### 2. Verify Heartbeat
```
After registration:
- Check logs for: "✅ Heartbeat #1 sent successfully"
- Check logs for: "✅ Heartbeat #2 sent successfully" (30s later)
- Verify every 30 seconds: "✅ Heartbeat #N sent successfully"
```

### 3. Verify Consistency
```
Run consistency check:
- Check logs for: "✅ Device ID consistency verified across all locations"
- If repair needed: "✅ Device ID consistency repaired"
```

## Common Issues & Solutions

### Issue: "❌ HEARTBEAT BLOCKED: device_id not found in storage"
**Cause:** Device not registered or device ID cleared
**Solution:** 
1. Complete device registration first
2. Verify server returns device_id in response
3. Check logs for validation errors

### Issue: "❌ HEARTBEAT BLOCKED: device_id is ANDROID-*"
**Cause:** Using locally-generated device ID instead of server-assigned
**Solution:**
1. Check server registration response
2. Verify server returns proper device_id (e.g., "DEV-B5AF7F0BEDEB")
3. Contact backend team if server not returning device_id

### Issue: "⚠️ Device ID inconsistency detected - repairing..."
**Cause:** Device ID out of sync across storage locations
**Solution:**
1. Automatic repair will sync from primary location
2. Check logs to verify repair succeeded
3. If persists, clear app data and re-register

### Issue: Heartbeat stops after app restart
**Cause:** Cache not invalidated on app lifecycle
**Solution:**
1. Cache now invalidated automatically on app restart
2. Device ID retrieved fresh from storage
3. Heartbeat should resume immediately

## Monitoring

### Log Patterns to Watch

**Good Signs:**
```
✅ Device ID saved and synced: DEV-...
✅ Heartbeat #1 sent successfully
✅ Heartbeat #2 sent successfully
✅ Device ID consistency verified
```

**Warning Signs:**
```
❌ HEARTBEAT BLOCKED: device_id not found
❌ HEARTBEAT BLOCKED: device_id is ANDROID-*
⚠️ Device ID inconsistency detected
⚠️ Invalid or missing device ID
```

**Error Signs:**
```
❌ Heartbeat HTTP 401/403/404
❌ Heartbeat send failed: SocketException
❌ Data collection failed
```

## API Endpoints

### Registration
```
POST /api/devices/mobile/register/
Request: DeviceRegistrationRequest
Response: DeviceRegistrationResponse (includes device_id)
```

### Heartbeat
```
POST /api/devices/{device_id}/data/
Path Parameter: device_id (from registration response)
Request: HeartbeatRequest
Response: HeartbeatResponse (lock/unlock/deactivate commands)
```

## Database Schema

### complete_device_registrations Table
```sql
CREATE TABLE complete_device_registrations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    deviceId TEXT UNIQUE NOT NULL,
    loanNumber TEXT UNIQUE NOT NULL,
    manufacturer TEXT,
    model TEXT,
    serialNumber TEXT,
    androidId TEXT,
    deviceImeis TEXT,
    osVersion TEXT,
    sdkVersion INTEGER,
    buildNumber INTEGER,
    securityPatchLevel TEXT,
    bootloader TEXT,
    installedRam TEXT,
    totalStorage TEXT,
    language TEXT,
    deviceFingerprint TEXT,
    systemUptime INTEGER,
    installedAppsHash TEXT,
    systemPropertiesHash TEXT,
    isDeviceRooted INTEGER,
    isUsbDebuggingEnabled INTEGER,
    isDeveloperModeEnabled INTEGER,
    isBootloaderUnlocked INTEGER,
    isCustomRom INTEGER,
    tamperSeverity TEXT,
    tamperFlags TEXT,
    latitude REAL,
    longitude REAL,
    registrationStatus TEXT,
    registeredAt INTEGER,
    lastSyncAt INTEGER,
    serverResponse TEXT
);
```

## SharedPreferences Locations

### Primary (Single Source of Truth)
```
device_data.device_id_for_heartbeat = "DEV-B5AF7F0BEDEB"
```

### Backups (Auto-synced)
```
device_registration.device_id = "DEV-B5AF7F0BEDEB"
device_owner_prefs.device_id = "DEV-B5AF7F0BEDEB"
control_prefs.device_id = "DEV-B5AF7F0BEDEB"
```

## Next Steps

1. **Build & Deploy**
   ```bash
   ./gradlew assembleDebug
   # or
   ./gradlew assembleRelease
   ```

2. **Test Registration**
   - Install app
   - Complete device registration
   - Verify device_id saved

3. **Test Heartbeat**
   - Monitor logs for heartbeat messages
   - Verify every 30 seconds
   - Check server receives heartbeats

4. **Monitor Production**
   - Watch for error patterns in logs
   - Check server logs for heartbeat receipts
   - Monitor device lock/unlock commands

## Support

If heartbeat still not working:
1. Check logs for specific error message
2. Verify device_id in database
3. Verify device_id in SharedPreferences
4. Check server logs for registration response
5. Contact backend team with device_id and error logs
