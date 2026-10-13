param(
    [string]$MySqlExe = '',
    [string]$MySqlUser = 'root',
    [string]$MySqlHost = '127.0.0.1',
    [int]$MySqlPort = 3306,
    [string]$TestPattern = 'MySqlInnoDbAcceptanceTest',
    [switch]$IncludeUpgradeSchema,
    [switch]$IncludeNotificationSchema
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (-not $MySqlExe) {
    if ($env:MYSQL_EXE) {
        $MySqlExe = $env:MYSQL_EXE
    } elseif (Get-Command mysql.exe -ErrorAction SilentlyContinue) {
        $MySqlExe = (Get-Command mysql.exe).Source
    } else {
        throw 'Set MYSQL_EXE to the absolute path of mysql.exe.'
    }
}
if (-not (Test-Path -LiteralPath $MySqlExe -PathType Leaf)) {
    throw "mysql.exe not found: $MySqlExe"
}
if (-not $env:MYSQL_ACCEPTANCE_PASSWORD) {
    throw 'Set MYSQL_ACCEPTANCE_PASSWORD for the isolated test database connection.'
}

$database = 'yuncang_acceptance_' + (Get-Date -Format 'yyyyMMdd') + '_' +
        [guid]::NewGuid().ToString('N').Substring(0, 8)
if ($database -notmatch '^yuncang_acceptance_[0-9]{8}_[0-9a-f]{8}$') {
    throw 'Invalid generated database name.'
}
$mysqlArgs = @('-h', $MySqlHost, '-P', "$MySqlPort", '-u', $MySqlUser,
        '--default-character-set=utf8mb4')
$priorPwd = $env:MYSQL_PWD
$priorUrl = $env:MYSQL_TEST_URL
$priorUser = $env:MYSQL_TEST_USERNAME
$priorTestPwd = $env:MYSQL_TEST_PASSWORD
$priorRabbitQueue = $env:RABBIT_TEST_QUEUE
$priorRabbitExchange = $env:RABBIT_TEST_EXCHANGE
$created = $false
try {
    $env:MYSQL_PWD = $env:MYSQL_ACCEPTANCE_PASSWORD
    $existing = & $MySqlExe @mysqlArgs -N -e "SHOW DATABASES LIKE '$database'"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to check MySQL databases.' }
    if ($existing) { throw 'Generated acceptance database already exists.' }
    & $MySqlExe @mysqlArgs -e "CREATE DATABASE $database CHARACTER SET utf8mb4"
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create isolated database.' }
    $created = $true

    foreach ($sqlFile in @('01-schema.sql', '03-v1-business-schema.sql', '02-demo-data.sql')) {
        $script = Join-Path $projectRoot "docs/sql/$sqlFile"
        Get-Content -LiteralPath $script | & $MySqlExe @mysqlArgs -D $database
        if ($LASTEXITCODE -ne 0) { throw "SQL initialization failed: $sqlFile" }
    }
    if ($IncludeUpgradeSchema) {
        Get-Content -LiteralPath (Join-Path $projectRoot 'docs/sql/04-v1_1-idempotency.sql') |
                & $MySqlExe @mysqlArgs -D $database
        if ($LASTEXITCODE -ne 0) { throw 'V1.1 SQL initialization failed.' }
    }
    if ($IncludeNotificationSchema) {
        Get-Content -LiteralPath (Join-Path $projectRoot 'docs/sql/05-v1_1-notifications.sql') |
                & $MySqlExe @mysqlArgs -D $database
        if ($LASTEXITCODE -ne 0) { throw 'Notification SQL initialization failed.' }
    }
    if ($IncludeNotificationSchema -and $TestPattern -match 'Rabbit|Outbox') {
        $env:RABBIT_TEST_EXCHANGE = "yuncang.acceptance.$database"
        $env:RABBIT_TEST_QUEUE = "yuncang.acceptance.$database.notification"
    }
    $env:MYSQL_TEST_URL = "jdbc:mysql://$($MySqlHost):$MySqlPort/$database" +
            '?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
    $env:MYSQL_TEST_USERNAME = $MySqlUser
    $env:MYSQL_TEST_PASSWORD = $env:MYSQL_ACCEPTANCE_PASSWORD
    Push-Location $projectRoot
    try {
        & mvn -pl cloud-warehouse-boot -am "-Dtest=$TestPattern" '-Dsurefire.failIfNoSpecifiedTests=false' test
        if ($LASTEXITCODE -ne 0) { throw 'MySQL acceptance tests failed.' }
    } finally {
        Pop-Location
    }
    Write-Host "PASS: MySQL acceptance tests completed against $database"
} finally {
    if ($created) {
        & $MySqlExe @mysqlArgs -e "DROP DATABASE $database"
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Could not remove isolated acceptance database $database"
        } else {
            Write-Host "Removed isolated acceptance database $database"
        }
    }
    $env:MYSQL_PWD = $priorPwd
    $env:MYSQL_TEST_URL = $priorUrl
    $env:MYSQL_TEST_USERNAME = $priorUser
    $env:MYSQL_TEST_PASSWORD = $priorTestPwd
    $env:RABBIT_TEST_QUEUE = $priorRabbitQueue
    $env:RABBIT_TEST_EXCHANGE = $priorRabbitExchange
}
