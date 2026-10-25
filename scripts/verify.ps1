param(
    [switch]$E2E,
    [int]$BackendPort = 8081
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
    throw "Java 25 was not found. Set JAVA_HOME or install the JDK described in README.md."
}

$javaHome = Resolve-JavaHome
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

Write-Host "== Backend tests ==" -ForegroundColor Cyan
Push-Location (Join-Path $root "backend")
try {
    & .\gradlew.bat test --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Backend tests failed." }
} finally { Pop-Location }

Write-Host "== Frontend unit tests ==" -ForegroundColor Cyan
Push-Location (Join-Path $root "frontend")
try {
    npm test
    if ($LASTEXITCODE -ne 0) { throw "Frontend unit tests failed." }

    Write-Host "== Frontend production build ==" -ForegroundColor Cyan
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend build failed." }

    if ($E2E) {
        Write-Host "== Playwright end-to-end tests ==" -ForegroundColor Cyan
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$BackendPort/api/v1/health" -TimeoutSec 3
        } catch {
            throw "Start the backend on port $BackendPort before running E2E tests. See README.md."
        }
        if ($health.status -ne "ok") { throw "The backend health check is not OK." }

        $env:E2E_BASE_URL = "http://127.0.0.1:5173"
        $env:E2E_API_URL = "http://127.0.0.1:$BackendPort"
        npx playwright test
        if ($LASTEXITCODE -ne 0) { throw "Playwright tests failed." }
    }
} finally { Pop-Location }

Write-Host "Verification finished." -ForegroundColor Green
