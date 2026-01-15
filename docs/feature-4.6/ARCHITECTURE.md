# Feature 4.6: Architecture & Design

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Application Layer                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              OverlayController (API)                     │   │
│  │  - Public interface for overlay operations              │   │
│  │  - Specific methods for each overlay type               │   │
│  │  - Boot overlay management                              │   │
│  └──────────────────────────────────────────────────────────┘   │
│                          ↓                                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │         EnhancedOverlayController (Lock-Aware)          │   │
│  │  - Lock state management                                │   │
│  │  - Action callbacks                                     │   │
│  │  - Hardware button interception                         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                          ↓                                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │         OverlayCommandReceiver (Backend)                │   │
│  │  - Process backend overlay commands                     │   │
│  │  - Validate and parse commands                          │   │
│  │  - Color and type validation                            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Service Layer                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │           OverlayManager (Service)                       │   │
│  │  - Lifecycle management (START_STICKY)                  │   │
│  │  - Window management                                    │   │
│  │  - Queue processing                                     │   │
│  │  - State persistence                                    │   │
│  │  - Audit logging                                        │   │
│  └──────────────────────────────────────────────────────────┘   │
│                          ↓                                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │         OverlayStateManager (Persistence)               │   │
│  │  - Lock state persistence                               │   │
│  │  - Active overlay persistence                           │   │
│  │  - State restoration                                    │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      View Layer                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              OverlayView (Custom View)                   │   │
│  │  - Rendering (title, message, button)                   │   │
│  │  - Touch event handling                                 │   │
│  │  - Lock-state aware UI                                  │   │
│  │  - Hardware button interception                         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                          ↓                                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │         EnhancedOverlayView (Advanced)                   │   │
│  │  - Hardware button interception                         │   │
│  │  - Touch event consumption                              │   │
│  │  - Lock-state aware rendering                           │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Data Layer                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              OverlayData (Model)                         │   │
│  │  - Overlay information                                  │   │
│  │  - JSON serialization                                   │   │
│  │  - Expiry checking                                      │   │
│  └──────────────────────────────────────────────────────────┘   │
│                          ↓                                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │         OverlayType (Enum)                              │   │
│  │  - 8 overlay types                                      │   │
│  │  - Type-specific behavior                               │   │
│  └──────────────────────────────────────────────────────────┘   │
│                          ↓                                        │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │         SharedPreferences (Persistence)                 │   │
│  │  - Active overlays                                      │   │
│  │  - Overlay queue                                        │   │
│  │  - Lock state                                           │   │
│  │  - Boot overlays                                        │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Component Interactions

### Overlay Display Flow

```
1. OverlayController.showOverlay(overlay)
   ↓
2. Intent to OverlayManager with action "SHOW_OVERLAY"
   ↓
3. OverlayManager.onStartCommand() receives intent
   ↓
4. OverlayData.fromJson() deserializes overlay
   ↓
5. Check if overlay already active
   ↓
6. Check if overlay expired
   ↓
7. If another overlay active → Add to queue
   ↓
8. Create OverlayView with overlay data
   ↓
9. Create WindowManager.LayoutParams
   ↓
10. windowManager.addView(overlayView, params)
    ↓
11. Save overlay state to SharedPreferences
    ↓
12. Log to IdentifierAuditLog
    ↓
13. Overlay displayed on screen
```

### Overlay Dismissal Flow

```
1. User taps action button
   ↓
2. OverlayView.onClickListener triggered
   ↓
3. onDismiss callback called with overlayId
   ↓
4. OverlayController.dismissOverlay(overlayId)
   ↓
5. Intent to OverlayManager with action "DISMISS_OVERLAY"
   ↓
6. OverlayManager.dismissOverlay() called
   ↓
7. windowManager.removeView(overlayView)
   ↓
8. Remove from activeOverlays map
   ↓
9. Log dismissal to audit log
   ↓
10. Check if queue has next overlay
    ↓
11. If yes → showOverlay(nextOverlay)
    ↓
12. Overlay dismissed and next displayed
```

### Lock State Transition Flow

```
1. RemoteLockManager detects lock state change
   ↓
2. EnhancedOverlayController.updateLockState(newState)
   ↓
3. OverlayStateManager.saveLockState(newState)
   ↓
4. Current overlay behavior updated
   ↓
5. If SOFT_LOCK → Allow dismissal
   ↓
6. If HARD_LOCK → Block all interactions
   ↓
7. If UNLOCKED → Normal behavior
   ↓
8. Log state transition
```

### Backend Command Processing Flow

