# BUILD INSTRUCTIONS

**Project**: Device Owner Management System  
**Date**: January 15, 2026  
**Purpose**: How to build the APK

---

## ⚠️ IMPORTANT: Command Line Build Issue

There's a known issue with Kotlin compiler and Java 25.0.1 that prevents command-line builds.

**Solution**: Use **Android Studio** to build the APK.

---

## METHOD 1: BUILD IN ANDROID STUDIO (RECOMMENDED)

### Step 1: Open Project
1. Open Android Studio
2. Click **File** → **Open**
3. Select the `DEVICEOWNER` folder
4. Wait for Gradle sync to complete

### Step 2: Sync Gradle
1. Click **File** → **Sync Project with Gradle Files**
2. Wait for sync to complete (may take 2-5 minutes)
3. Check for any errors in the **Build** tab

### Step 3: Build APK

#### For Debug APK (Testing):
1. Click **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
2. Wait for build to complete
3. Click **locate** in the notification
4. APK location: `app/build/outputs/apk/debug/app-debug.apk`

#### For Release APK (Production):
1. Click **Build** → **Generate Signed Bundle / APK**
2. Select **APK**
3. Click **Next**
4. Create or select keystore
5. Enter keystore password
6. Click **Next**
7. Select **release** build variant
8. Click **Finish**
9. APK location: `app/build/outputs/apk/release/app-release.apk`

---

## METHOD 2: BUILD VIA TERMINAL IN ANDROID STUDIO

1. Open **Terminal** tab in Android Studio (bottom)
2. Run one of these commands:

```bash
# Debug APK
.\gradlew assembleDebug

# Release APK (unsigned)
.\gradlew assembleRelease

# Clean and build
.\gradlew clean assembleDebug
```

**Note**: This uses Android Studio's embedded Gradle, which handles Java version issues better.

---

## METHOD 3: FIX COMMAND LINE BUILD (Advanced)

If you want to build from PowerShell/CMD, you need to fix the Java version issue:

### Option A: Use Java 17 or 21

1. Download Java 17 or 21 from:
   - https://adoptium.net/
   - Or https://www.oracle.com/java/technologies/downloads/

2. Set JAVA_HOME environment variable:
```powershell
# In PowerShell (as Administrator)
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Java\jdk-17', 'Machine')
```

3. Restart PowerShell and try again:
```powershell
.\gradlew assembleDebug
```

### Option B: Use Android Studio's Java

1. Find Android Studio's JDK path:
   - Usually: `C:\Program Files\Android\Android Studio\jbr`

2. Set JAVA_HOME temporarily:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
```

---

## APK OUTPUT LOCATIONS

After successful build:

### Debug APK
```
app/build/outputs/apk/debug/app-debug.apk
```
- Size: ~15-20 MB
- Debuggable: Yes
- Signed: Debug keystore
- Use for: Testing

### Release APK
```
app/build/outputs/apk/release/app-release.apk
```
- Size: ~10-15 MB (smaller due to ProGuard)
- Debuggable: No
- Signed: Your keystore
- Use for: Production

---

## VERIFY APK

### Check APK Info
```bash
# Get APK info
aapt dump badging app-debug.apk

# Check package name
aapt dump badging app-debug.apk | grep package

# Expected output:
# package: name='com.example.deviceowner' versionCode='1' versionName='1.0'
```

### Install APK
```bash
# Install debug APK
adb install app-debug.apk

# Install release APK
adb install app-release.apk

# Install with replacement
adb install -r app-debug.apk
```

---

## TROUBLESHOOTING

### Error: "Build failed with Java version issue"
**Solution**: Use Android Studio to build (Method 1)

### Error: "Gradle sync failed"
**Solution**: 
1. Click **File** → **Invalidate Caches**
2. Select **Invalidate and Restart**
3. Wait for Android Studio to restart
4. Try building again

### Error: "SDK not found"
**Solution**:
1. Open **File** → **Project Structure**
2. Click **SDK Location**
3. Set Android SDK location (usually `C:\Users\YourName\AppData\Local\Android\Sdk`)
4. Click **OK**

### Error: "Keystore not found" (Release build)
**Solution**:
1. Create new keystore in Android Studio
2. Or use debug keystore for testing:
   - Location: `C:\Users\YourName\.android\debug.keystore`
   - Password: `android`
   - Alias: `androiddebugkey`
   - Alias password: `android`

---

## QUICK START (RECOMMENDED)

**For Testing** (Easiest):

1. Open project in Android Studio
2. Wait for Gradle sync
3. Click **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
4. Click **locate** when done
5. Copy `app-debug.apk` to your device
6. Install: `adb install app-debug.apk`
7. Set device owner: `adb shell dpm set-device-owner com.example.deviceowner/.receivers.AdminReceiver`

**Done!** ✅

---

## BUILD VARIANTS

The project has 2 build variants:

### Debug
- Debuggable: Yes
- Minified: No
- Obfuscated: No
- Logging: Verbose
- Use for: Development and testing

### Release
- Debuggable: No
- Minified: Yes (ProGuard/R8)
- Obfuscated: Yes
- Logging: Minimal
- Use for: Production deployment

---

## NEXT STEPS AFTER BUILD

1. **Install APK**: `adb install app-debug.apk`
2. **Set Device Owner**: `adb shell dpm set-device-owner com.example.deviceowner/.receivers.AdminReceiver`
3. **Verify**: `adb shell dpm list-owners`
4. **Test**: Open app and check features

See `docs/DEVICE_OWNER_COMPONENT_NAME.md` for detailed setup instructions.

---

**Document Version**: 1.0  
**Last Updated**: January 15, 2026  
**Status**: ✅ READY FOR USE

---

**END OF DOCUMENT**
