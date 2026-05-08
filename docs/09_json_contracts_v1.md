# JSON Contracts (v1)

## 1) POST /api/v1/auth/login
Request:
```json
{
  "login": "client1",
  "password": "client123"
}
```
Response 200:
```json
{
  "access_token": "token-value",
  "role": "client",
  "user_id": "user-client-001"
}
```

## 2) GET /api/v1/client/me/dashboard
Header:
`Authorization: Bearer <access_token>`

Response 200:
```json
{
  "client_id": "client-001",
  "bike_model": "Ninebot E-Bike Pro",
  "rental_start": "2026-04-12",
  "paid_until": "2026-05-03",
  "debt_rub": 1500,
  "balance_rub": 0,
  "total_adjustment_rub": -1500,
  "presets": {
    "day_rub": 430,
    "week_rub": 3000,
    "two_weeks_rub": 6000,
    "month_rub": 12000,
    "debt_exact_rub": 1500
  }
}
```

## 3) GET /api/v1/admin/clients
Header:
`Authorization: Bearer <admin_token>`

Response 200:
```json
[
  {
    "client_id": "client-001",
    "full_name": "Иван Петров",
    "bike_model": "Ninebot E-Bike Pro",
    "bike_avatar_url": "https://example.com/bikes/ninebot-pro.png",
    "status_text": "Долг за 3 дн.",
    "debt_rub": 1500,
    "profit_rub": 0,
    "total_adjustment_rub": -1500
  }
]
```

## 4) POST /api/v1/payments/create
Header:
`Authorization: Bearer <client_token>`

Request:
```json
{
  "payment_type": "day"
}
```

Response 200:
```json
{
  "payment_id": "uuid",
  "amount_rub": 430,
  "confirmation_url": "https://yoomoney.ru/checkout/payments/v2/contract?orderId=...",
  "idempotence_key": "uuid",
  "status": "pending"
}
```

Notes:
- `payment_type`: `day`, `week`, `two_weeks`, `month`, `debt_exact`.
- Backend сам находит активную аренду клиента и привязывает платеж к `rental_id`.
- Деньги в ledger не попадают в момент создания платежа. Начисление происходит только после подтверждения ЮKassa.
- Если переменные ЮKassa не заданы, backend использует mock-provider для локальной разработки.

## 5) GET /api/v1/payments/{payment_id}
Header:
`Authorization: Bearer <client_token | admin_token>`

Response 200:
```json
{
  "payment_id": "uuid",
  "amount_rub": 430,
  "confirmation_url": "https://yoomoney.ru/checkout/payments/v2/contract?orderId=...",
  "provider_payment_id": "2f4f...",
  "status": "succeeded",
  "debt_rub": 0
}
```

Notes:
- Клиент может смотреть только свои платежи, админ - любые.
- Endpoint нужен как fallback: если webhook задержался, приложение опрашивает backend, backend проверяет статус в ЮKassa и применяет успешный платеж один раз.
- `status`: `pending`, `succeeded`, `canceled`, `failed`.

## 6) POST /api/v1/payments/yookassa/webhook
Request:
```json
{
  "type": "notification",
  "event": "payment.succeeded",
  "object": {
    "id": "provider-payment-id",
    "status": "succeeded",
    "amount": {
      "value": "430.00",
      "currency": "RUB"
    },
    "metadata": {
      "local_payment_id": "uuid-from-payments-create",
      "client_id": "client-001",
      "rental_id": "rental-001",
      "payment_type": "day"
    }
  }
}
```

Response 200:
```json
{
  "applied": true,
  "message": "Payment applied",
  "payment_id": "uuid",
  "client_id": "client-001",
  "debt_rub": 1070
}
```

Notes:
- Backend принимает `payment.succeeded` и `payment.canceled`.
- Перед начислением backend проверяет актуальный статус платежа через API ЮKassa.
- Повторный webhook не создает вторую ledger-запись.
- Успешный платеж добавляется в ledger с конкретным `rental_id`.
