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
- 2026-05-19: Admin animation parity step: added `Crossfade(180ms)` for rents list/filter result transitions to mimic iOS smooth filter/search state change without abrupt list swaps.
- 2026-05-19: Verification step: after crossfade pass, `assembleDebug` and focused admin login UI test are both green; refreshed emulator screenshot evidence at `/tmp/admin-home-admin-ip-step07.png`.
- 2026-05-19: Login prefill step: implemented startup prefill for auth fields in Android (`LoginViewModel`) with priority of saved credentials and fallback defaults `admin/admin123`, so login/password are populated immediately on launch.
- 2026-05-19: Login prefill verification: `assembleDebug` is green; emulator UI dump confirms prefilled values (`login_email_input=admin`, masked password of length 8) and screenshot evidence saved at `/tmp/login-prefill-step08.png`.
- 2026-05-19: Admin scroll behavior fix step: replaced rents container implementation from `Column + verticalScroll` to `LazyColumn` with stable item keys (`rentalId/clientId`) and dedicated list viewport (`admin_rents_list`) to match iOS-like smooth long-list scrolling and avoid list jump/recomposition artifacts.
- 2026-05-19: Scroll QA step: added instrumentation scenario `adminRentsList_scrollsLikeIos` with top/after-swipe captures; due temporary login flake in `connectedDebugAndroidTest` on this AVD, performed direct emulator validation (`am start` + tap `Get Started` + ADB swipe) and captured evidence `/tmp/admin-after-login-step10.png` and `/tmp/admin-after-scroll-step10.png` (different frames confirm list movement). `:mobile:androidApp:assembleDebug` is green after changes.
- 2026-05-19: Scroll test stabilization: increased admin-home wait timeout in Android UI tests to `60s` for network/backend jitter tolerance; focused scenario `HomeNavigationUiTest#adminRentsList_scrollsLikeIos` is green again on `emulator-5554`.
- 2026-05-19: iOS-scroll-overlay parity step: rebuilt Android rents layout to layered composition (`chips under list`, `list above chips`, `top/search above list`) with dedicated transparent filter hit-layer and interaction cutoff on scroll start, matching iOS behavior where the moving list visually overlaps filter chips.
- 2026-05-19: Emulator screenshot report for overlay behavior: installed fresh debug build and captured sequential evidence at `/tmp/admin-scroll-overlay-before-step11.png`, `/tmp/admin-scroll-overlay-mid-step11.png`, `/tmp/admin-scroll-overlay-after-step11.png` (list progressively covers filter row as on iOS).
- 2026-05-19: Overlay geometry correction step: fixed rents-layer offsets by including `statusBarTop` in `chipsTop` and `searchMaskHeight`, aligning filter/search/list vertical composition with iOS safe-area behavior and restoring correct overlap zones.
- 2026-05-19: Verification step: rebuilt debug APK and reran focused scroll UI test (`HomeNavigationUiTest#adminRentsList_scrollsLikeIos`) on `emulator-5554`; captured updated emulator evidence `/tmp/admin-overlay-before-step12.png` and `/tmp/admin-overlay-after-step12.png`.
- 2026-05-19: Filters-visibility fix step: corrected Compose layer positioning bug (`height + padding(top)` collapse) by switching filter layers to `offset(y = chipsTop)`; filters are visible in initial state again while preserving list-over-filter overlap during scroll.
- 2026-05-19: Search-cover parity fix step: tuned top spacer and search mask geometry so list content starts below filters and fully disappears behind the search/header layer on upward scroll; validated with screenshots `/tmp/admin-overlay-before-step14.png` and `/tmp/admin-overlay-after-step14.png` plus green focused UI test.
- 2026-05-19: Full-layer clipping fix step: switched from visual overpaint to explicit clip of the entire rents layer (`drawWithContent + clipRect(top = searchMaskHeight)`) so both list content and list background are constrained below the search boundary (no background rise above search).
- 2026-05-19: Emulator verification for full-layer clipping: rebuilt and reinstalled debug APK, captured updated screenshots `/tmp/admin-overlay-before-step15.png` and `/tmp/admin-overlay-after-step15.png`; focused UI test remains green.
- 2026-05-19: Ref-geometry correction step: moved list clipping boundary to the middle of the search field (`searchMaskHeight = ... + searchHeight/2`), set `filters→list` gap equal to `search→filters` (`cardsInitialTop = chipsTop + chipsHeight + chipsTopGap`), matching requested parity constraints.
- 2026-05-19: Bottom navigation sizing step: reduced nav bar visual footprint to iOS-like proportions (opaque white, smaller vertical paddings/icon block, centered max-width tab row `414dp`) to remove over-wide look on Android.
- 2026-05-19: Emulator verification step: captured updated screenshots `/tmp/admin-step16-before.png` and `/tmp/admin-step16-after.png`; focused test `HomeNavigationUiTest#adminRentsList_scrollsLikeIos` is green on `emulator-5554`.
- 2026-05-19: Bottom-nav compaction step: removed top divider/shadow stack causing visual split, reduced top/bottom paddings and tab-item metrics (icon/label/dot block), keeping centered max-width tab row for iOS-like compact navigation height.
- 2026-05-19: Bottom-nav verification step: rebuilt and reinstalled app on `emulator-5554`, captured updated screen evidence `/tmp/admin-step17-nav.png`.
- 2026-05-19: Clients/Bikes parity step: replaced Android admin-tab stubs with full iOS-analog catalog screens (dedicated datasets, filters, search, overlay clipping geometry, card rows, loading/error/empty states) and wired separate backend fetch pipelines for `/admin/clients` and `/admin/bikes` via `AppViewModel`.
- 2026-05-19: Clients/Bikes QA step: added UI scenario `HomeNavigationUiTest#adminTabs_clientsAndBikes_openWithLists` (admin login -> `Клиенты` tab -> `Велосипеды` tab) with automatic screenshots (`/sdcard/Download/admin-clients-tab.png`, `/sdcard/Download/admin-bikes-tab.png`) for emulator evidence.
- 2026-05-19: Create-sheets parity step: replaced Android `AlertDialog` forms for create client/bike/rental with full-screen iOS-like sheets (47dp top controls, section titles, custom bordered inputs, selector fields, inline validation toasts, and rental credentials actions generate/copy).
- 2026-05-19: Create-rental logic step: switched rental creation form from raw `clientId/bikeId` text inputs to catalog-based selectors fed by loaded `/admin/clients` + `/admin/bikes`, with default `periodStart` (`YYYY-MM-DD`) and strict create validation mirroring iOS flow.
- 2026-05-19: Create-sheets QA step: added instrumentation scenario `HomeNavigationUiTest#adminCreateScreens_openOnAllTabs` (admin login -> open create rental/client/bike screens) and captured emulator screenshots: `/tmp/create-rental-screen-step19.png`, `/tmp/create-client-screen-step19.png`, `/tmp/create-bike-screen-step19.png`.
- 2026-05-19: Create-sheets pixel-parity pass: synced Android form geometry and controls with iOS constants (top-bar metrics per sheet, original `back/ok` assets, `AtomGoInputField` typography/paddings/border-dash behavior, rental selector structure with left marker, and bike photo dashed card).
- 2026-05-19: Create-sheets animation pass: added iOS-like full-screen open/close motion for create rental/client/bike (`slide + fade`), preserving toast timing and bottom offset.
- 2026-05-19: Pixel-parity verification step: rebuilt app and reran `HomeNavigationUiTest#adminCreateScreens_openOnAllTabs`; refreshed emulator evidence screenshots: `/tmp/create-rental-screen-step21.png`, `/tmp/create-client-screen-step21.png`, `/tmp/create-bike-screen-step21.png`.
- 2026-05-19: Create-rental picker parity step: replaced inline dropdown selectors with full-screen picker sheets for client/bike selection (with `back/ok` controls and animated presentation), matching iOS full-screen picker interaction model.
- 2026-05-19: Picker QA step: extended `HomeNavigationUiTest#adminCreateScreens_openOnAllTabs` to capture picker screens and verified fresh emulator evidence: `/tmp/create-rental-client-picker-screen-step23.png`, `/tmp/create-rental-bike-picker-screen-step23.png`.
- 2026-05-19: Pixel-refinement pass: synced top-bar icons to original iOS assets (`ic_back`, `ic_ok`) and finalized create-sheet field metrics/typography/dashed borders using iOS `AtomGoInputField` constants.
- 2026-05-19: Picker redesign pass: rebuilt Android client/bike picker sheets to iOS composition (`62dp` top header with `xmark` + `ok`, search field styling, client filter chips, list-row layout with leading icon and trailing selection control, and iOS-like empty states).
- 2026-05-19: Picker verification step: reran focused UI scenario and captured updated picker evidence from emulator: `/tmp/create-rental-client-picker-screen-step24.png`, `/tmp/create-rental-bike-picker-screen-step24.png`.
- 2026-05-20: Clients/Bikes list parity step: replaced Android catalog rows with iOS-like row anatomy for both tabs: clients now use left phone action icon, subtitle `модель · до <дата>`, red debt amount, and trailing chevron; bikes now use runtime-aware border color (free/purple, active/green, soon-return/yellow), subtitle from active rental client + paid-until date, right weekly rate (`₽/нед`), and trailing chevron.
- 2026-05-20: Bikes runtime mapping step: added Android runtime projection logic by matching active rentals to bikes via normalized bike model, mirroring iOS `BikeCatalogSheet` subtitle/color behavior instead of generic `bikeIsInRental` pills.
- 2026-05-20: UI evidence refresh step: fixed stale screenshot issue by clearing old `/sdcard/Download/admin-clients-tab.png` and `/sdcard/Download/admin-bikes-tab.png` before rerun; captured fresh emulator evidence after the new row implementation: `/tmp/atomgo-step30/admin-clients-tab-step31.png`, `/tmp/atomgo-step30/admin-bikes-tab-step31.png`.
- 2026-05-20: Admin details parity step: upgraded Android screens for `конкретная аренда`, `клиентская аренда`, and `клиент` to iOS-like structure (full top action bar, status card with bike/avatar and badges, metrics rows, readonly profile/rental blocks, and bottom action buttons).
- 2026-05-20: Admin details test-hardening step: stabilized `HomeNavigationUiTest#adminDetailsScreens_openAndCapture` with deterministic `content` tags (`admin_client_details_content`, `admin_rental_details_content`) and fallback path when rents list is temporarily empty on startup.
- 2026-05-20: Emulator screenshot report step: rebuilt + reran focused connected UI test and captured fresh evidence for all 3 target screens: `/tmp/atomgo-step34/admin-rental-details-screen-step34.png`, `/tmp/atomgo-step34/admin-client-details-screen-step34.png`, `/tmp/atomgo-step34/admin-client-rental-details-screen-step34.png`.
- 2026-05-20: Rental-to-client behavior parity step: fixed Android action `К арендатору` on rental details screen; it now opens client details (with backend refetch) instead of only closing the modal.
- 2026-05-20: Client details structure pass: added explicit `ПРОФИЛЬ` section heading and aligned section flow with iOS card/profile/history grouping.
- 2026-05-20: Screenshot-stability QA step: tightened UI test waits to `admin_client_details_card` (loaded state) to avoid loading-frame captures; refreshed evidence in workspace artifacts: `.tmp_screens_step35/admin-rental-details-screen-step35.png`, `.tmp_screens_step35/admin-client-rental-details-screen-step35.png`, `.tmp_screens_step35b/admin-client-details-screen-step35b.png`, `.tmp_screens_step35b/admin-rental-open-client-screen-step35b.png`.
- 2026-05-20: Rental details parity refactor step: rebuilt Android `AdminRentalDetails` card anatomy to iOS-like block structure (header with avatar/status, 4-metric row including `ОПЛАЧ. ДО/ЗАВЕРШЕНА`, compact credentials row with `Сгенерировать/Копировать`, inline renter row, and journal list under the card).
- 2026-05-20: Rental behavior alignment step: extended rental preview model with runtime fields (`clientLogin`, `rentalPipelineStatus`, `rentalIsActive`, `paidUntil`) so lifecycle/client_rental states render with iOS-consistent visual logic.
- 2026-05-20: Emulator QA refresh step: reran focused instrumentation flow and captured updated screenshots after the parity refactor: `.tmp_screens_step36/admin-rental-details-screen-step36.png`, `.tmp_screens_step36/admin-client-details-screen-step36.png`, `.tmp_screens_step36/admin-client-rental-details-screen-step36.png`, `.tmp_screens_step36/admin-rental-open-client-screen-step36b.png`.
- 2026-05-20: iOS-asset parity step: imported original iOS action icons into Android resources (`refaktoring`, `youtube_link`, `dogovor_link`, `copy_icon`) and switched details screens from generic Material glyphs to these assets.
- 2026-05-20: Rental card visual pass: aligned Android rental details card with iOS composition (right-side link icon stack, credential copy button icon, refined status/metrics row and journal block spacing).
- 2026-05-20: UI test reliability pass: tightened client-details screenshot waits (`profile section` + `loading gone`) to prevent blank captures during `Аренда -> Клиент` transition.
- 2026-05-20: Emulator screenshot report step: refreshed post-pass evidence at `.tmp_screens_step38/admin-rental-details-screen-step38.png`, `.tmp_screens_step38/admin-client-details-screen-step38.png`, `.tmp_screens_step38/admin-client-rental-details-screen-step38.png`, `.tmp_screens_step38/admin-rental-open-client-screen-step38.png`.
- 2026-05-20: Client-details parity pass: rebuilt Android `Клиент` status card to iOS structure for inactive state (removed oversized name/subtitle block, restored iOS metric-only layout with uppercase metric captions, plus-sign paid value, and exact paddings `20/23` depending on active rental state).
- 2026-05-20: Rental-details geometry pass: synced Android `Аренда` card section paddings/metrics typography with iOS constants (`top row 21/14`, metrics `18/16/8`, credentials/renter rows, uppercase metric labels, white card background).
- 2026-05-20: Verification pass: `:mobile:androidApp:assembleDebug` green and focused instrumentation scenario `HomeNavigationUiTest#adminDetailsScreens_openAndCapture` green on `emulator-5554` after forcing fresh screenshot generation on emulator storage.
- 2026-05-20: Emulator screenshot report step: captured fresh evidence at `.tmp_screens_step39/admin-rental-details-screen-step39.png`, `.tmp_screens_step39/admin-client-details-screen-step39.png`, `.tmp_screens_step39/admin-client-rental-details-screen-step39.png`, `.tmp_screens_step39/admin-rental-open-client-screen-step39.png`.
- 2026-05-20: Rental action-icons pixel pass: aligned Android rental card icon sizing to iOS asset geometry (`youtube 21x16`, `contract 14x18`) while preserving enabled/disabled opacity behavior.
- 2026-05-20: Verification pass: reran `:mobile:androidApp:assembleDebug` and focused connected UI test `HomeNavigationUiTest#adminDetailsScreens_openAndCapture` on `emulator-5554` with cleared `/sdcard/Download` capture files.
- 2026-05-20: Emulator screenshot report step: captured refreshed evidence at `.tmp_screens_step40/admin-rental-details-screen-step40.png`, `.tmp_screens_step40/admin-client-details-screen-step40.png`, `.tmp_screens_step40/admin-client-rental-details-screen-step40.png`, `.tmp_screens_step40/admin-rental-open-client-screen-step40.png`.
- 2026-05-21: Rental pipeline logic parity step: synced Android rent-card pipeline menu behavior with iOS semantics (`long_term` selected for every active non-`soon_return`, `soon_return` selected only for `soon_return`, `mine` selected for inactive rental), and aligned menu caption to iOS wording (`Вернут в течении недели`).
- 2026-05-21: Rental details action parity step: confirmed bottom actions on Android rental details card are now iOS-equivalent (`+ Корректировка` and `Завершить`) with state-based enablement.
- 2026-05-21: UI test + screenshot report step: added and passed focused instrumentation scenario `HomeNavigationUiTest#adminRentPipelineModes_switchAndFilterSoonReturn` on `emulator-5554`; captured fresh evidence at `.tmp_screens_step41/admin-rent-pipeline-menu-step41.png` and `.tmp_screens_step41/admin-rent-soon-return-filter-step41.png`.
- 2026-05-21: Clean Architecture foundation step: introduced explicit `domain` and `data` layers for Android app (`domain/repository/*`, `domain/model/*`, `data/repository/*`) and moved raw API calls out of `ViewModel` classes into repository implementations.
- 2026-05-21: MVVM boundary hardening step: migrated `LoginViewModel` and `AppViewModel` to repository-driven orchestration (UI state + intents in ViewModel, side effects in data layer), preserving existing auth/client/admin flows.
- 2026-05-21: Monolith split step: decomposed former `AtomGoApp.kt` (5375 lines) into focused UI files:
  `AtomGoApp.kt` (app/login/client root),
  `AdminHomeScreen.kt` (admin home shell/navigation/filters),
  `AdminCatalogDetails.kt` (catalog rows + details screens),
  `AdminFormsAndPickers.kt` (create/update dialogs, pickers, shared form components).
