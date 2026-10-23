# Shared settings for the ATM11 end-to-end test server scripts. Source this file.
# Every value can be overridden from the environment.

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
REPO_DIR="$(cd "$E2E_DIR/../.." && pwd)"

# Where the dedicated test server lives (never the player's client instance).
SERVER_DIR="${SERVER_DIR:-$HOME/atm11-testserver}"
# The player's CurseForge instance: READ-ONLY, only used to copy the CC: Tweaked jar.
CLIENT_INSTANCE="${CLIENT_INSTANCE:-$HOME/Documents/curseforge/minecraft/Instances/All the Mods 11 - ATM11}"

# Official ATM11 server pack (CurseForge project 1148445, file "ServerFiles-0.12.0-beta.zip").
ATM11_PROJECT_ID="${ATM11_PROJECT_ID:-1148445}"
ATM11_SERVERPACK_FILE_ID="${ATM11_SERVERPACK_FILE_ID:-9187108}"
ATM11_SERVERPACK_NAME="${ATM11_SERVERPACK_NAME:-ServerFiles-0.12.0-beta.zip}"
ATM11_SERVERPACK_SIZE="${ATM11_SERVERPACK_SIZE:-622597985}"
NEOFORGE_VERSION="${NEOFORGE_VERSION:-26.1.2.109}"
CCT_JAR="${CCT_JAR:-cc-tweaked-26.1.2-forge-1.120.0.jar}"

JAVA="${JAVA:-/opt/homebrew/opt/openjdk@25/bin/java}"
# The Mac is shared with the player's game: never more than 8 GB.
SERVER_XMX="${SERVER_XMX:-8G}"
SERVER_XMS="${SERVER_XMS:-2G}"

# Ports: none of them may collide with the player's game (25565, cccloud web 8787).
MC_PORT="${MC_PORT:-25570}"
RCON_PORT="${RCON_PORT:-25576}"
WEB_PORT="${WEB_PORT:-8797}"

SCRATCH="${SCRATCH:-${TMPDIR:-/tmp}/cccloud-e2e}"
LOG_FILE="$SERVER_DIR/logs/latest.log"
CONSOLE_LOG="$SERVER_DIR/e2e-console.log"
PID_FILE="$SERVER_DIR/e2e-server.pid"
RCON_PASSWORD_FILE="$SERVER_DIR/.e2e-rcon-password"

port_busy() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }
