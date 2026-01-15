# Feature 4.5: Disable Shutdown & Restart - Architecture & Design

**Version**: 1.0  
**Date**: January 7, 2026  
**Status**: ✅ Complete

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    POWER MANAGEMENT SYSTEM                              │
│                    Feature 4.5 - Disable Shutdown & Restart             │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                          ANDROID APPLICATION                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    POWER MENU BLOCKER                            │  │
│  │  (app/src/main/java/com/example/deviceowner/managers/)          │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │                                                                  │  │
│  │  ┌─────────────────────────────────────────────────────────┐   │  │
│  │  │ PowerMenuBlocker.kt                                     │   │  │
│  │  ├─────────────────────────────────────────────────────────┤   │  │
│  │  │                                                         │   │  │
│  │  │ OEM-Specific Blocking:                                  │   │  │
│  │  │  • Samsung KNOX API                                     │   │  │
│  │  │  • Xiaomi MIUI API                                      │   │  │
│  │  │  • OnePlus OxygenOS API                                 │   │  │
│  │  │  • Google Pixel Standard API                            │   │  │
│  │  │  • Fallback: Overlay interception                       │   │  │
│  │  │                                                         │   │  │
│  │  │ Methods:                                                │   │  │
│  │  │  • blockPowerMenu()                                     │   │  │
│  │  │  • unblockPowerMenu()                                   │   │  │
│  │  │  • isPowerMenuBlocked()                                 │   │  │
│  │  │  • blockPowerButton()                                   │   │  │
│  │  │  • getBlockingStatus()                                  │   │  │
│  │  │                                                         │   │  │
│  │  └─────────────────────────────────────────────────────────┘   │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    REBOOT DETECTION                              │  │
│  │  (app/src/main/java/com/example/deviceowner/receivers/)         │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │                                                                  │  │
│  │  ┌─────────────────────────────────────────────────────────┐   │  │
│  │  │ RebootDetectionReceiver.kt                              │   │  │
│  │  ├─────────────────────────────────────────────────────────┤   │  │
│  │  │                                                         │   │  │
│  │  │ Boot Event Handling:                                    │   │  │
│  │  │  • Verify device owner status                           │   │  │
│  │  │  • Verify app still installed                           │   │  │
│  │  │  • Check boot count                                     │   │  │
│  │  │  • Verify device fingerprint                            │   │  │
│  │  │  • Detect unauthorized reboot                           │   │  │
│  │  │  • Trigger auto-lock if needed                          │   │  │
│  │  │  • Log boot event                                       │   │  │
│  │  │  • Alert backend                                        │   │  │
│  │  │                                                         │   │  │
│  │  │ Methods:                                                │   │  │
│  │  │  • onReceive(intent)                                    │   │  │
│  │  │  • verifyBootIntegrity()                                │   │  │
│  │  │  • detectUnauthorizedReboot()                           │   │  │
│  │  │                                                         │   │  │
│  │  └─────────────────────────────────────────────────────────┘   │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    POWER LOSS MONITOR                            │  │
│  │  (app/src/main/java/com/example/deviceowner/managers/)          │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │                                                                  │  │
│  │  ┌─────────────────────────────────────────────────────────┐   │  │
│  │  │ PowerLossMonitor.kt                                     │   │  │
│  │  ├─────────────────────────────────────────────────────────┤   │  │
│  │  │                                                         │   │  │
│  │  │ Power Loss Detection:                                   │   │  │
│  │  │  • Monitor battery level (30s interval)                 │   │  │
│  │  │  • Detect sudden drops (>20% in 1 min)                 │   │  │
│  │  │  • Monitor power state changes                          │   │  │
│  │  │  • Detect unexpected shutdowns                          │   │  │
│  │  │  • Alert backend of anomalies                           │   │  │
│  │  │                                                         │   │  │
│  │  │ Methods:                                                │   │  │
│  │  │  • startMonitoring()                                    │   │  │
│  │  │  • stopMonitoring()                                     │   │  │
│  │  │  • isMonitoring()                                       │   │  │
│  │  │  • getPowerLossStatus()                                 │   │  │
│  │  │  • detectPowerLoss()                                    │   │  │
│  │  │                                                         │   │  │
│  │  └─────────────────────────────────────────────────────────┘   │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    AUTO-LOCK MANAGER                             │  │
│  │  (app/src/main/java/com/example/deviceowner/managers/)          │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │                                                                  │  │
│  │  ┌─────────────────────────────────────────────────────────┐   │  │
│  │  │ AutoLockManager.kt                                      │   │  │
│  │  ├─────────────────────────────────────────────────────────┤   │  │
│  │  │                                                         │   │  │
│  │  │ Auto-Lock Response:                                     │   │  │
│  │  │  • Lock device after unauthorized reboot                │   │  │
│  │  │  • Lock device after power loss                         │   │  │
│  │  │  • Display lock message                                 │   │  │
│  │  │  • Alert backend                                        │   │  │
│  │  │  • Prevent unlock without backend approval              │   │  │
│  │  │                                                         │   │  │
│  │  │ Methods:                                                │   │  │
│  │  │  • autoLockOnReboot()                                   │   │  │
│  │  │  • autoLockOnPowerLoss()                                │   │  │
│  │  │  • isAutoLockEnabled()                                  │   │  │
│  │  │  • setAutoLockEnabled(enabled)                          │   │  │
│  │  │                                                         │   │  │
│  │  └─────────────────────────────────────────────────────────┘   │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    POWER STATE MANAGER                           │  │
│  │  (app/src/main/java/com/example/deviceowner/managers/)          │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │                                                                  │  │
│  │  ┌─────────────────────────────────────────────────────────┐   │  │
│  │  │ PowerStateManager.kt                                    │   │  │
│  │  ├─────────────────────────────────────────────────────────┤   │  │
│  │  │                                                         │   │  │
│  │  │ Power State Persistence:                                │   │  │
│  │  │  • Save power state on shutdown                         │   │  │
│  │  │  • Restore power state on boot                          │   │  │
│  │  │  • Verify state integrity                               │   │  │
│  │  │  • Detect state tampering                               │   │  │
│  │  │  • Track boot count                                     │   │  │
│  │  │  • Track power loss events                              │   │  │
│  │  │                                                         │   │  │
│  │  │ Methods:                                                │   │  │
│  │  │  • savePowerState()                                     │   │  │
│  │  │  • restorePowerState()                                  │   │  │
│  │  │  • getPowerState()                                      │   │  │
│  │  │  • verifyPowerStateIntegrity()                          │   │  │
│  │  │                                                         │   │  │
│  │  └─────────────────────────────────────────────────────────┘   │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    ANDROID FRAMEWORK INTEGRATION                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ DevicePolicyManager                                              │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │                                                                  │  │
│  │ • lockNow() - Lock device immediately                           │  │
│  │ • reboot(admin) - Reboot device                                 │  │
│  │ • setUsbDataSignalingEnabled(enabled) - Control USB             │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ BroadcastReceiver                                                │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │                                                                  │  │
│  │ • BOOT_COMPLETED - Device boot event                            │  │
│  │ • BATTERY_CHANGED - Battery level change                        │  │
│  │ • ACTION_POWER_CONNECTED - Power connected                      │  │
│  │ • ACTION_POWER_DISCONNECTED - Power disconnected                │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                         BACKEND INTEGRATION                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Backend Server (Node.js)                                         │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │                                                                  │  │
│  │ Endpoints:                                                      │  │
│  │  • POST /api/devices/:deviceId/power-events                     │  │
│  │    └─ Report power events (reboot, power loss)                  │  │
│  │                                                                  │  │
│  │  • POST /api/devices/:deviceId/alerts                           │  │
│  │    └─ Send power anomaly alerts                                 │  │
│  │                                                                  │  │
│  │  • GET /api/devices/:deviceId/power-status                      │  │
│  │    └─ Get power status from backend                             │  │
│  │                                                                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    POWER MENU BLOCKING FLOW                             │
└─────────────────────────────────────────────────────────────────────────┘

