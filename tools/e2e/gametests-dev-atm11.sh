#!/usr/bin/env bash
# Runs the cccloud GameTests together with ALL mods of the ATM11 server pack.
#
# Why not in the real server: a production NeoForge install never ticks GameTests. GameTestHooks.isGametestEnabled()
# returns false as soon as FMLEnvironment.isProduction() is true, before it looks at -Dneoforge.enableGameTest or the
# GameTestServer entrypoint. Verified on this pack: `/test run cccloud:*` (from RCON or a command block) leaves all
# 8 tests in status "cleared" forever, and launching the pack with net.neoforged.fml.startup.GameTestServer spins
# (thousands of ticks/s, ~100 MB log in 10 min) without ever starting a test.
#
# So this uses the repo's dev GameTestServer run (ModDevGradle, built from HEAD in a throwaway worktree) and puts the
# ATM11 server pack's mods, config and kubejs into its run directory. CC: Tweaked comes from the dev classpath, cccloud
# from the build output; AE2 and RS come from the pack (no -PstorageMods). Only cccloud:* tests run (other mods ship
# data-driven test instances, some of which never finish).
# STATUS (2026-10-23, ATM11 0.12.0-beta): does not get to the tests yet. A dev run is stricter than production and the
# pack trips it: Jade rethrows The Undergarden's plugin error, and NeoForge throws on duplicate creative-tab entries
# (MysticalAgriculture, then Theurgy, ...) when CC rebuilds the tabs on server start. DEV_EXCLUDE removes such mods;
# extend it until the run starts, at the cost of a less faithful mod set.
# Usage: tools/e2e/gametests-dev-atm11.sh      (needs setup-server.sh to have run; the test server must be stopped)
set -uo pipefail
source "$(dirname "$0")/env.sh"
TIMEOUT="${GAMETEST_TIMEOUT:-1500}"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "the test server is running; stop it first (RAM)" >&2; exit 1
fi
WT="$SCRATCH/cccloud-head"
if [ ! -d "$WT" ]; then
  git -C "$REPO_DIR" worktree add --detach "$WT" HEAD >/dev/null || exit 1
fi
echo "worktree $WT at $(git -C "$WT" rev-parse --short HEAD)"
(cd "$WT" && ./gradlew createGameTestServerLaunchScript --max-workers=3 -q) || exit 1

RUN="$WT/run"
mkdir -p "$RUN/mods"
rm -f "$RUN"/mods/*.jar
# Mods whose harmless production errors become fatal in a dev run (Jade rethrows plugin errors outside production):
# The Undergarden's Jade plugin fails to instantiate; NeoForge rejects MysticalAgriculture's duplicate creative-tab
# entries (IllegalArgumentException when CC rebuilds the tabs on server start). Space-separated jar name prefixes.
DEV_EXCLUDE="${DEV_EXCLUDE:-The_Undergarden MysticalAg MysticalAutomation MysticalCustomization matc-}"
excluded=""
for jar in "$SERVER_DIR"/mods/*.jar; do
  name="$(basename "$jar")"
  case "$name" in cc-tweaked-*|cccloud-*) continue ;; esac
  skip=false
  for x in $DEV_EXCLUDE; do case "$name" in "$x"*) skip=true ;; esac; done
  if [ "$skip" = true ]; then excluded="$excluded $name"; continue; fi
  ln -s "$jar" "$RUN/mods/"
done
[ -n "$excluded" ] && echo "excluded from the dev run:$excluded"
rsync -a --delete "$SERVER_DIR/config/" "$RUN/config/"
for d in kubejs local; do [ -d "$SERVER_DIR/$d" ] && rsync -a --delete "$SERVER_DIR/$d/" "$RUN/$d/"; done
rm -rf "$RUN/gametestserver"
echo "run dir: $(ls "$RUN/mods" | wc -l | tr -d ' ') pack mods linked"

M="$WT/build/moddev"
REPORT="$SCRATCH/gametest-dev-atm11-report.xml"
OUT="$SCRATCH/gametest-dev-atm11.log"
rm -f "$REPORT"
cd "$RUN"
"$JAVA" @"$M/gameTestServerRunClasspath.txt" @"$M/gameTestServerRunVmArgs.txt" -Xmx"$SERVER_XMX" \
  "-Dfml.modFolders=cccloud%%$WT/build/classes/java/main:cccloud%%$WT/build/resources/main" \
  net.neoforged.devlaunch.Main @"$M/gameTestServerRunProgramArgs.txt" --tests 'cccloud:*' --report "$REPORT" \
  </dev/null >"$OUT" 2>&1 &
pid=$!
start=$(date +%s)
while kill -0 "$pid" 2>/dev/null; do
  if [ $(( $(date +%s) - start )) -gt "$TIMEOUT" ]; then echo "timeout, stopping" >&2; kill "$pid"; sleep 15; kill -9 "$pid" 2>/dev/null; break; fi
  sleep 5
done
wait "$pid"; rc=$?
echo "exit code $rc after $(( $(date +%s) - start ))s; log: $OUT"
grep -vE "GameTestServer\]: \[" "$OUT" | grep -E "tests are now running|will be run|passed|failed|required|cccloud:[a-z_]+|Exception in|FATAL" \
  | grep -vE "RecipeManager|Jupiter|KubeJS|potionsmaster" | cut -c1-600 | tail -40
[ -f "$REPORT" ] && { echo "== junit report =="; cat "$REPORT"; echo; }
exit $rc
