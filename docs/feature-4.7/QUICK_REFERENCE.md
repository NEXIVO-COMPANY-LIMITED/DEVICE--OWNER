# Feature 4.7 - Quick Reference Guide

**Version**: 3.0  
**Date**: January 15, 2026  
**Status**: ✅ Production Ready

---

## Quick Start

### Enable Complete Protection

```kotlin
// 1. Enable uninstall prevention
val preventionManager = UninstallPreventionManager(context)
preventionManager.enableUninstallPrevention()

// 2. Setup real-time monitoring
val realTimeDetection = RealTimeRemovalDetection.getInstance(context)
realTimeDetection.setupRealTimeMonitoring()

// 3. Set protection level
val adaptiveProtection = AdaptiveProtectionManager.getInstance(context)
adaptiveProtection.setProtectionLevel(ProtectionLevel.STANDARD, "Initial setup")
```

---

## Manager Classes

### 1. DeviceOwnerRecoveryManager
**Purpose**: Sophisticated device owner recovery

```kotlin
val recovery = DeviceOwnerRecoveryManager.getInstance(context)

// Attempt recovery
val success = recovery.secureDeviceOwnerRestore()

// Verify recovery
val verified = recovery.verifyRecoverySuccess()
```

### 2. RemovalAlertManager
**Purpose**: Queue and send removal alerts to backend

```kotlin
val alertManager = RemovalAlertManager.getInstance(context)

// Queue alert
alertManager.queueRemovalAlert(attemptNumber, "Details")

// Send queued alerts
val sentCount = alertManager.sendQueuedAlerts()

// Get queue count
val count = alertManager.getQueuedAlertCount()
```

### 3. EncryptedProtectionStatus
**Purpose**: Encrypt protection status

```kotlin
val encrypted = EncryptedProtectionStatus.getInstance(context)

// Store status
val status = ProtectionStatus(
    uninstallBlocked = true,
    forceStopBlocked = true,
    appDisabled = false,
    deviceOwnerEnabled = true
)
encrypted.storeProtectionStatus(status)

// Retrieve status
val retrieved = encrypted.retrieveProtectionStatus()

// Verify encryption
val working = encrypted.verifyEncryption()
```

### 4. MultiLayerVerification
**Purpose**: Comprehensive 6-layer verification

```kotlin
val verification = MultiLayerVerification.getInstance(context)

// Comprehensive verification
val result = verification.comprehensiveVerification()
if (!result.allChecksPassed) {
    Log.e(TAG, "Failed: ${result.failedChecks}")
}

// Quick verification
val passed = verification.quickVerification()
```

### 5. RealTimeRemovalDetection
**Purpose**: Real-time threat detection

```kotlin
val realTime = RealTimeRemovalDetection.getInstance(context)

// Setup monitoring
realTime.setupRealTimeMonitoring()

// Check status
val isActive = realTime.isMonitoringActive()
val attempts = realTime.getCurrentAttemptCount()

// Shutdown monitoring
realTime.shutdownRealTimeMonitoring()

// Reset counter
realTime.resetAttemptCounter()
```

### 6. AdaptiveProtectionManager
**Purpose**: Adaptive protection levels

```kotlin
val adaptive = AdaptiveProtectionManager.getInstance(context)

// Set protection level
adaptive.setProtectionLevel(ProtectionLevel.ENHANCED, "Threat detected")

// Get current level
val level = adaptive.getProtectionLevel()

// Evaluate threat
adaptive.evaluateThreatLevel()

// Auto-escalate
adaptive.autoEscalate("High threat detected")

// Get info
val info = adaptive.getProtectionLevelInfo()
```

### 7. AdvancedRecoveryManager
**Purpose**: Advanced recovery mechanisms

```kotlin
val recovery = AdvancedRecoveryManager.getInstance(context)

// Quick check
val needsRecovery = recovery.quickRecoveryCheck()

// Execute recovery
val result = recovery.executeRecoverySequence()
Log.d(TAG, "Success: ${result.success}")
Log.d(TAG, "Issues found: ${result.issuesFound}")
Log.d(TAG, "Issues resolved: ${result.issuesResolved}")

// Get statistics
val stats = recovery.getRecoveryStatistics()
Log.d(TAG, "Success rate: ${stats.successRate}%")
```

---

## Protection Levels

### STANDARD
- Normal monitoring
- Standard heartbeat (60s)
- Basic protection

### ENHANCED
- Increased monitoring
- Faster heartbeat (30s)
- Real-time detection enabled

### CRITICAL
- Maximum monitoring
- Fastest heartbeat (10s)
- Device locked
- All protections active

---

## Common Tasks

### Check Protection Status

```kotlin
val preventionManager = UninstallPreventionManager(context)

// Get status string
val status = preventionManager.getUninstallPreventionStatus()
Log.d(TAG, status)

// Check individual protections
val uninstallBlocked = preventionManager.isUninstallBlocked()
val forceStopBlocked = preventionManager.isForceStopBlocked()
```

### Handle Removal Attempt

```kotlin
// Automatically handled by UninstallPreventionManager
// When removal detected:
// 1. Increment counter
// 2. Queue alert
// 3. Lock device if threshold reached
```

### Perform Verification

```kotlin
val preventionManager = UninstallPreventionManager(context)

// Comprehensive verification
val result = preventionManager.performComprehensiveVerification()

if (!result.allChecksPassed) {
    // Handle failed verification
    Log.e(TAG, "Verification failed: ${result.failedChecks}")
}
```

