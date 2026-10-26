# Dhanam AI — Jules End-to-End Remediation Plan

> **MANDATORY EXECUTION DIRECTIVE:** This is an implementation task, not an audit-only task. Jules must fix or rigorously verify every issue below, one at a time, through the end of the list. Add tests, run checks, record evidence, and continue after individual blockers. **Do not open the PR until the entire plan has been worked through.** Then open exactly one PR from the work branch to `main`. Do not merge it.

- Repository: `iam816anonymous-commits/dhanam-ai`
- Audit baseline: `main` at `eee37ad7e6f02ad4e343f8bbb7305a26b0639dcb` (2026-10-26)
- Detailed companion audit: see the original audit supplied with this task; this file preserves the actionable scope and execution contract.

## 1. Rules Jules must follow

1. **Scope alignment first:** Read this entire file; inspect current `main`, latest commits, project instructions, Gradle configuration, source, tests, and working tree before editing. Re-check each finding against the current code; the baseline may have changed.
2. **Work on a branch:** Create a fresh branch from latest `main`, e.g. `jules/dhanam-remediation`. Never commit directly to `main`. If a branch of that name exists, inspect it and choose another.
3. **Work issue-by-issue in order:** For every issue: inspect implementation and all call sites; make the smallest complete safe fix; add/update regression tests; run relevant checks; review the diff; update the status/evidence log; then proceed. Do not stop after planning, fixing only the first issue, or making cosmetic changes.
4. **Keep this file current:** Update issue status and the execution log in this document as work progresses. Preserve the requirements and acceptance criteria; do not replace this document with a short summary.
5. **Evidence, not assumptions:** Record exact commands and their real exit/results. Never claim tests, builds, device checks, Firebase requests, 16 KB compatibility, or performance gains passed unless evidence exists. If the environment lacks a required emulator, SDK, secret, Firebase project setting, physical device, or service, mark only that verification blocked/partial, explain why, perform all feasible checks, and continue with independent issues.
6. **Protect financial data:** No destructive Room migrations, no deleting user data to make tests pass, no broad rewrites, and no breaking legacy PIN migration without a safe recovery path. Financial calculations and database mutations must be deterministic Kotlin code, not model-generated decisions or arithmetic.
7. **Security:** Never commit API keys, Firebase config secrets, signing credentials/keystores, App Check debug tokens, PINs, or real financial data. Do not add insecure fallbacks. Cloud AI must remain disabled when consent is off. Review logs and crash reporting for sensitive payloads.
8. **Firebase-specific limitation:** Implement and test the client-side integration/configuration that can be done from repository code. Do not invent Firebase project IDs, credentials, `google-services.json`, model access, or console enforcement. If console configuration or credentials are unavailable, document the exact manual step and mark runtime verification blocked, then continue all other work.
9. **Test continuously:** Run targeted tests after each change, relevant test suites after each issue group, then full final validation. Fix regressions caused by this work before proceeding. Do not bundle risky dependency upgrades into one commit.
10. **No unnecessary edits:** If a finding is already fixed or demonstrably inapplicable, prove that with code/tests and mark it accordingly instead of making needless changes.
11. **Finish the whole scope before PR:** Do not create an early PR. Work through all 27 issues and all acceptance criteria. Issues requiring unavailable external access must be documented as blocked, not falsely marked complete; all safely implementable work must still be done. Then open one PR to `main`, with summary, issue-by-issue status, tests actually run, blocked checks and manual owner actions. Do not merge.
12. **Statuses:** `[ ] NOT STARTED`, `[-] IN PROGRESS`, `[x] VERIFIED COMPLETE`, `[!] BLOCKED/PARTIALLY VERIFIED`, `[~] ALREADY RESOLVED/NOT APPLICABLE`. A blocked item is not complete.

## 2. Issue-by-issue implementation checklist

### P0 — Data safety, security, financial correctness

