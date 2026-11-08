#!/usr/bin/env bash
# Run SonarQube analysis and report high-severity issues.
# Usage: ./scripts/sonar-check.sh
#
# SONAR_TOKEN is read from the environment, or else from the private env file
# (~/.config/workflow-platform/env, override with WORKFLOW_ENV_FILE) — never from this repo.
set -euo pipefail

if [ -z "${SONAR_TOKEN:-}" ]; then
  ENV_FILE="${WORKFLOW_ENV_FILE:-$HOME/.config/workflow-platform/env}"
  if [ ! -f "$ENV_FILE" ]; then
    echo "SONAR_TOKEN is not set and env file $ENV_FILE does not exist." >&2
    exit 1
  fi
  PERMS=$(stat -c '%a' "$ENV_FILE")
  if [ "${PERMS: -2}" != "00" ]; then
    echo "Refusing to use $ENV_FILE: permissions are $PERMS, expected 600." >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

if [ -z "${SONAR_TOKEN:-}" ]; then
  echo "SONAR_TOKEN is not set in the environment or the env file." >&2
  exit 1
fi

# Defaults target SonarCloud; override via env to use a local SonarQube instead, e.g.
#   SONAR_URL=http://localhost:9000 SONAR_ORG= SONAR_PROJECT_KEY=user-workflow-platform
SONAR_URL="${SONAR_URL:-https://sonarcloud.io}"
SONAR_ORG="${SONAR_ORG-chriswdw}"
PROJECT_KEY="${SONAR_PROJECT_KEY:-chriswdw_user-workflow-platform}"

# SonarCloud keeps results per branch: analyse the current branch and query that same branch,
# otherwise the issue check would read the main branch's results.
BRANCH="$(git branch --show-current)"
SCAN_ARGS=()
BRANCH_QUERY=""
if [ -n "$SONAR_ORG" ]; then
  SCAN_ARGS+=("-Dsonar.organization=$SONAR_ORG")
  BRANCH_QUERY="&organization=$SONAR_ORG"
fi
if [ -n "$BRANCH" ] && [ "$BRANCH" != "main" ] && [ -n "$SONAR_ORG" ]; then
  SCAN_ARGS+=("-Dsonar.branch.name=$BRANCH")
  # This SonarCloud organization may not read non-main *branch* results through the API
  # ("Organization is not allowed to access data from non main branches"), but pull request
  # results are readable. So check the PR analysis; it reflects the last CI run, not this local scan.
  PR_NUMBER="$(gh pr list --head "$BRANCH" --json number --jq '.[0].number' 2>/dev/null || true)"
  if [ -n "$PR_NUMBER" ]; then
    BRANCH_QUERY="$BRANCH_QUERY&pullRequest=$PR_NUMBER"
    echo "Note: issues below are read from the SonarCloud analysis of PR #$PR_NUMBER (last CI run)."
  else
    echo "No open PR for $BRANCH: the analysis will upload, but its issues cannot be read via the API." >&2
    echo "Open a PR and let CI analyse it, then re-run this script." >&2
    NO_READABLE_RESULTS=1
  fi
fi

echo "Running Sonar analysis ($SONAR_URL, project $PROJECT_KEY, branch ${BRANCH:-n/a})..."
./gradlew sonar \
  -Dsonar.host.url="$SONAR_URL" \
  -Dsonar.token="$SONAR_TOKEN" \
  -Dsonar.projectKey="$PROJECT_KEY" \
  "${SCAN_ARGS[@]}"

# The gradle task returns as soon as the report is *uploaded* — SonarQube then processes it
# asynchronously via a background Compute Engine task. Querying the Issues API immediately
# afterwards can read the *previous* analysis's results instead of this one, so poll the CE
# task (its id is written to report-task.txt by the scanner) until it finishes.
REPORT_TASK_FILE="build/sonar/report-task.txt"
if [ -f "$REPORT_TASK_FILE" ]; then
  CE_TASK_URL=$(grep '^ceTaskUrl=' "$REPORT_TASK_FILE" | cut -d= -f2-)
  echo "Waiting for SonarQube to finish processing the analysis..."
  for _ in $(seq 1 60); do
    CE_STATUS=$(curl -s -H "Authorization: Bearer $SONAR_TOKEN" "$CE_TASK_URL" \
      | python3 -c "import json,sys; print(json.load(sys.stdin)['task']['status'])")
    [ "$CE_STATUS" = "SUCCESS" ] || [ "$CE_STATUS" = "FAILED" ] || [ "$CE_STATUS" = "CANCELED" ] && break
    sleep 2
  done
  if [ "$CE_STATUS" != "SUCCESS" ]; then
    echo "✗ SonarQube background processing did not complete successfully (status: $CE_STATUS)"
    exit 1
  fi
fi

echo ""
echo "Checking for high-severity issues..."

# Query both old-style (CRITICAL,BLOCKER) and new clean-code model (impactSeverities=HIGH)
if [ "${NO_READABLE_RESULTS:-0}" = 1 ]; then
  echo "✗ Cannot verify issues for this branch (see above)."
  exit 1
fi

# Fail loudly on an API error body: an error has no 'issues' key and would otherwise read as
# "no issues" and report a false pass.
query_issues() {
  local body
  body=$(curl -s -H "Authorization: Bearer $SONAR_TOKEN" "$1")
  if echo "$body" | python3 -c "import json,sys; sys.exit(0 if 'errors' in json.load(sys.stdin) else 1)"; then
    echo "✗ SonarQube API error: $body" >&2
    exit 1
  fi
  echo "$body"
}

OLD_ISSUES=$(query_issues "$SONAR_URL/api/issues/search?projectKeys=$PROJECT_KEY&severities=CRITICAL%2CBLOCKER&resolved=false&ps=50$BRANCH_QUERY")
NEW_ISSUES=$(query_issues "$SONAR_URL/api/issues/search?projectKeys=$PROJECT_KEY&impactSeverities=HIGH&resolved=false&ps=50$BRANCH_QUERY")

print_issues() {
  python3 - "$1" <<'EOF'
import json, sys
data = json.loads(sys.argv[1])
issues = data.get('issues', [])
for i in issues:
    loc = i.get('component','').split(':')[-1] + ':' + str(i.get('line','?'))
    sev = i.get('severity') or next(
        (imp['severity'] for imp in i.get('impacts', [])), '?')
    print(f"  [{sev}] {i['rule']}  {loc}")
    print(f"    {i['message']}")
EOF
}

OLD_COUNT=$(echo "$OLD_ISSUES" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('issues',[])))")
NEW_COUNT=$(echo "$NEW_ISSUES" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('issues',[])))")
TOTAL=$((OLD_COUNT + NEW_COUNT))

if [ "$TOTAL" -eq 0 ]; then
  echo "✓ No high-severity issues."
else
  echo "✗ $TOTAL issue(s) require attention before merging:"
  [ "$OLD_COUNT" -gt 0 ] && print_issues "$OLD_ISSUES"
  [ "$NEW_COUNT" -gt 0 ] && print_issues "$NEW_ISSUES"
  exit 1
fi
