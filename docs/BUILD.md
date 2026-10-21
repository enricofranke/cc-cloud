# Building and testing CC: Cloud

Requirements: JDK 25 (Gradle's toolchain resolver downloads it if missing). Keep Gradle at most 3 workers on this machine:

```sh
./gradlew build --max-workers=3              # compile, unit tests, jar (build/libs/cccloud-<version>.jar)
./gradlew runGameTestServer --max-workers=3  # GameTests without AE2/RS (bridge tests are skipped by their conditions)
./gradlew runGameTestServer -PstorageMods --max-workers=3   # GameTests with AE2 + Refined Storage
./gradlew runServer -PstorageMods --max-workers=3           # dev server with both mods
./gradlew runClient -PstorageMods --max-workers=3
```

## Optional storage mods (Cloud Bridges)
The bridges compile against the versions in All the Mods 11. No local jars are needed; Gradle downloads them:

| Mod | Coordinates | Repository |
| --- | --- | --- |
| Applied Energistics 2 26.1.13-beta | `org.appliedenergistics:appliedenergistics2:26.1.13-beta:api` (compileOnly), full jar for `-PstorageMods` | https://maven.neoforged.net/releases |
| GuideME 26.1.15-beta (AE2 runtime dependency) | `org.appliedenergistics:guideme:26.1.15-beta` | https://maven.neoforged.net/releases |
| Refined Storage neoforge 3.2.1 | `maven.modrinth:refined-storage:P12kjgPK` | https://api.modrinth.com/maven |

Refined Storage 2 only publishes to GitHub Packages (needs a token), so the Modrinth maven is used. Modrinth addresses files by version id because the Fabric and NeoForge builds share the version number `3.2.1`; `P12kjgPK` is `refinedstorage-neoforge-3.2.1.jar` (SHA-1 `1bd09d5e51f82418d8602dec1e8fc6883beb852d`, identical to the ATM11 jar). Versions live in `gradle.properties` (`ae2_version`, `guideme_version`, `rs_modrinth_version`).

Without `-PstorageMods` the dev runs contain neither mod; that is the soft-dependency check (the server must start and all non-bridge GameTests must pass).

## Tests
- Unit tests (`src/test`): token/PIN/pairing, rate limits, routing, message parsing (incl. `query`, `layout_set`, op values), widget validation for every widget type, the layout schema, the bridge index/watchlist logic, the WebHub snapshot/layout/chart behaviour, and the ROM Lua (`cloud.ui`, `cloud-demo`, `cloud-storage`) running on Cobalt, CC's Lua VM (`cc.tweaked:cobalt`, test-only), against a fake uplink that validates through the real `WidgetFactory`.
- GameTests (`data/cccloud/test_instance`): mainframe placement/ownership, the load test (50 clients, 4×32 widgets, target < 0.5 ms/tick), the ROM mount contents, and with `-PstorageMods` one AE2 and one RS2 network test (creative energy cell + drive + 1k cell, creative controller + 1k storage block) that insert items and check the bridge, its peripheral and the direct-mode Storage template.
- Headless smoke test (with and without `-PstorageMods`): enable RCON for localhost in `run/server.properties` (`enable-rcon=true`, `server-ip=127.0.0.1`, a random `rcon.password`; `run/` is not committed), start `runServer` until `Done`, check the log for `[cccloud]` errors and "Dashboard web server listening", probe the WebSocket (a tiny auth frame gets `unauthorized`/4401, a 9 KB frame before auth gets 1009), then send `stop` over RCON (Gradle does not forward stdin) and check for "Dashboard web server stopped", exit code 0 and that ports 8787/25565/25575 are free.
