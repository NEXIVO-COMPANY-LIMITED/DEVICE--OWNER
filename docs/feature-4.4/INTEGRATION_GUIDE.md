# Feature 4.4: Remote Lock/Unlock - Integration Guide

**Date**: January 15, 2026  
**Version**: 2.0

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Backend Integration](#backend-integration)
4. [Android Integration](#android-integration)
5. [Testing Integration](#testing-integration)
6. [Troubleshooting](#troubleshooting)

---

## Overview

This guide provides step-by-step instructions for integrating Feature 4.4 (Remote Lock/Unlock) with your backend system and Android application.

### Integration Architecture

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Admin     │         │   Backend   │         │   Android   │
│   Portal    │────────>│   Server    │<────────│   Device    │
└─────────────┘         └─────────────┘         └─────────────┘
                               │
                               ▼
                        ┌─────────────┐
                        │  Database   │
                        └─────────────┘
```

---

## Prerequisites

### Backend Requirements
- REST API server (Node.js, Python, Java, etc.)
- Database (PostgreSQL, MySQL, MongoDB, etc.)
- HTTPS support
- Authentication system

### Android Requirements
- Android SDK 24+ (Android 7.0+)
- Device Owner mode enabled
- Kotlin 1.9+
- Retrofit 2.9+
- Coroutines support

### Network Requirements
- Stable internet connection
- Backend accessible from device
- Firewall rules configured

---

## Backend Integration

### Step 1: Database Schema

Create the following tables in your database:

```sql
-- Device lock status table
CREATE TABLE device_lock_status (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) UNIQUE NOT NULL,
    is_locked BOOLEAN DEFAULT FALSE,
    reason TEXT,
    locked_at TIMESTAMP,
    locked_by VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(device_id)
);

-- Lock history table (optional)
CREATE TABLE lock_history (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL, -- 'lock' or 'unlock'
    reason TEXT,
    performed_by VARCHAR(255),
    performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (device_id) REFERENCES devices(device_id)
);
```

### Step 2: API Endpoints

Implement the following endpoints:

#### 1. Manage Device (Lock/Unlock)

**Endpoint:** `POST /api/devices/{device_id}/manage/`

**Request Body:**
```json
{
    "action": "lock",
    "reason": "Payment overdue"
}
```

**Response:**
```json
{
    "success": true,
    "message": "Device locked successfully",
    "timestamp": 1234567890
}
```

**Implementation Example (Node.js/Express):**
```javascript
app.post('/api/devices/:device_id/manage', async (req, res) => {
    const { device_id } = req.params;
    const { action, reason } = req.body;
    
    try {
        // Validate action
        if (!['lock', 'unlock'].includes(action)) {
            return res.status(400).json({
                success: false,
                message: 'Invalid action'
            });
        }
        
        // Update database
        const isLocked = action === 'lock';
        await db.query(
            `UPDATE device_lock_status 
             SET is_locked = $1, reason = $2, locked_at = $3, updated_at = NOW()
             WHERE device_id = $4`,
            [isLocked, reason, new Date(), device_id]
        );
        
        // Log history
        await db.query(
            `INSERT INTO lock_history (device_id, action, reason, performed_by)
             VALUES ($1, $2, $3, $4)`,
            [device_id, action, reason, req.user.id]
        );
        
        res.json({
            success: true,
            message: `Device ${action}ed successfully`,
            timestamp: Date.now()
        });
    } catch (error) {
        console.error('Error managing device:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
});
```

#### 2. Heartbeat Endpoint

**Endpoint:** `POST /api/devices/{device_id}/data/`

**Request Body:**
```json
{
    "device_id": "device_123",
    "timestamp": 1234567890,
    "device_info": {
        "model": "Samsung Galaxy S21",
        "manufacturer": "Samsung",
        "android_version": "12",
        "sdk_int": 31
    },
    "lock_status": {
        "is_locked": false,
        "reason": null,
        "locked_at": 0
    }
}
```

**Response:**
```json
{
    "success": true,
    "lock_status": {
        "is_locked": true,
        "reason": "Payment overdue"
    }
}
```

**Implementation Example (Node.js/Express):**
```javascript
app.post('/api/devices/:device_id/data', async (req, res) => {
    const { device_id } = req.params;
    const { timestamp, device_info, lock_status } = req.body;
    
    try {
        // Update device last seen
        await db.query(
            `UPDATE devices 
             SET last_seen = NOW(), device_info = $1
             WHERE device_id = $2`,
            [JSON.stringify(device_info), device_id]
        );
        
        // Get lock status from database
        const result = await db.query(
            `SELECT is_locked, reason 
             FROM device_lock_status 
             WHERE device_id = $1`,
            [device_id]
        );
        
        const dbLockStatus = result.rows[0] || {
            is_locked: false,
            reason: null
        };
        
        // Return lock status to device
        res.json({
            success: true,
            lock_status: {
                is_locked: dbLockStatus.is_locked,
                reason: dbLockStatus.reason
            }
        });
    } catch (error) {
        console.error('Error processing heartbeat:', error);
        res.status(500).json({
            success: false,
            message: 'Internal server error'
        });
    }
});
```

### Step 3: Authentication

Add authentication to protect endpoints:

```javascript
const authenticateToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];
    
    if (!token) {
        return res.status(401).json({ message: 'Unauthorized' });
    }
    
    jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
        if (err) {
            return res.status(403).json({ message: 'Forbidden' });
        }
        req.user = user;
        next();
    });
};

// Apply to endpoints
app.post('/api/devices/:device_id/manage', authenticateToken, ...);
app.post('/api/devices/:device_id/data', authenticateToken, ...);
```

### Step 4: Error Handling

Implement comprehensive error handling:

```javascript
// Global error handler
app.use((err, req, res, next) => {
    console.error('Error:', err);
    res.status(err.status || 500).json({
        success: false,
        message: err.message || 'Internal server error'
    });
});

// Validation middleware
const validateManageRequest = (req, res, next) => {
    const { action, reason } = req.body;
    
    if (!action || !['lock', 'unlock'].includes(action)) {
        return res.status(400).json({
            success: false,
            message: 'Invalid or missing action'
        });
    }
    
    if (!reason || reason.trim() === '') {
        return res.status(400).json({
            success: false,
            message: 'Reason is required'
        });
    }
    
    next();
};
```

---

## Android Integration

### Step 1: Add Dependencies

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
}
```

### Step 2: Configure API Client

Update `ApiClient.kt` with your backend URL:

```kotlin
object ApiClient {
    private const val BASE_URL = "https://your-backend.com/" // Update this
    
    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message ->
            Log.d("ApiClient", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(SecurityInterceptor()) // Add auth token
            .addInterceptor(RetryInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
```

### Step 3: Add Security Interceptor

Create `SecurityInterceptor.kt`:

```kotlin
class SecurityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        
        // Get auth token from SharedPreferences
        val token = getAuthToken()
        
        val request = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .method(original.method, original.body)
            .build()
        
        return chain.proceed(request)
    }
    
    private fun getAuthToken(): String {
        // Implement token retrieval
        return "your_auth_token"
    }
}
```

### Step 4: Initialize LockManager

In your Application class or MainActivity:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize LockManager
        val lockManager = LockManager.getInstance(this)
        
        // Start HeartbeatService
        val heartbeatIntent = Intent(this, HeartbeatService::class.java)
        startService(heartbeatIntent)
    }
}
```

### Step 5: Handle Lock/Unlock

Example usage in your code:

```kotlin
class DeviceManagementActivity : AppCompatActivity() {
    private lateinit var lockManager: LockManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lockManager = LockManager.getInstance(this)
        
