# Encryption System Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Application Layer                             │
│  (Activities, Services, Repositories, ViewModels)               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              Encryption Abstraction Layer                        │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  EncryptionInitializer                                   │  │
│  │  - Initialize all encryption components                 │  │
│  │  - Verify encryption health                             │  │
│  │  - Provide status reporting                             │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Preferences  │  │  Database    │  │    Files     │
│ Encryption   │  │  Encryption  │  │  Encryption  │
└──────────────┘  └──────────────┘  └──────────────┘
        │                │                │
        ▼                ▼                ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Core Encryption Layer                         │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  SecureDataEncryption (AES-256-GCM)                      │  │
│  │  - String encryption/decryption                         │  │
│  │  - Byte array encryption/decryption                     │  │
│  │  - Map encryption/decryption                            │  │
│  │  - IV management                                        │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Android      │  │ SQLCipher    │  │ File System  │
│ KeyStore     │  │ Database     │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
```

## Component Architecture

### 1. Preferences Encryption Flow

```
Application
    │
    ▼
EncryptedPreferencesManager
    │
    ├─► getDeviceDataPreferences()
    ├─► getRegistrationPreferences()
    ├─► getPaymentPreferences()
    ├─► getSecurityPreferences()
    └─► getLoanPreferences()
    │
    ▼
storeEncryptedString() / retrieveEncryptedString()
    │
    ▼
SecureDataEncryption
    │
    ├─► encryptString()
    └─► decryptString()
    │
    ▼
Android KeyStore
    │
    ▼
EncryptedSharedPreferences
    │
    ▼
Device Storage
```

### 2. Database Encryption Flow

```
Application
    │
    ▼
HeartbeatRepository
    │
    ▼
EncryptedDatabaseHelper
    │
    ├─► createEncryptedDatabase()
    │
    ▼
DatabasePassphraseManager
    │
    ├─► getOrCreatePassphrase()
    │
    ▼
EncryptedSharedPreferences
    │
    ▼
SQLCipher
    │
    ├─► AES-256 Encryption
    │
    ▼
Database File
```

### 3. Data Encryption Flow

```
Sensitive Data
    │
    ▼
SensitiveDataEncryptor
    │
    ├─► encryptObject()
    ├─► encryptRegistrationData()
    ├─► encryptPaymentData()
    └─► encryptDeviceIdentifiers()
    │
    ▼
SecureDataEncryption
    │
    ├─► encryptString()
    │
    ▼
Android KeyStore
    │
    ├─► AES-256-GCM
    │
    ▼
Encrypted Data
```

## Data Flow Diagram

### Storing Encrypted Data

```
┌─────────────────────┐
│  Raw Sensitive Data │
│  (Loan ID, IMEI)    │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  SensitiveDataEncryptor             │
│  - Serialize to JSON                │
│  - Prepare for encryption           │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  SecureDataEncryption               │
│  - Generate random IV               │
│  - Encrypt with AES-256-GCM         │
│  - Prepend IV to ciphertext         │
│  - Base64 encode                    │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  EncryptedPreferencesManager        │
│  - Store in encrypted preferences   │
│  - Additional encryption layer      │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  EncryptedSharedPreferences         │
│  - AES-256-SIV key encryption       │
│  - AES-256-GCM value encryption     │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  Device Storage                     │
│  (Encrypted at rest)                │
└─────────────────────────────────────┘
```

### Retrieving Encrypted Data

```
┌─────────────────────────────────────┐
│  Device Storage                     │
│  (Encrypted at rest)                │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  EncryptedSharedPreferences         │
│  - Decrypt with master key          │
│  - Retrieve encrypted value         │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  EncryptedPreferencesManager        │
│  - Retrieve encrypted string        │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  SecureDataEncryption               │
│  - Base64 decode                    │
│  - Extract IV from ciphertext       │
│  - Decrypt with AES-256-GCM         │
│  - Verify authentication tag        │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────┐
│  SensitiveDataEncryptor             │
│  - Parse JSON                       │
│  - Deserialize to object            │
└──────────┬──────────────────────────┘
           │
           ▼