```
1. Backend sends overlay commands in heartbeat response
   ↓
2. HeartbeatApiService receives response
   ↓
3. OverlayCommandReceiver.processOverlayCommands()
   ↓
4. For each command:
   a. Validate overlay type
   b. Check expiry time
   c. Parse color string
   d. Create OverlayData
   e. Call OverlayController.showOverlay()
   ↓
5. Overlay displayed on device
   ↓
6. Log backend command received
```

## Design Patterns

### 1. Service Pattern

**OverlayManager** implements Android Service pattern:
- Runs in background
- Persistent (START_STICKY)
- Survives app crashes
- Survives device reboots

```kotlin
class OverlayManager : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle commands
        return START_STICKY  // Restart on crash
    }
}
```

### 2. Controller Pattern

**OverlayController** implements Controller pattern:
- Central API for overlay operations
- Encapsulates business logic
- Delegates to service
- Provides high-level methods

```kotlin
class OverlayController(context: Context) {
    fun showPaymentReminder(amount, dueDate, loanId)
    fun showWarningMessage(title, message, warningId)
    // ... more methods
}
```

### 3. Observer Pattern

**OverlayActionCallback** implements Observer pattern:
- Notifies listeners of overlay actions
- Decouples overlay from action handlers
- Supports multiple listeners

```kotlin
interface OverlayActionCallback {
    suspend fun onAcknowledge(overlayId: String)
    suspend fun onPayNow(overlayId: String, amount: String)
    // ... more callbacks
}
```

### 4. Queue Pattern

**OverlayQueue** implements Queue pattern:
- FIFO ordering with priority
- Multiple overlays handled sequentially
- Persisted to SharedPreferences
- Restored on app restart

```kotlin
private val overlayQueue = mutableListOf<OverlayData>()

// Add to queue
overlayQueue.add(overlay)

// Process queue
if (overlayQueue.isNotEmpty()) {
    val nextOverlay = overlayQueue.removeAt(0)
    showOverlay(nextOverlay)
}
```

### 5. State Pattern

**LockState** enum implements State pattern:
- Defines lock states (UNLOCKED, SOFT_LOCK, HARD_LOCK)
- Overlay behavior changes based on state
- State transitions handled smoothly

```kotlin
enum class LockState {
    UNLOCKED,
    SOFT_LOCK,
    HARD_LOCK
}

// Behavior changes based on state
when (currentLockState) {
    LockState.SOFT_LOCK -> overlay.dismissible = true
    LockState.HARD_LOCK -> overlay.blockAllInteractions = true
}
```

### 6. Adapter Pattern

**OverlayCommandReceiver** implements Adapter pattern:
- Adapts backend commands to OverlayData
- Validates and transforms data
- Handles color parsing

```kotlin
fun processOverlayCommand(command: OverlayCommandResponse) {
    val overlayData = OverlayData(
        id = command.overlayId,
        type = OverlayType.valueOf(command.type),
        // ... map other fields
    )
}
```

## Data Flow Diagrams

### Overlay Display Sequence

```
User/Backend          OverlayController      OverlayManager        WindowManager
    |                      |                      |                      |
    |--showOverlay()------->|                      |                      |
    |                      |--startService()----->|                      |
    |                      |                      |--addView()---------->|
    |                      |                      |                      |--Render
    |                      |                      |<--View Added---------|
    |                      |<--Service Started----|                      |
    |<--Overlay Shown------|                      |                      |
```

### Overlay Dismissal Sequence

```
User                  OverlayView            OverlayManager        WindowManager
 |                       |                       |                      |
 |--Tap Button---------->|                       |                      |
 |                       |--onDismiss()--------->|                      |
 |                       |                       |--removeView()------->|
 |                       |                       |                      |--Remove
 |                       |                       |<--View Removed-------|
 |                       |<--Dismissed-----------|                      |
 |<--Overlay Gone--------|                       |                      |
```

### Lock State Update Sequence

```
RemoteLockManager    EnhancedOverlay      OverlayStateManager    OverlayView
      |              Controller                  |                    |
      |--updateLockState()-->|                   |                    |
      |                      |--saveLockState()-->|                   |
      |                      |                   |--Save to Prefs---->|
      |                      |<--Saved-----------|                    |
      |                      |--Update Behavior---->|                 |
      |                      |                      |--Update UI----->|
      |<--State Updated------|                      |                 |
```

## State Management

### Overlay State Lifecycle

```
┌─────────────┐
│   Created   │
└──────┬──────┘
       │
       ↓
┌─────────────────────┐
│  Queued (if active) │
└──────┬──────────────┘
       │
       ↓
┌──────────────────┐
│   Displayed      │
└──────┬───────────┘
       │
       ├─→ User Action → Dismissed
       │
       ├─→ Expired → Auto-removed
       │
       └─→ App Crash → Persisted → Restored
```

