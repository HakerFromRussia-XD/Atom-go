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
  "confirmation_url": "https://yookassa.ru/pay/uuid",
  "idempotence_key": "uuid",
  "status": "new"
}
```

## 5) POST /api/v1/payments/yookassa/webhook
Request:
```json
{
  "type": "notification",
  "event": "payment.succeeded",
  "object": {
    "id": "provider-payment-id",
    "status": "succeeded",
    "metadata": {
      "local_payment_id": "uuid-from-payments-create"
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
