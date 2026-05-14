# Текущее описание логики аренды после разделения сущностей

Файл временный. Используется как опорное описание актуальной backend-логики, пока жизненный цикл `Аренды` и `Клиентской аренды` продолжает уточняться.

## Главное разделение

В backend теперь есть две разные сущности:

1. `RentalRecord` — жизненная аренда велосипеда.
2. `ClientRentalRecord` — конкретная клиентская аренда внутри жизненной аренды.

`RentalRecord` остается карточкой велосипеда на главном экране администратора. Она живет до удаления/вывода велосипеда из эксплуатации и меняет pipeline-статус.

`ClientRentalRecord` создается на каждый период использования велосипеда клиентом. Она хранит клиента, credentials, даты, ссылки, комментарий, платежи/корректировки через ledger/payment `rentalId = ClientRentalRecord.id`.

## Статусы жизненной аренды

Backend enum `RentalPipelineStatus`:

- `LONG_TERM` -> API `long_term`: долгосрочная аренда, зеленая рамка.
- `SOON_RETURN` -> API `soon_return`: скоро вернут, желтая рамка.
- `IN_STOCK` -> API `in_stock`: велосипед у администратора, фиолетовая рамка.

Канонический технический идентификатор - `in_stock` (использует backend, БД, JSON API и текущие тесты). Пользовательская подпись статуса - "У меня". Старое имя `mine` встречается только как legacy-синоним в iOS UI-фильтре (`AdminFilterTab.mine`) и в части старых файлов; новые код и доки должны использовать `in_stock`.

## Жизненная аренда `RentalRecord`

`RentalRecord`:

- одна на велосипед в рамках администратора;
- отображается на главном экране админа;
- хранит `bikeId`, `adminId`, `taxMode`, `pipelineStatus`;
- может хранить текущий активный `clientId` как быстрый указатель для списка, но не хранит реальные client credentials;
- `clientLogin/clientPassword` у lifecycle-аренды очищаются и оставлены только как legacy/fallback для миграции;
- не является источником платежной истории конкретного клиента.

Если у lifecycle-аренды нет активной `ClientRentalRecord`, она считается состоянием `IN_STOCK`: клиент не привязан, логин/пароль пустые, журнал и расчеты текущей клиентской аренды не должны отображаться как активные данные.

## Клиентская аренда `ClientRentalRecord`

`ClientRentalRecord`:

- имеет собственный `id`;
- ссылается на lifecycle-аренду через `rentalId`;
- хранит `clientId`, `bikeId`, `clientLogin`, `clientPassword`;
- хранит `startDate` и `endDate`;
- хранит `videoUrl`, `contractUrl`, `comment`;
- хранит `adminId`, `taxMode`;
- является владельцем credentials для входа клиента;
- является владельцем платежей, корректировок и истории через `ledger.rentalId` / `payment.rentalId`.

**Инвариант credentials**: `clientLogin` и `clientPassword` обязаны быть непустыми для каждой `ClientRentalRecord` — и для активной, и для закрытой. Это критично для возможности клиента войти в свою аренду даже после её завершения (просмотр истории и оплата долга). Если запись поступает с пустыми credentials из legacy-данных, `ensureClientRentalModel` бэкфилит их (см. ниже).

Активная клиентская аренда определяется так:

- `startDate <= today`;
- `endDate == null || endDate > today`.
- `deletedAt == null` (soft-deleted `ClientRentalRecord` никогда не может считаться активной).
- если удаление вызвано по id активной `ClientRentalRecord`, это трактуется как удаление lifecycle-аренды (вывод велосипеда из эксплуатации), а не как точечное удаление истории.

Завершенная клиентская аренда продолжает открываться по своему старому логину/паролю и должна показывать свои сохраненные данные, включая дату завершения. Статус lifecycle-аренды (`long_term/soon_return/in_stock`) — это статус **lifecycle**, а не статус закрытой клиентской аренды; на UI закрытой `ClientRentalRecord` пайплайн-статус НЕ применяется (рамка аватарки нейтрально-серая, метрики показываются как историческая запись).

## Миграция старых данных

При старте backend вызывается `ensureClientRentalModel(store)`.

