# API Draft v0.2

Base URL: `/api/v1`

API должен поддерживать модель из `docs/14_rental_lifecycle.md`: `Аренда` живет вместе с велосипедом, а `клиентская аренда` создается на каждый клиентский период.

## Auth

- `POST /auth/login`
  - request: `{ login, password }`
  - response: `{ access_token, refresh_token, role, user_id, client_rental_id? }`
  - клиентский логин и пароль указывает на конкретную `клиентскую аренду`.  
  - у разных `клиентских аренд` может быть одинаковый логин, но никогда одинаковый логин и пароль вместе.
  

- `POST /auth/refresh`
  - request: `{ refresh_token }`
  - response: `{ access_token }`

## Admin: clients

- `GET /admin/clients`
  - response: каталог клиентов, отсортированные по ФИО.

- `GET /admin/clients?available_for_rental=true`
  - response: свободные клиенты, не участвующие в активных `клиентских арендах`, отсортированные по ФИО.

- `POST /admin/clients`
  - request: профиль клиента без логина, пароля, велосипеда и ставки.

- `GET /admin/clients/{clientId}`
  - response: профиль клиента + история `клиентских аренд` + клиентская задолженность.

- `PATCH /admin/clients/{clientId}`
  - request: редактирование профиля.

## Admin: bikes

- `GET /admin/bikes`
  - response: каталог велосипедов.

- `POST /admin/bikes`
  - request: фото, модель, недельная ставка, серийные номера.

- `PATCH /admin/bikes/{bikeId}`
  - request: редактирование велосипеда.

- `POST /admin/bikes/{bikeId}/decommission`
  - action: пометить велосипед как выведенный из эксплуатации.

## Admin: rentals

- `GET /admin/rentals`
  - response: список карточек `Аренд` для главного экрана админа.

- `POST /admin/rentals`
  - request: `{ bike_id, client_id, login, password, period_start, video_url?, contract_url?, comment? }`
  - action: создать `Аренду` для велосипеда и первую активную `клиентскую аренду`.
  - validation: `bike_id` не должен быть уже привязан к другой неудаленной `Аренде`.

- `GET /admin/rentals/{rentalId}`
  - response: карточка `Аренды`, текущая `клиентская аренда` если есть, черновик доступа для следующего цикла если статус `mine`.

- `POST /admin/rentals/{rentalId}/pipeline-status`
  - request: `{ pipeline_status: "long_term" | "soon_return" | "mine" }`
  - action: сменить статус. Переход в `mine` закрывает текущую `клиентскую аренду` сегодняшней датой.

- `POST /admin/rentals/{rentalId}/finish`
  - action: закрыть текущую `клиентскую аренду` и перевести `Аренду` в `mine`.

- `POST /admin/rentals/{rentalId}/client-rentals`
  - request: `{ client_id, login, password, period_start }`
  - action: создать новую `клиентскую аренду` внутри существующей `Аренды` и вернуть `Аренду` в `long_term`.
  - validation:
    - `Аренда` должна быть в статусе `mine`;
    - клиент должен быть свободен;
    - логин должен быть уникален;
    - пароль должен быть сложным и не должен повторять ранее созданные пароли.

- `DELETE /admin/rentals/{rentalId}`
  - action: закрыть последнюю активную `клиентскую аренду`, перенести остаточный долг на клиента при необходимости и удалить карточку `Аренды` с главного экрана.

## Admin: client rental adjustments

- `POST /admin/client-rentals/{clientRentalId}/adjustments`
  - request: `{ amount_rub, sign, comment }`
  - where `sign` in `plus|minus`
  - response: новый баланс и суммарная корректировка `клиентской аренды`.

## Client

- `GET /client/me/dashboard`
  - response:
    - `client_rental_id`;
    - статус `active|completed`;
    - bike model;
    - rental start date;
    - rental end date if completed;
    - paid_until;
    - debt;
    - balance;
    - payment presets.

- `GET /client/me/ledger`
  - response: список начислений, оплат и корректировок текущей `клиентской аренды`.

## Payments

- `POST /payments/create`
  - request: `{ payment_type }`
  - `payment_type`: `day|week|two_weeks|month|debt_exact`
  - action: backend привязывает платеж к `client_rental_id` из клиентского токена.
  - completed rental: разрешен только платеж долга, если долг > 0.

- `POST /payments/yookassa/webhook`
  - request: webhook payload YooKassa
  - action: verify + idempotent apply + ledger write to `client_rental_id`.

## Health

- `GET /health/live`
- `GET /health/ready`
