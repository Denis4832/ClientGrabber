$ErrorActionPreference = 'Stop'

function Find-LoggerJava {
    param([switch]$Compiler)
    $Homes = @($env:JAVA_HOME, $env:JDK_HOME)
    $Commands = @(Get-Command java.exe -All -ErrorAction SilentlyContinue)
    foreach ($Command in $Commands) {
        $Homes += Split-Path -Parent (Split-Path -Parent $Command.Source)
    }
    $InstallRoots = @()
    if ($env:ProgramFiles) {
        foreach ($Vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto', 'Semeru')) {
            $InstallRoots += Join-Path $env:ProgramFiles $Vendor
        }
    }
    if ($env:LOCALAPPDATA) { $InstallRoots += Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium' }
    if ($env:USERPROFILE) { $InstallRoots += Join-Path $env:USERPROFILE '.jdks' }
    foreach ($InstallRoot in $InstallRoots) {
        if (Test-Path -LiteralPath $InstallRoot) {
            $Homes += @(Get-ChildItem -LiteralPath $InstallRoot -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
        }
    }
    foreach ($JavaHome in ($Homes | Where-Object { $_ } | Select-Object -Unique)) {
        $Java = Join-Path $JavaHome 'bin\java.exe'
        $Javac = Join-Path $JavaHome 'bin\javac.exe'
        $JarTool = Join-Path $JavaHome 'bin\jar.exe'
        if (!(Test-Path -LiteralPath $Java)) { continue }
        if ($Compiler -and (!(Test-Path -LiteralPath $Javac) -or !(Test-Path -LiteralPath $JarTool))) { continue }
        $SavedErrorPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            $Version = (& $Java --version 2>$null | Out-String)
        } finally { $ErrorActionPreference = $SavedErrorPreference }
        if ($Version -match '(?:openjdk|java)\s+(\d+)' -and [int]$Matches[1] -ge 17) {
            return @{ Java = $Java; Javac = $Javac; Jar = $JarTool }
        }
    }
    throw 'Нужна Java 17 или новее (для сборки — JDK). Укажите путь к ней в JAVA_HOME.'
}
