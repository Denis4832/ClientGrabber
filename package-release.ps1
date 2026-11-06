param([string]$OutputPath)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$Root = [IO.Path]::GetFullPath((Split-Path -Parent $MyInvocation.MyCommand.Path))
if (!$OutputPath) { $OutputPath = Join-Path $Root 'dist\ClientGrabber.zip' }
$OutputPath = [IO.Path]::GetFullPath($OutputPath)
$Inputs = @(Get-Content -LiteralPath (Join-Path $Root 'publication-files.txt') -Encoding UTF8 |
    Where-Object { $_.Trim() -and !$_.StartsWith('#') } | ForEach-Object { $_.Trim() })
if (($Inputs | Select-Object -Unique).Count -ne $Inputs.Count) { throw 'В списке публикации есть повторные файлы.' }
foreach ($Relative in $Inputs) {
    if ([IO.Path]::IsPathRooted($Relative) -or $Relative -match '(^|[/\\])\.\.([/\\]|$)' -or
        $Relative -match '(^|/)(backups|diagnostics|logs|session-logs|world|build)(/|$)' -or
        $Relative -eq 'server/proxy/localplayerlogger-proxy.properties') { throw "Нельзя включать в публикацию: $Relative" }
    $Source = [IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if (!$Source.StartsWith($Root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Файл вне проекта.' }
    if (!(Test-Path -LiteralPath $Source -PathType Leaf)) { throw "Не найден файл публикации: $Relative" }
    if (((Get-Item -LiteralPath $Source -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Ссылка не включается в архив: $Relative" }
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath) | Out-Null
$Stream = [IO.File]::Open($OutputPath, [IO.FileMode]::Create, [IO.FileAccess]::Write)
try {
    $Zip = New-Object IO.Compression.ZipArchive($Stream, [IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        foreach ($Relative in $Inputs) {
            $Source = Join-Path $Root $Relative
            $EntryName = 'ClientGrabber/' + $Relative.Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Zip, $Source, $EntryName,
                [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $Zip.Dispose() }
} finally { $Stream.Dispose() }
$Hasher = [Security.Cryptography.SHA256]::Create()
$InputStream = [IO.File]::OpenRead($OutputPath)
try { $Hash = [BitConverter]::ToString($Hasher.ComputeHash($InputStream)).Replace('-', '').ToLowerInvariant() }
finally { $InputStream.Dispose(); $Hasher.Dispose() }
[IO.File]::WriteAllText("$OutputPath.sha256", "$Hash  $([IO.Path]::GetFileName($OutputPath))`n", (New-Object Text.UTF8Encoding($false)))
Write-Host "Архив готов: $OutputPath"
Write-Host "Файлов: $($Inputs.Count); SHA256: $Hash"
