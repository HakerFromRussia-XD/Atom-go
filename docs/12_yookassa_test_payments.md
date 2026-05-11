# YooKassa Test Payments

## Зачем это нужно

Клиент нажимает `Оплатить` в iOS-приложении, backend создает тестовый платеж в ЮKassa, приложение открывает страницу оплаты, а деньги начисляются только после подтверждения от ЮKassa.

Секретный ключ ЮKassa хранится только на backend. В iOS он не попадает.

## Почему без Kotlin SDK

На странице SDK у ЮKassa официальными серверными SDK указаны PHP и Python. Java/Kotlin SDK находятся в community-разделе и не тестируются ЮKassa. Поэтому для нашего Kotlin/Ktor backend используется прямой официальный HTTP API, закрытый за интерфейсом `PaymentProvider`. Если позже появится официальный или проверенный Kotlin SDK, его можно будет заменить внутри provider-слоя без переписывания приложения.

## Настройка локального теста

1. Создай локальный env-файл рядом со скриптами:
```bash
cd "/Users/motoricallc/Documents/Codex prod/Atom go"
cp .atomgo_backend.env.example .atomgo_backend.env
```

2. Открой `.atomgo_backend.env` и впиши секрет тестового магазина:
```bash
YOOKASSA_SHOP_ID=1345724
YOOKASSA_SECRET_KEY=секрет_из_личного_кабинета
YOOKASSA_API_BASE=https://api.yookassa.ru/v3
YOOKASSA_PUBLIC_BASE_URL=https://replace-after-tunnel.trycloudflare.com
YOOKASSA_TEST_MODE=true
```

3. Запусти backend:
```bash
./start_backend.sh
```

4. Запусти публичный tunnel до локального backend:
```bash
./start_yookassa_tunnel.sh
```

5. Скрипт напечатает публичный URL. Скопируй строку вида:
```bash
YOOKASSA_PUBLIC_BASE_URL=https://example.trycloudflare.com
```

6. Вставь этот URL в `.atomgo_backend.env` и перезапусти backend:
```bash
./stop_backend.sh
./start_backend.sh
```

7. В тестовом магазине ЮKassa открой настройки HTTP-уведомлений и укажи:
```text
https://example.trycloudflare.com/api/v1/payments/yookassa/webhook
```

8. Подпишись минимум на события:
```text
payment.succeeded
payment.canceled
```

## Как проверить оплату в приложении

1. Зайди в iOS-приложение как тестовый клиент.
2. На клиентском экране выбери период оплаты.
3. Нажми `Оплатить`.
4. Приложение откроет страницу ЮKassa.
5. Проведи тестовый платеж.
6. Закрой страницу оплаты или вернись в приложение.
7. Приложение запросит `GET /api/v1/payments/{payment_id}` и обновит dashboard.

## Тестовые карты

- Успешная оплата без 3-D Secure: `5555 5555 5555 4444`.
- Успешная оплата с 3-D Secure: `5555 5555 5555 4477`.
- Недостаточно средств: `5555 5555 5555 4600`.
- Срок действия: любая будущая дата.
- CVC и 3-D Secure код: любые числа.

## Где смотреть результат

- В приложении: `Оплачено до`, `Долг`, `Всего оплачено`.
- В backend DB UI: `payments_view` и `ledger_view`.
- В кабинете ЮKassa: https://yookassa.ru/my/payments.

## Что важно

- Факт возврата со страницы оплаты не считается доказательством оплаты.
- Начисление происходит только после webhook или после backend-проверки статуса платежа через ЮKassa API.
- Повторный webhook не дублирует оплату.
- Платеж привязан к конкретной клиентской аренде через `client_rental_id`.
- Для завершенной клиентской аренды платежи разрешены только для погашения зафиксированного долга; новые начисления после даты завершения не создаются.

## Источники

- https://yookassa.ru/developers/using-api/using-sdks
- https://yookassa.ru/developers/payment-acceptance/getting-started/quick-start
- https://yookassa.ru/developers/using-api/interaction-format
- https://yookassa.ru/developers/using-api/webhooks
- https://yookassa.ru/developers/payment-acceptance/testing-and-going-live/testing
