# CrackHash (Task 2: Fault Tolerance)

Распределенная система для brute-force подбора слова по MD5-хэшу.

- Пользователь взаимодействует с `manager` по JSON REST API.
- Внутренний обмен `manager <-> worker` идет через RabbitMQ в XML (JAXB + `MarshallingMessageConverter`).
- Состояние запросов и задач хранится в MongoDB replica set (1 primary + 2 secondary).

## Что реализовано

### Надежность менеджера

- Все запросы и подзадачи сохраняются в MongoDB (`hash_requests`, `hash_tasks`).
- Для записи Mongo включен `WriteConcern.MAJORITY`, поэтому менеджер подтверждает запрос после устойчивой записи.
- Если RabbitMQ недоступен, задачи остаются в БД в состоянии `PENDING_QUEUE` и переотправляются по расписанию.

### Надежность очереди

- RabbitMQ поднят в `docker-compose`.
- Очереди и exchange — durable.
- Сообщения отправляются как persistent (`deliveryMode=PERSISTENT`).
- После рестартов очереди сообщения сохраняются.

### Надежность воркеров

- Запущено 3 воркера (`worker1`, `worker2`, `worker3`).
- Воркеры читают общую очередь задач с `MANUAL` acknowledgement.
- `ack` выполняется только после успешной отправки результата в очередь результатов.
- При сбое обработки/отправки делается `nack(requeue=true)`, задача уходит на повторную обработку (в т.ч. другим воркером).

### Отмена задач

- Поддерживается отмена конкретной задачи и всех задач.
- Менеджер переводит задачу в terminal-статус и помечает незавершенные части как `CANCELED`.
- Команда отмены рассылается воркерам через отдельный cancel-exchange RabbitMQ (XML).

### Таймауты и статусы

- Задачи с превышением лимита переводятся в `TIMEOUT`.
- Доступны статусы: `IN_PROGRESS`, `READY`, `TIMEOUT`, `CANCELED`, `ERROR_NO_WORKERS`, `ERROR_DISPATCH`, `ERROR_WORKER_RESPONSE`, `ERROR_NOT_FOUND`.

## REST API менеджера (JSON)

### 1) Запуск подбора

`POST /api/hash/crack`

```json
{
  "hash": "e2fc714c4727ee9395f324cd2e7f331f",
  "maxLength": 4
}
```

Ответ:

```json
{
  "requestId": "730a04e6-4de9-41f9-9d5b-53b88b17afac"
}
```

### 2) Статус

`GET /api/hash/status?requestId=<uuid>`

Ответ (пример):

```json
{
  "status": "IN_PROGRESS",
  "data": null
}
```

или

```json
{
  "status": "READY",
  "data": ["abcd"]
}
```

### 3) Отмена конкретной задачи

`POST /api/hash/cancel?requestId=<uuid>`

### 4) Отмена всех активных задач

`POST /api/hash/cancel-all`

### 5) Генерация MD5

`POST /api/hash/generate`

```json
{
  "word": "abcd"
}
```

Ответ:

```json
{
  "hash": "e2fc714c4727ee9395f324cd2e7f331f"
}
```

## Веб-интерфейс

- Доступен по адресу: [http://localhost:8080](http://localhost:8080)
- Русский UI.
- История задач хранится в `localStorage` и не пропадает после перезагрузки страницы.
- Есть кнопка очистки истории.
- Автообновление статусов — каждые 1 секунду.

## Запуск через Docker Compose

```bash
docker compose up -d --build
```

Проверка контейнеров:

```bash
docker compose ps
```

## Состав docker-compose

- `manager`
- `worker1`, `worker2`, `worker3`
- `rabbitmq` (+ management UI на порту `15672`)
- `mongo1`, `mongo2`, `mongo3` (replica set `rs0`)
- `mongo-init` (инициализация replica set)