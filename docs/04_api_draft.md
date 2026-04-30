# API Draft v0.1

Base URL: `/api/v1`

## Auth
- `POST /auth/login`
  - request: `{ login, password }`
  - response: `{ access_token, refresh_token, role, user_id }`

- `POST /auth/refresh`
  - request: `{ refresh_token }`
  - response: `{ access_token }`

## Admin: clients
- `GET /admin/clients`
  - response: список карточек для админ-экрана

- `POST /admin/clients`
  - request: профиль клиента + `login/password` + `weekly_rate` + bike binding

- `GET /admin/clients/{clientId}`
  - response: полная карточка клиента + история аренд

- `PATCH /admin/clients/{clientId}`
  - request: редактирование профиля

## Admin: debt adjustments
- `POST /admin/clients/{clientId}/adjustments`
  - request: `{ amount_rub, sign, comment }`
  - where `sign` in `plus|minus`
  - response: новый баланс и суммарная корректировка

## Admin: rentals
- `POST /admin/clients/{clientId}/rentals`
  - request: `{ bike_id, start_date, end_date?, weekly_rate_snapshot, video_url?, contract_url?, comment? }`

- `PATCH /admin/rentals/{rentalId}`
  - request: обновление ссылок/комментария/дат

## Client
- `GET /client/me/dashboard`
  - response:
    - bike model
    - rental start date
    - paid_until
    - debt
    - payment presets

- `GET /client/me/ledger`
  - response: список начислений/оплат/корректировок

## Payments
- `POST /payments/create`
  - request: `{ client_id, payment_type }`
  - `payment_type`: `day|week|two_weeks|month|debt_exact`
  - response: `{ payment_id, confirmation_url, amount_rub }`

- `POST /payments/yookassa/webhook`
  - request: webhook payload YooKassa
  - action: verify + idempotent apply + ledger write

## Health
- `GET /health/live`
- `GET /health/ready`
