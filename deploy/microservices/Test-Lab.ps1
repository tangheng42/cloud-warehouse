#requires -Version 7.0
param([string]$BaseUrl = "http://127.0.0.1:18080")
$ErrorActionPreference = "Stop"
if ($BaseUrl -ne "http://127.0.0.1:18080") {
    throw "This script writes demo data and is restricted to the isolated lab gateway on port 18080."
}
$repo = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
$dockerExe = if ($dockerCommand) { $dockerCommand.Source } else {
    Join-Path $env:LOCALAPPDATA "Programs/DockerDesktop/resources/bin/docker.exe"
}
$containerIds = & $dockerExe ps --filter label=com.docker.compose.project=yuncang-lab --format "{{.Names}}"
if ($LASTEXITCODE -ne 0 -or $containerIds -notcontains "yuncang-lab-gateway-1") {
    throw "The isolated yuncang-lab gateway is not running."
}
$published = & $dockerExe port yuncang-lab-gateway-1 8080/tcp
if ($published -notcontains "127.0.0.1:18080") { throw "Lab gateway port does not match." }
$script:checks = [Collections.Generic.List[string]]::new()
function Assert-Lab([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAILED: $Message" }
    $script:checks.Add($Message)
    Write-Host "PASS: $Message"
}
function Invoke-Lab([string]$Path, [string]$Method = "GET", $Body = $null,
        [int]$Expected = 200, [hashtable]$Headers = @{}) {
    $params = @{ Uri="$BaseUrl$Path"; Method=$Method; Headers=$Headers;
        SkipHttpErrorCheck=$true; TimeoutSec=15 }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10 -Compress))
    }
    $response = Invoke-WebRequest @params
    if ([int]$response.StatusCode -ne $Expected) {
        throw "Unexpected HTTP $($response.StatusCode): $Method $Path; expected $Expected"
    }
    if ($response.Content) { return ($response.Content | ConvertFrom-Json) }
}
function Wait-Notification([long]$WarehouseId, [long]$SkuId, [string]$Type, [hashtable]$Headers) {
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        $page = Invoke-Lab "/api/v1.1/notifications?size=100" -Headers $Headers
        $found = @($page.data.items | Where-Object {
            $_.warehouseId -eq $WarehouseId -and $_.skuId -eq $SkuId -and $_.type -eq $Type
        })
        if ($found.Count -gt 0) { return $found[0] }
        Start-Sleep -Seconds 2
    }
    throw "Notification did not arrive through the outbox/MQ/independent database chain: $Type"
}
$null = Invoke-Lab "/actuator/health"
$null = Invoke-Lab "/api/v1.1/notifications" -Expected 401
Assert-Lab $true "Gateway reachable and unauthenticated notification query returns 401"
$null = Invoke-Lab "/internal/v1/notifications/page" -Expected 404
Assert-Lab $true "Gateway does not expose notification internal endpoints"
$login = Invoke-Lab "/api/v1/auth/login" "POST" @{username="admin";password="123456"}
$headers = @{ Authorization="Bearer $($login.data.token)" }
Assert-Lab (-not [string]::IsNullOrWhiteSpace($login.data.token)) "Login succeeds through gateway and service discovery"
$null = Invoke-Lab "/api/v1.1/notifications?page=0" -Expected 400 -Headers $headers
Assert-Lab $true "Public notification pagination rejects page zero"
$tag = [Guid]::NewGuid().ToString("N").Substring(0,10)
$wh = (Invoke-Lab "/api/v1/warehouses" "POST" @{code="LAB-W-$tag";name="Integration warehouse";address="Docker lab"} 201 $headers).data
$cat = (Invoke-Lab "/api/v1/sku-categories" "POST" @{code="LAB-C-$tag";name="Integration category"} 201 $headers).data
$sku = (Invoke-Lab "/api/v1/skus" "POST" @{code="LAB-S-$tag";name="Integration SKU";categoryId=$cat.id;unit="piece";safetyStock=10} 201 $headers).data
$inbound = (Invoke-Lab "/api/v1/inbounds" "POST" @{warehouseId=$wh.id;items=@(@{skuId=$sku.id;quantity=5});remark="lab low-stock trigger"} 201 $headers).data
$confirmed = (Invoke-Lab "/api/v1/inbounds/$($inbound.id)/confirm" "POST" -Headers $headers).data
Assert-Lab ($confirmed.status -eq "COMPLETED") "Real MySQL inbound confirmation succeeds"
$low = Wait-Notification $wh.id $sku.id "LOW_STOCK" $headers
Assert-Lab ($low.status -eq "UNREAD" -and -not [string]::IsNullOrWhiteSpace($low.eventId)) "Low-stock event reaches independent notification database and Feign page"
$restock = (Invoke-Lab "/api/v1/inbounds" "POST" @{warehouseId=$wh.id;items=@(@{skuId=$sku.id;quantity=10});remark="lab recovered trigger"} 201 $headers).data
$null = Invoke-Lab "/api/v1/inbounds/$($restock.id)/confirm" "POST" -Headers $headers
$recovered = Wait-Notification $wh.id $sku.id "STOCK_RECOVERED" $headers
Assert-Lab ($recovered.eventId -ne $low.eventId) "Recovery event is independently delivered"
$idemHeaders = $headers.Clone()
$idemHeaders["Idempotency-Key"] = "lab-outbound-$tag"
$orderBody = @{warehouseId=$wh.id;items=@(@{skuId=$sku.id;quantity=8});remark="lab idempotency"}
$outbound = (Invoke-Lab "/api/v1/outbounds" "POST" $orderBody 201 $idemHeaders).data
$duplicate = (Invoke-Lab "/api/v1/outbounds" "POST" $orderBody 201 $idemHeaders).data
Assert-Lab ($outbound.id -eq $duplicate.id) "Same idempotency key returns the same outbound order"
$null = Invoke-Lab "/api/v1/outbounds/$($outbound.id)/reserve" "POST" -Headers $headers
$reserved = (Invoke-Lab "/api/v1/stocks/warehouses/$($wh.id)/skus/$($sku.id)" -Headers $headers).data
Assert-Lab ($reserved.physicalStock -eq 15 -and $reserved.availableStock -eq 7 -and $reserved.lockedStock -eq 8) "Reservation preserves physical = available + locked"
$null = Invoke-Lab "/api/v1/outbounds/$($outbound.id)/confirm" "POST" -Headers $headers
$stock = (Invoke-Lab "/api/v1/stocks/warehouses/$($wh.id)/skus/$($sku.id)" -Headers $headers).data
Assert-Lab ($stock.physicalStock -eq 7 -and $stock.availableStock -eq 7 -and $stock.lockedStock -eq 0) "Outbound confirmation consumes only the reserved inventory"
$page = (Invoke-Lab "/api/v1.1/notifications?size=100" -Headers $headers).data
Assert-Lab ($page.items.Count -gt 0 -and $page.page -eq 1) "Original public notification response contract remains usable"
$skuA = (Invoke-Lab "/api/v1/skus" "POST" @{code="LAB-RA-$tag";name="Rollback first SKU";categoryId=$cat.id;unit="piece";safetyStock=10} 201 $headers).data
$skuB = (Invoke-Lab "/api/v1/skus" "POST" @{code="LAB-RB-$tag";name="Rollback second SKU";categoryId=$cat.id;unit="piece";safetyStock=10} 201 $headers).data
$rollbackInbound = (Invoke-Lab "/api/v1/inbounds" "POST" @{warehouseId=$wh.id;items=@(@{skuId=$skuA.id;quantity=10},@{skuId=$skuB.id;quantity=10});remark="lab rollback setup"} 201 $headers).data
$null = Invoke-Lab "/api/v1/inbounds/$($rollbackInbound.id)/confirm" "POST" -Headers $headers
$sql = "SELECT (SELECT COUNT(*) FROM stock_flow WHERE warehouse_id=$($wh.id)),(SELECT COUNT(*) FROM stock_alert_outbox)"
$queryCommand = 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" -D "$MYSQL_DATABASE" -N -B -e "$1"'
$before = & $dockerExe exec yuncang-lab-warehouse-db-1 sh -c $queryCommand sh $sql
if ($LASTEXITCODE -ne 0) { throw "Cannot read lab rollback baseline" }
$rollbackOrder = (Invoke-Lab "/api/v1/outbounds" "POST" @{warehouseId=$wh.id;items=@(@{skuId=$skuA.id;quantity=5},@{skuId=$skuB.id;quantity=20});remark="lab rollback failure"} 201 $headers).data
$failure = Invoke-Lab "/api/v1/outbounds/$($rollbackOrder.id)/reserve" "POST" -Expected 409 -Headers $headers
Assert-Lab ($failure.code -eq "INSUFFICIENT_AVAILABLE_STOCK") "Multi-SKU reservation fails when the second SKU is insufficient"
$after = & $dockerExe exec yuncang-lab-warehouse-db-1 sh -c $queryCommand sh $sql
if ($LASTEXITCODE -ne 0) { throw "Cannot read lab rollback result" }
Assert-Lab ($before -eq $after) "Failed transaction rolls back stock flows and the first SKU alert outbox event"
$rollbackStock = (Invoke-Lab "/api/v1/stocks/warehouses/$($wh.id)/skus/$($skuA.id)" -Headers $headers).data
$rollbackState = (Invoke-Lab "/api/v1/outbounds/$($rollbackOrder.id)" -Headers $headers).data
Assert-Lab ($rollbackStock.availableStock -eq 10 -and $rollbackStock.lockedStock -eq 0 -and $rollbackState.status -eq "CREATED") "Failed reservation restores stock and order state"
$null = Invoke-Lab "/api/v1/outbounds/$($rollbackOrder.id)/cancel" "POST" -Headers $headers
$result = [ordered]@{
    checkedAt=[DateTimeOffset]::Now.ToString("o"); gateway=$BaseUrl; project="yuncang-lab";
    passed=$script:checks.Count; checks=$script:checks; warehouseId=$wh.id; skuId=$sku.id;
    lowStockEventId=$low.eventId; recoveredEventId=$recovered.eventId; outboundId=$outbound.id
}
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $repo ".local/lab-smoke-result.json") -Encoding utf8
Write-Host "Completed $($script:checks.Count) checks. Result saved without credentials in .local/lab-smoke-result.json."
