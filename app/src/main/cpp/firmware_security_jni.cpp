/**
 * Firmware Security JNI Bridge
 * See docs/android-firmware-security-complete and docs/deviceowner-firmware-integration-complete
 * Package: com.example.deviceowner.security.firmware
 */

#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <time.h>
#include <math.h>

#define LOG_TAG "FirmwareSecurityJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SYSFS_INPUT_SECURITY "/sys/kernel/input_security/enabled"
#define SYSFS_INPUT_STATS "/sys/kernel/input_security/stats"
#define SECURITY_STATE_FILE "/data/local/tmp/security_state.dat"
#define SECURITY_LOG_FILE "/data/local/tmp/security_violations.log"

#define PROP_SECURITY_ENABLED "persist.security.mode.enabled"
#define PROP_BOOTLOADER_LOCK "ro.boot.flash.locked"
#define PROP_SECURITY_VIOLATION "persist.security.violation"

static int write_file(const char* path, const char* content) {
    FILE* f = fopen(path, "w");
    if (!f) {
        LOGW("Failed to open for writing: %s", path);
        return 0;
    }
    size_t len = strlen(content);
    size_t written = fwrite(content, 1, len, f);
    fclose(f);
    return (written == len) ? 1 : 0;
}

static char* read_file(const char* path) {
    FILE* f = fopen(path, "r");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0 || size > 1024*1024) {
        fclose(f);
        return NULL;
    }
    char* content = (char*)malloc((size_t)size + 1);
    if (!content) {
        fclose(f);
        return NULL;
    }
    size_t read_size = fread(content, 1, (size_t)size, f);
    content[read_size] = '\0';
    fclose(f);
    return content;
}