Если `store.clientRentals` пустой, старые `RentalRecord` с `clientId` превращаются в `ClientRentalRecord`:

- новый id: `client-rental-{legacyRentalId}`;
- `clientLogin/clientPassword`, даты, клиент, велосипед и ссылки переносятся из legacy-записи;
- `ledger`, `payments`, `sessions` со старым `rentalId` перепривязываются на новый `ClientRentalRecord.id`.

После этого для каждой пары `adminId + bikeId` остается одна lifecycle-аренда. Остальные старые rental-записи становятся клиентскими историями и больше не используются как lifecycle-карточки.

Если у lifecycle-аренды нет активной клиентской аренды, она переводится в `IN_STOCK`, очищает клиента и credentials.

Если активная клиентская аренда есть, lifecycle-аренда получает текущий `clientId`, `startDate`, `endDate = null`, но credentials остаются только в `ClientRentalRecord`.

### Backfill credentials для client_rental

Дополнительный шаг внутри `ensureClientRentalModel` (после установки правильного `rentalId` для каждой `ClientRentalRecord`):

- Для каждой `ClientRentalRecord` с пустыми `clientLogin` или `clientPassword`:
  1. Пробуем заполнить из связанной `RentalRecord` (legacy: пара хранилась там);
  2. Если там пусто — заполняем из последнего `AppUser` клиента (`role=CLIENT && clientId=...`);
  3. Если оба источника пусты — оставляем пустыми (ничего фабриковать нельзя, инвариант нарушен на уровне исходных данных).
- Шаг идемпотентен: запись с уже непустыми credentials не меняется.
- Это закрывает legacy-кейс «закрытая `ClientRentalRecord` без логина/пароля» и обеспечивает инвариант credentials для всех записей.

## Создание новой lifecycle-аренды

`createRentalForClient(...)` создает:

1. `RentalRecord` для велосипеда.
2. Первую `ClientRentalRecord` для выбранного клиента.

Для одного велосипеда у одного администратора нельзя создать вторую lifecycle-аренду. Повторный цикл использования этого же велосипеда должен идти через создание новой `ClientRentalRecord` внутри существующей `RentalRecord`.

## Перевод lifecycle-аренды в `IN_STOCK`

`transitionRentalToInStock(...)`:

- находит активную `ClientRentalRecord` для lifecycle-аренды;
- проставляет ей `endDate = today`;
- lifecycle-аренде очищает `clientId`, `clientLogin`, `clientPassword`;
- ставит `pipelineStatus = IN_STOCK`;
- удаляет активные сессии бывшего клиента, но credentials завершенной `ClientRentalRecord` остаются валидными для просмотра этой клиентской аренды.

Новая snapshot-копия `RentalRecord` больше не создается.

## Создание новой клиентской аренды на существующей lifecycle-аренде

`startClientRentalInExistingRental(...)`:

- работает только если у lifecycle-аренды нет активной `ClientRentalRecord`;
- проверяет, что выбранный клиент не участвует в другой активной клиентской аренде этого администратора;
- создает новую `ClientRentalRecord` с новым логином/паролем и датой старта;
- переводит lifecycle-аренду в `LONG_TERM`;
- записывает в lifecycle-аренду текущий `clientId` только как указатель для списка;
- не создает новую `RentalRecord` для того же велосипеда.

## Авторизация клиента

`AuthService` сначала проверяет администратора, затем ищет `ClientRentalRecord` по `clientLogin/clientPassword`.

В клиентскую сессию кладется:

- `clientId`;
- `rentalId = ClientRentalRecord.id`.

Это значит, что `client1/client123` и `client1/client1234` могут вести в разные клиентские аренды, если это разные `ClientRentalRecord`.

Fallback по `AppUser` выбирает активную или последнюю `ClientRentalRecord` клиента.

## Клиентский dashboard

Клиентский dashboard строится по `ClientRentalRecord`.

Данные велосипеда берутся через `bikeId`, а pipeline-статус берется из связанной lifecycle-аренды, если она найдена.

Все расчеты, платежи и корректировки используют `ClientRentalRecord.id`.

## Админский главный список

