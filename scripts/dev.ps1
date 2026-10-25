param(
    [int]$BackendPort = 8081,
    [int]$FrontendPort = 5173,
    [string]$DataDir = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return $env:JAVA_HOME
    }
    $bundled = Join-Path $root ".tools\jdk25\jdk-25.0.4.1+1"
    if (Test-Path (Join-Path $bundled "bin\java.exe")) {
        return $bundled
    }
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) {
        return (Split-Path -Parent (Split-Path -Parent $java.Source))
    }
    throw "Java 25 was not found. Set JAVA_HOME or install the JDK described in README.md."
}

$javaHome = Resolve-JavaHome
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"
if (-not $DataDir) {
    $DataDir = Join-Path $env:TEMP "ghostshift-dev-data"
}
$env:GHOSTSHIFT_PORT = "$BackendPort"
$env:GHOSTSHIFT_DATA_DIR = $DataDir
$env:VITE_API_PROXY_TARGET = "http://127.0.0.1:$BackendPort"

$frontend = Join-Path $root "frontend"
if (-not (Test-Path (Join-Path $frontend "node_modules"))) {
    Write-Host "Installing frontend dependencies..." -ForegroundColor Cyan
    Push-Location $frontend
    try { npm install } finally { Pop-Location }
}

Write-Host "Starting GhostShift backend on http://127.0.0.1:$BackendPort ..." -ForegroundColor Cyan
Write-Host "Data directory: $DataDir"
$backend = Start-Process `
    -FilePath (Join-Path $root "backend\gradlew.bat") `
    -ArgumentList "run", "--no-daemon", "--console=plain" `
    -WorkingDirectory (Join-Path $root "backend") `
    -PassThru -NoNewWindow

try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($backend.HasExited) {
            throw "The backend exited before becoming ready. Check the Gradle output above."
        }
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$BackendPort/api/v1/health" -TimeoutSec 2
            if ($health.status -eq "ok") {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 1000
        }
    }
    if (-not $ready) {
        throw "The backend did not become healthy within 60 seconds."
    }

    Write-Host "Backend ready. Starting Vite on http://127.0.0.1:$FrontendPort ..." -ForegroundColor Green
    Write-Host "Press Ctrl+C to stop both processes." -ForegroundColor DarkGray
    Push-Location $frontend
    try {
        npm run dev -- --port $FrontendPort
    } finally {
        Pop-Location
    }
} finally {
    if ($backend -and -not $backend.HasExited) {
        Write-Host "Stopping backend..." -ForegroundColor DarkGray
        Stop-Process -Id $backend.Id -Force
    }
}
