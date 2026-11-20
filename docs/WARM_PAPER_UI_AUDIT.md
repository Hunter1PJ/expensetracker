# Warm Paper Journal — UI implementation and interaction audit

This file is the source-of-truth checklist for the redesign. The 41-page `App presentation.pdf` is the original UI baseline; the selected visual direction is **Warm Paper Journal**. Screens must be redesigned individually, but existing Kotlin ViewModels, callback APIs, navigation, test tags and business rules remain authoritative.

## Design rules

- Canvas `#FAF7F2`; paper surface `#FFFCF7`; secondary paper `#F1ECE3`.
- Forest green `#2D5A43` is the primary action and income accent; terracotta `#B86B4F` is the expense accent.
- Main text `#282923`; secondary `#77766F`; border/divider `#E5DED2`; muted sage `#9AA88F`.
- Editorial serif display/section headings, neutral sans-serif body and controls; tabular currency figures.
- Flat paper surfaces, hairline dividers, restrained 8–12dp corners, no glow, neon, violet, gradient or heavy shadow.
- Mobile baseline 390dp; 16dp horizontal inset; minimum interactive hit target 48dp.
- Five bottom destinations only: Home, Transactions, Statistics, Budgets, Settings. Labels remain one line in English, Uzbek Latin and Russian.

## Implementation progress — 20 November 2026

The current branch now contains screen-specific Compose layout work for these destinations and flows:

- **Home:** balance and per-currency summary consolidated into one paper hero; the separate monthly metric block removed; primary entry, AI draft workflow and recent ledger retained.
- **Transactions:** search, type/category filters, empty/loading states and date-grouped rows placed in one continuous archive scroll. No creation CTA was added. Historical Transfer filtering remains available.
- **Statistics:** period, currency and comparison controls grouped into one compact paper panel; summary, interactive trend chart, comparison and category breakdown paths retained.
- **Budgets:** Create Budget moved from the floating action button into the page header; currency filtering, overview, budget cards and deactivation confirmation retained.
- **Add Transaction and Edit Transaction:** date and time changed from one combined control into two independent, equal-width paper controls; both existing picker callbacks and their original test tags remain. AI draft warnings and Expense/Income-only selection remain in Add; the historical Transfer read-only branch remains in Edit.
- **Transaction Detail:** amount/type hero changed from centered stacked content to a left-aligned editorial hierarchy. Existing detail rows, edit/delete actions and delete confirmation remain.
- **Categories:** Add Category moved from a floating button into the top-bar action slot; the original callback and `add_category_fab` test tag remain.
- **Recurring Rules:** Add Rule moved into the top-bar actions beside Run Due Now; both callbacks and their original test tags remain.
- **Add/Edit Category:** name/type and icon/color selection are now grouped into one paper panel with a subtle divider; name, type, icon, color and save controls remain wired.
- **Add/Edit Budget:** scope and optional category are grouped into one panel; period and date range into another. The scope accent uses the forest-green theme primary; currency dropdown, amount, period chips, date pickers and save remain wired.
- **Add/Edit Recurring Rule:** type/amount and category are grouped into one panel; the recurrence schedule remains separate. Start/end dates use paper date cards; the optional end-date switch, edit-mode locked frequency, read-only historical Transfer branch and save behavior remain.

**Canvas cleanup only (not counted as full screen redesign):** residual glow decorations were removed from Settings, Data & Storage, and Financial Export. Their deeper screen-specific layout work is still pending.

**Still pending screen-specific redesign:** Settings, Data & Storage, Financial Export, and remaining modal/picker visual states. The three form groups above are layout-updated but still require AVD and locale verification. Do not treat shared-theme styling or canvas cleanup as completion.

**Validation status:** a source-level delimiter scan passed for the eight screens in the preceding pass and the three form screens in this pass (11 Kotlin screens total). Required key test-tag literals, form picker triggers and moved primary-action callbacks were checked in source. This is not a Kotlin/Android compile. No Android Gradle build, unit tests, locale validation or Medium_Phone AVD visual review has been run. Do not treat this milestone as complete until the completion gate below passes.

## Screens and required controls

