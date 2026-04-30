Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Args,
        [switch]$AllowFail
    )
    & docker compose @Args
    if (-not $AllowFail -and $LASTEXITCODE -ne 0) {
        throw "docker compose $($Args -join ' ') завершился с ошибкой"
    }
}

function Get-MongoService {
    $runningRaw = & docker compose ps --services --status running
    if ($LASTEXITCODE -ne 0) {
        throw "Не удалось получить список запущенных сервисов docker compose"
    }

    $running = @($runningRaw | ForEach-Object { $_.Trim() }) | Where-Object { $_ }
    foreach ($svc in @("mongo1", "mongo2", "mongo3")) {
        if ($running -notcontains $svc) {
            continue
        }

        try {
            $null = & docker compose exec -T $svc mongosh --quiet --eval "db.adminCommand('ping').ok" 2>$null
            if ($LASTEXITCODE -eq 0) {
                return $svc
            }
        } catch {
            continue
        }
    }
    throw "Нет доступной Mongo ноды"
}

function Invoke-MongoEval {
    param([Parameter(Mandatory = $true)][string]$Js)
    $svc = Get-MongoService
    $out = & docker compose exec -T $svc mongosh --quiet --eval $Js
    if ($LASTEXITCODE -ne 0) {
        throw "Ошибка выполнения mongosh на $svc."
    }
    return ($out | Out-String).Trim()
}

function Get-LatestRequestId {
    $js = @"
const cur = db.getSiblingDB('crackhash').hash_requests.find().sort({createdAt: -1}).limit(1);
if (cur.hasNext()) { print(cur.next()._id); }
"@
    $id = Invoke-MongoEval -Js $js
    if ([string]::IsNullOrWhiteSpace($id)) {
        throw "Не найдено ни одного запроса в MongoDB. Сначала создайте задачу"
    }
    return (($id -split "`r?`n")[-1]).Trim()
}