- [ ] **Issue 1 — Backup rules:** Audit `backup_rules.xml`, `data_extraction_rules.xml`, and `AppDatabase.kt`. The audited mismatch is database name `dhanam_database` vs actual `dhanam_expense_database`. Correct cloud backup and device transfer rules; keep `dhanam_secure_pin.xml` excluded; review other preferences explicitly. Test backup/restore policy and restored expenses, loans, budgets and database integrity.
- [ ] **Issue 2 — Embedded Gemini key:** Remove direct Gemini REST use of `BuildConfig.GEMINI_API_KEY`; migrate behind a `FinancialAiClient` interface to Firebase AI Logic. Use Play Integrity App Check for release and debug provider only for debug. Remove obsolete key configuration and paths. Test failures, timeouts, quota/rate limit, missing/invalid attestation and malformed output as far as the environment permits.
- [ ] **Issue 3 — Model configuration:** Replace hard-coded `gemini-2.5-flash` with a remotely configurable, allowlisted model ID (e.g. Remote Config), validated before use, with cached last-known-good config and safe local fallback. Verify model access with the real Firebase project and a generation smoke test if credentials are available; catalog presence alone is not proof of access.
- [ ] **Issue 4 — PIN security:** Review `PreferenceManager.kt`: audited concerns include PBKDF2-HMAC-SHA256 at 5,000 iterations and SHA-256 fallback. Design and implement a vetted Keystore-backed verification approach; remove weak fallback; benchmark any KDF on low-end hardware; preserve safe legacy migration and recovery. Test create/verify, wrong PIN, persistent lockout, restart, migration success/failure, key invalidation/loss, PIN change/removal, concurrent attempts, and absence of PIN logging. Do not implement a simplistic HMAC construction that leaves a cheap offline PIN oracle.
- [ ] **Issue 5 — Cloud AI consent boundary:** Enforce consent centrally at the AI-client boundary immediately before every request, including retries/background paths. Minimize payloads; avoid unnecessary identifiers and raw financial history; no prompt/response payload logging. Default local-only when opt-out/offline. Tests must prove opt-out results in zero cloud calls.
- [ ] **Issue 6 — Financial mutation correctness:** Audit expense, loan repayment/settlement, budget, clear-data and import flows. Use Room transactions for related writes; validate amounts, signs, dates, IDs and repayment bounds; use integer minor units (paise) or another exact-money representation; define overpayment semantics; prevent duplicates on retried commands/imports; confirm destructive actions and add recovery/undo where practical. Resolve and test `MainViewModel.clearAllData()` semantics because audited code deletes expenses and loans but not category budgets.

### P1 — Persistence, tests, CI, background behavior

- [ ] **Issue 7 — Room schema history:** Generate and commit Room schema JSON under `app/schemas/` (audited expectation: `1.json` if current schema version is 1). Add `MigrationTestHelper` tests for historical schema/data migration. Preserve records, indices, defaults and constraints. Never use destructive migration as a shortcut.
- [ ] **Issue 8 — Regression coverage:** Add tests for DB creation/migration; budget and month-boundary calculations; totals/currency precision; loan repayment/due dates/settlement; duplicate voice submissions and worker retries; malformed/ambiguous/adversarial AI output; PIN migration/lockout; notification dedup and permission denial; exports/FileProvider; process death/resume/interrupted operations. Use pure Kotlin tests, Room tests and instrumentation tests at the appropriate layers.
- [ ] **Issue 9 — GitHub Actions CI:** Add `.github/workflows/android-ci.yml` (or a clearly equivalent workflow) with JDK setup, Gradle caching, `:app:lintDebug`, `:app:testDebugUnitTest`, release assembly, schema/migration tests, APK checks, and upload of reports/artifacts. Keep secrets out of Git. Distinguish unsigned compile/test artifacts from signed distribution builds.
- [ ] **Issue 10 — API matrix:** Configure Gradle Managed Devices where supported for API 27, 30, 34 and 36. API 27 may not provision in every environment; report unavailable honestly, never silently skip or claim pass. Document emulator vs physical OEM coverage separately.
- [ ] **Issue 11 — 16 KB page size:** Run SDK `zipalign -c -P 16 -v 4 <actual-release-apk>`; capture command/output in CI. Inspect packaged native libraries and ELF segment alignment if any. Zip alignment alone is not proof of full 16 KB runtime compatibility. If no native libraries exist, record that finding without claiming device validation.
- [ ] **Issue 12 — Notification idempotency:** Audit `DhanamBudgetKhataWorker.kt` and `NotificationReminderManager.kt`. Use safe stable IDs that avoid `Long` to `Int` collisions/overflow; make WorkManager retries idempotent; distinguish sent/suppressed/permission-denied/failed states; handle due-date edits and settled loans; test overdue transitions, API 33+ permission denial, channels and older APIs with deterministic clocks.
- [ ] **Issue 13 — Lifecycle and work scheduling:** Test background/foreground auto-lock before/after timeout, process recreation, PIN/biometric combinations, absent biometric enrollment, rescheduling after reboot/update/process death, duplicate scheduling, Doze and offline behavior. Enforce security at navigation/screen entry points, not only via visual overlay.
- [ ] **Issue 14 — Voice lifecycle:** Audit `VoiceAgentManager.kt`. Use session IDs; guarantee each session terminates once; cancel/time out deterministically; release recognizer/audio resources; bound retries; separate recognition from transcript cleanup/command parsing. Test empty/partial/duplicate results, unavailable service, permission denial, offline mode, Telugu fallback and API 27 limitations. Do not claim offline Telugu support without device/service/model evidence.

