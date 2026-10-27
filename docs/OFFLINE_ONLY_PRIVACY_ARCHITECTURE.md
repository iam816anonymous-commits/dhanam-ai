# Offline-Only Privacy Architecture

## Non-negotiable privacy contract

Dhanam AI must not send financial records, prompts, transcripts, contact names, loan details, categories, budgets, or derived insights to any external service. Firebase is prohibited: no Firebase AI Logic, Remote Config, App Check, Analytics, Crashlytics, Auth, Firestore, or other Firebase SDKs.

## Changes in this branch

- Remove Firebase SDK dependencies and Google Services / Secrets Gradle plugins.
- Remove android.permission.INTERNET and ACCESS_NETWORK_STATE from the application manifest. The app process therefore has no ordinary Android network permission.
- Keep deterministic intent and expense parsers as the default. Add an optional ML Kit Prompt API adapter for Gemini Nano; it performs a runtime capability probe and only runs inference when ML Kit reports the model AVAILABLE. The adapter uses no API key and does not call a cloud AI endpoint. It does not automatically download a model. If unavailable, existing local rules remain active and no cloud fallback occurs.
- Keep financial data in the existing on-device Room database and preferences.
- Disable Android cloud backup and device-to-device transfer for app data, with explicit exclusion rules for both paths.
- Unrecognized input must fail with a clear message instead of silently sending a prompt to a cloud model.

## Optional local Gemini Nano provider

- Dependency: `com.google.mlkit:genai-prompt:1.0.0-beta4`.
- The Settings screen includes an explicit runtime support probe. It reports available, download required, downloading, unsupported, unknown, or error; it does not start a model download.
- `MlKitGeminiNanoClient` implements the existing `FinancialAiClient` interface, checks availability before inference, and returns a fail-closed error when the model is not ready. It closes the ML Kit model after the probe.
- ML Kit Prompt API depends on Android AICore and device/model support. Do not infer support from Android version, brand, or RAM. Run the probe on the actual target device.
- The ML Kit dependency is separate from Firebase SDKs. CI must continue to reject Firebase dependencies and references.

## Separate online AI boundary

Online AI is not enabled by this local-provider implementation. The app's manifest currently has no `INTERNET` permission, and there is no API-key storage or online provider in this branch. Do not add a cloud fallback to `MlKitGeminiNanoClient`. A later online-AI feature must be a separate provider with a separate user-controlled mode, explicit data-sharing confirmation, protected API-key storage, and tests proving that online requests are impossible while the mode is disabled. Enabling online AI would require a deliberate manifest/network-policy change and would weaken the current OS-level network boundary.

## Important distinction: offline app vs offline AI

The current assistant is not yet a general-purpose offline LLM. Its supported commands use local deterministic parsing and calculations. The AI-client interface now fails closed for unsupported requests. This protects privacy but reduces the range of natural-language requests until a local inference runtime is integrated and evaluated.

## Proposed on-device intelligence

1. Keep deterministic operations as the source of truth. Use Kotlin rules and Room queries for expense/loan recording, budgets, totals, due dates, and navigation. Model output must never write directly to the database; convert output into a typed proposal, validate it, and require confirmation for ambiguous/high-impact actions.
2. Improve the local parser first. Add explicit grammar, locale-aware amount/date parsing, merchant/category aliases, Telugu/English/Hindi/Kannada examples, confidence scoring, and clarification prompts. This is small, testable, fast, and suitable for low-memory devices.
3. Optional local LLM, not a cloud fallback. Evaluate a small quantized GGUF model using llama.cpp or another audited Android on-device runtime. Bundle the model in a separately distributed, user-selected offline model pack; do not auto-download it in the app. Model size, RAM use, latency, thermal behavior, language quality, and licence must be measured on the actual target phone before selecting it. On a 4 GB RAM device, start with a small 1B–2B class quantized model and a short context window; this is a test hypothesis, not a performance guarantee.
4. Gemini Nano / ML Kit is not the privacy baseline. It can run inference on-device on supported devices, but device/model/API availability is not universal and AICore may need to download/update its model through system services. Do not assume this provides a universally available, permanently offline path on the target Tecno device. It can be evaluated as an optional adapter only after device support is confirmed.
5. Speech must also be local. Do not launch generic system speech-recognition intents that may route audio through another provider. Use an explicitly on-device recognizer only when available, and otherwise require text entry. Verify Telugu and other needed languages work offline.
6. Keep local data local. Room remains the source of truth. No sync queue, analytics upload, remote crash reporting, cloud backup, or model telemetry should be added. Review Android backup/data-extraction rules and device-level keyboard/voice IME behavior separately; the app cannot guarantee how a third-party keyboard or system service handles text typed into it.
7. Encrypt and protect local records. Review database-at-rest encryption, Android Keystore key lifecycle, PIN verifier, app backup exclusions, exported files, clipboard behavior, screenshots, logs, notifications, and lock-screen previews. A missing INTERNET permission prevents app network access but does not itself encrypt local files or control other apps/system services.

## Verification gates

- Inspect merged release manifest and assert INTERNET is absent.
- Inspect Gradle dependency graph and APK contents for any Firebase artifacts; fail CI if found.
- Static scan application source for Firebase SDK references, network clients, HTTP URLs, analytics, remote crash reporting, and cloud AI fallback paths.
- Run unit/integration tests with airplane mode enabled for expense, loan, budget, export, navigation, and insights flows.
- Inspect voice flow and ensure no external recognizer intent is launched in privacy mode.
- Test a release APK on the target physical device with packet capture/router monitoring as an additional defense-in-depth check; this does not replace code and dependency review.
- Verify Android backup and device-transfer are disabled and backup rules exclude app data; review user-initiated exports separately.

## Explicit limitations

This branch removes app-level network access and Firebase integrations. It does not yet prove that every Android system component, third-party keyboard, backup mechanism, or user-invoked export keeps data local. It also does not yet integrate a general-purpose local LLM. These are separate verification/implementation tasks and must not be described as complete until tested.
