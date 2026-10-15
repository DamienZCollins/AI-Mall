<#
一键启动本地开发环境。

用法：
  powershell -ExecutionPolicy Bypass -File scripts/start.ps1

参数：
  -ResetDb        重建数据库（删除并重灌 mall 表）
  -NoApp          只启动中间件，不启动 Spring Boot 应用
#>
[CmdletBinding()]
param(
    [switch]$ResetDb,
    [switch]$NoApp
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Info($message) { Write-Host "==> $message" -ForegroundColor Cyan }
function Ok($message)   { Write-Host "[OK] $message" -ForegroundColor Green }
function Fail($message) { Write-Host "[FAIL] $message" -ForegroundColor Red; exit 1 }

function Assert-Command($name, $hint) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        Fail "$name 不可用。$hint"
    }
}

function Get-EnvValue($lines, $key) {
    $line = $lines | Where-Object { $_ -match "^\s*$key=" } | Select-Object -First 1
    if ($null -eq $line) { Fail ".env 缺少 $key" }
    return ($line -replace "^\s*$key=", "").Trim()
}

function Invoke-Mysql($container, $password, $database, $sql) {
    # cmd 按原始 UTF-8 字节转发 SQL，避免 PowerShell 5.1 管道把中文转成本机 ANSI。
    & cmd /c "type `"$sql`" | docker exec -i -e MYSQL_PWD=$password $container mysql -uroot --default-character-set=utf8mb4 --binary-mode $database"
    return $LASTEXITCODE
}

Assert-Command docker '请先安装并启动 Docker Desktop。'
Assert-Command mvn '请先安装 Maven 并加入 PATH。'
Assert-Command java '请先安装 Java 21 并加入 PATH。'

if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
    Info '已从 .env.example 创建 .env'
}

$envLines = Get-Content .env
$mysqlUser = Get-EnvValue $envLines 'MYSQL_USER'
$mysqlRootPassword = Get-EnvValue $envLines 'MYSQL_ROOT_PASSWORD'
$mysqlDatabase = Get-EnvValue $envLines 'MYSQL_DATABASE'
$embeddingApiKey = Get-EnvValue $envLines 'EMBEDDING_API_KEY'

if ($embeddingApiKey -eq 'change-me') {
    Fail 'EMBEDDING_API_KEY 仍为 change-me，请先在 .env 中配置 embedding 接口密钥。'
}

Info '构建应用'
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { Fail 'Maven 构建失败' }

Info '启动中间件（首次会构建 ES 镜像，耗时较长）'
# Start-Process 屏蔽 Docker 未启动时写入 stderr 的诊断信息，
# PowerShell 5.1 会把 NativeCommandError 当成脚本错误中断。
$dockerInfo = Start-Process -FilePath docker -ArgumentList 'info' -WindowStyle Hidden -Wait -PassThru
$dockerRunning = $dockerInfo.ExitCode -eq 0
if (-not $dockerRunning) {
    $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path $dockerDesktop)) {
        Fail 'Docker Desktop 未运行，且未找到默认安装路径。请手动启动 Docker Desktop 后重试。'
    }
    Info 'Docker Desktop 未运行，正在启动'
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden

    $dockerReady = $false
    foreach ($i in 1..60) {
        Start-Sleep -Seconds 3
        $probe = Start-Process -FilePath docker -ArgumentList 'info' -WindowStyle Hidden -Wait -PassThru
        if ($probe.ExitCode -eq 0) { $dockerReady = $true; break }
    }
    if (-not $dockerReady) { Fail 'Docker Desktop 启动超时，请手动检查 Docker Desktop 状态。' }
}

docker compose up -d --build --wait
if ($LASTEXITCODE -ne 0) { Fail '中间件启动失败' }

Info '初始化数据库'
if ($ResetDb) {
    $dropSql = "DROP DATABASE IF EXISTS $mysqlDatabase; CREATE DATABASE $mysqlDatabase DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    $dropSql | docker exec -i -e "MYSQL_PWD=$mysqlRootPassword" mall-mysql mysql -uroot
    if ($LASTEXITCODE -ne 0) { Fail '重建数据库失败' }
}

if ((Invoke-Mysql mall-mysql $mysqlRootPassword $mysqlDatabase 'sql/schema.sql') -ne 0) { Fail '执行 schema.sql 失败' }

if ((Invoke-Mysql mall-mysql $mysqlRootPassword $mysqlDatabase 'sql/data.sql') -ne 0) { Fail '执行 data.sql 失败' }

Info '初始化 RocketMQ 主题'
$rocketmqHome = '/home/rocketmq/rocketmq-5.3.2'
foreach ($topic in @(@('mall-seckill-order', 'NORMAL'), @('mall-seckill-timeout', 'DELAY'))) {
    docker exec mall-rocketmq-broker sh -c "cd $rocketmqHome && sh bin/mqadmin updateTopic -b 127.0.0.1:10911 -t $($topic[0]) -r 4 -w 4 -a '+message.type=$($topic[1])'"
    if ($LASTEXITCODE -ne 0) { Fail "RocketMQ 主题初始化失败：$($topic[0])" }
}

docker compose restart rocketmq-proxy | Out-Null
if ($LASTEXITCODE -ne 0) { Fail '重启 RocketMQ 代理失败' }
docker compose up --wait rocketmq-proxy
if ($LASTEXITCODE -ne 0) { Fail 'RocketMQ 代理未恢复健康' }

Info '导入环境变量'
foreach ($line in Get-Content .env) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $parts = $line -split '=', 2
    [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
}

if ($NoApp) {
    Ok '中间件已就绪：'
}
else {
    $java = (Get-Command java).Source
    $appProcess = Start-Process -FilePath $java `
        -ArgumentList @('-jar', 'target/mall-0.0.1-SNAPSHOT.jar') `
        -WorkingDirectory $root -WindowStyle Hidden -PassThru

    $healthUrl = 'http://localhost:8080/actuator/health'
    $ready = $false
    foreach ($i in 1..60) {
        Start-Sleep -Seconds 2
        if ($appProcess.HasExited) { Fail "应用进程提前退出，ExitCode=$($appProcess.ExitCode)" }
        try {
            $response = Invoke-RestMethod $healthUrl -TimeoutSec 2
            if ($response.status -eq 'UP') { $ready = $true; break }
        }
        catch { }
    }
    if (-not $ready) {
        Stop-Process -Id $appProcess.Id -Force -ErrorAction SilentlyContinue
        Fail '应用健康检查超时'
    }
    Ok "应用已启动，PID=$($appProcess.Id)"
}

Write-Host ''
Write-Host '服务地址：'
Write-Host '  Swagger        http://localhost:8080/swagger-ui/index.html'
Write-Host '  健康检查      http://localhost:8080/actuator/health'
Write-Host '  RocketMQ 面板 http://localhost:18080'
Write-Host ''
Write-Host '停止方式：'
Write-Host '  停止应用      Stop-Process -Id <应用PID>'
Write-Host '  停止中间件    docker compose down'
if (-not $NoApp) {
    Write-Host "  本次应用 PID  $($appProcess.Id)"
}

