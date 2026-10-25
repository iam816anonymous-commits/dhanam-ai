# Dhanam AI (ధనం AI) - Compatibility & Production Hardening

**Package**: `com.aistudio.dhanam.expense`  
**Target Platform**: Android API 27 (Android 8.1 Oreo) to API 36+ (Android 16 Vanilla Ice Cream & beyond)  
**Supported Languages**: Bilingual (Telugu & English)  
**Target Devices**: Budget/Low-RAM (2 GB devices e.g. MediaTek Helio P22/P23, Android Go) to Flagship foldables and tablets  

---

## 1. Compatibility Matrix

| OEM Skin / Device Tier | Android Version Range | Audio & Voice Recognition | Notification & Background Workers | Specific Hardening Applied |
| :--- | :--- | :--- | :--- | :--- |
| **Samsung One UI** | API 27 – 36+ | System Speech + Cloud Fallback | WorkManager + Battery whitelist dialog | Protected against Smart Manager / App sleep; auto-lock on background |
| **Xiaomi / Redmi (MIUI / HyperOS)** | API 27 – 36+ | Native SpeechRecognizer + Virtual Mic | WorkManager with 24h deduplication | In-app guidance for MIUI Autostart & "No Restrictions" battery mode |
| **Oppo / Realme (ColorOS / Realme UI)** | API 27 – 36+ | Google Speech / System Intent fallback | WorkManager periodic channel alert | Handled background freezer; wrapped implicit intents in safe try-catch |
| **Vivo / iQOO (Funtouch OS / OriginOS)** | API 27 – 36+ | Native TTS / Locale switcher | WorkManager 6h periodic flex schedule | High background power consumption guide in Settings |
| **OnePlus (OxygenOS)** | API 27 – 36+ | Low-memory audio pipeline | NotificationChannel per category | Deep clean battery optimization guidance; safe dialer intent |
| **Motorola & Google Pixel (Stock Android)** | API 27 – 36+ | On-device API 31+ & API 33+ language checks | Standard WorkManager & NotificationManager | Seamless edge-to-edge insets, Predictive Back, splash screen |
| **Infinix / Tecno (XOS / HiOS - Transsion)** | API 27 – 36+ | RMS dB throttled pipeline (65ms frame gate) | Strict 24h rate-limiting per loan reminder | CPU throttling in Power Save mode; Phone Master autostart guide |
| **Low-RAM (<= 2GB) Devices** | API 27 – 32 | Particle & holographic animations throttled | Lightweight SQLite transaction queries | Throttled `JarvisLivingCore`, static angles on Power Save / Low RAM |

---

## 2. Verification Results Summary

All builds and tests were executed and verified on branch `compat-hardening`:

| Command / Task | Result | Execution Details |
| :--- | :--- | :--- |
| `gradle :app:testDebugUnitTest` | **PASSED (BUILD SUCCESSFUL)** | All unit tests executed successfully across business logic, voice state manager, database entities, and pin migration. |
| `gradle :app:lintDebug` | **PASSED (BUILD SUCCESSFUL)** | 0 fatal errors, 0 lint breaking issues. Full backup rules validated with `<include path=".">` compliance. |
| `gradle :app:assembleDebug` | **PASSED (BUILD SUCCESSFUL)** | Universal debug APK assembled for device installation and emulator testing. |
| `STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:assembleRelease` | **PASSED (BUILD SUCCESSFUL)** | R8 minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) enabled. Output: `app-release.apk` (**4.2 MB**). |
| `STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:bundleRelease` | **PASSED (BUILD SUCCESSFUL)** | Production App Bundle generated for Google Play Store upload: `app-release.aab` (**8.3 MB**). |

---

## 3. How to Build & Sign Release Artifacts

Release signing configuration in `app/build.gradle.kts` securely references environment variables rather than hardcoded credentials.