static void log_violation(const char* type, const char* details) {
    FILE* f = fopen(SECURITY_LOG_FILE, "a");
    if (!f) return;
    
    time_t now = time(NULL);
    struct tm* tm_info = localtime(&now);
    char timestamp[32];
    strftime(timestamp, sizeof(timestamp), "%Y-%m-%d_%H:%M:%S", tm_info);
    
    fprintf(f, "%s %s %s\n", timestamp, type, details);
    fclose(f);
    chmod(SECURITY_LOG_FILE, 0644);
    
    // Also set system property for immediate detection
    char prop_name[256];
    snprintf(prop_name, sizeof(prop_name), "%s_%ld", PROP_SECURITY_VIOLATION, now);
    __system_property_set(PROP_SECURITY_VIOLATION, prop_name);
    
    LOGE("SECURITY VIOLATION: %s - %s", type, details);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_deviceowner_security_firmware_FirmwareSecurity_isBootloaderLocked(
    JNIEnv* env, jobject thiz) {

    (void)env;   // Suppress unused parameter warning
    (void)thiz;  // Suppress unused parameter warning

    char prop_value[PROP_VALUE_MAX] = {0};
    __system_property_get(PROP_BOOTLOADER_LOCK, prop_value);
    if (strcmp(prop_value, "1") == 0) {
        LOGI("Bootloader: LOCKED (via property)");
        return JNI_TRUE;
    }
    
    __system_property_get("ro.boot.verifiedbootstate", prop_value);
    if (strcmp(prop_value, "green") == 0 || strcmp(prop_value, "yellow") == 0) {
        LOGI("Bootloader: LOCKED (verified boot)");
        return JNI_TRUE;
    }
    
    // Check for unlocked bootloader - this is a security violation
    __system_property_get("ro.boot.verifiedbootstate", prop_value);
    if (strcmp(prop_value, "orange") == 0 || strcmp(prop_value, "red") == 0) {
        log_violation("BOOTLOADER_UNLOCKED", "Bootloader is unlocked - critical security risk");
        LOGE("CRITICAL: Bootloader is UNLOCKED - device compromised");
        return JNI_FALSE;
    }
    
    // Check for fastboot unlock
    __system_property_get("ro.boot.flash.locked", prop_value);
    if (strcmp(prop_value, "0") == 0) {
        log_violation("FASTBOOT_UNLOCKED", "Fastboot is unlocked - device can be flashed");
        LOGE("CRITICAL: Fastboot is UNLOCKED");
        return JNI_FALSE;
    }
    
    LOGW("Bootloader: UNLOCKED or status unknown");
    log_violation("BOOTLOADER_STATUS_UNKNOWN", "Cannot determine bootloader lock status");
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_deviceowner_security_firmware_FirmwareSecurity_enableButtonBlocking(
    JNIEnv* env, jobject thiz, jboolean enable) {

    (void)env;   // Suppress unused parameter warning
    (void)thiz;  // Suppress unused parameter warning

    const char* value = enable ? "1" : "0";
    int success = 0;
    
    // Check for tampering attempts
    if (!enable) {
        log_violation("BUTTON_UNBLOCK_ATTEMPT", "Attempt to disable button blocking");
    }
    
    if (access(SYSFS_INPUT_SECURITY, W_OK) == 0) {
        if (write_file(SYSFS_INPUT_SECURITY, value)) {
            LOGI("Button blocking via sysfs: SUCCESS");
            success = 1;
        } else {
            log_violation("SYSFS_WRITE_FAILED", "Failed to write to input security sysfs");
        }
    } else {
        LOGW("Sysfs not accessible (kernel module may not be loaded)");
        log_violation("SYSFS_ACCESS_DENIED", "Cannot access input security sysfs - possible tampering");
    }
    
    if (__system_property_set(PROP_SECURITY_ENABLED, value) == 0) {
        LOGI("Security property set: SUCCESS");
        success = 1;
    } else {
        LOGW("Security property set: FAILED");
        log_violation("PROPERTY_SET_FAILED", "Failed to set security property - possible tampering");
    }
    
    char state_content[256];
    snprintf(state_content, sizeof(state_content), "enabled=%d\ntimestamp=%ld\n",
             enable ? 1 : 0, (long)time(NULL));
    if (write_file(SECURITY_STATE_FILE, state_content)) {
        LOGI("Security state persisted");
        chmod(SECURITY_STATE_FILE, 0644);
    } else {
        log_violation("STATE_FILE_WRITE_FAILED", "Cannot persist security state - possible tampering");
    }
    
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_deviceowner_security_firmware_FirmwareSecurity_enableSecurityMode(
    JNIEnv* env, jobject thiz) {

    LOGI("=== Enabling Full Security Mode ===");
    jboolean button_result = Java_com_example_deviceowner_security_firmware_FirmwareSecurity_enableButtonBlocking(
        env, thiz, JNI_TRUE);
    bool success = (button_result == JNI_TRUE);
    if (__system_property_set(PROP_SECURITY_ENABLED, "1") != 0) {
        LOGE("Failed to set security property");
        success = false;
    }
    write_file("/data/local/tmp/security_enabled", "1");
    LOGI("=== Security Mode: %s ===", success ? "ENABLED" : "PARTIALLY ENABLED");
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_deviceowner_security_firmware_FirmwareSecurity_disableSecurityMode(
    JNIEnv* env, jobject thiz, jstring authToken) {

    const char* token = env->GetStringUTFChars(authToken, NULL);
    if (!token || strlen(token) < 32) {
        if (token) env->ReleaseStringUTFChars(authToken, token);
        log_violation("INVALID_DISABLE_TOKEN", "Invalid or short authentication token provided");
        return JNI_FALSE;
    }
    
    // Enhanced token validation - check for expected format
    // Token should be: DISABLE_<timestamp>_<hash>
    if (strncmp(token, "DISABLE_", 8) != 0) {
        env->ReleaseStringUTFChars(authToken, token);
        log_violation("MALFORMED_DISABLE_TOKEN", "Token does not match expected format");
        return JNI_FALSE;
    }
    
    // Extract timestamp and validate it's recent (within 5 minutes)
    long token_timestamp = 0;
    if (sscanf(token + 8, "%ld", &token_timestamp) != 1) {
        env->ReleaseStringUTFChars(authToken, token);
        log_violation("INVALID_TOKEN_TIMESTAMP", "Cannot parse timestamp from token");
        return JNI_FALSE;
    }
    
    long current_time = (long)time(NULL);
    if (abs(current_time - token_timestamp) > 300) { // 5 minutes
        env->ReleaseStringUTFChars(authToken, token);
        log_violation("EXPIRED_DISABLE_TOKEN", "Token timestamp is too old or future");
        return JNI_FALSE;
    }
    
    // Log the disable attempt
    log_violation("SECURITY_DISABLE_ATTEMPT", "Valid token provided - disabling security");
    
    env->ReleaseStringUTFChars(authToken, token);

    Java_com_example_deviceowner_security_firmware_FirmwareSecurity_enableButtonBlocking(env, thiz, JNI_FALSE);
    __system_property_set(PROP_SECURITY_ENABLED, "0");
    unlink("/data/local/tmp/security_enabled");
    LOGI("=== Security Mode: DISABLED ===");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_deviceowner_security_firmware_FirmwareSecurity_getSecurityStatus(
    JNIEnv* env, jobject thiz) {

    jboolean bootloader_locked = Java_com_example_deviceowner_security_firmware_FirmwareSecurity_isBootloaderLocked(env, thiz);
    char prop_enabled[PROP_VALUE_MAX] = {0};
    __system_property_get(PROP_SECURITY_ENABLED, prop_enabled);
    bool security_enabled = (strcmp(prop_enabled, "1") == 0);

    char* sysfs_enabled = read_file(SYSFS_INPUT_SECURITY);
    bool button_blocking = false;
    if (sysfs_enabled) {
        button_blocking = (strcmp(sysfs_enabled, "1\n") == 0 || strcmp(sysfs_enabled, "1") == 0);
        free(sysfs_enabled);
    }

    char* stats_content = read_file(SYSFS_INPUT_STATS);
    unsigned long total_blocked = 0, recovery_attempts = 0, fastboot_attempts = 0;
    if (stats_content) {
        sscanf(stats_content, "Total Blocked: %lu\nRecovery Attempts: %lu\nFastboot Attempts: %lu",
               &total_blocked, &recovery_attempts, &fastboot_attempts);
        free(stats_content);
    }

    char prop_violation[PROP_VALUE_MAX] = {0};
    __system_property_get(PROP_SECURITY_VIOLATION, prop_violation);

    char json[2048];
    snprintf(json, sizeof(json),
        "{\"bootloaderLocked\":%s,\"securityEnabled\":%s,\"buttonBlocking\":%s,"
        "\"violations\":{\"total\":%lu,\"recovery\":%lu,\"fastboot\":%lu},"
        "\"lastViolation\":\"%s\",\"timestamp\":%ld}",
        bootloader_locked ? "true" : "false",
        security_enabled ? "true" : "false",
        button_blocking ? "true" : "false",
        total_blocked, recovery_attempts, fastboot_attempts,
        prop_violation, (long)time(NULL));

    return env->NewStringUTF(json);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_deviceowner_security_firmware_FirmwareSecurity_getViolationLog(
    JNIEnv* env, jobject thiz) {
    (void)thiz;
    
    char* log_content = read_file(SECURITY_LOG_FILE);
    if (!log_content) {
        return env->NewStringUTF("[]");
    }
    
    // Parse log file and convert to JSON array
    char json[8192] = "[";
    char* line = strtok(log_content, "\n");
    bool first = true;
    
    while (line != NULL) {
        if (strlen(line) > 0) {
            char timestamp[32], type[64], details[256];
            if (sscanf(line, "%31s %63s %255[^\n]", timestamp, type, details) == 3) {
                if (!first) strcat(json, ",");
                char entry[512];
                snprintf(entry, sizeof(entry), 
                    "{\"timestamp\":\"%s\",\"type\":\"%s\",\"details\":\"%s\"}", 
                    timestamp, type, details);
                strcat(json, entry);
                first = false;
            }
        }
        line = strtok(NULL, "\n");
    }
    strcat(json, "]");
    
    free(log_content);
    return env->NewStringUTF(json);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_deviceowner_security_firmware_FirmwareSecurity_clearViolationLog(
    JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    int r = unlink(SECURITY_LOG_FILE);
    if (r == 0) return JNI_TRUE;
    if (errno == ENOENT) return JNI_TRUE;
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_deviceowner_security_firmware_FirmwareSecurity_testSecurityFeatures(
    JNIEnv* env, jobject thiz) {

    (void)thiz;  // Suppress unused parameter warning

    char results[4096] = "{\"tests\":[";

    size_t n = strlen(results);
    snprintf(results + n, sizeof(results) - n, "{\"name\":\"Sysfs Access\",\"status\":\"%s\"}",
             access(SYSFS_INPUT_SECURITY, F_OK) == 0 ? "PASS" : "FAIL");

    char test_val[PROP_VALUE_MAX];
    __system_property_set("persist.security.test", "1");
    __system_property_get("persist.security.test", test_val);
    n = strlen(results);
    snprintf(results + n, sizeof(results) - n, ",{\"name\":\"Property System\",\"status\":\"%s\"}",
             strcmp(test_val, "1") == 0 ? "PASS" : "FAIL");

    bool file_ok = (write_file("/data/local/tmp/test.txt", "test") != 0);
    unlink("/data/local/tmp/test.txt");
    n = strlen(results);
    snprintf(results + n, sizeof(results) - n, ",{\"name\":\"File System Write\",\"status\":\"%s\"}]}",
             file_ok ? "PASS" : "FAIL");

    return env->NewStringUTF(results);
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    LOGI("=== Firmware Security JNI Loaded === Package: com.example.deviceowner.security.firmware");
    return JNI_VERSION_1_6;
}
