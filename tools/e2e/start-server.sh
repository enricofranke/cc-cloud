#!/usr/bin/env bash
# Starts the ATM11 test server in the background and waits until it is ready ("Done (...)!" in the log).
# Refuses to start if one of its ports is taken (the player's game must never be affected).
set -euo pipefail
source "$(dirname "$0")/env.sh"
TIMEOUT="${START_TIMEOUT:-900}"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "already running (pid $(cat "$PID_FILE"))"; exit 0
fi
for p in "$MC_PORT" "$RCON_PORT" "$WEB_PORT"; do
  if port_busy "$p"; then echo "port $p is in use, not starting:" >&2; lsof -nP -iTCP:"$p" -sTCP:LISTEN >&2; exit 1; fi
done
[ -f "$SERVER_DIR/e2e_jvm_args.txt" ] || { echo "run tools/e2e/setup-server.sh first" >&2; exit 1; }

cd "$SERVER_DIR"
[ -f "$LOG_FILE" ] && mv "$LOG_FILE" "$SERVER_DIR/logs/previous-e2e.log" || true
# stdin from /dev/null: the console is not used, everything goes through RCON.
nohup "$JAVA" @e2e_jvm_args.txt @"libraries/net/neoforged/neoforge/$NEOFORGE_VERSION/unix_args.txt" nogui \
  </dev/null >"$CONSOLE_LOG" 2>&1 &
echo $! >"$PID_FILE"
echo "started pid $(cat "$PID_FILE"), waiting for Done (max ${TIMEOUT}s)"

start=$(date +%s)
while true; do
  if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "server exited during startup; last console lines:" >&2; tail -40 "$CONSOLE_LOG" >&2; exit 1
  fi
  if grep -qE 'Done \([0-9.]+s\)! For help' "$CONSOLE_LOG" 2>/dev/null; then break; fi
  if [ $(( $(date +%s) - start )) -gt "$TIMEOUT" ]; then echo "timeout waiting for startup" >&2; exit 1; fi
  sleep 5
done
echo "ready after $(( $(date +%s) - start ))s: $(grep -oE 'Done \([0-9.]+s\)' "$CONSOLE_LOG" | tail -1)"
grep -E "Dashboard web server" "$CONSOLE_LOG" | tail -2 || true
