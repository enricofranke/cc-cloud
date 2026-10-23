#!/usr/bin/env bash
# Stops the test server cleanly over RCON ("stop"), waits for the JVM to exit and checks that its ports are free.
# Only kills the process as a last resort (after STOP_TIMEOUT seconds).
set -uo pipefail
source "$(dirname "$0")/env.sh"
TIMEOUT="${STOP_TIMEOUT:-180}"

pid=""
[ -f "$PID_FILE" ] && pid=$(cat "$PID_FILE")
if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
  echo "server not running"; rm -f "$PID_FILE"
else
  node "$E2E_DIR/rcon.mjs" "save-all flush" "stop" || echo "RCON stop failed" >&2
  start=$(date +%s)
  while kill -0 "$pid" 2>/dev/null; do
    if [ $(( $(date +%s) - start )) -gt "$TIMEOUT" ]; then
      echo "server did not stop within ${TIMEOUT}s, sending SIGTERM" >&2
      kill "$pid"; sleep 20; kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
      break
    fi
    sleep 2
  done
  echo "server stopped after $(( $(date +%s) - start ))s"
  rm -f "$PID_FILE"
fi
grep -E "Dashboard web server stopped" "$LOG_FILE" 2>/dev/null | tail -1
rc=0
for p in "$MC_PORT" "$RCON_PORT" "$WEB_PORT"; do
  if port_busy "$p"; then echo "port $p still in use!" >&2; rc=1; fi
done
[ $rc = 0 ] && echo "ports $MC_PORT/$RCON_PORT/$WEB_PORT are free"
exit $rc