1. Application Initialization
   ┌──────────────────────────────────────────────────────────────────┐
   │ AdminReceiver.onEnabled()                                        │
   │  └─ Initialize power management                                  │
   └──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
2. PowerMenuBlocker Activation
   ┌──────────────────────────────────────────────────────────────────┐
   │ PowerMenuBlocker.blockPowerMenu()                                │
   │  ├─ Detect OEM (Samsung, Xiaomi, OnePlus, Pixel)                │
   │  ├─ Apply OEM-specific blocking                                  │
   │  ├─ Fallback to overlay interception                             │
   │  └─ Store blocking status                                        │
   └──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
3. Power Menu Blocked
   ┌──────────────────────────────────────────────────────────────────┐
   │ ✅ Power menu disabled                                           │
   │ ✅ Power button intercepted                                      │
   │ ✅ User cannot access power menu                                 │
   │ ✅ Status persists across reboots                                │
   └──────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    REBOOT DETECTION FLOW                                │
└─────────────────────────────────────────────────────────────────────────┘

1. Device Reboots
   ┌──────────────────────────────────────────────────────────────────┐
   │ • Device power cycles                                            │
   │ • System boots                                                   │
   └──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
2. RebootDetectionReceiver Triggered
   ┌──────────────────────────────────────────────────────────────────┐
   │ BOOT_COMPLETED intent received                                   │
   │  ├─ Verify device owner status                                   │
   │  ├─ Verify app still installed                                   │
   │  ├─ Check boot count                                             │
   │  ├─ Verify device fingerprint                                    │
   │  └─ Detect unauthorized reboot                                   │
   └──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