Главный список админа строится по lifecycle `RentalRecord`.

Для активной lifecycle-аренды данные клиента, расчетов и статуса карточки берутся из активной `ClientRentalRecord`.

Для `IN_STOCK` lifecycle-аренды карточка должна показывать состояние `у меня`: клиент не выбран, нет активной клиентской истории, нет активных расчетов.

Фильтр клиентов `Должники` (экран `Клиенты`) опирается на `/admin/clients` и должен учитывать не только активные аренды:

- `debt_rub` в `ApiAdminClientSummaryResponse` считается как суммарный долг по всем `ClientRentalRecord` клиента (активным и закрытым, исключая soft-deleted);
- для активной аренды долг считается per-week (`LedgerCalculator.debtRub`);
- для закрытой аренды долг считается per-day при закрытии (`LedgerCalculator.finalDebtOnClosure`);
- `carried_debt_rub` остаётся отдельным полем (перенесенный долг). На iOS клиент считается должником, если `debt_rub > 0 || carried_debt_rub > 0`.

## Экран деталей аренды в админке

`GET /api/v1/admin/rentals/{id}` поддерживает два типа id:

1. `RentalRecord.id` — открытие lifecycle-карточки с главного списка.
2. `ClientRentalRecord.id` — открытие конкретной клиентской аренды из истории клиента.

Если открыт lifecycle id:

- при наличии активной `ClientRentalRecord` показываются ее credentials, клиент, журнал и расчеты;
- при `IN_STOCK` без активной `ClientRentalRecord` показывается пустой клиент/credentials, история не должна использовать старую клиентскую аренду как активную.

Если открыт client-rental id:

- показываются данные конкретной клиентской аренды;
- для завершенной клиентской аренды отображается дата завершения;
- credentials остаются из этой клиентской аренды.
- карточка должна открываться даже если связанная lifecycle-аренда уже удалена вместе с велосипедом/карточкой с главного экрана.

## Удаление lifecycle-аренды

`POST /api/v1/admin/rentals/{rentalId}/delete` удаляет именно lifecycle `RentalRecord` с главного экрана.

Для совместимости с текущим iOS-потоком этот endpoint принимает:

- `RentalRecord.id`
- `ClientRentalRecord.id` активной клиентской аренды

Если пришел `ClientRentalRecord.id`:

- для **активной** записи backend находит связанную lifecycle-карточку и удаляет именно её;
- для **закрытой** записи выполняется точечный soft-delete только этой `ClientRentalRecord`.

Если у lifecycle-аренды в момент удаления есть активная `ClientRentalRecord`, backend:

- закрывает эту `ClientRentalRecord` сегодняшней датой (`endDate = today`);
- считает финальный долг по дням (см. ниже);
- если финальный долг > 0 — переносит его в `ClientAccount.carriedDebtRub` накопительно;
- удаляет активные сессии этого клиента;
- сохраняет клиентскую историю, credentials, платежи и корректировки в `ClientRentalRecord`;
- удаляет только lifecycle-карточку `RentalRecord`.

После удаления lifecycle-карточки:

- запись исчезает из `/admin/rents`;
- закрытая `ClientRentalRecord` остается в истории клиента;
- `GET /admin/rentals/{clientRentalId}` продолжает открывать эту клиентскую аренду;
- старый логин/пароль этой `ClientRentalRecord` продолжает вести клиента в завершенную клиентскую аренду;
- админ видит перенесенный долг в карточке клиента через поле `carried_debt_rub` ответа `GET /admin/clients/{id}` и `GET /admin/clients` (см. `ApiAdminClientSummaryResponse` / `ApiAdminClientDetailsResponse`).

### Финальный долг при закрытии (per-day formula)

Реализовано в `LedgerCalculator.finalDebtOnClosure`. Спецификация: docs/14_rental_lifecycle.md §3, docs/02_money_and_debt_rules.md §5.

```
day_amount    = weekly_rate / 7
covered_days  = floor(total_paid_rub / day_amount)
used_days     = days_between(rental.start_date, rental.end_date)
overdue_days  = max(0, used_days - covered_days)
final_debt    = max(0, round(overdue_days * day_amount) + total_adjustment_rub)
```

