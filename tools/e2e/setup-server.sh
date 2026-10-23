#!/usr/bin/env bash
# Sets up (or refreshes) the headless ATM11 test server in $SERVER_DIR:
#   1. downloads the official ATM11 server pack from CurseForge (skipped if SERVERPACK_ZIP is given or cached)
#   2. extracts it and installs NeoForge
#   3. adds CC: Tweaked (copied from the player's client instance, read-only) and a cccloud jar built from HEAD
#      in a throwaway git worktree (so other agents' uncommitted work is never picked up)
#   4. writes eula.txt, server.properties (localhost only, RCON with a random password) and the cccloud server config
# Usage: tools/e2e/setup-server.sh [--skip-build] [--jar path/to/cccloud.jar]
set -euo pipefail
source "$(dirname "$0")/env.sh"

SKIP_BUILD=false
CCCLOUD_JAR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-build) SKIP_BUILD=true ;;
    --jar) CCCLOUD_JAR="$2"; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "The test server is running (pid $(cat "$PID_FILE")). Stop it first: tools/e2e/stop-server.sh" >&2
  exit 1
fi

mkdir -p "$SCRATCH" "$SERVER_DIR"

# ---- 1. server pack -----------------------------------------------------------------------------------------------
ZIP="${SERVERPACK_ZIP:-$SCRATCH/serverpack/$ATM11_SERVERPACK_NAME}"
if [ ! -f "$ZIP" ] || [ "$(stat -f %z "$ZIP")" != "$ATM11_SERVERPACK_SIZE" ]; then
  mkdir -p "$(dirname "$ZIP")"
  # The CurseForge website API redirects to the official CDN (mediafilez.forgecdn.net).
  meta=$(curl -fsS -A "Mozilla/5.0" "https://www.curseforge.com/api/v1/mods/$ATM11_PROJECT_ID/files/$ATM11_SERVERPACK_FILE_ID")
  echo "$meta" | grep -q "\"fileName\":\"$ATM11_SERVERPACK_NAME\"" || { echo "unexpected file metadata: $meta" >&2; exit 1; }
  echo "Downloading $ATM11_SERVERPACK_NAME ..."
  curl -fSL -A "Mozilla/5.0" -o "$ZIP" "https://www.curseforge.com/api/v1/mods/$ATM11_PROJECT_ID/files/$ATM11_SERVERPACK_FILE_ID/download"
fi
[ "$(stat -f %z "$ZIP")" = "$ATM11_SERVERPACK_SIZE" ] || { echo "server pack has the wrong size" >&2; exit 1; }

if [ ! -f "$SERVER_DIR/startserver.sh" ]; then
  echo "Extracting server pack into $SERVER_DIR"
  unzip -q -o "$ZIP" -d "$SERVER_DIR"
fi

# ---- 2. NeoForge --------------------------------------------------------------------------------------------------
if [ ! -f "$SERVER_DIR/libraries/net/neoforged/neoforge/$NEOFORGE_VERSION/unix_args.txt" ]; then
  echo "Installing NeoForge $NEOFORGE_VERSION"
  (cd "$SERVER_DIR" && "$JAVA" -jar "neoforge-$NEOFORGE_VERSION-installer.jar" -installServer >"$SCRATCH/neoforge-install.log" 2>&1)
fi

# ---- 3. mods ------------------------------------------------------------------------------------------------------
cp "$CLIENT_INSTANCE/mods/$CCT_JAR" "$SERVER_DIR/mods/$CCT_JAR"

if [ -z "$CCCLOUD_JAR" ] && [ "$SKIP_BUILD" = false ]; then
  WT="$SCRATCH/cccloud-head"
  if [ -d "$WT" ]; then git -C "$REPO_DIR" worktree remove --force "$WT"; fi
  git -C "$REPO_DIR" worktree add --detach "$WT" HEAD >/dev/null
  echo "Building cccloud from $(git -C "$WT" rev-parse --short HEAD) in $WT"
  (cd "$WT" && ./gradlew build --max-workers=3 >"$SCRATCH/cccloud-build.log" 2>&1) || { tail -40 "$SCRATCH/cccloud-build.log"; exit 1; }
  CCCLOUD_JAR=$(ls -t "$WT"/build/libs/cccloud-*.jar | grep -v -e sources -e javadoc | head -1)
fi
if [ -n "$CCCLOUD_JAR" ]; then
  rm -f "$SERVER_DIR"/mods/cccloud-*.jar
  cp "$CCCLOUD_JAR" "$SERVER_DIR/mods/"
fi
ls "$SERVER_DIR"/mods/cccloud-*.jar >/dev/null || { echo "no cccloud jar in $SERVER_DIR/mods" >&2; exit 1; }

# ---- 4. config ----------------------------------------------------------------------------------------------------
echo "eula=true" >"$SERVER_DIR/eula.txt"   # the user's own local test server

if [ ! -f "$RCON_PASSWORD_FILE" ]; then
  (umask 077; openssl rand -hex 16 >"$RCON_PASSWORD_FILE")
fi
cat >"$SERVER_DIR/server.properties" <<EOF
motd=ATM11 cccloud e2e test server
server-ip=127.0.0.1
server-port=$MC_PORT
online-mode=true
enable-rcon=true
rcon.port=$RCON_PORT
rcon.password=$(cat "$RCON_PASSWORD_FILE")
broadcast-rcon-to-ops=false
enable-query=false
level-name=world
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
max-players=2
max-tick-time=180000
simulation-distance=5
view-distance=6
allow-flight=true
difficulty=peaceful
spawn-monsters=false
# Without players a 1.21.2+ server stops ticking after 60 s; the tests need it to keep running.
pause-when-empty-seconds=-1
EOF

# cccloud is a SERVER config. NeoForge 26.1 keeps server configs in <server>/config (not world/serverconfig).
CFG="$SERVER_DIR/config/cccloud-server.toml"
if [ -f "$CFG" ]; then
  sed -i '' -E "s/^([[:space:]]*webPort = ).*/\1$WEB_PORT/; s/^([[:space:]]*webBind = ).*/\1\"127.0.0.1\"/; s/^([[:space:]]*webEnabled = ).*/\1true/" "$CFG"
else
  mkdir -p "$SERVER_DIR/config"
  printf 'allowCloud = true\n\n[web]\n\twebEnabled = true\n\twebBind = "127.0.0.1"\n\twebPort = %s\n' "$WEB_PORT" >"$CFG"
fi

# JVM args: the pack's file plus the GameTest namespace for cccloud; heap capped at $SERVER_XMX (shared Mac).
grep -v -e '^-Xm[sx]' -e '^#' -e '^$' "$SERVER_DIR/user_jvm_args.txt" >"$SERVER_DIR/e2e_jvm_args.txt.tmp"
{
  echo "-Xms$SERVER_XMS"
  echo "-Xmx$SERVER_XMX"
  grep -v -e 'AlwaysPreTouch' "$SERVER_DIR/e2e_jvm_args.txt.tmp"
  # Registers the cccloud test instances (visible to /test). A production install never ticks them though, see
  # gametests-dev-atm11.sh for how the GameTests are run with the pack's mods.
  echo "-Dneoforge.enabledGameTestNamespaces=cccloud"
} >"$SERVER_DIR/e2e_jvm_args.txt"
rm "$SERVER_DIR/e2e_jvm_args.txt.tmp"

echo "Setup done: $SERVER_DIR (mods: $(ls "$SERVER_DIR/mods" | wc -l | tr -d ' ') jars, cccloud: $(basename "$(ls "$SERVER_DIR"/mods/cccloud-*.jar)"))"
