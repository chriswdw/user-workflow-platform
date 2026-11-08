#!/usr/bin/env bash
# Start platform-api with the `local` profile, loading secrets from a private env file.
#
# Usage: ./scripts/run-local.sh
#
# The env file is outside the repo and holds KEY=value lines (e.g. API_JWT_SECRET=<base64>).
# Default: ~/.config/workflow-platform/env — override with WORKFLOW_ENV_FILE.
# Secrets are exported only to the gradle process started here, not to your wider shell.
set -euo pipefail

ENV_FILE="${WORKFLOW_ENV_FILE:-$HOME/.config/workflow-platform/env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing env file: $ENV_FILE" >&2
  echo "Create it with: API_JWT_SECRET=\$(openssl rand -base64 32)  (chmod 600)" >&2
  exit 1
fi

# Refuse to run if the file is readable by group/others — it holds secrets.
PERMS=$(stat -c '%a' "$ENV_FILE")
if [ "${PERMS: -2}" != "00" ]; then
  echo "Refusing to use $ENV_FILE: permissions are $PERMS, expected 600 (chmod 600 \"$ENV_FILE\")." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

if [ -z "${API_JWT_SECRET:-}" ]; then
  echo "API_JWT_SECRET is not set in $ENV_FILE" >&2
  exit 1
fi

cd "$(dirname "$0")/.."
exec ./gradlew :platform-api:bootRun --args='--spring.profiles.active=local'