| Screen / route | Source PDF | Controls and actions that must remain functional |
|---|---:|---|
| Home | 10–11, 31–33 | Primary Add Transaction; AI text input and Parse; Configure in Settings; Dismiss extraction; candidate Edit & Confirm opens a prefilled Add Transaction form; See all; recent transaction rows open detail; multi-currency selector never combines currencies. |
| Transactions | 28, 41 | Search; All/Expense/Income/Transfer type filters (Transfer is historical/read-only); category filters; clear filters; date-grouped rows open Transaction Detail. No Add button on this tab. |
| Transaction Detail | not shown as a full page in PDF; reachable from rows | Back; Edit existing transaction; Delete; confirmation Cancel/Confirm according to the user's Confirm before delete preference. |
| Add Transaction | 5–8 | Back; Expense/Income selector only; amount; category; note; date picker; 24-hour time picker; save/review action; errors and validation. Never silently save an AI candidate. |
| Edit Transaction | additional required screen | Same form anatomy as Add Transaction, prefilled with the selected existing transaction; title/action clearly say Edit/Save changes; date/time/category/amount/note edits call the existing EditTransactionViewModel; preserve validation and success/back behavior. |
| Statistics | 27, 39–40 | Period selector; previous/next period controls; currency selector; compare toggle/mode; custom date-range dialog; chart point selection where present; category breakdown. Each currency is analyzed separately. |
| Budgets | 9, 29–30 | Empty state Add Budget; currency filter; overall/category budget cards; overflow menu Edit and Deactivate; confirm/cancel deactivation. |
| Add/Edit Budget | 1–4 | Overall vs Category scope; category selection; amount; currency dropdown; Monthly/Weekly/Yearly/Custom period; start/end date pickers; Save. No visible Accounts label or selector. |
| Categories | 12 | All/Expense/Income/other existing category tabs; Add Category; category row opens edit; archive action and archive confirmation/cancel; protected system categories remain protected. |
| Add/Edit Category | 13–14 | Name; Expense/Income/Both type; icon grid; color palette; Save; existing validation and back behavior. |
| Settings | 20–23, 34–35 | Categories, Recurring Rules, Data & Storage and Financial Export navigation; AI enable switch; Gemini API Key open/save/verify/delete; Appearance/theme; currency; currency-code display; week start; date format; 24-hour time format; language; Confirm before delete; About. |
| Data & Storage | 15–16, 35–39 | Google sign-in/sign-out; Automatic Backup & Restore preference; Back Up Now; Restore Now; local backup creation; backup-file picker; restore confirmation; CSV export; progress, success, conflict and error states. |
| Financial Export | 17–19 | Basic CSV / Filtered CSV / PDF format; date-range picker; Expense/Income/Transfer as an export filter only; category filters; Select All/clear; export CSV and generate PDF actions. Remove the visible internal Accounts filter but preserve export behavior and supported formats. |
| Recurring Rules | 24 | Empty state; Run Due Now; Add Rule; rule overflow actions Edit and pause/deactivate; confirmation/cancel; due-processing feedback. |
| Add/Edit Recurring Rule | 25–26 | Expense/Income; amount; category; frequency; start date; optional end-date switch and date; note; Save; existing validation. |

## Modal, picker and transient states

- Transaction and recurring-rule date/time pickers (24-hour clock).
- Budget currency dropdown and period date pickers.
- Statistics custom date-range dialog and period/currency selectors.
- Gemini API Key dialog: close, input, save/verify, delete.
- Sign-out confirmation: Cancel and Sign Out.
- Transaction deletion confirmation; category archive confirmation; budget deactivate confirmation; recurring-rule pause/deactivate confirmation.
- Backup creation / restore selection and destructive-restore confirmation; cloud sync progress, conflict and failure feedback.
- Export progress, file selection and success/failure feedback.

## Non-negotiable preservation invariants

- UI-only redesign. Do not modify Room schema/migrations, repositories, use cases, calculations, WorkManager, Firebase/Auth/Firestore, cloud backup orchestration, AI parsing/provider/security or DataStore settings behavior.
- Preserve every existing `onClick`, `onValueChange`, ViewModel call, route, `testTag`, loading/error/empty state and accessibility description. Moving a button is allowed; removing or disabling its behavior is not.
- AI entry remains `Preview → Edit → Confirm → Save`; no direct or silent AI save.
- Creation supports Expense and Income only. Historical Transfer records can still be viewed/filtered/exported; do not add transfer creation.
- Keep the single internal default financial ledger invisible as an Accounts UI concept.
- Do not aggregate unlike currencies into one balance/statistics value.
- Keep English, Uzbek Latin and Russian complete; avoid clipped text and keep bottom-navigation labels to one line.
- Never install or test on the user's Realme 7. Device QA uses the Android Studio Medium_Phone AVD.

## Completion gate

A screen is not complete until its visual layout is implemented, every listed control is connected to its pre-existing action, its empty/loading/error/data states are covered, all three locales are checked, and the app compiles with unit tests passing. Report any unavailable test execution explicitly; do not claim success without logs.
