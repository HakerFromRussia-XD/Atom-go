# Mobile (KMM)

Папка для мобильной части (Android + iOS) на базе Kotlin Multiplatform.

План структуры:
- `shared/` общая бизнес-логика, модели, сеть, репозитории
- `androidApp/` Android клиент
- `iosApp/` iOS клиент

На следующем шаге здесь будет создан технический каркас модулей.

## Backend Switch (One Flag)

Единый источник настроек: `mobile/iosApp/AtomGoIOS/BackendSwitch.properties`.

- `ATOMGO_ENV=local` — локальный backend.
- `ATOMGO_ENV=prod` — production backend (`https://atomgo.157.22.203.6.nip.io/api/v1`).

### Где переключать

Меняйте только одну строку в `BackendSwitch.properties`:

```text
ATOMGO_ENV=prod
```

или

```text
ATOMGO_ENV=local
```

### Android (override при необходимости)

Можно временно переопределить через Gradle property:

```bash
./gradlew :mobile:androidApp:assembleDebug -PatomgoEnv=prod
```

Опционально можно задать полный URL напрямую:

```bash
./gradlew :mobile:androidApp:assembleDebug -PatomgoBackendUrl=https://your-url/api/v1
```

### iOS (override при необходимости)

Можно временно переопределить env var в Scheme:

```text
ATOMGO_ENV=prod
```

Приоритет для iOS такой:
1. `ATOMGO_BACKEND_URL` (если задан)
2. `ATOMGO_ENV` (`prod`/`local`)
3. `ATOMGO_ENV` из `BackendSwitch.properties`
