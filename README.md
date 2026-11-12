# Helpdesk API

Учебный REST API техподдержки на Java 21, Spring Boot и PostgreSQL. Сотрудник создаёт заявку, инженер переводит её из `OPEN` в `IN_PROGRESS`, затем в `RESOLVED`. В ответе всегда есть история статусов.

## Запуск

Нужны JDK 21 и запущенный Docker с Compose. Переменная `JAVA_HOME` должна указывать на папку JDK. Запустите команды из папки проекта.

```sh
docker compose up -d --wait db
./mvnw spring-boot:run
```

В Windows вместо `./mvnw` используйте `.\mvnw.cmd`. API доступен на `http://localhost:8080`. Flyway создаёт таблицы при первом запуске. PostgreSQL слушает локальный порт `5432`, база, пользователь и пароль для локальной разработки — `helpdesk`. Подключение можно изменить через `DB_URL`, `DB_USER` и `DB_PASSWORD`. Заявки сохраняются в томе Docker между перезапусками.

## API

| Запрос | Результат |
| --- | --- |
| `POST /tickets` с `title` и `description` | `201`, заявка в статусе `OPEN` и ссылка в заголовке `Location` |
| `PATCH /tickets/{id}/status` с `status` | `200`, заявка с обновлённой историей |
| `GET /tickets/{id}` | `200`, заявка с историей |

Статус и событие сохраняются в одной транзакции, чтобы при сбое история всегда соответствовала текущему статусу. Блокировка строки позволяет проверить переход по актуальному статусу, даже если запросы пришли одновременно.

## Пример в PowerShell

```powershell
$base = 'http://localhost:8080'
$ticket = Invoke-RestMethod -Method Post -Uri "$base/tickets" -ContentType 'application/json' -Body '{"title":"GitLab access","description":"Access denied on sign-in"}'
$url = "$base/tickets/$($ticket.id)"
Invoke-RestMethod -Method Patch -Uri "$url/status" -ContentType 'application/json' -Body '{"status":"IN_PROGRESS"}'
Invoke-RestMethod -Method Patch -Uri "$url/status" -ContentType 'application/json' -Body '{"status":"RESOLVED"}'
Invoke-RestMethod -Uri $url | ConvertTo-Json -Depth 4
```

## Тесты

`./mvnw verify` или `.\mvnw.cmd verify`. Testcontainers запускает отдельную PostgreSQL, поэтому для тестов нужен работающий Docker. Базу из Compose для тестов поднимать не нужно.
