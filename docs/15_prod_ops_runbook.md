# Prod Ops Runbook (VPS, no Docker)

Текущий backend URL (dev/preprod):
- https://atomgo.157.22.203.6.nip.io

## 1) Базовые команды сервиса

```bash
sudo systemctl status atomgo-backend
sudo journalctl -u atomgo-backend -n 200 --no-pager
sudo systemctl restart atomgo-backend
```

## 2) Smoke-check после рестарта

```bash
curl -fsS https://atomgo.157.22.203.6.nip.io/health/ready
curl -fsS https://atomgo.157.22.203.6.nip.io/api/v1/health/ready || true
```

Дополнительно вручную в приложении:
- логин админа;
- экран аренд и клиентов;
- создание тестового платежа;
- проверка webhook статуса платежа.

## 3) Проверка webhook маршрута YooKassa

Проверяем что endpoint доступен и не 404:

```bash
curl -i https://atomgo.157.22.203.6.nip.io/api/v1/payments/yookassa/webhook
```

Ожидаемо для GET может быть 405/400 (это нормально), но не 404/502.

## 4) PostgreSQL backup

Ежедневный backup:
- `03:15` через root crontab
- скрипт: `/usr/local/bin/atomgo-db-backup.sh`
- папка: `/var/backups/atomgo`
- ротация: удалить старше 14 дней

Проверка:

```bash
sudo crontab -l
ls -lah /var/backups/atomgo
```

## 5) Rollback backend (без отката БД)

1. Остановить сервис:
```bash
sudo systemctl stop atomgo-backend
```

2. Вернуть предыдущий artifact (из заранее сохранённой копии `installDist` или из git-тега).

3. Запустить сервис:
```bash
sudo systemctl start atomgo-backend
sudo systemctl status atomgo-backend
```

4. Проверить smoke-check.

Важно:
- БД дамп назад НЕ откатывать автоматически.
- При инциденте платежей сначала откатываем только backend rollout.

## 6) Firewall expected state

Ожидается:
- allow: `22/tcp`, `80/tcp`, `443/tcp`
- deny: `8080/tcp`

Проверка:

```bash
sudo ufw status numbered
```

## 7) Переход с nip.io на постоянный домен

Когда появится домен:
1. DNS A-запись на IP сервера.
2. Обновить `server_name` в nginx.
3. Выпустить новый certbot сертификат.
4. Обновить:
   - `YOOKASSA_PUBLIC_BASE_URL`
   - mobile `ATOMGO_ENV=prod` target URL
   - webhook URL в YooKassa кабинете.