function Show-RequestState {
    param([Parameter(Mandatory = $true)][string]$RequestId)

    Write-Host "`n--- hash_requests ---" -ForegroundColor Cyan
    $jsReq = @"
const id = '$RequestId';
const doc = db.getSiblingDB('crackhash').hash_requests.findOne(
  { _id: id },
  { _id: 1, status: 1, completedParts: 1, matches: 1, partCount: 1, updatedAt: 1 }
);
printjson(doc);
"@
    Write-Host (Invoke-MongoEval -Js $jsReq)

    Write-Host "`n--- hash_tasks (group by state) ---" -ForegroundColor Cyan
    $jsTasks = @"
const id = '$RequestId';
const states = db.getSiblingDB('crackhash').hash_tasks.aggregate([
  { `$match: { requestId: id } },
  { `$group: { _id: '`$state', count: { `$sum: 1 } } },
  { `$sort: { _id: 1 } }
]).toArray();
printjson(states);
"@
    Write-Host (Invoke-MongoEval -Js $jsTasks)
}

function Get-RequestProgress {
    param([Parameter(Mandatory = $true)][string]$RequestId)

    $js = @"
const id = '$RequestId';
const d = db.getSiblingDB('crackhash').hash_requests.findOne(
  { _id: id },
  { status: 1, completedParts: 1, partCount: 1 }
);
if (!d) {
  print(JSON.stringify({ exists: false }));
} else {
  print(JSON.stringify({
    exists: true,
    status: d.status,
    completed: (d.completedParts || []).length,
    partCount: d.partCount || 0
  }));
}
"@

    $raw = Invoke-MongoEval -Js $js
    $line = (($raw -split "`r?`n") | Where-Object { $_.Trim() -ne "" } | Select-Object -Last 1)
    if ([string]::IsNullOrWhiteSpace($line)) {
        return $null
    }
    try {
        return ($line | ConvertFrom-Json)
    } catch {
        return $null
    }
}

function Get-TaskQueueStats {
    $out = & docker compose exec -T rabbitmq sh -lc "rabbitmqctl list_queues --no-table-headers name messages_ready messages_unacknowledged messages consumers"
    if ($LASTEXITCODE -ne 0) {
        return $null
    }

    $line = $out | Where-Object { $_ -match "^crackhash\.tasks\.queue\s+" } | Select-Object -First 1
    if (-not $line) {
        return $null
    }

    $parts = ($line -split "\s+") | Where-Object { $_ -ne "" }
    if ($parts.Count -lt 5) {
        return $null
    }

    return [PSCustomObject]@{
        Name      = $parts[0]
        Ready     = [int]$parts[1]
        Unacked   = [int]$parts[2]
        Total     = [int]$parts[3]
        Consumers = [int]$parts[4]
    }
}

function Show-RabbitQueues {
    Write-Host "`n--- RabbitMQ очереди ---" -ForegroundColor Cyan
    $out = & docker compose exec -T rabbitmq sh -lc "rabbitmqctl list_queues name messages_ready messages_unacknowledged messages consumers durable"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "RabbitMQ недоступен" -ForegroundColor Yellow
        return
    }
    $out | Where-Object { $_ -match "name|crackhash\.tasks\.queue|crackhash\.results\.queue|crackhash\.cancel\.queue" } | ForEach-Object { Write-Host $_ }
}

function Wait-ForRabbitReady {
    param([int]$TimeoutSeconds = 90)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        $null = & docker compose exec -T rabbitmq sh -lc "rabbitmq-diagnostics -q ping >/dev/null 2>&1 && rabbitmqctl list_queues --no-table-headers name >/dev/null 2>&1"
        if ($LASTEXITCODE -eq 0) {
            Write-Host "RabbitMQ готов (attempt $attempt)." -ForegroundColor Green
            return $true
        }
        Write-Host "Ожидание готовности RabbitMQ (attempt $attempt).." -ForegroundColor Yellow
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-ForConsumers {
    param([int]$TimeoutSeconds = 120)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        $stats = Get-TaskQueueStats
        if ($null -ne $stats) {
            Write-Host ("consumers check {0}: ready={1}, unacked={2}, consumers={3}" -f $attempt, $stats.Ready, $stats.Unacked, $stats.Consumers)
            if ($stats.Consumers -ge 1) {
                return $true
            }
        } else {
            Write-Host ("consumers check {0}: queue stats unavailable" -f $attempt)
        }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Wait-ForProcessingProgress {
    param(
        [Parameter(Mandatory = $true)][string]$RequestId,
        [Parameter(Mandatory = $true)][int]$InitialReady,
        [Parameter(Mandatory = $true)][int]$InitialCompleted,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        $stats = Get-TaskQueueStats
        $progress = Get-RequestProgress -RequestId $RequestId

        $ready = if ($null -ne $stats) { $stats.Ready } else { -1 }
        $unacked = if ($null -ne $stats) { $stats.Unacked } else { -1 }
        $consumers = if ($null -ne $stats) { $stats.Consumers } else { -1 }
        $completed = if ($null -ne $progress) { [int]$progress.completed } else { -1 }
        $partCount = if ($null -ne $progress) { [int]$progress.partCount } else { -1 }
        $status = if ($null -ne $progress) { [string]$progress.status } else { "unknown" }

        Write-Host ("progress {0}: queueReady={1}, queueUnacked={2}, consumers={3}, completedParts={4}/{5}, status={6}" -f $attempt, $ready, $unacked, $consumers, $completed, $partCount, $status)

        if (($ready -ge 0 -and $ready -lt $InitialReady) -or ($completed -gt $InitialCompleted) -or ($status -ne "IN_PROGRESS")) {
            return $true
        }

        Start-Sleep -Seconds 3
    }
    return $false
}

Write-Host "=== кейс 3: остановка RabbitMQ ===" -ForegroundColor Green

Invoke-Compose -Args @("ps")

Read-Host "`n1) Остановить всех воркеров"
Invoke-Compose -Args @("stop", "worker1", "worker2", "worker3")
Write-Host "Воркеры остановлены." -ForegroundColor Yellow

Read-Host "`n2) Создайте новую задачу"
$requestId = Get-LatestRequestId
Write-Host "Используется requestId: $requestId" -ForegroundColor Green

$progressBefore = Get-RequestProgress -RequestId $requestId
$initialCompleted = if ($null -ne $progressBefore) { [int]$progressBefore.completed } else { 0 }

Show-RabbitQueues
Show-RequestState -RequestId $requestId

$beforeStopStats = Get-TaskQueueStats
if ($null -eq $beforeStopStats) {
    throw "Не удалось прочитать crackhash.tasks.queue до остановки RabbitMQ"
}
Write-Host ("`nСнимок до stop RabbitMQ: ready={0}, unacked={1}, total={2}, consumers={3}" -f $beforeStopStats.Ready, $beforeStopStats.Unacked, $beforeStopStats.Total, $beforeStopStats.Consumers) -ForegroundColor Cyan

Read-Host "`n3) Остановить RabbitMQ"
Invoke-Compose -Args @("stop", "rabbitmq")
Write-Host "RabbitMQ остановлен" -ForegroundColor Yellow

Show-RequestState -RequestId $requestId

Read-Host "`n4) Запустить RabbitMQ обратно"
Invoke-Compose -Args @("up", "-d", "rabbitmq")

if (-not (Wait-ForRabbitReady -TimeoutSeconds 90)) {
    throw "RabbitMQ не стал готов в отведенное время"
}

$afterRestartStats = Get-TaskQueueStats
if ($null -eq $afterRestartStats) {
    throw "Не удалось прочитать crackhash.tasks.queue после рестарта RabbitMQ"
}
Write-Host ("Снимок после рестарта RabbitMQ: ready={0}, unacked={1}, total={2}, consumers={3}" -f $afterRestartStats.Ready, $afterRestartStats.Unacked, $afterRestartStats.Total, $afterRestartStats.Consumers) -ForegroundColor Cyan
if ($afterRestartStats.Total -ge $beforeStopStats.Total) {
    Write-Host "Сообщения не потерялись после рестарта RabbitMQ (total не уменьшился)." -ForegroundColor Green
} else {
    Write-Host "total уменьшился, надо проверить логи или состояние очереди" -ForegroundColor Yellow
}
Show-RabbitQueues

Read-Host "`n5) Запустить воркеры обратно"
Invoke-Compose -Args @("up", "-d", "worker1", "worker2", "worker3")

if (-not (Wait-ForConsumers -TimeoutSeconds 120)) {
    Write-Host "Не дождались consumers на tasks queue. Проверьте логи worker1/2/3." -ForegroundColor Yellow
}

$progressDetected = Wait-ForProcessingProgress -RequestId $requestId -InitialReady $afterRestartStats.Ready -InitialCompleted $initialCompleted -TimeoutSeconds 180
if ($progressDetected) {
    Write-Host "После запуска воркеров обработка продолжилась." -ForegroundColor Green
} else {
    Write-Host "Прогресс не зафиксирован в отведенное время. Возможно, задача слишком тяжелая или воркеры не готовы." -ForegroundColor Yellow
}

Show-RabbitQueues
Show-RequestState -RequestId $requestId

Write-Host "`nкейс 3 завершен." -ForegroundColor Green