### Send Alerts

```kotlin
val preventionManager = UninstallPreventionManager(context)

// Send queued alerts (called during heartbeat)
val sentCount = preventionManager.sendQueuedRemovalAlerts()
Log.d(TAG, "Sent $sentCount alerts")

// Check queue
val queuedCount = preventionManager.getQueuedAlertCount()
Log.d(TAG, "Queued: $queuedCount alerts")
```

---

## Integration Points

### Boot Receiver

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Enable protection
            val preventionManager = UninstallPreventionManager(context)
            preventionManager.enableUninstallPrevention()
            
            // Setup real-time monitoring
            val realTime = RealTimeRemovalDetection.getInstance(context)
            realTime.setupRealTimeMonitoring()
            
            // Verify protection
            preventionManager.performComprehensiveVerification()
        }
    }
}
```

### Heartbeat Service

```kotlin
// Already integrated - automatically sends queued alerts
private suspend fun sendHeartbeat() {
    // ... existing code ...
    
    // Send queued removal alerts
    val preventionManager = UninstallPreventionManager(this)
    val alertsSent = preventionManager.sendQueuedRemovalAlerts()
    
    // ... rest of heartbeat code ...
}
```

---

## Troubleshooting

### Encryption Not Working

```kotlin
val encrypted = EncryptedProtectionStatus.getInstance(context)

// Verify encryption
if (!encrypted.verifyEncryption()) {
    Log.e(TAG, "Encryption not working")
    // Check Android version (requires API 23+)
    // Check KeyStore availability
}
```

### Recovery Failing

```kotlin
val recovery = DeviceOwnerRecoveryManager.getInstance(context)

// Attempt recovery
val success = recovery.secureDeviceOwnerRestore()

if (!success) {
    // Check device admin status
    // Check device owner status
    // Check permissions
}
```

### Real-Time Detection Not Working

```kotlin
val realTime = RealTimeRemovalDetection.getInstance(context)

// Check if monitoring is active
if (!realTime.isMonitoringActive()) {
    // Re-setup monitoring
    realTime.setupRealTimeMonitoring()
}
```

---

## Performance Tips

1. **Use Quick Verification** for frequent checks
2. **Use Comprehensive Verification** periodically
3. **Monitor Protection Level** to optimize resources
4. **Batch Alerts** via heartbeat instead of immediate sending
5. **Reset Counters** after successful recovery

---

## Security Best Practices

1. **Always enable encryption** for protection status
2. **Setup real-time monitoring** on boot
3. **Perform regular verification** (hourly recommended)
4. **Monitor removal attempts** and respond quickly
5. **Keep protection level** appropriate for threat level
6. **Review recovery statistics** regularly
7. **Test recovery mechanisms** periodically

---

## Monitoring Checklist

### Daily
- [ ] Check removal attempt count
- [ ] Verify protection status
- [ ] Check queued alert count

### Weekly
- [ ] Review recovery statistics
- [ ] Analyze threat patterns
- [ ] Check protection level distribution

### Monthly
- [ ] Security audit
- [ ] Performance review
- [ ] Update threat thresholds

---

## API Reference

### UninstallPreventionManager

```kotlin
// Core
suspend fun enableUninstallPrevention(): Boolean
suspend fun detectRemovalAttempts(): Boolean
suspend fun verifyAppInstalled(): Boolean
suspend fun verifyDeviceOwnerEnabled(): Boolean

// Verification
suspend fun performComprehensiveVerification(): VerificationResult
fun isUninstallBlocked(): Boolean
fun isForceStopBlocked(): Boolean

// Alerts
suspend fun sendQueuedRemovalAlerts(): Int
fun getQueuedAlertCount(): Int

// Encryption
fun getEncryptedProtectionStatus(): ProtectionStatus?
fun updateEncryptedProtectionStatus()

// Control
fun disableUninstall(): Boolean
fun disableForceStop(): Boolean

// Status
fun getUninstallPreventionStatus(): String
suspend fun resetRemovalAttempts()
```

---

## Constants

```kotlin
// Protection Levels
ProtectionLevel.STANDARD
ProtectionLevel.ENHANCED
ProtectionLevel.CRITICAL

// Heartbeat Intervals
STANDARD_INTERVAL = 60000L  // 1 minute
ENHANCED_INTERVAL = 30000L  // 30 seconds
CRITICAL_INTERVAL = 10000L  // 10 seconds

// Thresholds
ATTEMPT_THRESHOLD = 3
MAX_FAILED_ATTEMPTS = 5
LOCKOUT_DURATION_MS = 900000L  // 15 minutes
```

---

## Error Codes

| Code | Meaning | Action |
|------|---------|--------|
| APP_NOT_INSTALLED | App removed | Cannot recover |
| DEVICE_OWNER_DISABLED | Device owner lost | Attempt recovery |
| UNINSTALL_NOT_BLOCKED | Protection removed | Re-enable |
| FORCE_STOP_NOT_BLOCKED | Protection removed | Re-enable |
| ENCRYPTED_STATUS_INVALID | Status corrupted | Recreate |

---

## Support

For issues or questions:
1. Check logs for detailed error messages
2. Verify device owner status
3. Check Android version compatibility
4. Review audit logs
5. Consult COMPLETE_IMPLEMENTATION_REPORT.md

---

**Quick Reference Version**: 1.0  
**Last Updated**: January 15, 2026  
**Status**: ✅ Complete
