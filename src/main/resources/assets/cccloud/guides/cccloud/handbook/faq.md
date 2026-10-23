---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Troubleshooting"
  position: 10
  icon: cccloud:cloud_analyzer
---

# Troubleshooting and FAQ

## "No such program"

You typed Lua code into the **shell**. The shell only runs programs. Put the code into a file with `edit startup.lua` and run `startup`, or type `lua` first. See [CC basics](cc-basics.md).

## "No widgets yet" / empty card

- The uplink must be **on a computer** (or a bridge) and **linked** with the Link Card.
- A computer without its own program shows the automatic Computer card. If your program cleared all widgets (`cloud.clear()`), the card comes back after the program ends.
- Your program must actually run: put it in `startup.lua` so it starts after a reboot or chunk load.

## The bridge does not show up

- The uplink must sit **directly on the bridge**, not on a cable next to it.
- The bridge must be part of a powered network (ME: it needs a channel – check your controller and cable channel count; RS: the controller must have energy).
- Link the **uplink**, not the bridge, with the Link Card.

## The dashboard is not reachable

- Use the URL from the mainframe screen. `localhost` only works on the PC that runs Minecraft.
- The browser's device and the Minecraft server must be in the same network (e.g. the same Wi-Fi, not mobile data) – or see [internet access](security.md).
- Allow Java through the PC firewall for private networks.
- Port 8787 must be free; if not, the server log says so and an admin can change `webPort`.
- On a hosted server the port must be opened, or use a reverse proxy / tunnel.

## "Mainframe has no power"

The FE buffer is empty. Connect a generator or FE cable to the mainframe; it reconnects by itself once the buffer refills. See [power](index.md).

## "Access revoked"

The device was removed (in the mainframe screen or with `/cccloud revoke`), it expired after 30 days, or the player who approved it lost access. Pair again with a new code.

## "LAN only"

The server only allows local addresses. An admin can enable `webAllowInternet` – preferably behind HTTPS. See [security](security.md).

## Controls are greyed out

The dashboard is disabled, the mainframe is offline or without power, the uplink is offline, or your device is read-only. The banner at the top says which.

## Wrong PIN / locked

After 5 wrong PINs the device is locked for 5 minutes. The owner can change the PIN with `/cccloud pin set`.