`total_adjustment_rub` использует существующую конвенцию `LedgerCalculator.totalAdjustmentRub`: положительное значение увеличивает долг, отрицательное — уменьшает. Если `weekly_rate <= 0` либо `end_date < start_date`, финальный долг = 0.

Эта формула применяется ТОЛЬКО при закрытии (`finish` через переход в `in_stock` и при `delete` lifecycle-аренды). Активный долг во время аренды по-прежнему считается по неделям через `LedgerCalculator.debtRub` (билинг идет по неделям, перерасход добивается только при закрытии).

### Перенос долга на ClientAccount

`ClientAccount.carriedDebtRub` (int, default 0) — клиентская задолженность, перенесенная при удалении lifecycle-аренды.

- Аккумулируется при каждом удалении lifecycle-аренды с непогашенным финальным долгом: `client.carriedDebtRub += finalDebt`.
- Уменьшается через admin-эндпоинт `POST /admin/clients/{id}/carried-debt` (см. ниже). Автоматически от оплаты закрытой `ClientRentalRecord` не уменьшается — это управляется отдельной admin-логикой.
- Хранится в Postgres-таблице `atomgo_clients.carried_debt_rub` (auto-migration через `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`).
- Сериализуется в `clients_view` для debug.
- Отдается в JSON клиентских ответов как `carried_debt_rub` (в `AdminClientSummaryResponse` и `AdminClientDetailsResponse`).

### Admin-операции над carriedDebt

`POST /admin/clients/{clientId}/carried-debt` — единая точка изменения перенесённого долга. См. `docs/04_api_draft.md` секция «Admin: carried debt operations» для контракта.

Реализовано в `Application.kt` через helper `applyCarriedDebtOperation` (sealed `CarriedDebtOutcome.Success` / `Failure`). Поведение:

| kind | amount ≤ carriedDebt | amount > carriedDebt |
|---|---|---|
| `writeoff` | списать; ledger ADJUSTMENT direction=-1 без rentalId | `400 amount_rub exceeds carried_debt_rub` |
| `payment` | списать; ledger PAYMENT direction=-1 без rentalId | до carriedDebt — в долг; излишек — PAYMENT в активную client_rental клиента (с её rentalId). Если активной аренды нет — `400 ... and no active rental to apply excess`. |

Аудит: каждая мутация = одна или две `LedgerEntry` записи с осмысленным `note`. `rentalId=null` для записей по carriedDebt, `rentalId=<clientRental.id>` для излишка.

## Нормализация legacy-состояния

Перед сохранением и перед чтением ключевых админских экранов backend повторно прогоняет нормализацию state:

- достраивает `clientRentals`, если store был поднят из legacy payload без отдельного списка `ClientRentalRecord`;
- перепривязывает `ledger`, `payments` и `sessions` с legacy lifecycle rental id на `ClientRentalRecord.id`;
- legacy-дубли lifecycle-аренд по ключу `adminId + bikeId` помечает soft-delete'ом (одна канонической, остальные сохраняются в истории);
- бэкфилит `clientPasswordFingerprint` (SHA-256 от `clientPassword`), если пустой — это нужно для проверки уникальности паролей среди новых записей.

Это нужно для ситуаций, когда старые lifecycle-записи успели накопиться до разделения сущностей и начинают повторно появляться в `Все аренды`.

## Soft-delete

`RentalRecord.deletedAt`, `ClientAccount.deletedAt`, `BikeAccount.deletedAt` (`Instant?`).

При `/admin/rentals/{id}/delete` / `/admin/clients/{id}/delete` / `/admin/bikes/{id}/delete` — backend проставляет `deletedAt = Instant.now()` ВМЕСТО физического удаления записи из store. Запись остаётся в БД (Postgres `deleted_at TIMESTAMPTZ`).

Все списочные эндпоинты (`GET /admin/rents`, `/admin/clients`, `/admin/bikes`) и валидации (уникальность серийников велосипеда, инвариант «один bike — одна неудалённая lifecycle», уникальность пароля) фильтруют по `deletedAt == null`.

