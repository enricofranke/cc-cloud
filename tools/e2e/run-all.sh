#!/usr/bin/env bash
# Full end-to-end run of CC: Cloud inside the All the Mods 11 server pack (headless, localhost only):
#   setup (pack + NeoForge + CC + cccloud from HEAD) -> start -> log check -> build the world scenario (RS + AE2 +
#   bridges + mainframe + computers) -> stop -> seed a dashboard device -> start -> dashboard protocol test ->
#   screenshots -> log check -> stop [-> GameTests with the pack's mods (dev run) when RUN_GAMETESTS=true].
# Every step is also usable on its own. Output goes to $SCRATCH (default $TMPDIR/cccloud-e2e).
# A fresh world is used when FRESH_WORLD=true (deletes $SERVER_DIR/world).
set -uo pipefail
source "$(dirname "$0")/env.sh"
export SCRATCH SERVER_DIR
mkdir -p "$SCRATCH"
step() { echo; echo "######## $*"; }
fail=0

step "setup";      "$E2E_DIR/setup-server.sh" "$@" || exit 1
if [ "${FRESH_WORLD:-false}" = true ]; then rm -rf "$SERVER_DIR/world"; fi
step "start";      "$E2E_DIR/start-server.sh" || exit 1
step "log check (startup)"; "$E2E_DIR/check-log.sh" >"$SCRATCH/startup-check.txt"; tail -4 "$SCRATCH/startup-check.txt"
step "scenario";   node "$E2E_DIR/scenario.mjs" build || fail=1
step "stop";       "$E2E_DIR/stop-server.sh" || fail=1
step "seed device"; node "$E2E_DIR/seed-device.mjs" || exit 1
step "start";      "$E2E_DIR/start-server.sh" || exit 1
sleep 20           # bridges and Lua probes refresh every 1-5 s
step "scenario status"; node "$E2E_DIR/scenario.mjs" status || fail=1
step "dashboard";  node "$E2E_DIR/dashboard.mjs" || fail=1
step "screenshots"; node "$E2E_DIR/screenshots.mjs" || fail=1
step "log check";  "$E2E_DIR/check-log.sh" >"$SCRATCH/final-check.txt"; tail -4 "$SCRATCH/final-check.txt"
step "stop";       "$E2E_DIR/stop-server.sh" || fail=1
if [ "${RUN_GAMETESTS:-false}" = true ]; then   # see the STATUS note in gametests-dev-atm11.sh
  step "GameTests (dev run with the ATM11 mods)"; "$E2E_DIR/gametests-dev-atm11.sh" || fail=1
fi
echo; echo "done (failures: $fail); results in $SCRATCH"
exit $fail