### P2 — Architecture, determinism, UI and diagnostics

- [ ] **Issue 15 — Oversized classes:** Refactor incrementally behind tests, not via a wholesale rewrite. Highest-priority audited files: `ExpenseAgentEngine.kt` (~1,497 lines), `MainViewModel.kt` (~43 KB), `AgentScreen.kt` (~53 KB), `ExpensesScreen.kt` (~69 KB), `SettingsScreen.kt` (~78 KB). Extract clear responsibilities and preserve behavior.
- [ ] **Issue 16 — Deterministic AI command pipeline:** Normalize input → parse typed command → validate amount/date/entities → clarify ambiguity → confirm sensitive/destructive actions → execute use case → transactional persistence → typed result → refresh from Room. Validate model output against typed schema; never let model output directly authorize DB mutation or determine financial arithmetic.
- [ ] **Issue 17 — Query and state efficiency:** Move appropriate date/month aggregation into DAO queries; add indexes only after inspecting query patterns/plans; use Flow for observable data and suspend functions for one-off work; avoid loading all rows for totals; Room remains source of truth; establish timezone/date policy; inject Clock for deterministic tests.
- [ ] **Issue 18 — Error contract:** Distinguish success, validation error, confirmation required, network unavailable, App Check/auth failure, rate limit, persistence failure and cancellation. Do not convert failures into empty success. Log sanitized error codes/correlation IDs, not financial payloads; provide localized user-facing messages.
- [ ] **Issue 19 — Compose UX/accessibility:** Audit semantics and touch targets for buttons/charts/custom overlays; font scaling and screen-reader navigation; adaptive tablet/large-screen layout; reduce idle animation/power usage; avoid expensive recomposition; stable list keys/pagination; test themes, Telugu text and display scaling.

### P2/P3 — Dependencies, performance, exports and release trust

- [ ] **Issue 20 — Staged dependency upgrades:** Record baseline build/tests/APK size/dependency graph. Upgrade compatible groups separately (Compose BOM/artifacts; Activity/Lifecycle as needed; OkHttp + logging interceptor; Retrofit + converter; then others). Run compile/lint/tests after each group; do not upgrade everything at once.
- [ ] **Issue 21 — Unused dependency cleanup:** Search source and dependency reports before removing catalog entries/runtime/transitive dependencies. Some CameraX, Coil, Accompanist permissions, location and other entries may be unused, but verify all direct/reflection/transitive uses. Keep Retrofit/OkHttp if other endpoints still need them.
- [ ] **Issue 22 — Baseline Profiles:** Only after measuring startup, add a compatible benchmark setup for cold launch, dashboard and expense entry; generate profiles on a supported device/emulator; add profileinstaller if needed; compare before/after measurements. No performance claim without actual results.
- [ ] **Issue 23 — Privacy-safe diagnostics:** Review `AppLogger.kt` and every call site for prompts, expense descriptions, phone numbers, PINs, loan details or secrets. If Crashlytics is added, review defaults/privacy disclosure and sanitize reports. Keep local diagnostics bounded and clearable; verify release logs.
- [ ] **Issue 24 — CSV/PDF export safety:** Audit `CsvReportGenerator.kt`, `PdfReportGenerator.kt` and FileProvider. Limit provider paths; use read-only content URIs and temporary grants; test date filtering, Telugu, empty/large reports and cleanup; prevent CSV formula injection; ensure failed exports cannot look complete; do not log report contents.