История ClientRentalRecord остаётся доступной даже если связанная lifecycle помечена deletedAt — `lifecycleRentalForClientRental` сначала ищет lifecycle по id (включая soft-deleted), чтобы открытие карточки закрытой client_rental из истории клиента всегда работало.

Постгрес-миграция: `ALTER TABLE ... ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ` для каждой из трёх таблиц.

## Уникальность пароля (docs/14_rental_lifecycle.md §4)

`ClientRentalRecord.clientPasswordFingerprint` (`String`, default `""`) — SHA-256 hex от UTF-8 байт `clientPassword` (с предварительным trim'ом). При создании client_rental через `createRentalForClient` / `startClientRentalInExistingRental` backend:

1. Считает `passwordFingerprint(password)`.
2. Проверяет, что fingerprint не встречается ни в одной существующей `ClientRentalRecord` (включая soft-deleted lifecycle, чтобы не вернуть к жизни старые credentials как «новые»). Если совпадает — `409 password is already used`.
3. Сохраняет fingerprint в записи.

Plaintext пароль (`clientPassword`) хранится отдельно — он нужен админу для кнопки «Копировать». Аутентификация продолжает работать по plaintext через `AuthService`. Это компромисс между требованием уникальности и UX-требованием «копировать пароль клиенту».

## Финальный долг закрытой client_rental — единая семантика для `/finish` и `/delete`

При закрытии client_rental (любым путём) долг считается per-day через `LedgerCalculator.finalDebtOnClosure`:
- `daily_rate = weekly_rate / 7`
- `covered_days = floor(total_paid / daily_rate)`
- `used_days = days_between(start, end)`
- `overdue_days = max(0, used - covered)`
- `final_debt = max(0, round(overdue * daily_rate) + total_adjustment)`

Эта формула применяется во ВСЕХ местах, где отображается долг закрытой client_rental:
- `buildAdminClientDetails` (история аренд клиента);
- `GET /admin/rentals/{closedClientRentalId}` (детали закрытой);
- `GET /client/me/dashboard` (клиентский экран для закрытой аренды);
- `PaymentService.createPayment` (расчёт суммы DEBT_EXACT и валидация preset amount).

Активный долг (rentalIsActive == true) по-прежнему считается per-week через `LedgerCalculator.debtRub`.

## Презеты payment-кнопок (closed rental clamp)

Для активной client_rental презеты — фиксированные `{day, week, two_weeks, month}` по тарифу. Для закрытой — кнопка обнуляется (preset = 0), если её сумма больше остаточного долга. Например debt=5999, weekly=3500: `day_rub = clamp(500, 5999) = 500`, `week_rub = clamp(3500, 5999) = 3500`, `two_weeks_rub = clamp(7000, 5999) = 0`, `month_rub = clamp(14000, 5999) = 0`, `debt_exact_rub = 5999`.

iOS должен делать кнопки с amount=0 disabled. Backend в `PaymentService.createPayment` дополнительно проверяет: если closed rental И `rawAmount > debt`, отвергает с «Amount is zero. Nothing to pay.».

## GET /client/me/ledger

Журнал текущей client_rental клиента (`ApiClientLedgerResponse`). Возвращает entries (`type`, `amount_rub`, `created_at`, `note?`), отсортированные по `created_at` убыванием. Источник — `store.ledger`, отфильтрованный по `entry.rentalId == session.rentalId` (плюс legacy-записи с `rentalId == null`, привязанные по clientId). См. `docs/04_api_draft.md` секция «Client».

## Экран "Новая аренда" (iOS)

- Экран создания аренды открывается как `fullScreenCover`, а не modal form-sheet.
- Выбор клиента выполняется не через `Picker`, а через отдельный экран выбора `RentalStartClientPickerSheet`:
  - визуально и по поведению как выбор клиента в карточке lifecycle-аренды в состоянии `in_stock`;
  - в списке только свободные клиенты (`rentalIsActive == false`);
  - выбор одного клиента, подтверждение кнопкой `checkmark`.
- Выбор велосипеда сделан аналогично через отдельный экран `RentalStartBikePickerSheet`:
  - список велосипедов с поиском;
  - выбор одного велосипеда, подтверждение кнопкой `checkmark`.
- На главном экране создания аренды клиент и велосипед представлены как кликабельные selector-поля.
- После выбора клиента логин автоподставляется из `clientLogin`, если он у клиента есть.

## Платежи

`PaymentService` ищет активную или указанную `ClientRentalRecord`.

`PaymentRecord.rentalId` и `LedgerEntry.rentalId` теперь должны ссылаться на `ClientRentalRecord.id`.

Tax mode для платежа берется из связанной lifecycle-аренды, если она найдена, иначе из `ClientRentalRecord.taxMode`.

Разделение YooKassa сохраняется:

- самозанятые используют `YOOKASSA_SHOP_ID` / `YOOKASSA_SECRET_KEY` и платеж без `receipt`;
- ИП используют `YOOKASSA_SHOP_ID_IP` / `YOOKASSA_SECRET_KEY_IP` и платеж с `receipt`.

## Важные инварианты

- Один велосипед у одного администратора = одна lifecycle `RentalRecord`.
- Одна lifecycle `RentalRecord` может иметь много `ClientRentalRecord`.
- В один момент у lifecycle `RentalRecord` может быть только одна активная `ClientRentalRecord`.
- Клиент не может иметь две активные `ClientRentalRecord` у одного администратора.
- Credentials принадлежат `ClientRentalRecord`, а не lifecycle `RentalRecord`.
- Платежи и корректировки принадлежат `ClientRentalRecord`.
- Завершенная `ClientRentalRecord` не равна lifecycle-аренде в статусе `IN_STOCK`.
- Финальный долг при закрытии клиентской аренды считается строго по дням, а не по неделям (см. `LedgerCalculator.finalDebtOnClosure`).
- Непогашенный финальный долг при удалении lifecycle-аренды переносится в `ClientAccount.carriedDebtRub` накопительно.
- У каждой `ClientRentalRecord` (активной и закрытой) обязаны быть непустые `clientLogin` и `clientPassword`. Бэкфилл встроен в `ensureClientRentalModel`.
- Статусы `long_term/soon_return/in_stock` относятся к lifecycle-аренде. К закрытой `ClientRentalRecord` они не применяются: на UI она показывается как историческая запись с нейтрально-серой рамкой аватарки и без пайплайн-семантики.
- Кнопка «Сгенерировать» в карточке аренды живёт только в lifecycle-аренде в статусе `IN_STOCK`. Она генерирует пару (логин + пароль) одной операцией.

## Текущий статус задач (см. docs/06_roadmap_2_weeks.md, docs/08_sprint1_execution_plan.md)

| Область | Статус | Комментарий |
|---|---|---|
| Удаление lifecycle-аренды: закрытие активной клиентской | ✅ | `deleteLifecycleRental` в `Application.kt`. |
| Удаление: перенос долга на клиента | ✅ | `ClientAccount.carriedDebtRub` + `LedgerCalculator.finalDebtOnClosure`. |
| Per-day final debt | ✅ | `LedgerCalculator.finalDebtOnClosure` + unit-тесты `LedgerCalculatorTest`. |
| Integration-тесты на удаление | ✅ | `ApiIntegrationTest`: `delete same-day rental…`, `delete fully covered rental…`, `delete with overdue days…`, `delete in_stock lifecycle…`, `deleting already-deleted…`, `delete should accumulate carriedDebt…`. |
| iOS UI кнопка "удалить" с подтверждением | ✅ | `AdminRentalDetailsScreen` (trash-иконка) + `confirmationDialog`. `AdminHomeViewModel.deleteRental` отдельно обрабатывает `BackendError.httpError(404)` (рефреш + нейтральное сообщение «Аренда уже удалена») и зовёт `refreshAfterMutation` для главного списка/каталога/велосипедов. |
| Списание/оплата `carriedDebt` админом | ✅ | `POST /admin/clients/{id}/carried-debt`: `kind=writeoff\|payment`, излишек payment автоматически уходит в активную client_rental. Тесты в `ApiIntegrationTest`: `carriedDebt writeoff…`, `carriedDebt payment full…`, `carriedDebt payment excess should overflow into active client rental`, `carriedDebt payment excess without active rental should fail`, и валидационные. |
| iOS UI для carriedDebt-операций | ✅ | В `clientStatusCard` (AdminHomeView) появляется блок «Перенесённый долг: N ₽» при `carriedDebtRub > 0` с двумя кнопками: «Принять оплату» (accent CTA) и «Списать» (bordered). Обе открывают `CarriedDebtOperationSheet` с заранее выбранным типом. Sheet знает про наличие активной аренды и заранее показывает понятную подсказку про распределение излишка. `AdminHomeViewModel.applyCarriedDebt` собирает контекстное success-сообщение («Принято 1500 ₽: 1000 ₽ в перенесённый долг, 500 ₽ в активную аренду» и т.п.) и зовёт `refreshAfterMutation`. |
| iOS: фиолетовая рамка у закрытой client_rental | ✅ | `isInStockState` и `avatarBorderColor` теперь учитывают `details.completedAt`. Для закрытой `ClientRentalRecord` рамка нейтрально-серая, метрики не маскируются дефисами, кнопка «Сгенерировать» скрыта. |
| iOS: «Сгенерировать» с пустым action | ✅ | `generateCredentials()` создаёт ОБЕ части: логин (`userNNNNNN`) и пароль (12 символов из безопасного алфавита). Кнопка показывается только при `isInStockState`. |
| Backend: закрытая client_rental без credentials | ✅ | `ensureClientRentalModel` бэкфилит `clientLogin/clientPassword` из связанной `RentalRecord` или из `AppUser` клиента. Тест `every client rental must expose credentials including after closure` проверяет инвариант. |
| iOS: удаление аренды в статусе IN_STOCK | ✅ | Confirmation dialog в `AdminRentalDetailsScreen` теперь требует только `rentalId` (clientId опционален — в IN_STOCK его нет). Раньше `guard let clientId` тихо валился и кнопка не работала. |
| `/finish` per-day final debt | ✅ | `LedgerCalculator.finalDebtOnClosure` теперь применяется ВЕЗДЕ, где отображается debt закрытой client_rental: client details, rental details, client dashboard, PaymentService. |
| Уникальность пароля | ✅ | `clientPasswordFingerprint` (SHA-256) + проверка в `createRentalForClient` и `startClientRentalInExistingRental`. Backfill для legacy через `ensureClientRentalModel`. iOS показывает понятное сообщение «пароль уже используется». |
| `GET /client/me/ledger` | ✅ | `ApiClientLedgerResponse` с entries (type, amount_rub, created_at, note). Использует session.rentalId. |
| Clamp payment presets на closed rental | ✅ | В `/client/me/dashboard`: если client_rental закрыта, кнопки `day/week/two_weeks/month` обнуляются когда сумма > debt. `debt_exact_rub` = debt. `PaymentService.createPayment` дополнительно валидирует. |
| Soft-delete для Rental/Client/Bike | ✅ | `deletedAt: Instant?` на трёх сущностях, Postgres `ALTER TABLE ADD COLUMN deleted_at`. Все listing/validation/normalization фильтруют по `deletedAt == null`. История сохраняется. |
| iOS pickers фильтруют сами | ✅ | `RentalStartClientPickerSheet` применяет `.availableForRentalStart()` к входному списку. `RentalStartBikePickerSheet` фильтрует по новому полю `bike_is_in_rental` из backend `AdminBikeResponse`. |
| iOS avatarBorderColor через pipeline_status | ✅ | Switch по `rentalPipelineStatus`: long_term → зелёный, soon_return → жёлтый, in_stock/mine → фиолетовый. Fallback по rentalIsActive только если статус не пришёл. |
| iOS дедуп availableStartClients | ✅ | `Sequence<AdminClientSummaryResponse>.availableForRentalStart()` extension в `AppModels.swift`. Используется и в `CreateRentalSheet`, и в `AdminRentalDetailsScreen`. |
| Backend seed без duplicate lifecycle | ✅ | `rental-000` в `InMemoryStore.seed` помечен `deletedAt = Instant.now().minusDays(48)`. Invariant «один bike — одна неудалённая lifecycle» соблюдён. |