        // Lock device
        btnLock.setOnClickListener {
            lifecycleScope.launch {
                val result = lockManager.lockDevice("Manual lock")
                if (result.isSuccess) {
                    Toast.makeText(this@DeviceManagementActivity, 
                        "Device locked", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Note: Device can ONLY be unlocked by admin via backend
        // No local unlock button needed
        // Device will auto-unlock when admin sends unlock command
        
        // Optionally show contact admin button
        btnContactAdmin.setOnClickListener {
            Toast.makeText(this@DeviceManagementActivity,
                "Please contact admin to unlock device",
                Toast.LENGTH_LONG).show()
        }
    }
}
```

### Step 6: Configure Permissions

Add to `AndroidManifest.xml`:

```xml
<manifest>
    <!-- Required permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    
    <application>
        <!-- HeartbeatService -->
        <service
            android:name=".services.HeartbeatService"
            android:enabled="true"
            android:exported="false" />
        
        <!-- OverlayManager -->
        <service
            android:name=".overlay.OverlayManager"
            android:enabled="true"
            android:exported="false" />
    </application>
</manifest>
```

---

## Testing Integration

### Step 1: Backend Testing

Test backend endpoints using curl or Postman:

```bash
# Lock device
curl -X POST https://your-backend.com/api/devices/device_123/manage/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "action": "lock",
    "reason": "Payment overdue"
  }'

# Heartbeat
curl -X POST https://your-backend.com/api/devices/device_123/data/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "device_id": "device_123",
    "timestamp": 1234567890,
    "device_info": {},
    "lock_status": {
      "is_locked": false
    }
  }'
```

### Step 2: Android Testing

Create integration tests:

```kotlin
@Test
fun testLockUnlockIntegration() = runBlocking {
    val lockManager = LockManager.getInstance(context)
    
    // Lock device
    val lockResult = lockManager.lockDevice("Test lock")
    assertTrue(lockResult.isSuccess)
    
    // Verify locked
    val status = lockManager.getLocalLockStatus()
    assertTrue(status["is_locked"] as Boolean)
    
    // Note: Device can ONLY be unlocked by admin via backend
    // Simulate admin unlock via heartbeat
    val heartbeatResponse = mapOf(
        "lock_status" to mapOf(
            "is_locked" to false,
            "reason" to "Admin unlock"
        )
    )
    lockManager.handleHeartbeatResponse(heartbeatResponse)
    
    // Verify unlocked
    val newStatus = lockManager.getLocalLockStatus()
    assertFalse(newStatus["is_locked"] as Boolean)
}
```

### Step 3: End-to-End Testing

1. Lock device from admin portal
2. Wait for heartbeat (up to 60 seconds)
3. Verify device auto-locks
4. Verify overlay displays
5. Unlock from admin portal
6. Wait for heartbeat (up to 60 seconds)
7. Verify device auto-unlocks
8. Verify overlay dismisses

---

## Troubleshooting

### Common Issues

#### 1. Device Not Locking

**Symptoms:**
- Lock command sent but device doesn't lock
- No overlay displayed

**Solutions:**
- Check Device Owner status: `adb shell dpm list-owners`
- Verify backend response includes lock_status
- Check heartbeat service is running
- Verify network connectivity

#### 2. Heartbeat Not Working

**Symptoms:**
- Device not syncing with backend
- Lock status not updating

**Solutions:**
- Check HeartbeatService is running
- Verify backend endpoint is accessible
- Check authentication token
- Review logs: `adb logcat | grep HeartbeatService`

#### 3. Offline Queue Not Working

**Symptoms:**
- Commands not queued when offline
- Commands not applied on reconnection

**Solutions:**
- Check SharedPreferences storage
- Verify OfflineLockQueue initialization
- Check network state detection
- Review logs: `adb logcat | grep OfflineLockQueue`

#### 4. PIN Verification Failing

**This feature has been removed.**

Device can ONLY be unlocked by admin via backend. There is no PIN verification.

### Debug Commands

```bash
# Check Device Owner status
adb shell dpm list-owners

# Check running services
adb shell dumpsys activity services | grep Heartbeat

# View logs
adb logcat | grep -E "LockManager|HeartbeatService|OfflineLockQueue"

# Check SharedPreferences
adb shell run-as com.example.deviceowner cat /data/data/com.example.deviceowner/shared_prefs/lock_status.xml

# Force heartbeat
adb shell am startservice com.example.deviceowner/.services.HeartbeatService
```

### Support

For additional support:
- Review documentation: `docs/feature-4.4/`
- Check logs for errors
- Contact development team

---

*Last Updated: January 15, 2026*
