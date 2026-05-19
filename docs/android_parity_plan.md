# Android parity plan with iOS (AtomGo)

## Goal
Build Android app behavior, UX, animations, and flows 1:1 with current iOS implementation.

## Source of truth
- iOS code: `mobile/iosApp/AtomGoIOS/...`
- Shared API contracts: `mobile/shared/src/commonMain/kotlin/com/atomgo/shared/api/...`

## Mandatory engineering standards (Android)
- Android implementation must follow `Clean Architecture` boundaries (`presentation` / `domain` / `data`) as migration continues.
- UI layer must follow `MVVM` with clear responsibility split between `View` (Compose), `ViewModel`, and use-case/data access orchestration.
- Code must remain human-readable with explicit class/module responsibility and predictable folder structure.
- Best-practice defaults are required: immutable UI state models, unidirectional data flow, explicit side-effects, and dependency isolation.
- Test coverage is required continuously during implementation: tests are added in the same step where behavior is introduced or changed.

## Execution plan
1. Foundation and architecture parity (in progress)
- Move Android to single-root app flow like iOS (`launching` -> `login` -> `clientHome`/`adminHome`).
- Introduce Android design system tokens matching iOS `AppDesign`.
- Add stateful ViewModels equivalent to iOS app/login/home models.
- Add progress tracker and keep it updated during implementation.

2. Login screen parity
- Recreate iOS login layout, typography, spacing, assets, and interactions.
- Implement remember-me persistence and same status/error logic.
- Implement keyboard behavior and toast/feedback behavior equivalent to iOS.

3. Client home parity
- Port client dashboard screen and all payment flows (create payment, refresh payment status, receipt email updates) with same messaging and edge cases.

4. Admin home parity
- Port full admin list/cards/details flows.
- Port create/edit/delete client, bike, rental flows and all sheets/dialogs.
- Preserve lifecycle nuances and mutation refresh strategy from iOS.

5. Visual and animation parity pass
- Match transitions, sheet behavior, loading states, and micro-interactions.
- Verify typography and iconography parity against iOS assets.

6. QA and hardening
- Add/port UI tests for critical flows (login, client payment, admin rental lifecycle).
- Manual parity checklist run on Android emulator and iOS side-by-side.

