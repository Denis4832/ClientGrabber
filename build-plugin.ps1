param([string]$OutputJar)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $Root 'java-tools.ps1')
$Tools = Find-LoggerJava -Compiler
$Src = Join-Path $Root 'plugin-src\src\main\java'
$BuildRoot = [IO.Path]::GetFullPath((Join-Path $Root 'plugin-src\build'))
$Build = Join-Path $BuildRoot ('compile-' + [Guid]::NewGuid().ToString('N'))
$Jar = if ($OutputJar) { [IO.Path]::GetFullPath($OutputJar) } else { Join-Path $Root 'server\proxy\LocalPlayerLoggerProxy.jar' }
$PendingJar = "$Jar.new"
New-Item -ItemType Directory -Force -Path $Build | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Jar) | Out-Null
try {
    Write-Host 'Проверяю и собираю прокси...' -ForegroundColor Cyan
    $Sources = @(Get-ChildItem -LiteralPath $Src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    & $Tools.Javac --release 17 -encoding UTF-8 -Xlint:all -d $Build $Sources
    if ($LASTEXITCODE -ne 0) { throw "Ошибка компиляции Java: код $LASTEXITCODE. Прокси не запущен." }
    & $Tools.Jar --create --file $PendingJar --main-class local.logger.MinecraftLoggerPlugin -C $Build .
    if ($LASTEXITCODE -ne 0) { throw "Не удалось собрать JAR: код $LASTEXITCODE." }
    Move-Item -LiteralPath $PendingJar -Destination $Jar -Force
    Write-Host 'Сборка завершена.' -ForegroundColor Green
} finally {
    $ResolvedBuild = [IO.Path]::GetFullPath($Build)
    if ($ResolvedBuild.StartsWith($BuildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $ResolvedBuild -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $PendingJar) { Remove-Item -LiteralPath $PendingJar -Force }
}