3. Boot Verification
   ┌──────────────────────────────────────────────────────────────────┐
   │ If authorized reboot:                                            │
   │  ├─ Update boot count                                            │
   │  ├─ Update last boot time                                        │
   │  ├─ Log boot event                                               │
   │  └─ Continue normal operation                                    │
   │                                                                  │
   │ If unauthorized reboot:                                          │
   │  ├─ Trigger auto-lock                                            │
   │  ├─ Log reboot attempt                                           │
   │  ├─ Alert backend                                                │
   │  └─ Prevent device interaction                                   │
   └──────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    POWER LOSS DETECTION FLOW                            │
└─────────────────────────────────────────────────────────────────────────┘

1. PowerLossMonitor Started
   ┌──────────────────────────────────────────────────────────────────┐
   │ PowerLossMonitor.startMonitoring()                               │
   │  ├─ Start background service                                     │
   │  ├─ Register battery change receiver                             │
   │  └─ Initialize monitoring interval (30s)                         │
   └──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
2. Continuous Monitoring
   ┌──────────────────────────────────────────────────────────────────┐
   │ Every 30 seconds:                                                │
   │  ├─ Check battery level                                          │
   │  ├─ Check power state                                            │
   │  ├─ Detect sudden drops (>20% in 1 min)                         │
   │  ├─ Detect power state changes                                   │
   │  └─ Detect unexpected shutdowns                                  │
   └──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
3. Power Loss Detected
   ┌──────────────────────────────────────────────────────────────────┐
   │ PowerLossMonitor.detectPowerLoss()                               │
   │  ├─ Increment power loss counter                                 │
   │  ├─ Record power loss time                                       │
   │  ├─ Trigger auto-lock                                            │
   │  ├─ Log power loss event                                         │
   │  └─ Alert backend                                                │
   └──────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    AUTO-LOCK RESPONSE FLOW                              │
└─────────────────────────────────────────────────────────────────────────┘

1. Trigger Event
   ┌──────────────────────────────────────────────────────────────────┐
   │ • Unauthorized reboot detected                                   │
   │ • Power loss detected                                            │
   │ • Tampering detected                                             │
   └──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
2. AutoLockManager Response
   ┌──────────────────────────────────────────────────────────────────┐
   │ AutoLockManager.autoLockOnReboot() or autoLockOnPowerLoss()      │
   │  ├─ Lock device immediately                                      │
   │  ├─ Display lock message                                         │
   │  ├─ Log lock event                                               │
   │  ├─ Alert backend                                                │
   │  └─ Prevent unlock without backend approval                      │
   └──────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
3. Device Locked
   ┌──────────────────────────────────────────────────────────────────┐
   │ ✅ Device locked immediately                                     │
   │ ✅ User cannot interact with device                              │
   │ ✅ Lock persists until backend approval                          │
   │ ✅ Event logged for audit trail                                  │
   │ ✅ Backend alerted of incident                                   │
   └──────────────────────────────────────────────────────────────────┘
```

---

## Component Interaction Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    COMPONENT INTERACTIONS                               │
└─────────────────────────────────────────────────────────────────────────┘

                          ┌──────────────────┐
                          │  Application     │
                          │  Code            │
                          └────────┬─────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
                    ▼              ▼              ▼
        ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
        │ PowerMenuBlocker  │ │ RebootDetection  │ │ PowerLossMonitor │
        │                  │ │ Receiver         │ │                  │
        └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘
                 │                    │                    │
                 └────────────────────┼────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                    ▼                 ▼                 ▼
        ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
        │ AutoLockManager  │ │ PowerStateManager│ │ DeviceOwnerManager
        │                  │ │                  │ │ (Feature 4.1)    │
        └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘
                 │                    │                    │
                 └────────────────────┼────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                    ▼                 ▼                 ▼
        ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
        │ OverlayController│ │ HeartbeatService │ │ CommandQueueService
        │ (Feature 4.6)    │ │ (Feature 4.8)    │ │ (Feature 4.9)    │
        └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘
                 │                    │                    │
                 └────────────────────┼────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                    ▼                 ▼                 ▼
        ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
        │ Device           │ │ Backend          │ │ Logging &        │
        │ State            │ │ Server           │ │ Audit Trail      │
        │                  │ │                  │ │                  │
        └──────────────────┘ └──────────────────┘ └──────────────────┘
```

