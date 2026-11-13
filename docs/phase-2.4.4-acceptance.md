# Phase 2.4.4 Acceptance Checklist

This checklist uses the existing Settings key workflow and secure storage. Do not send API keys in chat, terminal commands, screenshots, test fixtures, or logs. Enter and remove credentials only in the app.

## Gemini BYOK

1. Install a debug build on a test device and open Settings > Gemini API key.
2. Enter the key directly in the app and save it. Reopen the key dialog and confirm the stored value is masked; do not capture the input while it is visible.
3. Use **Test key**. Mark the key verified only after the app completes its authenticated request and reports success. A stored or masked key alone is not verification.
4. Try an invalid key only in the app. Confirm a safe error is shown, no key or backend response detail is exposed, and the offline/manual workflows remain available.
5. Delete the key in Settings and confirm the configured state and masked value clear.

## Gemini Language Cases

Run each sentence separately with a valid user key and an available network. Record the returned values as-is before editing. For every actual Gemini result, inspect transaction type, amount and currency, date, suggested title, suggested category, confidence, and warnings. Do not silently correct a result; make any correction explicitly in the candidate editor.

| Language | Input |
| --- | --- |
| English | `I spent 25 dollars on lunch today.` |
| English | `Received 150 dollars from freelance work.` |
| English | `I paid for groceries.` (amount intentionally missing) |
| Uzbek Latin | `Bugun tushlik uchun 85 000 so'm sarfladim.` |
| Uzbek Latin | `Ish haqimdan 2 500 000 so'm oldim.` |
| Uzbek Latin | `Do'kondan oziq-ovqat oldim.` (amount intentionally missing) |
| Russian | `Сегодня потратил 1200 рублей на продукты.` |
| Russian | `Получил 5000 рублей за работу.` |
| Russian | `Купил продукты.` (amount intentionally missing) |

For each missing-amount case, verify the preview and draft retain a missing amount and require explicit entry before saving.

## Candidate-to-Save Flow

1. Enter one or more natural-language transactions on Home and extract them.
2. Check each preview, open one candidate, and verify its title, amount, type, date, account/category suggestions, source, confidence, and warnings in the Add Transaction draft.
3. Edit fields and navigate back without saving. Confirm the candidate remains and reopening it does not create a transaction.
4. Reopen it, complete required fields, and explicitly save. Confirm Home returns, exactly one transaction is visible, only the saved candidate is removed, and other candidates remain.
5. Try saving a missing-amount draft before entering an amount. Confirm validation blocks saving. Create a transfer and confirm source/destination validation remains enforced.
6. Create a transaction from the normal Add action and confirm it works independently of Gemini configuration.

## Offline Matrix

Repeat extraction and manual transaction creation for each condition, checking that the offline parser or manual creation remains usable:

- No API key (delete it in Settings).
- AI feature disabled in Settings while retaining a configured key.
- Internet unavailable.
- Temporary Gemini/network failure.
- Invalid API key.

## Automated Checks

Use the repository Gradle wrapper to run `testDebugUnitTest`, `compileDebugKotlin`, `assembleDebug`, and `connectedDebugAndroidTest` when a device/emulator is available. The added instrumentation checks use an in-memory Room database and fake AI provider; they never require a real key.