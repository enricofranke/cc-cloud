#!/usr/bin/env bash
# The test server with the machine gallery's own world and the dev commands (never the player's game or the cluster).
#   tools/e2e/gallery-server.sh install   copies the jar in build/libs to the server (gradle: jar -PdevBuild=99)
#   tools/e2e/gallery-server.sh start     starts it (world "gallery"; server.properties stays as it is)
#   tools/e2e/gallery-server.sh stop
set -euo pipefail
source "$(dirname "$0")/env.sh"
case "${1:-start}" in
  install)
    jar=$(ls -t "$REPO_DIR"/build/libs/cccloud-*.jar | grep -v -e sources -e javadoc -e modpack | head -1)
    rm -f "$SERVER_DIR"/mods/cccloud-*.jar
    cp "$jar" "$SERVER_DIR/mods/"
    echo "installed $(basename "$jar")" ;;
  start)
    E2E_WORLD="${E2E_WORLD:-gallery}" E2E_JVM="-Dcccloud.devCommands=true" "$E2E_DIR/start-server.sh" ;;
  stop)
    "$E2E_DIR/stop-server.sh" ;;
  *) echo "usage: $0 install|start|stop" >&2; exit 2 ;;
esac