---

## File Structure

```
app/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── deviceowner/
│       │               ├── managers/
│       │               │   ├── PowerMenuBlocker.kt ✅
│       │               │   ├── PowerLossMonitor.kt ✅
│       │               │   ├── AutoLockManager.kt ✅
│       │               │   ├── PowerStateManager.kt ✅
│       │               │   └── DeviceOwnerManager.kt (Feature 4.1)
│       │               ├── receivers/
│       │               │   ├── RebootDetectionReceiver.kt ✅
│       │               │   ├── AdminReceiver.kt (Feature 4.1)
│       │               │   └── BootReceiver.kt (Feature 4.1)
│       │               ├── services/
│       │               │   ├── PowerLossMonitorService.kt ✅
│       │               │   ├── HeartbeatVerificationService.kt (Feature 4.8)
│       │               │   └── CommandQueueService.kt (Feature 4.9)
│       │               ├── overlay/
│       │               │   └── OverlayController.kt (Feature 4.6)
│       │               └── ...
│       ├── res/
│       │   ├── xml/
│       │   │   └── device_admin_receiver.xml (Feature 4.1)
│       │   └── ...
│       └── AndroidManifest.xml ✅
│
backend/
├── server.js
├── package.json
└── logs/

Documentation:
├── FEATURE_4.5_PROFESSIONAL_DOCUMENTATION.md ✅
├── FEATURE_4.5_ARCHITECTURE.md ✅
├── FEATURE_4.5_QUICK_SUMMARY.md (pending)
├── FEATURE_4.5_IMPROVEMENTS.md (pending)
└── FEATURE_4.5_STATUS.txt (pending)
```

---

## Success Criteria Verification

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SUCCESS CRITERIA VERIFICATION                        │
└─────────────────────────────────────────────────────────────────────────┘

Criterion 1: Power menu blocked on supported devices
├─ Samsung: KNOX API blocking ✅
├─ Xiaomi: MIUI API blocking ✅
├─ OnePlus: OxygenOS API blocking ✅
├─ Google Pixel: Standard API blocking ✅
├─ Fallback: Overlay interception ✅
└─ Result: ✅ PASS - All OEMs supported

Criterion 2: Reboot detection working
├─ Boot event monitoring ✅
├─ Device owner verification ✅
├─ App installation verification ✅
├─ Boot count tracking ✅
├─ Device fingerprint verification ✅
└─ Result: ✅ PASS - Reboot detection complete

Criterion 3: Auto-lock triggered after unauthorized reboot
├─ Unauthorized reboot detection ✅
├─ Immediate device lock ✅
├─ Lock message display ✅
├─ Backend alert ✅
├─ Offline enforcement ✅
└─ Result: ✅ PASS - Auto-lock working

Criterion 4: Power loss monitoring active
├─ Battery level monitoring ✅
├─ Power state monitoring ✅
├─ Sudden drop detection ✅
├─ Unexpected shutdown detection ✅
├─ Backend alert ✅
└─ Result: ✅ PASS - Power loss monitoring complete

Criterion 5: Backend integration working
├─ Power event API endpoint ✅
├─ Alert API endpoint ✅
├─ Status sync ✅
├─ Offline queueing ✅
└─ Result: ✅ PASS - Backend integration complete
```

---

## Integration Points

### Feature 4.1 (Device Owner)
- Uses DeviceOwnerManager for device lock
- Leverages device owner privilege for power control
- Integrates with AdminReceiver for initialization

### Feature 4.6 (Overlay UI)
- Displays lock message overlay
- Shows power loss warning
- Displays reboot notification

### Feature 4.8 (Heartbeat)
- Reports power events to backend
- Syncs power status
- Receives power configuration updates

### Feature 4.9 (Command Queue)
- Queues power-related commands
- Executes commands offline
- Syncs execution results

---

## Conclusion

Feature 4.5 provides comprehensive power management with:

- ✅ Multi-OEM power menu blocking
- ✅ Robust reboot detection
- ✅ Automatic lock response
- ✅ Power loss monitoring
- ✅ Backend integration
- ✅ Offline enforcement
- ✅ Production-ready code

The architecture is scalable and ready for deployment.

---

*Last Updated: January 7, 2026*
