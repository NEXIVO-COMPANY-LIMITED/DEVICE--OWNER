# Feature 4.0: Wipe Sensitive Data - Architecture

**Date**: January 15, 2026  
**Version**: 1.0

---

## System Architecture

### Overview

Feature 4.0 provides a centralized data wiping system that can be triggered by multiple security events throughout the application.

```
┌─────────────────────────────────────────────────────────────┐
│                    Security Event Triggers                   │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐│
│  │ DeviceMismatch   │  │ BlockingCommand  │  │  Tamper    ││
│  │    Handler       │  │     Handler      │  │  Response  ││
│  └────────┬─────────┘  └────────┬─────────┘  └─────┬──────┘│
│           │                     │                    │       │
│           └─────────────────────┼────────────────────┘       │
│                                 │                            │
└─────────────────────────────────┼────────────────────────────┘
                                  │
                                  ▼
                    ┌─────────────────────────────┐
                    │  SensitiveDataWipeManager   │
                    └─────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                    ▼                           ▼
        ┌───────────────────────┐   ┌──────────────────────┐
        │   Data Wipe Engine    │   │   Audit Logger       │
        └───────────────────────┘   └──────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
        ▼                       ▼
┌───────────────┐       ┌──────────────┐
│ SharedPrefs   │       │ File System  │
│   Cleaner     │       │   Cleaner    │
└───────────────┘       └──────────────┘
        │                       │
        ▼                       ▼
┌───────────────┐       ┌──────────────┐
│ 15 Prefs      │       │ Directories  │
│ + Default     │       │ Databases    │
└───────────────┘       │ Temp Files   │
                        └──────────────┘
```

---

## Component Breakdown

### 1. SensitiveDataWipeManager

**Location**: `app/src/main/java/com/example/deviceowner/managers/protection/SensitiveDataWipeManager.kt`

**Responsibilities**:
- Coordinate all data wiping operations
- Manage wipe execution flow
- Handle errors gracefully
- Log all operations
- Report status

**Key Methods**:
```kotlin
fun wipeSensitiveData(): Boolean
fun wipeSharedPreferences(prefName: String): Boolean
fun wipeDatabase(dbName: String): Boolean
fun wipeFile(filePath: String): Boolean
fun getWipeStatusSummary(): Map<String, Any>
```

### 2. SharedPreferences Cleaner

**Responsibilities**:
- Clear all known SharedPreferences
- Clear default SharedPreferences
- Handle missing preferences gracefully

**Preferences Cleared**:
- identifier_prefs
- blocking_commands
- heartbeat_data
- command_queue
- mismatch_alerts
- audit_log
- device_profile
- boot_verification
- registration_data
- payment_data
- loan_data
- lock_status
- device_owner_prefs
- security_prefs
- app_preferences
- {packageName}_preferences (default)

### 3. File System Cleaner

**Responsibilities**:
- Clear cache directory
- Clear files directory
- Delete databases
- Clear temporary files
- Recursive directory deletion

**Directories Cleared**:
- `context.cacheDir`
- `context.filesDir`
- `context.noBackupFilesDir`
- `context.externalCacheDir`
- Database directory

### 4. Audit Logger

**Responsibilities**:
- Log all wipe operations
- Track success/failure
- Provide audit trail
- Integration with IdentifierAuditLog

**Log Events**:
- DATA_WIPE_COMPLETED
- DATA_WIPE_PARTIAL
- DATA_WIPE_FAILED
- DATA_WIPE_SUMMARY
- PREFS_WIPED
- DATABASE_WIPED
- FILE_WIPED

---

## Integration Points

### 1. DeviceMismatchHandler

**Trigger Conditions**:
- Device swap detected
- Device clone detected
- Critical mismatch event

**Flow**:
```
Device Mismatch Detected
    ↓
Check Severity
    ↓
If Critical
    ↓
Call wipeSensitiveData()
    ↓
Log Result
    ↓
Report to Backend
```

### 2. BlockingCommandHandler

**Trigger Conditions**:
- Backend sends WIPE_DATA command
- Remote wipe request
- Security policy enforcement

**Flow**:
```
Receive WIPE_DATA Command
    ↓
Validate Command
    ↓
Call wipeSensitiveData()
    ↓
Report Execution Status
    ↓
Update Command Status
```

### 3. TamperResponseHandler

**Trigger Conditions**:
- Tamper detection
- Security breach
- Unauthorized access

**Flow**:
```
Tamper Detected
    ↓
Assess Threat Level
    ↓
If High Threat
    ↓
Call wipeSensitiveData()
    ↓
Lock Device
    ↓
Report Incident
```

---

## Data Flow

### Wipe Operation Flow

