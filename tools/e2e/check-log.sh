#!/usr/bin/env bash
# Collects every ERROR/WARN line (plus the following stack-trace lines) that mentions cccloud, computercraft,
# refinedstorage or ae2 from the test server log, and the cccloud lifecycle lines.
# Usage: tools/e2e/check-log.sh [logfile]   (default: $SERVER_DIR/logs/latest.log)
set -uo pipefail
source "$(dirname "$0")/env.sh"
LOG="${1:-$LOG_FILE}"
PATTERN='cccloud|computercraft|cc-tweaked|cc\.tweaked|dan200|refinedstorage|refined_storage|com\.refinedmods|appeng|\bae2\b|appliedenergistics'

echo "== cccloud lifecycle =="
grep -E "Dashboard web server|\[cccloud\]" "$LOG" | grep -vE "pressed|approved|denied" | head -40

echo
echo "== ERROR/WARN lines mentioning our mods or the storage mods =="
# A log record starts with "[HH:MM:SS]"; continuation lines (stack traces) belong to the previous record.
awk -v pat="$PATTERN" '
  /^\[[^]]*[0-9][0-9]:[0-9][0-9]:[0-9][0-9]/ {
    if (rec != "" && keep) print rec "\n";
    rec = $0; keep = 0; lvl = ($0 ~ /\/(ERROR|WARN|FATAL)\]/); if (lvl && tolower($0) ~ pat) keep = 1; next
  }
  { if (rec != "") { if (length(rec) < 6000) rec = rec "\n" $0; if (lvl && tolower($0) ~ pat) keep = 1 } }
  END { if (rec != "" && keep) print rec }
' "$LOG"

echo
echo "== counts =="
printf "ERROR lines total: %s\n" "$(grep -cE '/(ERROR|FATAL)\]' "$LOG")"
printf "WARN lines total:  %s\n" "$(grep -cE '/WARN\]' "$LOG")"
printf "matching records:  %s\n" "$(awk -v pat="$PATTERN" '/^\[[^]]*[0-9][0-9]:[0-9][0-9]:[0-9][0-9]/ { if ($0 ~ /\/(ERROR|WARN|FATAL)\]/ && tolower($0) ~ pat) n++ } END { print n+0 }' "$LOG")"
