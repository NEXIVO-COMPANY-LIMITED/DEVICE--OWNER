# Feature 4.7: Improvements Checklist

**Date**: January 15, 2026  
**Purpose**: Track implementation of improvements

---

## Priority 1: Critical Improvements

### ✅ 1. Configurable Heartbeat Interval

**Status**: ❌ Not Implemented  
**Effort**: 2 hours  
**Impact**: High

**Changes Needed**:
- [ ] Add configuration storage
- [ ] Load interval from SharedPreferences
- [ ] Update interval from backend response
- [ ] Validate interval range (60s - 5min)

**Files to Modify**:
- `UnifiedHeartbeatService.kt`
- Add `HeartbeatConfiguration.kt`

---

### ✅ 2. Full Verification Every 5 Minutes

**Status**: ❌ Not Implemented  
**Effort**: 4 hours  
**Impact**: High

**Changes Needed**:
- [ ] Add full verification timer
- [ ] Implement `performFullVerification()`
- [ ] Implement `performQuickHeartbeat()`
- [ ] Track last full verification time

**Files to Modify**:
- `UnifiedHeartbeatService.kt`

---

### ✅ 3. Configuration Sync via API

**Status**: ⚠️ Partial  
**Effort**: 6 hours  
**Impact**: High

**Changes Needed**:
- [ ] Add `HeartbeatConfiguration` data class
- [ ] Parse configuration from API response
- [ ] Apply configuration to service
- [ ] Save configuration locally
- [ ] Validate configuration values

**Files to Modify**:
- `UnifiedHeartbeatService.kt`
- `HeartbeatApiService.kt`
- Add `HeartbeatConfiguration.kt`

---

## Priority 2: Important Improvements

### ✅ 4. Enhanced Command Result Reporting

**Status**: ❌ Not Implemented  
**Effort**: 3 hours  
**Impact**: Medium

**Changes Needed**:
- [ ] Add `CommandResult` data class
- [ ] Implement `reportCommandResult()` API call
- [ ] Queue failed reports for retry
- [ ] Add result reporting to all commands

**Files to Modify**:
- `BlockingCommandHandler.kt`
- `HeartbeatApiService.kt`

---

### ✅ 5. Enhanced Offline Queueing

**Status**: ⚠️ Basic  
**Effort**: 4 hours  
**Impact**: Medium

**Changes Needed**:
- [ ] Create `OfflineHeartbeatEntity` database table
- [ ] Implement `OfflineHeartbeatDao`
- [ ] Queue heartbeats when offline
- [ ] Process queue when online
- [ ] Clean up old queued items

**Files to Modify**:
- Add `OfflineHeartbeatQueue.kt`
- `AppDatabase.kt`
- `UnifiedHeartbeatService.kt`

---

### ✅ 6. More Granular Change Detection

**Status**: ⚠️ Basic  
**Effort**: 5 hours  
**Impact**: Medium

**Changes Needed**:
- [ ] Add `DetailedChangeReport` data class
- [ ] Add `DeviceChange` data class
- [ ] Implement `detectDetailedChanges()`
- [ ] Detect app-level changes
- [ ] Implement change whitelisting
- [ ] Report changes to backend

**Files to Modify**:
- `UnifiedHeartbeatComparison.kt`
- Add `ChangeDetector.kt`

---

## Priority 3: Nice-to-Have

### ✅ 7. Service Coordination

**Status**: ❌ Not Implemented  
**Effort**: 2 hours  
**Impact**: Low

**Changes Needed**:
- [ ] Create `HeartbeatCoordinator.kt`
- [ ] Centralize service management
- [ ] Avoid duplicate work

---

### ✅ 8. Enhanced Crash Recovery

**Status**: ⚠️ Basic  
**Effort**: 2 hours  
**Impact**: Low

**Changes Needed**:
- [ ] Save state before crash
- [ ] Recover state on restart
- [ ] Report recovery to backend

---

## Testing Checklist

### Unit Tests
- [ ] Test configurable interval
- [ ] Test full verification cycle
- [ ] Test configuration sync
- [ ] Test command result reporting
- [ ] Test offline queueing
- [ ] Test change detection

### Integration Tests
- [ ] Test heartbeat with backend
- [ ] Test configuration updates
- [ ] Test offline/online transitions
- [ ] Test command execution and reporting

### Manual Tests
- [ ] Verify 1-minute heartbeat
- [ ] Verify 5-minute full verification
- [ ] Verify configuration changes apply
- [ ] Verify offline queueing works
- [ ] Verify crash recovery

---

## Completion Tracking

**Total Tasks**: 8  
**Completed**: 0  
**In Progress**: 0  
**Not Started**: 8

**Estimated Total Effort**: 28 hours  
**Target Completion**: 2 weeks

---

## Sign-off

- [ ] All Priority 1 improvements completed
- [ ] All Priority 2 improvements completed
- [ ] All tests passing
- [ ] Documentation updated
- [ ] Code reviewed
- [ ] Production ready

---

*Last Updated: January 15, 2026*