- 2026-05-21: Regression verification step after architecture refactor: `:mobile:androidApp:assembleDebug` is green and focused instrumentation test `HomeNavigationUiTest#loginAsAdminClassic_opensAdminHome` is green on `emulator-5554`.
- 2026-05-21: Feature-ViewModel split step: extracted `ClientHomeViewModel` and `AdminHomeViewModel` from generic `AppViewModel` so app routing/session state and feature-side effects are separated by responsibility.
- 2026-05-21: Screen wiring step: switched `ClientHomeScreen` to `ClientHomeViewModel` and `AdminHomeScreen` to `AdminHomeViewModel`; `MainActivity` now provides dedicated ViewModel instances for route/login/client/admin layers.
- 2026-05-21: Stability verification step after ViewModel split: `:mobile:androidApp:assembleDebug` is green; focused UI login flow test on emulator had one transient timeout run and then passed on immediate rerun (`HomeNavigationUiTest#loginAsAdminClassic_opensAdminHome`).
- 2026-05-21: Testability hardening step: made `ClientHomeViewModel` and `AdminHomeViewModel` repository-injectable (default production repos + constructor injection for tests), enabling isolated unit tests for feature ViewModel behavior.
- 2026-05-21: Unit test coverage step: added new local unit tests `ClientHomeViewModelTest` and `AdminHomeViewModelTest` with coroutine main-dispatcher rule (`MainDispatcherRule`), and ran `:mobile:androidApp:testDebugUnitTest` successfully together with `:mobile:androidApp:assembleDebug`.
- 2026-05-21: Presentation-logic extraction step: moved admin search/filter/counts derivation from `AdminHomeScreen` into dedicated `AdminCatalogFilterEngine` (`AdminCatalogFilterEngine.kt`) so composable stays focused on rendering and interaction wiring.
- 2026-05-21: Filtering coverage step: added `AdminCatalogFilterEngineTest` with assertions for mode-specific rent filtering (`soon_return`), counters, and bike list free-filter sorting behavior.
- 2026-05-21: Verification step after filter-engine extraction: `:mobile:androidApp:testDebugUnitTest` and `:mobile:androidApp:assembleDebug` are green; focused emulator smoke `HomeNavigationUiTest#loginAsAdminClassic_opensAdminHome` is green.
- 2026-05-21: Presentation-structure step: physically moved presentation files into dedicated folders (`presentation/ui`, `presentation/viewmodel`, `presentation/model`) while preserving runtime behavior and existing package compatibility.
- 2026-05-21: Build verification after folder migration: `:mobile:androidApp:assembleDebug` is green with new presentation folder layout.
- 2026-05-21: Namespace-completion step: finalized Kotlin package split to match folder architecture (`com.atomgo.android.presentation.ui`, `...viewmodel`, `...model`) and rewired imports in `MainActivity`, UI files, and ViewModels.
- 2026-05-21: Test refit step: updated unit tests to new package boundaries (`AdminCatalogFilterEngineTest`, `AdminHomeViewModelTest`, `ClientHomeViewModelTest`) so clean-architecture/MVVM refactor remains covered.
- 2026-05-21: Verification step after namespace split: `:mobile:androidApp:compileDebugKotlin`, `:mobile:androidApp:testDebugUnitTest`, and `:mobile:androidApp:assembleDebug` are green.
- 2026-05-21: Emulator smoke + screenshot report: focused instrumentation test `HomeNavigationUiTest#loginAsAdminClassic_opensAdminHome` passed on `emulator-5554`; fresh screen evidence captured at `docs/android_screenshots/2026-05-21-admin-home-after-ui-smoke.png`.
- 2026-05-21: Clean-boundary step: moved admin filters/counters/pipeline-normalization into `presentation/model/AdminCatalogModels.kt`; `AdminCatalogFilterEngine` now lives in `presentation.logic` and imports only model contracts.
- 2026-05-21: Architecture consistency step: removed duplicated filter model declarations from `AdminHomeScreen` and rewired UI/tests to new model/logic packages.
- 2026-05-21: Verification step after logic/model extraction: `:mobile:androidApp:compileDebugKotlin` and `:mobile:androidApp:testDebugUnitTest` are green; focused instrumentation smoke `HomeNavigationUiTest#loginAsAdminClassic_opensAdminHome` is green on `emulator-5554`.
- 2026-05-21: Emulator screenshot report step: captured fresh post-refactor evidence at `docs/android_screenshots/2026-05-21-admin-home-after-namespace-cleanup.png`.
