#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${GHOSTSHIFT_PORT:-8081}"
E2E="${E2E:-0}"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  :
elif [[ -x "$ROOT/.tools/jdk25/jdk-25.0.4.1+1/bin/java" ]]; then
  JAVA_HOME="$ROOT/.tools/jdk25/jdk-25.0.4.1+1"
else
  echo "Java 25 was not found. Set JAVA_HOME or install the JDK described in README.md." >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

echo "== Backend tests =="
(cd "$ROOT/backend" && ./gradlew test --no-daemon --console=plain)

echo "== Frontend unit tests =="
(cd "$ROOT/frontend" && npm test)

echo "== Frontend production build =="
(cd "$ROOT/frontend" && npm run build)

if [[ "$E2E" == "1" ]]; then
  echo "== Playwright end-to-end tests =="
  if ! curl -fsS "http://127.0.0.1:$BACKEND_PORT/api/v1/health" >/dev/null; then
    echo "Start the backend on port $BACKEND_PORT before running E2E tests." >&2
    exit 1
  fi
  (cd "$ROOT/frontend" && \
    E2E_BASE_URL="http://127.0.0.1:5173" \
    E2E_API_URL="http://127.0.0.1:$BACKEND_PORT" \
    npx playwright test)
fi

echo "Verification finished."
