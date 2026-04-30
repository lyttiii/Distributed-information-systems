Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

function Invoke-Compose {
    param(
        [Parameter(Mandatory = $true)][string[]]$Args,
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
        throw "Не удалось прочитать список запущенных сервисов docker compose"
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

    throw "Нет доступной ноды Mongo"
}

function Invoke-MongoEval {
    param([Parameter(Mandatory = $true)][string]$Js)
    $svc = Get-MongoService
    $out = & docker compose exec -T $svc mongosh --quiet --eval $Js
    if ($LASTEXITCODE -ne 0) {
        throw "Команда mongosh завершилась с ошибкой на $svc."
    }
    return ($out | Out-String).Trim()
}

function Get-LatestRequestId {
    $js = @"
const cur = db.getSiblingDB('crackhash').hash_requests.find().sort({createdAt: -1}).limit(1);
if (cur.hasNext()) print(cur.next()._id);
"@
    $id = Invoke-MongoEval -Js $js
    if ([string]::IsNullOrWhiteSpace($id)) {
        throw "В MongoDB не найдено ни одного запроса"
    }
    return (($id -split "`r?`n")[-1]).Trim()
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

function Get-RequestSnapshot {
    param([Parameter(Mandatory = $true)][string]$RequestId)

    $js = @"
const id = '$RequestId';
const req = db.getSiblingDB('crackhash').hash_requests.findOne(
  { _id: id },
  { _id: 1, status: 1, completedParts: 1, partCount: 1, updatedAt: 1 }
);
if (!req) {
  print(JSON.stringify({ exists: false }));
} else {
  const states = db.getSiblingDB('crackhash').hash_tasks.aggregate([
    { `$match: { requestId: id } },
    { `$group: { _id: '`$state', count: { `$sum: 1 } } }
  ]).toArray();

  const map = {};
  states.forEach(s => map[s._id] = s.count);

  print(JSON.stringify({
    exists: true,
    requestId: req._id,
    status: req.status,
    completedParts: (req.completedParts || []).length,
    partCount: req.partCount || 0,
    doneTasks: map['DONE'] || 0,
    queuedTasks: map['QUEUED'] || 0,
    pendingQueueTasks: map['PENDING_QUEUE'] || 0,
    canceledTasks: map['CANCELED'] || 0,
    updatedAt: req.updatedAt
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

function Show-RabbitQueues {
    Write-Host "`n--- Очереди RabbitMQ ---" -ForegroundColor Cyan
    $out = & docker compose exec -T rabbitmq sh -lc "rabbitmqctl list_queues name messages_ready messages_unacknowledged messages consumers durable"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "RabbitMQ недоступен." -ForegroundColor Yellow
        return
    }
    $out | Where-Object { $_ -match "name|crackhash\.tasks\.queue|crackhash\.results\.queue|crackhash\.cancel\.queue" } | ForEach-Object { Write-Host $_ }
}

function Show-RequestState {
    param([Parameter(Mandatory = $true)][string]$RequestId)

    $snap = Get-RequestSnapshot -RequestId $RequestId
    if ($null -eq $snap -or -not $snap.exists) {
        Write-Host ("`nЗапрос {0} не найден в MongoDB" -f $RequestId) -ForegroundColor Yellow
        return
    }

    Write-Host "`n--- запрос ---" -ForegroundColor Cyan
    Write-Host ("requestId={0}" -f $snap.requestId)
    Write-Host ("status={0}" -f $snap.status)
    Write-Host ("completedParts={0}/{1}" -f $snap.completedParts, $snap.partCount)
    Write-Host ("tasks: DONE={0}, QUEUED={1}, PENDING_QUEUE={2}, CANCELED={3}" -f $snap.doneTasks, $snap.queuedTasks, $snap.pendingQueueTasks, $snap.canceledTasks)
    Write-Host ("updatedAt={0}" -f $snap.updatedAt)
}

function Wait-ForSingleWorkerConsumer {
    param([int]$TimeoutSeconds = 120)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        $q = Get-TaskQueueStats
        if ($null -ne $q) {
            Write-Host ("ожидание consumer {0}: ready={1}, unacked={2}, consumers={3}" -f $attempt, $q.Ready, $q.Unacked, $q.Consumers)
            if ($q.Consumers -eq 1) {
                return $q
            }
        } else {
            Write-Host ("ожидание consumer {0}: статистика очереди недоступна" -f $attempt)
        }

        if ($attempt -eq 10) {
            Write-Host "Перезапуск worker1, чтобы восстановить consumer.." -ForegroundColor Yellow
            Invoke-Compose -Args @("restart", "worker1")
        }
        Start-Sleep -Seconds 2
    }
    return $null
}

function Wait-ForActiveProcessing {
    param(
        [Parameter(Mandatory = $true)][string]$RequestId,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        $q = Get-TaskQueueStats
        $s = Get-RequestSnapshot -RequestId $RequestId

        if ($null -ne $q -and $null -ne $s -and $s.exists) {
            Write-Host ("ожидание обработки {0}: ready={1}, unacked={2}, consumers={3}; done={4}, queued={5}, status={6}" -f $attempt, $q.Ready, $q.Unacked, $q.Consumers, $s.doneTasks, $s.queuedTasks, $s.status)
            $active = ($q.Consumers -ge 1 -and $q.Unacked -gt 0)
            $alreadyProgressing = ($s.status -eq "IN_PROGRESS" -and $s.doneTasks -gt 0 -and $s.queuedTasks -gt 0)
            if ($active -or $alreadyProgressing) {
                return [PSCustomObject]@{ Queue = $q; Request = $s }
            }
        }

        if ($attempt -eq 20 -and $null -ne $q -and $q.Consumers -eq 0) {
            Write-Host "Consumer не обнаружен, перезапуск worker1.." -ForegroundColor Yellow
            Invoke-Compose -Args @("restart", "worker1")
        }

        Start-Sleep -Seconds 2
    }
    return $null
}

function Wait-ForTakeover {
    param(
        [Parameter(Mandatory = $true)][string]$RequestId,
        [Parameter(Mandatory = $true)][int]$BaselineCompletedParts,
        [Parameter(Mandatory = $true)][int]$BaselineDoneTasks,
        [Parameter(Mandatory = $true)][int]$BaselineQueuedTasks,
        [int]$TimeoutSeconds = 240
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        $q = Get-TaskQueueStats
        $s = Get-RequestSnapshot -RequestId $RequestId

        if ($null -ne $q -and $null -ne $s -and $s.exists) {
            Write-Host ("ожидание перехвата {0}: ready={1}, unacked={2}, consumers={3}; completed={4}/{5}, done={6}, queued={7}, status={8}" -f $attempt, $q.Ready, $q.Unacked, $q.Consumers, $s.completedParts, $s.partCount, $s.doneTasks, $s.queuedTasks, $s.status)

            $hasConsumers = $q.Consumers -ge 1
            $progress = ($s.completedParts -gt $BaselineCompletedParts) -or ($s.doneTasks -gt $BaselineDoneTasks) -or ($s.queuedTasks -lt $BaselineQueuedTasks) -or ($s.status -ne "IN_PROGRESS")
            if ($hasConsumers -and $progress) {
                return [PSCustomObject]@{ Queue = $q; Request = $s }
            }

            if ($s.status -eq "READY") {
                return [PSCustomObject]@{ Queue = $q; Request = $s }
            }
        }

        Start-Sleep -Seconds 3
    }
    return $null
}

function Wait-ForFinalStatus {
    param(
        [Parameter(Mandatory = $true)][string]$RequestId,
        [int]$TimeoutSeconds = 600
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        $s = Get-RequestSnapshot -RequestId $RequestId
        $q = Get-TaskQueueStats

        if ($null -ne $s -and $s.exists) {
            if ($null -ne $q) {
                Write-Host ("ожидание финала {0}: status={1}, completed={2}/{3}, done={4}, queued={5}, qReady={6}, qUnacked={7}, consumers={8}" -f $attempt, $s.status, $s.completedParts, $s.partCount, $s.doneTasks, $s.queuedTasks, $q.Ready, $q.Unacked, $q.Consumers)
            } else {
                Write-Host ("ожидание финала {0}: status={1}, completed={2}/{3}, done={4}, queued={5}" -f $attempt, $s.status, $s.completedParts, $s.partCount, $s.doneTasks, $s.queuedTasks)
            }

            if ($s.status -eq "READY") {
                return $s
            }
            if ($s.status -ne "IN_PROGRESS") {
                return $s
            }
        } else {
            Write-Host ("ожидание финала {0}: снимок запроса недоступен" -f $attempt)
        }

        Start-Sleep -Seconds 3
    }

    return $null
}

Write-Host "=== кейс 4: падение воркера во время обработки ===" -ForegroundColor Green

Invoke-Compose -Args @("up", "-d", "manager", "rabbitmq", "mongo1", "mongo2", "mongo3", "worker1", "worker2", "worker3")
Invoke-Compose -Args @("ps")

Read-Host "`n1) остановить worker2 и worker3 (оставить только worker1)"
Invoke-Compose -Args @("stop", "worker2", "worker3")
Invoke-Compose -Args @("up", "-d", "worker1")

$singleConsumer = Wait-ForSingleWorkerConsumer -TimeoutSeconds 120
if ($null -eq $singleConsumer) {
    throw "Не удалось получить consumers=1 для crackhash.tasks.queue. Проверьте worker1 и rabbitmq"
}

Read-Host "`n2) Создайте задачу "
$requestId = Get-LatestRequestId
Write-Host ("Используется requestId: {0}" -f $requestId) -ForegroundColor Green
Show-RequestState -RequestId $requestId

Write-Host "`nОжидание активной обработки на worker1.." -ForegroundColor Yellow
$active = Wait-ForActiveProcessing -RequestId $requestId -TimeoutSeconds 180
if ($null -eq $active) {
    throw "Не удалось поймать фазу активной обработки. Попробуйте более тяжелую задачу и запустите снова"
}

$beforeQ = $active.Queue
$beforeS = $active.Request
Write-Host ("`nСнимок ДО остановки worker1: ready={0}, unacked={1}, consumers={2}; completed={3}/{4}, done={5}, queued={6}" -f $beforeQ.Ready, $beforeQ.Unacked, $beforeQ.Consumers, $beforeS.completedParts, $beforeS.partCount, $beforeS.doneTasks, $beforeS.queuedTasks) -ForegroundColor Cyan
Show-RabbitQueues

Read-Host "`n3) остановить worker1 во время обработки"
Invoke-Compose -Args @("stop", "worker1")
Write-Host "worker1 остановлен." -ForegroundColor Yellow
Show-RabbitQueues

Read-Host "`n4) запустить worker2 и worker3 для перехвата задач"
Invoke-Compose -Args @("up", "-d", "worker2", "worker3")

$takeover = Wait-ForTakeover -RequestId $requestId -BaselineCompletedParts $beforeS.completedParts -BaselineDoneTasks $beforeS.doneTasks -BaselineQueuedTasks $beforeS.queuedTasks -TimeoutSeconds 240
if ($null -ne $takeover) {
    Write-Host "После падения worker1 обработка продолжилась на других воркерах, задача не потерялась" -ForegroundColor Green
} else {
    Write-Host "Перехват не удалось зафиксировать в отведенное время" -ForegroundColor Yellow
}

Show-RabbitQueues
Show-RequestState -RequestId $requestId

$waitAnswer = Read-Host "`nДождаться статуса READY?"
if ($waitAnswer -match "^(y|Y)$") {
    $final = Wait-ForFinalStatus -RequestId $requestId -TimeoutSeconds 600
    if ($null -eq $final) {
        Write-Host "Истек таймаут ожидания финального статуса. Запрос все еще в работе" -ForegroundColor Yellow
    } elseif ($final.status -eq "READY") {
        Write-Host "запрос достиг статуса READY" -ForegroundColor Green
    } else {
        Write-Host ("Запрос завершился со статусом {0}" -f $final.status) -ForegroundColor Yellow
    }
    Show-RabbitQueues
    Show-RequestState -RequestId $requestId
}

$answer = Read-Host "`nЗапустить worker1 обратно? (y/n)"
if ($answer -match "^(y|Y)$") {
    Invoke-Compose -Args @("up", "-d", "worker1")
    Write-Host "worker1 запущен" -ForegroundColor Green
}

Write-Host "`nкейс 4 завершен." -ForegroundColor Green
