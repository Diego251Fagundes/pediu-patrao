$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $PSScriptRoot
$localDir = Join-Path $projectDir '.local'
$mongoExecutable = Get-ChildItem -Path "$localDir\mongodb-*\bin\mongod.exe" -ErrorAction SilentlyContinue |
    Select-Object -First 1

if (-not $mongoExecutable) {
    throw 'Extraia o MongoDB Community para Windows na pasta .local do projeto.'
}

$runningMongo = Get-Process -Name mongod -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -eq $mongoExecutable.FullName }
if ($runningMongo) {
    Write-Host 'O MongoDB deste projeto ja esta em execucao.'
    return
}

$dataDir = Join-Path $localDir 'mongodb-data'
$logPath = Join-Path $localDir 'mongodb.log'
New-Item -ItemType Directory -Path $dataDir -Force | Out-Null

$mongoProcess = Start-Process -FilePath $mongoExecutable.FullName -WindowStyle Hidden -PassThru -ArgumentList @(
    '--bind_ip', '127.0.0.1', '--port', '27017',
    '--dbpath', "`"$dataDir`"", '--logpath', "`"$logPath`"", '--logappend'
)
Write-Host "MongoDB iniciado (PID $($mongoProcess.Id)). Log: $logPath"