### P3 — Compatibility, release and documentation

- [ ] **Issue 25 — Compatibility claims:** Replace unsupported README claims for API 27–36+, OEMs, low-RAM and Telugu voice with a tested matrix. Record device/OS/date/results. Emulator evidence does not prove OEM background behavior; API 36 does not prove API 27.
- [ ] **Issue 26 — Release evidence:** Document commit SHA, JDK/Gradle/AGP/SDK versions, tasks passed/skipped, actual APK/AAB size, signing status and device verification. Separate build success from test and device success. Add a repeatable release checklist.
- [ ] **Issue 27 — Signing policy:** Audit release signing configuration and fallback keystore path. Production release must fail clearly if protected signing inputs are missing; never silently use debug/placeholder signing. Keep credentials out of Git, check history if a real keystore may have been committed, and verify app ID/version/Firebase registration.

## 3. Required validation and completion gate

Run the commands appropriate to this project/environment and record exact output/results:

- `./gradlew :app:lintDebug`
- `./gradlew :app:testDebugUnitTest`
- Room schema and migration tests
- `./gradlew :app:assembleRelease` (or a clearly documented safe unsigned equivalent if signing secrets are unavailable)
- Instrumentation/managed-device tests for every API image actually available
- `zipalign -c -P 16 -v 4 <actual-release-apk>`
- Native `.so`/ELF alignment inspection if native libraries are packaged
- Dependency report, final diff review and secret scan
- Backup/restore validation and Firebase smoke test when actual project access is available

A failed check must be investigated and fixed if caused by the changes. Environment-blocked checks must be labelled blocked with reasons, not reported as passed. Run final checks against the final branch head.

## 4. PR requirements — only after the entire plan is processed

Open exactly one PR from the remediation branch into `main` after issues 1–27 have each been marked verified, already resolved/not applicable with evidence, or blocked/partially verified with the exact external prerequisite documented. Complete all work that can be done without that external prerequisite. Do not merge.

The PR body must include:
- Summary of implementation and major design choices.
- A table for issues 1–27 with status and evidence.
- Exact commands/checks passed and failed.
- Checks not run, with precise reasons.
- Firebase Console/credentials/physical-device actions still required.
- Any remaining risks and how to reproduce validation.

## 5. Execution log — Jules must maintain this section

### Baseline
- Branch:
- Starting commit:
- Environment (OS, JDK, Gradle/AGP, Android SDK):
- Baseline commands/results:
- Environment limitations:

### Issue records
For every issue, record: **status; files changed; design decision; tests added/updated; exact commands and results; blockers/manual steps**.

| Issue | Status | Evidence / tests / files / blockers |
|---:|---|---|
| 1 | NOT STARTED | |
| 2 | NOT STARTED | |
| 3 | NOT STARTED | |
| 4 | NOT STARTED | |
| 5 | NOT STARTED | |
| 6 | NOT STARTED | |
| 7 | NOT STARTED | |
| 8 | NOT STARTED | |
| 9 | NOT STARTED | |
| 10 | NOT STARTED | |
| 11 | NOT STARTED | |
| 12 | NOT STARTED | |
| 13 | NOT STARTED | |
| 14 | NOT STARTED | |
| 15 | NOT STARTED | |
| 16 | NOT STARTED | |
| 17 | NOT STARTED | |
| 18 | NOT STARTED | |
| 19 | NOT STARTED | |
| 20 | NOT STARTED | |
| 21 | NOT STARTED | |
| 22 | NOT STARTED | |
| 23 | NOT STARTED | |
| 24 | NOT STARTED | |
| 25 | NOT STARTED | |
| 26 | NOT STARTED | |
| 27 | NOT STARTED | |

### Final evidence
- Lint:
- Unit tests:
- Room migration/schema:
- Release assembly/signing status:
- API/device tests actually run:
- 16 KB and native library checks:
- Final diff/secret review:
- Blocked external checks and exact owner actions:

### Final PR
- URL:
- Source branch / base:
- Summary:
- Passed checks:
- Blocked/not-run checks and reasons:
- Manual steps still required:
