# Sprint 1 Execution Plan

## Goal
Подготовить инфраструктурно и технически рабочую основу для дальнейшей функциональной разработки.

## Tasks
1. Backend
- Поднять `backend/app` локально.
- Добавить JWT auth skeleton.
- Добавить миграции БД (Flyway).
- Реализовать `POST /auth/login` и `GET /health/*`.

2. Database
- Развернуть Managed PostgreSQL (dev).
- Применить схему `docs/05_db_schema.sql`.
- Добавить индексы и ограничения для idempotency.

3. Mobile KMM
- Добавить shared network layer:
  - Ktor client factory (Android/iOS)
  - auth API interface
  - error mapping
- Добавить экран логина Android + iOS контейнеры.
- Реализовать маршрутизацию по роли.

4. DevOps
- Подключить CI pipeline:
  - lint/build backend
  - build shared/android module
- Настроить секреты окружений.

## Done Criteria
- Локально запускается backend (`/health/live` отвечает 200).
- Миграции применяются к dev БД.
- Логин работает на тестовом admin аккаунте.
- Мобильный клиент получает токен и делает role-based navigation.
