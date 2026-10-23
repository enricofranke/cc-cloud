#!/usr/bin/env node
// Seeds dashboard credentials into world/data/cccloud/web.dat of the STOPPED test server.
//
// Why: pairing needs an in-game approval by a player (owner/trusted/op) and every /cccloud command needs a player.
// No player can join this headless server, and the mod has no RCON/console path for codes or approvals
// (CloudWebServer.issueTestToken exists but is Java-only, used by the load GameTest). So the test writes the same
// records the mod would write after a real approval:
//   - a device token (stored as SHA-256 like AuthStore does) owned by the fake owner E2E_OWNER, canControl=true
//   - a pairing code for the mainframe (so POST /api/pair can be tested up to the "pending" state)
//   - the mainframe ACL (owner = E2E_OWNER); the mainframe re-syncs it from its block entity on load anyway
// Output: $SCRATCH/e2e-device.json with the token (never committed).
//   node tools/e2e/seed-device.mjs
import { readNbtFile, writeNbtFile, plain, S, L, B, C } from "./nbt.mjs";
import { createHash, randomBytes } from "node:crypto";
import { readFileSync, writeFileSync, existsSync, copyFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { homedir, tmpdir } from "node:os";

const SERVER_DIR = process.env.SERVER_DIR ?? join(homedir(), "atm11-testserver");
const SCRATCH = process.env.SCRATCH ?? join(tmpdir(), "cccloud-e2e");
const pidFile = join(SERVER_DIR, "e2e-server.pid");
if (existsSync(pidFile)) {
  try { process.kill(Number(readFileSync(pidFile, "utf8")), 0); console.error("server is running; stop it first"); process.exit(1); } catch {}
}
const scenario = JSON.parse(readFileSync(join(SCRATCH, "scenario.json"), "utf8"));
const { mainframeId, owner } = scenario;
const file = join(SERVER_DIR, "world/data/cccloud/web.dat");
mkdirSync(join(SERVER_DIR, "world/data/cccloud"), { recursive: true });
const doc = existsSync(file) ? readNbtFile(file) : { name: "", root: C({ data: C({}), DataVersion: { type: "int", value: 4790 } }) };
if (existsSync(file)) copyFileSync(file, `${file}.e2e-backup`);
const data = doc.root.value.data.value;

const now = Date.now();
const token = randomBytes(32).toString("base64url");
const hash = createHash("sha256").update(token, "utf8").digest("hex");
const deviceName = "e2e-dashboard";

// devices: list of compounds
const devices = data.devices?.value ?? { elementType: "compound", items: [] };
devices.elementType = "compound";
devices.items = devices.items.filter((d) => d.name?.value !== deviceName);
devices.items.push({
  hash: S(hash), mainframe: S(mainframeId), name: S(deviceName), can_control: B(true),
  created: L(now), last_used: L(now), player: S(owner.uuid), player_name: S(owner.name),
});
data.devices = { type: "list", value: devices };

// pairing code (alphabet ABCDEFGHJKMNPQRSTUVWXYZ23456789), valid for 2 hours
const code = "E2EPAWS7";
data.pairing_codes = data.pairing_codes ?? C({});
data.pairing_codes.value[mainframeId] = C({ code: S(code), expires: L(now + 2 * 3600_000), issuer: S(owner.uuid) });

data.acls = data.acls ?? C({});
data.acls.value[mainframeId] = C({ owner: S(owner.uuid), trusted: { type: "list", value: { elementType: "string", items: [] } } });

// v0.6+: per-mainframe access (owner + grants); older versions ignore this key.
data.access = data.access ?? C({});
data.access.value[mainframeId] = C({ owner: S(owner.uuid), owner_name: S(owner.name) });

writeNbtFile(file, doc);
const check = plain(readNbtFile(file).root);
const out = { mainframeId, token, deviceName, pairingCode: code, owner, seededAt: new Date(now).toISOString() };
mkdirSync(SCRATCH, { recursive: true });
writeFileSync(join(SCRATCH, "e2e-device.json"), JSON.stringify(out, null, 2), { mode: 0o600 });
console.log(`seeded device '${deviceName}' and pairing code ${code} for mainframe ${mainframeId}`);
console.log(`web.dat now: ${check.data.devices.length} device(s), codes for ${Object.keys(check.data.pairing_codes).length} mainframe(s)`);
