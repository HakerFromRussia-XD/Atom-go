# JSON Contracts (v2 draft)

Контракты обновлены под `Аренда + клиентская аренда` из `docs/14_rental_lifecycle.md`.

## 1) POST /api/v1/auth/login

Request:
```json
{
  "login": "client-rental-login",
  "password": "strong-password"
}
```

Response 200:
```json
{
  "access_token": "token-value",
  "role": "client",
  "user_id": "client-rental-001",
  "client_rental_id": "client-rental-001"
}
```

Notes:
- Клиентский логин относится к конкретной `client_rental`.
- Логин закрытой `client_rental` продолжает работать для просмотра истории и оплаты долга.

## 2) GET /api/v1/client/me/dashboard

Header:
`Authorization: Bearer <access_token>`

Response 200 for active client rental:
```json
{
  "client_rental_id": "client-rental-001",
  "status": "active",
  "client_id": "client-001",
  "bike_model": "Ninebot E-Bike Pro",
  "bike_avatar_url": "https://example.com/bikes/ninebot-pro.png",
  "rental_start": "2026-04-12",
  "rental_end": null,
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

Response 200 for completed client rental with debt:
```json
{
  "client_rental_id": "client-rental-001",
  "status": "completed",
  "client_id": "client-001",
  "bike_model": "Ninebot E-Bike Pro",
  "bike_avatar_url": "https://example.com/bikes/ninebot-pro.png",
  "rental_start": "2026-05-04",
  "rental_end": "2026-05-13",
  "paid_until": "2026-05-11",
  "debt_rub": 1000,
  "balance_rub": 0,
  "total_adjustment_rub": 0,
  "presets": {
    "day_rub": 500,
    "week_rub": 3500,
    "two_weeks_rub": 7000,
    "month_rub": 14000,
    "debt_exact_rub": 1000
  }
}
```

Notes:
- Для `status = completed` новые начисления не создаются.
- Оплаты уменьшают долг этой `client_rental`.

## 3) GET /api/v1/admin/rentals

Header:
`Authorization: Bearer <admin_token>`

Response 200:
```json
[
  {
    "rental_id": "rental-001",
    "bike_id": "bike-001",
    "bike_model": "Ninebot E-Bike Pro",
    "bike_avatar_url": "https://example.com/bikes/ninebot-pro.png",
    "weekly_rate_rub": 3500,
    "pipeline_status": "long_term",
    "current_client_rental_id": "client-rental-001",
    "client_id": "client-001",
    "full_name": "Иван Петров",
    "status_text": "Оплачено на 4 дня",
    "debt_rub": 0,
    "profit_rub": 3500,
    "total_adjustment_rub": -1000
  },
  {
    "rental_id": "rental-002",
    "bike_id": "bike-002",
    "bike_model": "Aventon Level 2",
    "bike_avatar_url": "",
    "weekly_rate_rub": 2600,
    "pipeline_status": "in_stock",
    "current_client_rental_id": null,
    "client_id": null,
    "full_name": null,
    "status_text": "У меня",
    "debt_rub": null,
    "profit_rub": null,
    "total_adjustment_rub": null
  }
]
```

## 4) GET /api/v1/admin/rentals/{rentalId}

Response 200:
```json
{
  "rental_id": "rental-001",
  "bike_id": "bike-001",
  "bike_model": "Ninebot E-Bike Pro",
  "bike_avatar_url": "https://example.com/bikes/ninebot-pro.png",
  "weekly_rate_rub": 3500,
  "pipeline_status": "in_stock",
  "current_client_rental": null,
  "next_login": "client1",
  "next_password_is_set": false,
  "metrics": null,
  "journal_entries": []
}
```

Notes:
- В `in_stock` статистика и журнал текущей аренды отвязаны от карточки `Аренды`. Пользовательская подпись статуса - "У меня".
- Для активных статусов `current_client_rental`, `metrics` и `journal_entries` заполнены.

## 5) POST /api/v1/admin/rentals/{rentalId}/client-rentals

Request:
```json
{
  "client_id": "client-002",
  "login": "client2",
  "password": "new-strong-password",
  "period_start": "2026-05-11"
}
```

Response 200:
```json
{
  "rental_id": "rental-001",
  "pipeline_status": "long_term",
  "client_rental_id": "client-rental-002",
  "client_id": "client-002"
}
```

Notes:
- Endpoint не создает новую `rental`.
- Он создает новую `client_rental` внутри существующей `rental`.

## 6) POST /api/v1/payments/create

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
  "client_rental_id": "client-rental-001",
  "amount_rub": 430,
  "confirmation_url": "https://yoomoney.ru/checkout/payments/v2/contract?orderId=...",
  "idempotence_key": "uuid",
  "status": "pending"
}
```

Notes:
- `payment_type`: `day`, `week`, `two_weeks`, `month`, `debt_exact`.
- Backend берет `client_rental_id` из токена клиента.
- Для завершенной `client_rental` разрешается оплачивать только долг.
- Деньги в ledger не попадают в момент создания платежа.

## 7) GET /api/v1/payments/{payment_id}

Header:
`Authorization: Bearer <client_token | admin_token>`

Response 200:
```json
{
  "payment_id": "uuid",
  "client_rental_id": "client-rental-001",
  "amount_rub": 430,
  "confirmation_url": "https://yoomoney.ru/checkout/payments/v2/contract?orderId=...",
  "provider_payment_id": "2f4f...",
  "status": "succeeded",
  "debt_rub": 0
}
```

## 8) POST /api/v1/payments/yookassa/webhook

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
      "client_rental_id": "client-rental-001",
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
  "client_rental_id": "client-rental-001",
  "debt_rub": 1070
}
```

Notes:
- Повторный webhook не создает вторую ledger-запись.
- Успешный платеж добавляется в ledger с конкретным `client_rental_id`.
