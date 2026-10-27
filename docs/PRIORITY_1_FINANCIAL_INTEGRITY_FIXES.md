# Priority 1 Financial Integrity Fixes

Branch: `fix/financial-integrity-priority-1`

## Changes in this patch

1. **Preserve budget precision.** Overall budgets are now stored as decimal strings rather than narrowed to a 32-bit float. The reader accepts legacy numeric SharedPreferences values and migrates them to strings without changing valid saved values.
2. **Reject invalid overall budgets.** Non-finite and negative values are ignored by the preference setter.
3. **Stop inventing category limits.** Expense confirmation/overspending calculations no longer substitute a fabricated ₹5,000 category budget when the configured budget is missing.
4. **Stop false overspending notifications.** The ViewModel only sends a category overspending notification when a real category budget exists and is greater than zero.

## Verification status

- Source edits are committed to this feature branch.
- Android compilation, unit tests, lint, and physical-device testing have **not** been run from this environment. Do not treat these changes as release-verified until CI and device checks pass.
- Next priority is regression tests for preference migration, category budget warnings, expense/loan operations, batch transaction rollback, and cloud AI opt-in behavior.

## Scope intentionally excluded

This patch does not change the Room schema, alter existing expense or loan records, enable cloud AI, or merge into `main`. It is a small reviewable patch intended to minimize regression risk.
