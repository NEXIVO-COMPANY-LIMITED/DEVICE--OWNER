package com.deviceowner.compatibility

/**
 * Maintains the compatibility matrix for supported devices
 */
object CompatibilityMatrix {
    
    private val supportedDevices = mutableListOf<SupportedDevice>()
    
    init {
        initializeSupportedDevices()
    }
    
    /**
     * Initialize the list of supported devices
     */
    private fun initializeSupportedDevices() {
        // Samsung devices
        supportedDevices.add(SupportedDevice(
            manufacturer = "Samsung",
            model = "Galaxy A12",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "Samsung",
            model = "Galaxy A13",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "Samsung",
            model = "Galaxy S20",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc", "android.hardware.fingerprint"),
            notes = "Full support with NFC and fingerprint"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "Samsung",
            model = "Galaxy S21",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc", "android.hardware.fingerprint"),
            notes = "Full support with NFC and fingerprint"
        ))
        
        // Xiaomi devices
        supportedDevices.add(SupportedDevice(
            manufacturer = "Xiaomi",
            model = "Redmi Note 9",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "Xiaomi",
            model = "Redmi Note 10",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "Xiaomi",
            model = "Mi 11",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc", "android.hardware.fingerprint"),
            notes = "Full support with NFC and fingerprint"
        ))
        
        // OPPO devices
        supportedDevices.add(SupportedDevice(
            manufacturer = "OPPO",
            model = "A12",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "OPPO",
            model = "A53",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        
        // Vivo devices
        supportedDevices.add(SupportedDevice(
            manufacturer = "vivo",
            model = "Y12",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "vivo",
            model = "Y20",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        
        // Realme devices
        supportedDevices.add(SupportedDevice(
            manufacturer = "realme",
            model = "C11",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "realme",
            model = "C15",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        
        // Motorola devices
        supportedDevices.add(SupportedDevice(
            manufacturer = "motorola",
            model = "Moto G9",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "motorola",
            model = "Moto G10",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc"),
            notes = "Supported with NFC"
        ))
        
        // Google Pixel devices
        supportedDevices.add(SupportedDevice(
            manufacturer = "Google",
            model = "Pixel 4",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc", "android.hardware.fingerprint"),
            notes = "Full support with NFC and fingerprint"
        ))
        supportedDevices.add(SupportedDevice(
            manufacturer = "Google",
            model = "Pixel 5",
            minApiLevel = 28,
            requiredFeatures = setOf("android.hardware.nfc", "android.hardware.fingerprint"),
            notes = "Full support with NFC and fingerprint"
        ))
    }
    
    /**
     * Get all supported devices
     */
    fun getSupportedDevices(): List<SupportedDevice> {
        return supportedDevices.toList()
    }
    
    /**
     * Check if a device is supported
     */
    fun isDeviceSupported(manufacturer: String, model: String, apiLevel: Int): Boolean {
        return supportedDevices.any { device ->
            device.manufacturer.equals(manufacturer, ignoreCase = true) &&
            device.model.equals(model, ignoreCase = true) &&
            apiLevel >= device.minApiLevel &&
            apiLevel <= device.maxApiLevel
        }
    }
    
    /**
     * Get supported device info
     */
    fun getSupportedDeviceInfo(manufacturer: String, model: String): SupportedDevice? {
        return supportedDevices.find { device ->
            device.manufacturer.equals(manufacturer, ignoreCase = true) &&
            device.model.equals(model, ignoreCase = true)
        }
    }
    
    /**
     * Add a supported device
     */
    fun addSupportedDevice(device: SupportedDevice) {
        supportedDevices.add(device)
    }
    
    /**
     * Remove a supported device
     */
    fun removeSupportedDevice(manufacturer: String, model: String) {
        supportedDevices.removeAll { device ->
            device.manufacturer.equals(manufacturer, ignoreCase = true) &&
            device.model.equals(model, ignoreCase = true)
        }
    }
    
    /**
     * Update compatibility matrix
     */
    fun updateMatrix(devices: List<SupportedDevice>) {
        supportedDevices.clear()
        supportedDevices.addAll(devices)
    }
}
