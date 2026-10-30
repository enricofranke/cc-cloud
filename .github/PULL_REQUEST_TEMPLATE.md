## What and why

<!-- One or two sentences. Link the issue if there is one. -->

## Checks

- [ ] `./gradlew build` (unit tests) and, for game logic, `./gradlew runGameTestServer -PskipLoadTest`
- [ ] Dashboard changes: `cd cloud && npm run typecheck && npx vitest run`, bundle rebuilt with `npm run build`
- [ ] Docs, handbook and `CHANGELOG.md` ("Unreleased") updated where behaviour changes
- [ ] No Minecraft or third-party textures added (icons are exported at build time)

<!-- Script Store submissions go to https://github.com/enricofranke/cc-cloud-scripts instead. -->