## Progress log
- 2026-05-19: Plan created. Started Phase 1 (architecture + Compose root routing + Android ViewModel layer parity baseline).
- 2026-05-19: Implemented Compose app shell (`AppRoute`, `AppViewModel`, `LoginViewModel`, `AtomGoApp`) and switched `MainActivity` to single-root Compose flow.
- 2026-05-19: First Android build attempt failed on missing `AppCompat` theme dependency; fix applied and rebuild started.
- 2026-05-19: `:mobile:androidApp:assembleDebug` is green after Compose migration baseline.
- 2026-05-19: Login parity step: copied iOS fonts and core login assets (icon/user/lock/eye) into Android `res/font` and `res/drawable`.
- 2026-05-19: Login parity step: replaced Android login Compose with iOS-like geometry/layout, typography, icons, remember-me row, password visibility toggle, and animated status toast.
- 2026-05-19: Login parity step: fixed Compose scaling/font integration issues; Android build is green again (`:mobile:androidApp:assembleDebug`).
- 2026-05-19: Client flow parity step: extended Android app logic with client payment creation and payment status refresh hooks (including receipt email update path), plus switched admin list source to `/admin/rents` parity path.
- 2026-05-19: Client UI parity step: replaced generic client dashboard shell with dedicated ClientHome screen (bike/debt card, quick-pay debt action, tariff selection for day/week/two_weeks/month, selected tariff payment action, payment status refresh UI).
- 2026-05-19: Admin UI parity step: replaced generic Admin shell with dedicated rents screen using `/admin/rents` data, added search + filters (all/debtors/active), and rental cards with status/debt/profit/correction metrics.
- 2026-05-19: Admin operations step: added Android create dialogs and wired backend calls for creating client, bike, and rental directly from Admin screen, with status messaging and automatic list refresh.
- 2026-05-19: Admin lifecycle step: added rent-card actions for client details, rental deletion, and client deletion with confirmation dialogs; wired backend methods and auto-refresh after mutations.
- 2026-05-19: Admin update step: added Android update flows for client, bike, and rental (dialogs + backend calls + success/error feedback + refresh), completing CRUD parity baseline for core admin entities.
- 2026-05-19: Admin lifecycle step: added Android finish/start rental operations (dialogs + backend wiring + status/refresh handling), closing the next parity gap for rental lifecycle management.
- 2026-05-19: UX parity polish step: added animated operation feedback toasts for both ClientHome and AdminHome flows to better match iOS interaction feedback cadence.
- 2026-05-19: Visual parity polish step: added selected-state styling for client tariff chips and admin filter chips, plus elevated admin rent cards for closer iOS-like depth hierarchy.
- 2026-05-19: Hardening step: removed obsolete Android legacy dashboard code paths (`DashboardShell`, old text-only loaders in AppViewModel) after migration to dedicated Client/Admin Compose screens.
- 2026-05-19: Emulator QA step: added stable Compose selectors (`testTag` + `contentDescription`) for login/admin controls and introduced instrumentation UI test (`LoginUiTest`); `connectedDebugAndroidTest` passed on AVD (`1/1`).
- 2026-05-19: Login layout fix step: switched Android login screen to centered scaled canvas (414x896 reference) to prevent controls rendering off-screen on tall devices/emulators; keeps iOS geometry while guaranteeing visibility.
- 2026-05-19: Login parity refinement step: aligned Android login scaling model with iOS (`xScale`/`yScale` + `textScale`) and updated typography details (button letter spacing, input text sizing formula) for closer 1:1 rendering.
- 2026-05-19: Login animation parity step: tuned login toast enter/exit to iOS-equivalent motion (`opacity + move from bottom`, `easeInOut`, `180ms`), matched toast shadow depth, and set `Get Started` button to iOS accent color (`#1F2937`) instead of Material default.
- 2026-05-19: Emulator verification step: reinstalled debug APK and validated updated login screen/controls on `AtomGo_API34` with fresh screenshot capture after animation/style fixes.
- 2026-05-19: Keyboard animation parity step: implemented iOS-like animated `keyboardLift` for Android login (`200ms`, `easeOut`-style curve) by tracking IME/status bar insets and smoothly shifting the full login layout upward while keyboard is visible.
- 2026-05-19: Cross-screen animation parity step: introduced shared Android `AppToast` composable with iOS-equivalent timing/style (`opacity + bottom move`, `180ms`, white 0.98 background, stronger shadow) and switched both `ClientHome` and `AdminHome` to this unified behavior.
- 2026-05-19: Toast positioning parity step: moved `ClientHome` and `AdminHome` toast rendering from scroll/content flow into bottom overlay alignment (`BottomCenter`) with iOS-like bottom paddings (`86` client, `96` admin), so feedback does not shift page layout.
- 2026-05-19: UI test coverage step: added `HomeNavigationUiTest` with admin login-to-home assertion (running on AVD via `ANDROID_SERIAL=emulator-5554`) and tagged `client` navigation test as `@Ignore` until stable seeded client credentials are guaranteed on the backend environment.
- 2026-05-19: Visual self-check step: captured fresh emulator screenshots for flow validation (`/tmp/admin-home-screen.png`, `/tmp/client-home-attempt-screen.png`) and validated screen state using `uiautomator` dumps (`admin_home_title` present for admin path; client path remains on login with current credentials on this stand).
- 2026-05-19: Admin layout stabilization step: reworked top action area into adaptive two-column rows with fixed heights/weights and scroll-safe filter chips to eliminate compressed vertical button labels on narrow width combinations.
- 2026-05-19: Admin login verification step: added/ran focused UI test `loginAsAdminClassic_opensAdminHome` (`admin/admin123`) on emulator (`ANDROID_SERIAL=emulator-5554`), then captured updated admin screen screenshot (`/tmp/admin-home-after-layout-fix.png`).
- 2026-05-19: Process requirement step: added mandatory Android engineering constraints to this tracker (`Clean Architecture`, `MVVM`, human-readable modular structure, and test coverage added in the same step as new behavior).
- 2026-05-19: Admin card action layout step: refactored `AdminRentCard` action buttons into two full-width rows with equal-width controls to remove compressed vertical labels (notably for `Завершить`/`Удалить клиента`) and improve readability parity with iOS.
- 2026-05-19: Admin step verification: re-ran focused instrumentation login test (`admin/admin123`) and captured updated emulator screenshot for this step (`/tmp/admin-step-report-02-final-ok2.png`).
- 2026-05-19: Admin visual parity step: redesigned Android rents screen to iOS structure (square `exit/+` top actions, centered `Все аренды` title, iOS chip rows, 77dp row cards with pipeline-color avatar border, status pills, and bottom tab bar).
- 2026-05-19: Admin visual refinement step: replaced `OutlinedTextField` search with custom `BasicTextField` container (46dp height, iOS paddings, centered placeholder) to remove clipped placeholder baseline and match iOS search rendering.
- 2026-05-19: QA step: extended `HomeNavigationUiTest` with admin-home structural assertions and screenshot capture via `UiDevice.takeScreenshot(...)`; added `loginWithRememberedCredentials_opensAdminHome` scenario (submit-only flow for prefilled credentials).
- 2026-05-19: Emulator verification step: `loginAsAdmin_opensAdminHome` passed and produced screenshot `/tmp/admin-home-admin-ip-step04.png` (source device artifact `/sdcard/Download/admin-home-admin-ip.png`).
- 2026-05-19: Emulator note: submit-only prefilled login test failed on this AVD because remembered credentials are not currently preloaded in its app data; scenario is retained for devices where fields are prefilled.
- 2026-05-19: Admin micro-parity step: tuned search icon size to iOS-like `14dp`, adjusted chip counter capsule color to fixed light neutral (`#E5E5E8` for unselected), and increased bottom tab bar bottom inset to match iOS (`22dp` over nav insets).
- 2026-05-19: Build-and-verify step: `:mobile:androidApp:assembleDebug` is green after the micro-parity pass; focused admin login UI test (`loginAsAdmin_opensAdminHome`) is green and produced updated screen evidence at `/tmp/admin-home-admin-ip-step06.png`.
- 2026-05-19: Prefill-flow note: `loginWithRememberedCredentials_opensAdminHome` still depends on runtime prefilled state of the specific emulator profile and may fail on clean AVD data despite user-side remembered credentials.
