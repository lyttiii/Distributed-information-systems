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

function Show-ReplicaStatus {
    Write-Host "`n--- rs.status() ---" -ForegroundColor Cyan
    $js = @"
rs.status().members.forEach(m => print(m.name + ' | ' + m.stateStr + ' | health=' + m.health));
"@
    Write-Host (Invoke-MongoEval -Js $js)
}

function Get-PrimaryServiceName {
    $js = @"
const p = rs.status().members.find(m => m.stateStr === 'PRIMARY');
if (p) { print(p.name.split(':')[0]); }
"@
    $primary = Invoke-MongoEval -Js $js
    if ([string]::IsNullOrWhiteSpace($primary)) {
        throw "Не удалось определить PRIMARY."
    }
    return (($primary -split "`r?`n")[-1]).Trim()
}

function Show-MongoRead {
    Write-Host "`n--- Mongo read check ---" -ForegroundColor Cyan
    $js = @"
const count = db.getSiblingDB('crackhash').hash_requests.countDocuments({});
print('hash_requests.count=' + count);
"@
    Write-Host (Invoke-MongoEval -Js $js)
}

function Get-RequestsCount {
    $js = @"
print(db.getSiblingDB('crackhash').hash_requests.countDocuments({}));
"@
    $value = Invoke-MongoEval -Js $js
    return [int](($value -split "`r?`n")[-1].Trim())
}

function Show-ManagerWorkCheck {
    Write-Host "`n--- Проверка работы после смены ноды ---" -ForegroundColor Cyan

    $beforeCount = Get-RequestsCount
    Write-Host "hash_requests до запроса: $beforeCount"

    $hash = "6b3ca2bfbb04937d0f8154ade0cf0c01"
    $createBody = @{ hash = $hash; maxLength = 4 } | ConvertTo-Json -Compress
    $createResponse = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/hash/crack" -ContentType "application/json" -Body $createBody

    if (-not $createResponse.requestId) {
        throw "менеджер не вернул requestId при POST /api/hash/crack"
    }

    $requestId = [string]$createResponse.requestId
    Write-Host "Создан новый requestId: $requestId"

    $status = $null
    for ($i = 1; $i -le 15; $i++) {
        $statusResponse = Invoke-RestMethod -Method Get -Uri ("http://localhost:8080/api/hash/status?requestId=" + $requestId)
        $status = [string]$statusResponse.status
        Write-Host ("poll {0}/15 => status={1}" -f $i, $status)
        if ($status -ne "IN_PROGRESS") {
            break
        }
        Start-Sleep -Seconds 1
    }

    $afterCount = Get-RequestsCount
    Write-Host "hash_requests после запроса: $afterCount"

    Write-Host "`nMongo документ нового запроса:" -ForegroundColor Cyan
    $jsDoc = @"
const id = '$requestId';
const doc = db.getSiblingDB('crackhash').hash_requests.findOne(
  { _id: id },
  { _id: 1, status: 1, createdAt: 1, updatedAt: 1, completedParts: 1 }
);
printjson(doc);
"@
    Write-Host (Invoke-MongoEval -Js $jsDoc)
}

Write-Host "=== кейс 2: остановка PRIMARY ноды ===" -ForegroundColor Green

Invoke-Compose -Args @("ps")
Show-ReplicaStatus

$primary = Get-PrimaryServiceName
Write-Host "`nТекущая PRIMARY: $primary" -ForegroundColor Green

Read-Host "остановить PRIMARY ($primary)"
Invoke-Compose -Args @("stop", $primary)
Write-Host "$primary остановлен." -ForegroundColor Yellow

Write-Host "Ожидание выбора новой PRIMARY.." -ForegroundColor Yellow
Start-Sleep -Seconds 15

Show-ReplicaStatus
$newPrimary = Get-PrimaryServiceName
Write-Host "`nНовая PRIMARY: $newPrimary" -ForegroundColor Green
Show-MongoRead
Show-ManagerWorkCheck

$answer = Read-Host "`nЗапустить обратно остановленную ноду ${primary}?"
if ($answer -match "^(y|Y|д|Д)$") {
    Invoke-Compose -Args @("up", "-d", $primary)
    Start-Sleep -Seconds 8
    Show-ReplicaStatus
}

Write-Host "`nкейс 2 завершен." -ForegroundColor Green