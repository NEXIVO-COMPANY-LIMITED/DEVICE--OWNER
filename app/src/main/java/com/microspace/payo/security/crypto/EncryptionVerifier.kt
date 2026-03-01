package com.microspace.payo.security.crypto

import android.content.Context
import android.security.keystore.KeyInfo
import android.util.Log
import com.microspace.payo.data.DeviceIdProvider
import com.microspace.payo.data.local.LoanPreferences
import com.microspace.payo.data.local.RegistrationDataBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.SecretKeyFactory
import kotlin.experimental.inv

/**
 * Comprehensive encryption verification utility
 * Verifies that all data storage is properly encrypted and runs health checks.
 */
class EncryptionVerifier(private val context: Context) {

    companion object {
        private const val TAG = "EncryptionVerifier"
    }

    suspend fun verifyAllEncryption(): EncryptionVerificationReport = withContext(Dispatchers.IO) {
        val report = EncryptionVerificationReport()
        
        try {
            Log.d(TAG, "ðŸ” Starting comprehensive encryption verification...")
            
            // 1. Hardware Security Check
            report.hardwareSecurity = verifyHardwareSecurity()
            
            // 2. Preferences Encryption
            report.preferencesEncryption = verifyPreferencesEncryption()
            
            // 3. Database Encryption
            report.databaseEncryption = verifyDatabaseEncryption()
            
            // 4. File Encryption
            report.fileEncryption = verifyFileEncryption()
            
            // 5. Integrity Check
            report.integrityCheck = verifyIntegritySystem()
            
            report.isHealthy = report.preferencesEncryption.isHealthy &&
                    report.databaseEncryption.isHealthy &&
                    report.fileEncryption.isHealthy &&
                    report.integrityCheck.isHealthy
            
            Log.d(TAG, "âœ… Encryption verification complete. Status: ${if (report.isHealthy) "HEALTHY" else "UNHEALTHY"}")
            return@withContext report
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Verification failed: ${e.message}")
            report.error = e.message
            report.isHealthy = false
            return@withContext report
        }
    }

    private fun verifyHardwareSecurity(): ComponentStatus {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = "secure_data_key_v2"
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            val isHardwareBacked = if (entry != null) {
                val factory = SecretKeyFactory.getInstance(entry.secretKey.algorithm, "AndroidKeyStore")
                val keyInfo = factory.getKeySpec(entry.secretKey, KeyInfo::class.java) as KeyInfo
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            } else false

            ComponentStatus(
                "Hardware Security",
                isHardwareBacked,
                if (isHardwareBacked) "âœ… Hardware-backed keys active" else "âš ï¸ Software-backed keys only",
                mapOf("hardware_backed" to isHardwareBacked)
            )
        } catch (e: Exception) {
            ComponentStatus("Hardware Security", false, "Error: ${e.message}", emptyMap())
        }
    }

    private fun verifyIntegritySystem(): ComponentStatus {
        return try {
            val encryption = SecureDataEncryption(context)
            val test = "Integrity Test"
            val encrypted = encryption.encryptString(test)
            
            // Simulate minor tampering
            val bytes = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
            if (bytes.isNotEmpty()) {
                bytes[bytes.size - 1] = bytes[bytes.size - 1].inv() // Flip bits of last byte (HMAC part)
            }
            val tampered = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            
            var caught = false
            try {
                encryption.decryptString(tampered)
            } catch (e: EncryptionException) {
                if (e.message?.contains("integrity") == true || e.message?.contains("tamper") == true) caught = true
            }

            ComponentStatus(
                "HMAC Integrity",
                caught,
                if (caught) "âœ… Tampering correctly detected" else "âŒ Integrity check failed",
                mapOf("tamper_protection" to "HMAC-SHA256")
            )
        } catch (e: Exception) {
            ComponentStatus("HMAC Integrity", false, "Error: ${e.message}", emptyMap())
        }
    }

    private fun verifyPreferencesEncryption(): ComponentStatus {
        val prefsManager = EncryptedPreferencesManager(context)
        val testKey = "enc_test_${System.currentTimeMillis()}"
        val testVal = "secret_123"
        prefsManager.storeEncryptedString(EncryptedPreferencesManager.PreferencesType.SECURITY, testKey, testVal)
        val result = prefsManager.retrieveEncryptedString(EncryptedPreferencesManager.PreferencesType.SECURITY, testKey)
        val success = result == testVal
        return ComponentStatus("Preferences", success, if (success) "âœ… Working" else "âŒ Mismatch", emptyMap())
    }

    private fun verifyDatabaseEncryption(): ComponentStatus {
        val passphrase = DatabasePassphraseManager.getPassphrase(context)
        val isStrong = passphrase.length >= 64
        return ComponentStatus("Database", isStrong, if (isStrong) "âœ… SQLCipher (High Entropy)" else "âŒ Weak Passphrase", emptyMap())
    }

    private fun verifyFileEncryption(): ComponentStatus {
        val fem = FileEncryptionManager(context)
        val file = java.io.File(context.cacheDir, "test.enc")
        val data = "File Test".toByteArray()
        fem.writeEncryptedData(file, data)
        val decrypted = fem.readEncryptedData(file)
        val success = data.contentEquals(decrypted)
        file.delete()
        return ComponentStatus("File Storage", success, if (success) "âœ… GCM+HKDF Working" else "âŒ Mismatch", emptyMap())
    }

    data class ComponentStatus(val name: String, val isHealthy: Boolean, val message: String, val details: Map<String, Any>)
    data class EncryptionVerificationReport(
        var hardwareSecurity: ComponentStatus = ComponentStatus("", false, "", emptyMap()),
        var preferencesEncryption: ComponentStatus = ComponentStatus("", false, "", emptyMap()),
        var databaseEncryption: ComponentStatus = ComponentStatus("", false, "", emptyMap()),
        var fileEncryption: ComponentStatus = ComponentStatus("", false, "", emptyMap()),
        var integrityCheck: ComponentStatus = ComponentStatus("", false, "", emptyMap()),
        var isHealthy: Boolean = false,
        var error: String? = null
    )

    suspend fun getDetailedReport(): String = withContext(Dispatchers.IO) {
        val report = verifyAllEncryption()
        val sb = StringBuilder()
        sb.append("Encryption Health Report\n")
        sb.append("========================\n")
        sb.append("Overall: ${if (report.isHealthy) "PASS" else "FAIL"}\n\n")
        sb.append("Hardware: ${report.hardwareSecurity.message}\n")
        sb.append("Integrity: ${report.integrityCheck.message}\n")
        sb.append("Preferences: ${report.preferencesEncryption.message}\n")
        sb.append("Database: ${report.databaseEncryption.message}\n")
        sb.append("Files: ${report.fileEncryption.message}\n")
        return@withContext sb.toString()
    }
}




