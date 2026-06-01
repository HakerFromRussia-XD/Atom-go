# План: смена дня оплаты аренды

## Цель

Добавить управляемый день оплаты аренды для создания и редактирования аренды:

- UI: сегментированный выбор из 7 дней `пн`, `вт`, `ср`, `чт`, `пт`, `сб`, `вс` с подписью `день оплаты аренды`.
- При создании аренды значение по умолчанию равно сегодняшнему дню недели, как дата начала по умолчанию равна сегодняшней дате.
- При сохранении день оплаты должен храниться в backend и возвращаться в деталях/истории аренды.
- Расчет активной долгосрочной аренды должен использовать выбранный день оплаты.
- Расчеты закрытых клиентских аренд (`у меня`) и `soon_return` не должны меняться из-за выбора дня оплаты.

## Расчет

1. Если день оплаты совпадает с днем старта аренды, сохраняется текущая недельная модель:
   `due = dueWeeksCount(start_date, as_of) * weekly_rate`.
2. Если день оплаты позже/раньше дня старта, первая граница оплаты находится на ближайшем выбранном дне недели после старта.
3. До первой границы оплаты долг считается пропорционально дням от старта до этой границы.
4. Начиная с первой границы оплаты, неоплаченная аренда переходит на обычный недельный долг от выбранного дня оплаты.
5. Если недельная ставка уже была оплачена до первой выбранной границы, эта оплата покрывает первую недельную границу, а переходный дневной отрезок становится долгом на этой границе.
6. Стоимость дня берется тем же способом, что уже используется в backend для дневной ставки: `PricingRules.dayAmount(weekly_rate)`.
7. Для закрытия аренды и для `soon_return` остается существующий `finalDebtOnClosure`.

## Этапы

1. Расширить доменную модель `RentalRecord` и `ClientRentalRecord` полем `paymentDay` (`1..7`, ISO: пн=1, вс=7).
2. Добавить поле `payment_day` в API create/update/start и ответы аренды.
3. Добавить миграцию Postgres и JSON persistence с fallback на день недели `startDate`.
4. Вынести расчет активного долга/проекции с учетом `paymentDay` в `LedgerCalculator`.
5. Подключить новый расчет в admin/client dashboard, payment exact debt, cash/adjustment response debt.
6. Добавить сегмент выбора дня оплаты в Android create/edit аренды и прокинуть поле через repository/usecase/viewmodel.
7. Добавить сегмент выбора дня оплаты в iOS create/edit аренды и прокинуть поле через shared API bridge.
8. Добавить unit/integration tests:
   - переход ср -> пт с долгом без платежей;
   - переход ср -> пт с платежом 3500;
   - совпадение дня оплаты со стартом сохраняет старую модель;
   - закрытые/soon_return расчеты не зависят от `paymentDay`;
   - create/update API сохраняют и возвращают `payment_day`.
9. Прогнать тесты локально, поднять локальный backend, выполнить smoke-check.
10. После зеленых проверок обновить prod и проверить health.

## Текущий статус

- 2026-05-31: план создан, начинается реализация.
- 2026-06-01: реализация завершена:
  - backend хранит `payment_day` для lifecycle и client rental записей, API create/update/start возвращают и принимают поле;
  - расчет активной долгосрочной аренды учитывает выбранный день оплаты через переходный дневной отрезок до первой границы и недельные начисления от выбранного дня;
  - закрытые аренды (`у меня`) и `soon_return` оставлены на `finalDebtOnClosure`;
  - Android и iOS create/edit формы получили сегмент `день оплаты аренды` с `пн`..`вс`, новые аренды стартуют с текущим днем недели.
- Проверки:
  - `./gradlew :backend:app:test :mobile:androidApp:testDebugUnitTest --console=plain` — успешно;
  - `./gradlew :mobile:androidApp:assembleDebug :mobile:shared:compileKotlinIosArm64 --console=plain` — успешно;
  - XcodeBuildMCP `build_sim` для `AtomGoIOS` — успешно;
  - XcodeBuildMCP `test_sim -only-testing:AtomGoIOSTests` — успешно, 38/38;
  - локальный backend поднят через `./start_backend.sh`, `/health/ready` вернул `ready`, admin login + `/api/v1/admin/rents` отработали.
- Prod rollout:
  - перед итоговой выкладкой сделан backup artifact: `/opt/atomgo/deploy-backups/20260601003642`;
  - backend artifact обновлен на VPS и `atomgo-backend` перезапущен;
  - public health `https://atomgo.157.22.203.6.nip.io/health/ready` вернул `ready`;
  - YooKassa webhook route вернул `405 Method Not Allowed` на GET, то есть endpoint доступен и не 404/502;
  - в prod PostgreSQL подтверждены колонки `atomgo_rentals.payment_day` и `atomgo_client_rentals.payment_day`.
