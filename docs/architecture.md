# Android Client Architecture

OneBook Dialer is a single-APK native Android application written in **Kotlin** and **Jetpack Compose** using MVVM + Repository architecture, Room SQLite caching, and WorkManager background synchronization.

---

## High-Level Data Flow

```
[Frappe CRM Server]
       ▲  ▲
       │  │  HTTPS (Token Auth / Session Cookie, Private File Upload)
       │  └──────────────────────────┐
       │                             │
[CrmRepository] ◄─────────── [CallSyncWorker (WorkManager)]
       ▲                             ▲
       │  Flow<List<T>>              │  Pending Queue (Exponential Backoff)
       ▼                             │
[Room SQLite (AppDatabase)] ◄── [CallStateReceiver] ◄── [Android Telephony & CallLog]
       ▲                             │
       │                             ▼
[Jetpack Compose UI]        [CallRecorderAccessibilityService + MediaRecorder (.m4a)]
```

---

## Package Structure (`com.example`)

| Package | Key Files | Responsibility |
| --- | --- | --- |
| `com.example` | `OneBookApplication.kt`, `MainActivity.kt` | Initializes Room DB, Keystore store, notification channels, edge-to-edge Compose `NavHost`, and post-call wrap-up deep links. |
| `com.example.data.local` | `AppDatabase.kt`, `Entities.kt`, `Daos.kt`, `SecureCredentialStore.kt` | Room tables (`leads`, `deals`, `contacts`, `tasks`, `call_logs`, `excluded_numbers`, `crm_notes`) + Android Keystore AES-256-GCM encrypted DataStore preferences. |
| `com.example.data.remote` | `FrappeApiService.kt`, `FrappeDtos.kt`, `FrappeClientFactory.kt` | Dynamic Retrofit + OkHttp client injecting `Authorization: token <key>:<secret>` or `Cookie: sid=...`. |
| `com.example.data.repository` | `CrmRepository.kt` | Coordinates Frappe pipeline sync, phone number matching, privacy/SIM filtering, call queue insertion, and private file + call log upload. |
| `com.example.service` | `CallRecorderAccessibilityService.kt`, `CallStateReceiver.kt`, `CallerIdOverlayService.kt`, `AudioPlaybackManager.kt` | Telephony state tracking, `.m4a` call recording, OEM folder watcher fallback, floating Caller ID overlay, and authenticated audio playback. |
| `com.example.worker` | `CallSyncWorker.kt` | `CoroutineWorker` uploading queued recordings and call logs when `NetworkType.CONNECTED`. |
| `com.example.util` | `PhoneNormalizer.kt`, `PermissionHelper.kt`, `SimManager.kt` | Phone digit normalization, permission/Accessibility/battery checks, and Dual-SIM `ACTION_CALL` dialing. |
| `com.example.ui.*` | `screens/`, `components/`, `viewmodel/`, `theme/`, `navigation/` | Material 3 UI with `Plus Jakarta Sans`, `Inter`, and `JetBrains Mono`, adaptive `NavigationBar`/`NavigationRail`, and type-safe `@Serializable` navigation routes. |

---

## Security & Credential Storage

1. **Android Keystore AES-256-GCM (`SecureCredentialStore.kt`):**
   - Generates a hardware-backed (where available) 256-bit AES-GCM key under alias `onebook_crm_master_key`.
   - Encrypts `apiKey`, `apiSecret`, and `sessionCookie` with random 12-byte IVs before writing to Jetpack DataStore.
2. **Private Recording Files:**
   - Local `.m4a` files are stored in app-private internal storage (`context.filesDir/recordings/`).
   - Temporary local recordings are deleted **only after** Frappe CRM confirms both the private file upload and `CRM Call Log` creation (`200 OK`).
3. **Zero Backup Exposure:**
   - `android:allowBackup="false"` is configured in `AndroidManifest.xml` to prevent unencrypted backup extraction of local CRM caches.

---

## Phone Number Normalization (`PhoneNormalizer.kt`)

Customer phone numbers in CRM databases frequently contain spaces, parentheses, leading zeros, or country codes (`+91 98765-43210` vs `09876543210` vs `9876543210`).  
`PhoneNormalizer.normalize()` strips all non-digit characters, international `00` exit codes, leading `0`s, and common country prefixes (`91`, `1`) to produce a canonical 10-digit matching key indexed in Room (`@Index(value = ["normalizedPhone"])`).
