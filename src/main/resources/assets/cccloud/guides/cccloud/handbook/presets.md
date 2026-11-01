---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Dashboard presets"
  position: 3
  icon: cccloud:cloud_display
---

# Dashboard presets

A **preset** is a ready-made dashboard page. It says which kinds of devices it wants and where their widgets go. Your dashboard fits it to the devices you have and adds it as a **new page**, so what you already built stays as it is.

A preset is layout data and nothing else: no Lua, nothing that runs, nothing that gets installed.

## Your first dashboard

As long as nobody has arranged a mainframe's dashboard, people who may edit the layout see **Start with a preset** above the cards: the official preset that fits this base best, one click away. It puts the new page in front and your automatic cards stay on their own pages. **Undo** in the message that follows goes back to how it was.

**Not now** hides the offer for this mainframe in this browser. Other mainframes still ask, and the **Presets** tab stays available. An empty page offers **Add a preset**, too.

## Add a preset

1. Press the **pencil**, then **+ Add**, then the **Presets** tab.
2. Choose where presets come from: **Official**, **Community** or **Code**.
3. Pick one. Each preset says how it suits this base: **Fits**, **Partly** or **Missing devices**. The best match is marked as recommended.
4. Check the live preview and what the preset uses, then press **Add as a new page**.

The preset lands on new pages and nothing else changes. Like every change in the editor, **Undo** takes it back and **Save** keeps it. A dashboard holds up to 12 pages. If a preset needs more free pages than there are, it says so and you delete a page first.

| Official preset | Made for |
|---|---|
| Base overview | the most important values of every device; works with any base |
| Power grid | everything that stores, makes and measures power |
| ME/RS command centre | capacity, item search, watchlist and crafting jobs |
| Reactor control | core values and limits beside the controls, tanks on a second page |
| Farm and production | farms, machines and full outputs, production on a second page |
| Doors and redstone | doors, gates and redstone outputs with your scenes |
| Alerts and status | every warning light and every value with a limit |
| Phone quick panel | one narrow column for the phone |

Official presets are part of the mod and need neither the store nor the internet.

## Which device goes where

A preset has **slots**: "a battery", "up to eight farms". The dashboard fills each slot from the devices it sees, working ones first, and each device fills one slot. If more devices fit than a slot holds, the preset asks you to choose; otherwise there is nothing to choose.

A slot can be optional. If it stays empty, that part of the preset is left out. If a required slot stays empty, the preset says what is missing and cannot be added yet.

## Share a page

Open the page, press the **pencil**, go to **+ Add**, **Presets**, and use **Share this page as preset**. You get a **code** that starts with `ccp1.`. It holds the layout and what kind of device each part needs: no device ids, no values, no coordinates. Cards named after a device take the name of the device that fills them on the other base.

To use a code, paste it under **Code** and press **Check code**. A code needs no server and no internet. It carries a checksum, so a cut-off code is refused with a clear message instead of doing half the job, and limits keep a code from getting too large.

Anyone can write a code, so a pasted code is marked **Community, unsigned**. It cannot run anything, but look at what it puts on your dashboard before you add it.

## Community presets

The **Community** tab lists the presets of the CC: Cloud store. Your **server** loads them, checks the signature and the SHA-256 and only then hands them to the dashboard; the browser fetches nothing itself. This needs the right to edit the layout. A server with the [Script Store](lua.md) switched off shows no community presets. Official presets and long codes keep working.

Each store preset also has a **short code**, its id, for example `door-panel`. Paste it under **Code**. The website shows every store preset with its short and long code.

A store preset can name the script it is made for. The dashboard then offers a button into the Script Store, where the usual steps apply: you see the full source and you decide. The preset installs nothing.

To publish a preset, start from a shared page and follow the submission guide on the website.

## What a preset cannot do

- **Run anything.** It holds positions, titles, icons and colours. The dashboard checks every field against a fixed format and refuses everything else.
- **Change a control.** Buttons, switches and sliders keep what they do, their name, their confirmation and their PIN. Who may use them stays the same, and a preset cannot rename them.
- **Show more than you may see.** Restricted sections (claims, waystones, the server's numbers) are used only by a slot that asks for them by name, and only appear for people who may see them. They are never part of a [share link](accounts.md).
- **Act on its own.** Nothing changes until someone with the right to edit the layout presses the button.