### Environment Variables
- `STORE_PASSWORD`: Keystore password (defaults to fallback if configured locally)
- `KEY_PASSWORD`: Key password
- `KEYSTORE_PATH`: (Optional) Custom keystore path

### Build Commands
```bash
# Sideloadable Universal Release APK (R8 Minified)
STORE_PASSWORD=uploadpassword KEY_PASSWORD=uploadpassword gradle :app:assembleRelease

# Google Play Production App Bundle (.aab)
STORE_PASSWORD=uploadpassword KEY_PASSWORD=uploadpassword gradle :app:bundleRelease
```

Output locations:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

---

## 4. Key Hardening Features Implemented

### Phase 1: Security & AI Reliability
- **PBKDF2-HMAC-SHA256**: PIN hashing upgraded from single unsalted SHA-256 to PBKDF2 with 5,000 iterations, 16-byte cryptographically secure per-install random salt, and brute-force lockouts (5 failed attempts = 30s lockout, 10 attempts = 5m lockout). Transparent legacy migration on next unlock.
- **ProcessLifecycleOwner Auto-Lock**: Automatically re-locks the app when backgrounded >= 30 seconds.
- **Screenshot & Recents Privacy**: Optional `FLAG_SECURE` toggle in Settings to protect financial balances from appearing in the Android task switcher.
- **Safe Room Migration & Atomic SQLite Seeding**: Room `exportSchema = true` enabled with generated schema version `1.json`. Database seeding converted from re-entrant `getInstance()` coroutines to atomic `db.execSQL()` SQLite callbacks.
- **Resilient Gemini Client**: Replaced invalid model ID with stable `gemini-2.5-flash` constant. Added `GeminiCallResult` typed responses (`Success`, `NoKey`, `NetworkError`, `HttpError`, `ParseError`) with exponential backoff retries on 429/5xx.
- **FileProvider Narrowing**: Restricted `file_paths.xml` strictly to `cache/reports/` and `cache/exports/`.

### Phase 2: Android API 27 – 36+ Compatibility
- **Splash Screen**: Integrated `androidx.core:core-splashscreen:1.0.1` (`Theme.App.Starting`) for a smooth, unified launch experience from Android 8.1 to Android 16.
- **Notification Architecture**: Replaced adaptive icon with monochrome vector `ic_notification.xml` on API 26+; separated notifications into 3 dedicated notification channels (`channel_budget`, `channel_repayment`, `channel_tips`).
- **Notification Deduplication**: Added 24-hour rate limiting in `DhanamBudgetKhataWorker` per loan to prevent duplicate spam.
- **16 KB Page Alignment**: Validated dependencies and R8 outputs; native ELF dependencies packaged cleanly.
- **Edge-to-Edge & Insets**: Enforced `enableEdgeToEdge()` and proper Compose `WindowInsets` handling with `adjustResize`.

### Phase 3: OEM Resilience & Voice Polish
- **Voice Lifecycle**: SpeechRecognizer and TTS are cleanly paused/stopped in `onStop()` to prevent holding microphone focus or battery drain in the background.
- **Telugu TTS Guidance**: Added one-tap `ACTION_INSTALL_TTS_DATA` intent for devices missing native Telugu speech synthesizers.
- **ActivityNotFoundException Guards**: All implicit intents (phone dialer, WhatsApp chooser, PDF/CSV viewers, system settings) wrapped in safe try-catch blocks with bilingual fallback toasts.
- **OEM Battery Saver Guide**: Interactive Settings dialog providing OEM-specific step-by-step instructions (Samsung, Xiaomi, Oppo, Vivo, OnePlus, Transsion) to grant "Unrestricted" background execution for reminders.
- **Power Save & Low-RAM Throttling**: `JarvisLivingCore` monitors `ACTION_POWER_SAVE_MODE_CHANGED` dynamically and checks `ActivityManager.isLowRamDevice` to throttle heavy holographic animations and conserve battery.
- **Clean Logging**: Replaced all `e.printStackTrace()` calls with structured `AppLogger`.
