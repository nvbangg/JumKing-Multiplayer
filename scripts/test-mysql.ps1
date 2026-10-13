param(
    [Parameter(Mandatory = $true)][string]$MySqlHome,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$testRoot = Join-Path $workspace '.local/mysql-tests'
$runRoot = Join-Path $testRoot ([guid]::NewGuid().ToString('N'))
$mysqlBase = (Resolve-Path -LiteralPath $MySqlHome).Path
$server = Join-Path $mysqlBase 'bin/mysqld.exe'
$client = Join-Path $mysqlBase 'bin/mysql.exe'
$database = 'jumpking_it_' + [guid]::NewGuid().ToString('N')
$password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$oldJava = $env:JAVA_HOME
$oldUrl = $env:JK_TEST_DATABASE_URL
$oldUser = $env:JK_TEST_DATABASE_USERNAME
$oldPassword = $env:JK_TEST_DATABASE_PASSWORD
$oldMysqlPassword = $env:MYSQL_PWD
$process = $null
$locationPushed = $false
try {
    New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
    $data = Join-Path $runRoot 'data'
    $init = Join-Path $runRoot 'initialize.sql'
    $sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$password'; CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    [IO.File]::WriteAllText($init, $sql)
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    & $server --no-defaults "--basedir=$mysqlBase" "--datadir=$data" --initialize-insecure --console 2>&1 | Out-File (Join-Path $runRoot 'initialize.log')
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed; see private test logs.' }
    $config = Join-Path $runRoot 'my.ini'
    $configText = "[mysqld]`nbasedir=$($mysqlBase.Replace('\','/'))`ndatadir=$($data.Replace('\','/'))`nbind-address=127.0.0.1`nport=$port`nmysqlx=0`ninit-file=$($init.Replace('\','/'))`nlog-error=$($runRoot.Replace('\','/'))/server.log`ninnodb-buffer-pool-size=64M`n"
    [IO.File]::WriteAllText($config, $configText)
    $process = Start-Process -FilePath $server -ArgumentList "--defaults-file=`"$config`"" -PassThru -WindowStyle Hidden
    $env:MYSQL_PWD = $password
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($process.HasExited) { throw 'Isolated MySQL stopped before becoming ready.' }
        & $client --no-defaults --host=127.0.0.1 "--port=$port" --user=root --silent --execute='SELECT 1' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    Remove-Item Env:MYSQL_PWD
    if (-not $ready) { throw 'Isolated MySQL startup timed out.' }
    $env:JAVA_HOME = $JavaHome
    $env:JK_TEST_DATABASE_URL = "jdbc:mysql://127.0.0.1:$port/$database`?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
    $env:JK_TEST_DATABASE_USERNAME = 'root'
    $env:JK_TEST_DATABASE_PASSWORD = $password
    Push-Location (Join-Path $workspace 'backend')
    $locationPushed = $true
    & .\mvnw.cmd -B -ntp verify -Pintegration
    if ($LASTEXITCODE -ne 0) { throw 'Integration verification failed.' }
}
finally {
    if ($locationPushed) { Pop-Location }
    $env:JAVA_HOME = $oldJava
    $env:JK_TEST_DATABASE_URL = $oldUrl
    $env:JK_TEST_DATABASE_USERNAME = $oldUser
    $env:JK_TEST_DATABASE_PASSWORD = $oldPassword
    try {
        if ($process) {
            $env:MYSQL_PWD = $password
            & (Join-Path $mysqlBase 'bin/mysqladmin.exe') --no-defaults --host=127.0.0.1 "--port=$port" --user=root --connect-timeout=2 shutdown 2>$null | Out-Null
            $process.WaitForExit(20000) | Out-Null
            $remaining = Get-CimInstance Win32_Process -Filter "Name = 'mysqld.exe'" |
                Where-Object { $_.CommandLine -and $_.CommandLine.Contains($config) }
            foreach ($ownedProcess in $remaining) { Stop-Process -Id $ownedProcess.ProcessId -Force }
        }
    } finally { $env:MYSQL_PWD = $oldMysqlPassword }
    $resolvedRun = [IO.Path]::GetFullPath($runRoot)
    $resolvedRoot = [IO.Path]::GetFullPath($testRoot) + [IO.Path]::DirectorySeparatorChar
    if ($resolvedRun.StartsWith($resolvedRoot, [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $resolvedRun)) {
        Remove-Item -LiteralPath $resolvedRun -Recurse -Force
    }
}
