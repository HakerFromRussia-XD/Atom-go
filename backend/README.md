# Backend

Папка для backend API (Ktor) и интеграции с YooKassa.

План:
- `app/` исходники сервиса
- `openapi/` спецификация API

MVP-фокус:
- auth
- clients/rentals
- debt ledger
- yookassa payment flow + webhooks

## Хранение состояния

Backend использует PostgreSQL для постоянного хранения состояния:
- клиенты
- аренды
- ledger платежей/корректировок
- платежи ЮKassa
- обработанные webhook-события (идемпотентность)
- сессии

При каждом изменении данных backend сразу сохраняет состояние в БД до отправки ответа.
Это позволяет переживать внезапные перезапуски процесса backend без потери уже подтвержденных изменений.

### Переменные окружения

- `ATOMGO_DB_URL` (по умолчанию `jdbc:postgresql://127.0.0.1:5432/atomgo`)
- `ATOMGO_DB_USER` (по умолчанию `atomgo`)
- `ATOMGO_DB_PASSWORD` (по умолчанию `atomgo`)

Для unit/integration тестов включён режим in-memory через `ATOMGO_USE_INMEMORY=true`.