### Lock State Lifecycle

```
┌──────────┐
│ UNLOCKED │
└────┬─────┘
     │
     ↓
┌──────────────┐
│  SOFT_LOCK   │
└────┬─────────┘
     │
     ↓
┌──────────────┐
│  HARD_LOCK   │
└────┬─────────┘
     │
     ↓
┌──────────┐
│ UNLOCKED │
└──────────┘
```

## Persistence Strategy

### SharedPreferences Structure

```
overlay_prefs
├── active_overlays: [
│   {
│     "id": "payment_1",
│     "type": "PAYMENT_REMINDER",
│     "title": "Payment Due",
│     "message": "Amount: $100",
│     "dismissible": false,
│     "priority": 10,
│     "createdAt": 1704873600000
│   }
│ ]
├── overlay_queue: [
│   {
│     "id": "warning_1",
│     "type": "WARNING_MESSAGE",
│     ...
│   }
│ ]
└── overlay_history: [...]

overlay_boot_prefs
├── boot_overlay_payment_1: {...}
├── boot_overlay_warning_1: {...}
└── ...

overlay_state_prefs
├── current_lock_state: "HARD_LOCK"
└── active_overlays: [...]
```

### Persistence Triggers

1. **On Overlay Show**: Save to active_overlays
2. **On Overlay Queue**: Save to overlay_queue
3. **On Overlay Dismiss**: Remove from active_overlays
4. **On Boot Overlay**: Save to overlay_boot_prefs
5. **On Lock State Change**: Save to overlay_state_prefs
6. **On App Crash**: Restored via START_STICKY
7. **On Device Reboot**: Restored via boot receiver

## Error Handling

### Error Scenarios

```
Scenario 1: Invalid Overlay Type
├── Catch IllegalArgumentException
├── Log error
├── Report to audit log
└── Skip overlay

Scenario 2: Overlay Expired
├── Check expiryTime
├── Skip display
├── Log expiry
└── Continue with next

Scenario 3: Window Manager Error
├── Catch WindowManager exception
├── Log error
├── Report to audit log
└── Retry or skip

Scenario 4: JSON Parsing Error
├── Catch JsonSyntaxException
├── Log error
├── Return null
└── Handle gracefully

Scenario 5: Service Crash
├── START_STICKY restarts service
├── Load persisted overlays
├── Resume queue processing
└── Continue normally
```

## Performance Optimization

### Memory Optimization

1. **Lazy Loading**: Load overlays on demand
2. **Expiry Filtering**: Remove expired overlays
3. **Queue Limiting**: Limit queue size
4. **View Reuse**: Reuse overlay views when possible

### CPU Optimization

1. **Efficient Rendering**: Minimal view hierarchy
2. **Coroutine Usage**: Non-blocking operations
3. **Batch Processing**: Process multiple commands together
4. **Debouncing**: Avoid rapid state changes

### Storage Optimization

1. **JSON Compression**: Compact JSON format
2. **Selective Persistence**: Only persist necessary data
3. **Automatic Cleanup**: Remove expired overlays
4. **Incremental Updates**: Update only changed fields

## Security Considerations

### Input Validation

```kotlin
// Validate overlay type
val overlayType = try {
    OverlayType.valueOf(command.type)
} catch (e: IllegalArgumentException) {
    return  // Invalid type
}

// Validate color
val color = try {
    Color.parseColor(command.actionButtonColor)
} catch (e: Exception) {
    Color.BLUE  // Default color
}

// Validate expiry
if (command.expiryTime != null && 
    System.currentTimeMillis() > command.expiryTime) {
    return  // Expired
}
```

### Access Control

- Only OverlayController can show overlays
- Only OverlayManager can manage window
- Only backend can send overlay commands
- Audit logging for all operations

### Data Protection

- Overlay data stored in encrypted SharedPreferences
- Metadata can contain sensitive information
- Backend commands validated before processing
- Audit logs for compliance

## Testing Strategy

### Unit Tests

- OverlayData creation and validation
- OverlayType enum values
- JSON serialization/deserialization
- Priority ordering
- Expiry checking
- Metadata handling

### Integration Tests

- OverlayController with OverlayManager
- Backend command processing
- Lock state transitions
- Queue processing
- State persistence

### UI Tests

- Overlay display and rendering
- User interaction handling
- Button clicks
- Touch event consumption
- Hardware button interception

## Conclusion

The architecture follows Android best practices with clear separation of concerns, robust error handling, and comprehensive state management. The design supports persistence, scalability, and security requirements for a production-ready overlay UI system.
