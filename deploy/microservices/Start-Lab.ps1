param([switch]$PullOnly)
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCommand) {
    $dockerExe = $dockerCommand.Source
} else {
    $dockerExe = Join-Path $env:LOCALAPPDATA "Programs/DockerDesktop/resources/bin/docker.exe"
}
if (-not (Test-Path -LiteralPath $dockerExe)) { throw "Docker executable not found." }
$env:Path = "$(Split-Path $dockerExe);$env:Path"
$localDir = Join-Path $repo ".local"
$envFile = Join-Path $localDir "microservices-lab.env"
if (-not (Test-Path -LiteralPath $envFile)) {
    New-Item -ItemType Directory -Path $localDir -Force | Out-Null
    $keys = @("LAB_WAREHOUSE_ROOT_PASSWORD","LAB_NOTIFICATION_ROOT_PASSWORD",
        "LAB_WAREHOUSE_PASSWORD","LAB_NOTIFICATION_PASSWORD","LAB_REDIS_PASSWORD",
        "LAB_RABBIT_PASSWORD","LAB_JWT_SECRET","LAB_INTERNAL_TOKEN","LAB_NACOS_IDENTITY")
    $lines = @("# Local integration lab secrets. Do not share or commit.")
    foreach ($key in $keys) {
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $value = ([BitConverter]::ToString($bytes)).Replace("-","").ToLowerInvariant()
        $lines += "$key=$value"
    }
    $tokenBytes = New-Object byte[] 64
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($tokenBytes) } finally { $rng.Dispose() }
    $lines += "LAB_NACOS_TOKEN=$([Convert]::ToBase64String($tokenBytes))"
    $lines += "LAB_GATEWAY_PORT=18080"
    [IO.File]::WriteAllLines($envFile, $lines, [Text.UTF8Encoding]::new($false))
}
$composeArgs = @("compose","--project-name","yuncang-lab","--env-file",$envFile,
    "-f",(Join-Path $repo "compose.lab.yaml"))
& $dockerExe @composeArgs config --quiet
if ($LASTEXITCODE -ne 0) { throw "Lab configuration validation failed." }
& $dockerExe @composeArgs pull warehouse-db notification-db redis rabbitmq nacos
if ($LASTEXITCODE -ne 0) { throw "Infrastructure image pull failed; no existing host databases were used." }
if ($PullOnly) { return }
& $dockerExe @composeArgs up -d --pull never --wait --wait-timeout 240
if ($LASTEXITCODE -ne 0) { throw "Lab startup did not reach healthy status; inspect compose logs." }
Write-Host "Lab ready: http://127.0.0.1:18080 ; no host database ports are published."
