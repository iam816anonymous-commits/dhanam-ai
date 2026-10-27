# Dhanam AI Next

A separate offline-first rewrite that retains the existing dark emerald and warm-gold Jarvis-inspired visual direction. The legacy app remains untouched as the feature/UI reference.

## Build from repository root
```bash
./gradlew -p nextgen :app:assembleDebug
./gradlew -p nextgen :app:testDebugUnitTest
```
Open `nextgen` directly in Android Studio for an independent project.

- Minimum Android API: 27 (Android 8.1)
- Compile/target API: 36 (Android 16)
- Kotlin, Jetpack Compose, Room/SQLite
- No network permission in this first version; core expense operations are offline-only.
- No demo financial records and no invented budget defaults.
- Monetary values stored as integer paise, not floating-point numbers.

## Delivery phases
1. Expense entry, persistence, list, and totals.
2. Category budgets, lending Khata and repayments.
3. CSV/PDF, reports, notifications and security settings.
4. Full offline English/Telugu command and voice support.
5. Optional on-device AI with runtime capability checks.
6. Optional cloud AI with explicit consent.
7. Migration, domain and physical-device acceptance tests.

This is the foundation of a new app, not a claim that all legacy features have already been reimplemented.