```
1. Trigger Event
   ↓
2. Create SensitiveDataWipeManager
   ↓
3. Call wipeSensitiveData()
   ↓
4. Wipe SharedPreferences
   ├─ Clear identifier_prefs
   ├─ Clear blocking_commands
   ├─ Clear heartbeat_data
   ├─ ... (all 15 preferences)
   └─ Clear default preferences
   ↓
5. Wipe Cache Directory
   ├─ List all files
   ├─ Delete recursively
   └─ Verify deletion
   ↓
6. Wipe Files Directory
   ├─ List all files
   ├─ Delete recursively
   └─ Verify deletion
   ↓
7. Wipe Databases
   ├─ Get database list
   ├─ Delete each database
   └─ Verify deletion
   ↓
8. Wipe Temporary Files
   ├─ Clear no-backup directory
   ├─ Clear external cache
   └─ Verify deletion
   ↓
9. Log Operation
   ├─ Log to audit trail
   ├─ Log success/failure
   └─ Log summary
   ↓
10. Return Result
    └─ true if all successful
    └─ false if any failures
```

---

## Error Handling

### Strategy

1. **Continue on Error**: If one operation fails, continue with others
2. **Log All Errors**: Every error is logged for debugging
3. **Return Status**: Return overall success/failure status
4. **Partial Success**: Track which operations succeeded/failed

### Error Scenarios

| Scenario | Handling | Result |
|----------|----------|--------|
| Permission Denied | Log error, continue | Partial success |
| File Not Found | Log info, continue | Success |
| Directory Not Empty | Retry recursively | Success or partial |
| Database Locked | Log error, continue | Partial success |
| Out of Memory | Log error, abort | Failure |

---

## Security Considerations

### 1. Data Permanence

**Issue**: Standard deletion doesn't guarantee data is unrecoverable

**Mitigation**:
- Document limitation
- Consider secure erase for future
- Recommend device encryption

### 2. Permissions

**Issue**: May not have permission to delete all files

**Mitigation**:
- Graceful error handling
- Log permission errors
- Continue with accessible files

### 3. Timing Attacks

**Issue**: Wipe time may reveal data size

**Mitigation**:
- Not a primary concern
- Wipe is triggered on security events
- Timing information not exposed

### 4. Incomplete Wipe

**Issue**: Wipe may be interrupted

**Mitigation**:
- Atomic operations where possible
- Log partial completion
- Can be retried

---

## Performance Optimization

### 1. Parallel Operations

**Current**: Sequential wiping
**Future**: Parallel wipe operations for faster completion

### 2. Selective Wiping

**Current**: Wipe all data
**Future**: Wipe only specific categories based on threat level

### 3. Background Execution

**Current**: Synchronous execution
**Recommendation**: Run in background thread for large datasets

---

## Testing Strategy

### Unit Tests

```kotlin
// Test individual wipe methods
testWipeSharedPreferences()
testWipeCacheDirectory()
testWipeFilesDirectory()
testWipeDatabases()
testWipeTempFiles()

// Test error handling
testWipeWithPermissionDenied()
testWipeWithMissingFiles()
testWipeWithLockedDatabase()

// Test status reporting
testGetWipeStatusSummary()
```

### Integration Tests

```kotlin
// Test trigger integration
testDeviceMismatchTriggersWipe()
testBlockingCommandTriggersWipe()
testTamperDetectionTriggersWipe()

// Test end-to-end flow
testCompleteWipeFlow()
testPartialWipeFlow()
testWipeWithErrors()
```

### Manual Tests

1. Trigger device swap → Verify wipe
2. Send WIPE_DATA command → Verify wipe
3. Trigger tamper detection → Verify wipe
4. Check audit logs → Verify logging
5. Verify data cleared → Check all locations

---

## Monitoring & Metrics

### Key Metrics

1. **Wipe Success Rate**: Percentage of successful wipes
2. **Wipe Duration**: Time taken for complete wipe
3. **Partial Wipe Rate**: Percentage of partial wipes
4. **Error Rate**: Frequency of wipe errors
5. **Trigger Distribution**: Which triggers are most common

### Logging

All operations logged to:
- Android Logcat (DEBUG/WARN/ERROR)
- IdentifierAuditLog (persistent)
- Backend reporting (via handlers)

---

## Future Enhancements

### 1. Secure Erase

Implement DOD 5220.22-M standard:
- Multiple pass overwrite
- Random data patterns
- Verification pass

### 2. Selective Wipe

Wipe based on data category:
- Financial data only
- User data only
- System data only
- Custom categories

### 3. Backup Before Wipe

Optional backup to backend:
- Encrypted backup
- Selective backup
- Restore capability

### 4. Progress Reporting

Real-time progress updates:
- Percentage complete
- Current operation
- Estimated time remaining

---

## Conclusion

Feature 4.0 provides a robust, well-integrated data wiping system that protects sensitive data on critical security events. The architecture is modular, extensible, and production-ready.

---

**Document Version**: 1.0  
**Last Updated**: January 15, 2026  
**Status**: ✅ Complete