┌─────────────────────┐
│  Decrypted Data     │
│  (Ready to use)     │
└─────────────────────┘
```

## Encryption Layers

### Layer 1: SharedPreferences Encryption
```
┌─────────────────────────────────────┐
│  EncryptedSharedPreferences         │
│  - Key Encryption: AES-256-SIV      │
│  - Value Encryption: AES-256-GCM    │
│  - Master Key: Android KeyStore     │
└─────────────────────────────────────┘
```

### Layer 2: Field-Level Encryption
```
┌─────────────────────────────────────┐
│  SecureDataEncryption               │
│  - Algorithm: AES-256-GCM           │
│  - IV: 96-bit random                │
│  - Tag: 128-bit authentication      │
│  - Key: Android KeyStore            │
└─────────────────────────────────────┘
```

### Layer 3: Database Encryption
```
┌─────────────────────────────────────┐
│  SQLCipher                          │
│  - Algorithm: AES-256               │
│  - Passphrase: Encrypted in Prefs   │
│  - Transparent encryption           │
│  - No code changes needed           │
└─────────────────────────────────────┘
```

### Layer 4: File Encryption
```
┌─────────────────────────────────────┐
│  FileEncryptionManager              │
│  - Algorithm: AES-256-GCM           │
│  - HKDF: Key derivation             │
│  - Block Size: 4KB                  │
│  - Key: Android KeyStore            │
└─────────────────────────────────────┘
```

## Key Management

```
┌─────────────────────────────────────┐
│  Android KeyStore                   │
│  (Hardware-backed when available)   │
└──────────┬──────────────────────────┘
           │
           ├─► Preferences Key
           │   - AES-256
           │   - GCM mode
           │   - Auto-generated
           │
           ├─► Database Passphrase Key
           │   - AES-256
           │   - GCM mode
           │   - Auto-generated
           │
           └─► File Encryption Key
               - AES-256
               - GCM mode
               - Auto-generated
```

## Data Categories and Storage

```
┌──────────────────────────────────────────────────────────┐
│                  Encrypted Data Storage                  │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  Device Data                                            │
│  ├─ IMEI                    → SharedPreferences         │
│  ├─ Serial Number           → SharedPreferences         │
│  └─ Android ID              → SharedPreferences         │
│                                                          │
│  Registration Data                                      │
│  ├─ Loan Number             → SharedPreferences         │
│  ├─ Device ID               → SharedPreferences         │
│  └─ Registration Token      → SharedPreferences         │
│                                                          │
│  Payment Data                                           │
│  ├─ Phone Number            → SharedPreferences         │
│  ├─ Amount                  → SharedPreferences         │
│  └─ Payment Reference       → SharedPreferences         │
│                                                          │
│  Security Data                                          │
│  ├─ Unlock Password         → SharedPreferences         │
│  ├─ Security Token          → SharedPreferences         │
│  └─ Lock State              → SharedPreferences         │
│                                                          │
│  Loan Data                                              │
│  ├─ Loan ID                 → SharedPreferences         │
│  └─ Device Token            → SharedPreferences         │
│                                                          │
│  Heartbeat Records                                      │
│  ├─ Heartbeat Data          → SQLCipher Database        │
│  └─ Response Data           → SQLCipher Database        │
│                                                          │
│  Configuration Files                                    │
│  ├─ App Config              → Encrypted Files           │
│  └─ Backup Data             → Encrypted Files           │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

## Initialization Sequence

```
Application.onCreate()
    │
    ▼
EncryptionInitializer.initializeEncryption()
    │
    ├─► initializeKeyStoreKeys()
    │   └─► SecureDataEncryption.encryptString("test")
    │       └─► Generate AES-256 key in KeyStore
    │
    ├─► initializeDatabasePassphrase()
    │   └─► DatabasePassphraseManager.getOrCreatePassphrase()
    │       └─► Generate and store encrypted passphrase
    │
    └─► initializeEncryptedPreferences()
        └─► Access all preference types
            └─► Create encrypted preference files
    │
    ▼
Encryption System Ready
```

## Error Handling Flow

```
Encryption Operation
    │
    ├─► Success
    │   └─► Return encrypted/decrypted data
    │
    └─► Failure
        │
        ├─► Catch Exception
        │
        ├─► Wrap in EncryptionException
        │
        ├─► Log error securely
        │
        └─► Throw to caller
            │
            └─► Application handles error
                ├─► Log error
                ├─► Notify user if needed
                └─► Implement fallback
```

## Performance Characteristics

```
Operation                    Time        Impact
─────────────────────────────────────────────────
Encrypt String (< 1KB)      < 1ms       Negligible
Decrypt String (< 1KB)      < 1ms       Negligible
Encrypt Map (10 items)      < 5ms       Negligible
Database Query              < 5ms       < 5% overhead
Database Insert             < 5ms       < 5% overhead
File Encryption (1MB)       < 50ms      Acceptable
File Decryption (1MB)       < 50ms      Acceptable
Initialization              < 100ms     One-time
```

## Security Properties

```
┌─────────────────────────────────────┐
│  Confidentiality                    │
│  - AES-256 encryption               │
│  - Prevents unauthorized access     │
│  - Keys in secure storage           │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  Integrity                          │
│  - AES-GCM authentication           │
│  - Detects tampering                │
│  - 128-bit authentication tag       │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  Key Security                       │
│  - Android KeyStore storage         │
│  - Hardware-backed when available   │
│  - Keys never exported              │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  Defense in Depth                   │
│  - Multiple encryption layers       │
│  - Separate keys for different data │
│  - Redundant security measures      │
└─────────────────────────────────────┘
```

---

**Architecture Version**: 1.0
**Last Updated**: February 2026
