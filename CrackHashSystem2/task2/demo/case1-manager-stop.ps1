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
    throw "Нет доступной Mongo ноды."
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
  { _id: 1, status: 1, completedParts: 1, matches: 1, partCount: 1, updatedAt: 1, createdAt: 1 }
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

function Show-RabbitQueues {
    Write-Host "`n--- RabbitMQ очереди ---" -ForegroundColor Cyan
    $out = & docker compose exec -T rabbitmq sh -lc "rabbitmqctl list_queues name messages_ready messages_unacknowledged messages durable"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Не удалось прочитать очереди" -ForegroundColor Yellow
        return
    }
    $out | Where-Object { $_ -match "name|crackhash\.tasks\.queue|crackhash\.results\.queue|crackhash\.cancel\.queue" } | ForEach-Object { Write-Host $_ }
}

Write-Host "=== кейс 1: стоп менеджера ===" -ForegroundColor Green

Invoke-Compose -Args @("ps")

Read-Host "`n1) Создайте задачу:"
$requestId = Get-LatestRequestId
Write-Host "Используется requestId: $requestId" -ForegroundColor Green

Show-RequestState -RequestId $requestId
Show-RabbitQueues

Read-Host "`n2) Остановить менэджера:"
Invoke-Compose -Args @("stop", "manager")
Write-Host "manager остановлен." -ForegroundColor Yellow

Show-RabbitQueues
Show-RequestState -RequestId $requestId

Read-Host "`n3) Запустить менеджера обратно:"
Invoke-Compose -Args @("up", "-d", "manager")
Start-Sleep -Seconds 8
Write-Host "manager запущен." -ForegroundColor Green

Show-RabbitQueues
Show-RequestState -RequestId $requestId

Write-Host "`nкейс 1 завершен." -ForegroundColor Green
