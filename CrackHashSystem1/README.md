# CrackHash (Manager + 3 Workers)

Актуальная версия распределенной системы перебора MD5-хэша на Spring Boot.

## Архитектура

- Пользователь <-> Менеджер: **JSON/HTTP**
- Менеджер <-> Воркеры: **XML/HTTP** (JAXB-модели по XSD)
- Хранение состояния запросов: потокобезопасные коллекции в памяти менеджера
- Запуск: `docker-compose` с 1 менеджером и 3 воркерами

## Сервисы

- `manager` (порт `8080`)
  - публичное API для пользователя
  - внутренний endpoint для ответов воркеров
  - веб-интерфейс проверки
- `worker1`, `worker2`, `worker3`
  - обработка диапазонов перебора
  - кооперативная отмена активных задач

## Запуск

```bash
docker compose up --build -d
```

Веб-интерфейс: [http://localhost:8080](http://localhost:8080)

## Публичное API менеджера (JSON)

### 1) Запуск взлома

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

### 2) Получение статуса

`GET /api/hash/status?requestId=<uuid>`

Пример:

```json
{
  "status": "IN_PROGRESS",
  "data": null
}
```

### 3) Отмена конкретной задачи

`POST /api/hash/cancel?requestId=<uuid>`

Возвращает текущий статус задачи.

### 4) Отмена всех активных задач

`POST /api/hash/cancel-all`

Пример ответа:

```json
{
  "canceledCount": 2,
  "requestIds": [
    "19471ef4-308b-4732-a7ce-642c49015941",
    "9ebc5f53-3ddb-4990-b7cb-54ecdd702d07"
  ]
}
```

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

## Внутренние endpoint-ы (XML)

### Manager -> Worker

- `POST /internal/api/worker/hash/crack/task`
- `POST /internal/api/worker/hash/crack/cancel`

### Worker -> Manager

- `PATCH /internal/api/manager/hash/crack/request`

## Статусы запросов

- `IN_PROGRESS`
- `READY`
- `TIMEOUT`
- `CANCELED`
- `ERROR_NO_WORKERS`
- `ERROR_DISPATCH`
- `ERROR_WORKER_RESPONSE`
- `ERROR_NOT_FOUND`

## Бизнес-правила

- Алфавит перебора: `abcdefghijklmnopqrstuvwxyz0123456789`
- Пространство слов делится между воркерами по `partNumber/partCount`
- При `TIMEOUT` и любом `ERROR_*` менеджер автоматически отправляет отмену задачи на воркеры
- После отмены/ошибки/таймаута воркеры прекращают вычисления по задаче

## Веб-интерфейс

В интерфейсе доступны:

- генерация MD5
- запуск взлома
- просмотр статуса (автообновление каждую секунду)
- отмена конкретного запроса
- отмена всех активных запросов
- история задач с сохранением в `localStorage`
- очистка истории кнопкой

## Контрактные XSD

- `contract/src/main/resources/xsd/manager-worker-task.xsd`
- `contract/src/main/resources/xsd/worker-manager-result.xsd`
- `contract/src/main/resources/xsd/manager-worker-cancel.xsd`