param([switch]$Rebuild)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jar = Join-Path $Root 'server\proxy\LocalPlayerLoggerProxy.jar'
$Config = Join-Path $Root 'server\proxy\localplayerlogger-proxy.properties'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
try { $Host.UI.RawUI.WindowTitle = 'Minecraft — локальный прокси и запись действий' } catch { }

try {
    Write-Host ''
    Write-Host '======================================================================' -ForegroundColor Red
    Write-Host '       НЕ ЗАКРЫВАЙТЕ КОНСОЛЬ ВО ВРЕМЯ ИГРЫ' -ForegroundColor Red
    Write-Host '       ЗАКРЫТИЕ ОКНА ПРЕРВЁТ СОЕДИНЕНИЕ С СЕРВЕРОМ' -ForegroundColor Red
    Write-Host '======================================================================' -ForegroundColor Red
    if (!(Test-Path -LiteralPath $Config)) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Config) | Out-Null
        $Template = "$Config.example"
        if (Test-Path -LiteralPath $Template) { Copy-Item -LiteralPath $Template -Destination $Config }
        else {
            [IO.File]::WriteAllText($Config, "listen_host=127.0.0.1`nlisten_port=25565`nbackend_host=server.example.invalid`nbackend_port=25565`n", (New-Object System.Text.UTF8Encoding($false)))
        }
    }
    if (Select-String -LiteralPath $Config -Pattern '^\s*(backend_host|upstream_host)\s*=\s*server\.example\.invalid\s*$' -Quiet) {
        throw "Сначала укажите адрес вашего сервера в файле $Config (backend_host). Сейчас там заглушка."
    }
    . (Join-Path $Root 'java-tools.ps1')
    $Tools = Find-LoggerJava
    if ($Rebuild -or !(Test-Path -LiteralPath $Jar)) { & (Join-Path $Root 'build-plugin.ps1') }
    Write-Host 'Настройки сервера находятся в server\proxy\localplayerlogger-proxy.properties' -ForegroundColor Cyan
    Push-Location (Join-Path $Root 'server\proxy')
    try {
        & $Tools.Java '-Dfile.encoding=UTF-8' '-Dsun.stdout.encoding=UTF-8' '-Dsun.stderr.encoding=UTF-8' -Xms64M -Xmx256M -jar $Jar
        if ($LASTEXITCODE -ne 0) { throw "Прокси остановлен с ошибкой (код $LASTEXITCODE). Причина указана выше." }
    } finally { Pop-Location }
} catch {
    Write-Host ''
    Write-Host ('ОШИБКА: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'Сохраните текст ошибки. Проверьте доступность сервера и не запущено ли второе окно прокси.' -ForegroundColor Yellow
    exit 1
}
