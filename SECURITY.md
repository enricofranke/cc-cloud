# Security policy

CC: Cloud runs a web server inside your Minecraft server, so security reports are very welcome.

## Reporting a vulnerability

Please report vulnerabilities **privately** through
[GitHub's private advisory form](https://github.com/enricofranke/cc-cloud/security/advisories/new) – not as a public
issue, a Discord post or a pull request. Include the version, your setup (singleplayer, dedicated, behind a proxy) and
the steps to reproduce. You will get an answer within a few days; fixes for confirmed issues ship in the next release
and are credited in the changelog if you like.

## Supported versions

| Version | Supported |
| --- | --- |
| 1.0.x (beta) | ✅ |
| older development builds | ❌ |

## How CC: Cloud is secured

The threat model, the defaults (LAN-only, in-game approval for every browser, hashed tokens, PINs, roles, rate and
connection limits) and the results of the security reviews are documented in [`docs/SECURITY.md`](docs/SECURITY.md).
Hosting advice for internet access: [`docs/HOSTING.md`](docs/HOSTING.md).
