# Backend Architecture (Chosen Variant: Yandex Cloud Managed)

## 1. Цель
- Надежный backend для Android/iOS.
- Централизованная бизнес-логика аренды, долга и платежей.
- Без хранения секретов ЮKassa в мобильном приложении.

## 2. Компоненты
- `API service`: Kotlin Ktor (container).
- `Managed PostgreSQL`: основная БД.
- `Object Storage`: если позже понадобятся вложения (сейчас в MVP храним ссылки).
- `Serverless Container / Managed container runtime`: запуск API.
- `API Gateway / Load Balancer`: публичная точка входа.
- `Container Registry`: хранение образов.
- `Observability`: Cloud Logging + Metrics + Alerts.
- `CI/CD`: GitHub Actions (build/test/deploy).

## 3. Почему не только Firebase
Firebase может закрыть:
- аутентификацию
- хранение комментариев/документов

Но для MVP все равно нужен backend из-за:
- защищенной интеграции с ЮKassa (secret key + idempotence + webhooks)
- централизованного расчета долга и корректировок
- аудита финансовых событий

Итог: можно использовать Firebase как вспомогательный сервис, но не как полную замену backend в этой предметной области.

## 4. Сервисы API
- `AuthService`:
  - логин по логину/паролю
  - выдача access/refresh токенов
- `ClientService`:
  - CRUD клиентов
  - привязка велосипедов
  - получение списка/карточки
- `RentService`:
  - создание/закрытие аренд
  - периодические начисления
- `PaymentService`:
  - создание платежа в ЮKassa
  - прием webhook
  - фиксация платежей в ledger
- `DebtService`:
  - баланс клиента
  - корректировки

## 5. Безопасность
- Пароли: Argon2id hash + salt.
- JWT access token (short TTL) + refresh token.
- TLS only.
- Ролевая модель `admin/client`.
- Idempotency для критических операций оплаты.

## 6. Развертывание (по шагам)
1. Создать облачную инфраструктуру (VPC, PostgreSQL, Registry, runtime).
2. Настроить секреты (DB URL, JWT keys, YooKassa keys).
3. Поднять `dev` окружение.
4. Подключить CI/CD pipeline.
5. Подключить мониторинг и алерты.
6. Поднять `prod` после пилота.
