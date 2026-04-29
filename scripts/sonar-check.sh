#!/usr/bin/env bash
# Run SonarQube analysis and report high-severity issues.
# Usage: ./scripts/sonar-check.sh
set -euo pipefail

SONAR_URL="http://localhost:9000"
SONAR_TOKEN="sqp_4ac42a243a89ec45aa26828299f4dcf203066ad3"
PROJECT_KEY="user-workflow-platform"

echo "Running SonarQube analysis..."
./gradlew sonar \
  -Dsonar.host.url="$SONAR_URL" \
  -Dsonar.token="$SONAR_TOKEN" \
  -Dsonar.projectKey="$PROJECT_KEY"

echo ""
echo "Checking for high-severity issues..."

# Query both old-style (CRITICAL,BLOCKER) and new clean-code model (impactSeverities=HIGH)
query_issues() {
  curl -s -H "Authorization: Bearer $SONAR_TOKEN" "$1"
}

OLD_ISSUES=$(query_issues "$SONAR_URL/api/issues/search?projectKeys=$PROJECT_KEY&severities=CRITICAL%2CBLOCKER&resolved=false&ps=50")
NEW_ISSUES=$(query_issues "$SONAR_URL/api/issues/search?projectKeys=$PROJECT_KEY&impactSeverities=HIGH&resolved=false&ps=50")

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
