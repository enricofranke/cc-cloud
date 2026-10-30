---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "Accounts, roles & sharing"
  position: 12
  icon: cccloud:cloud_link_card
---

# Accounts, roles and sharing

A **device** is any browser – on a PC, a tablet or a phone. Every page of the dashboard works the same on all of them, with mouse, keyboard or touch.

## Link your player

1. In Minecraft, type `/cccloud link` (or use "Link this browser" in the mainframe screen). You get a one-time code (10 minutes).
2. Open the dashboard, enter the code and a name for this browser, click **Pair device**.
3. Click **[Allow]** in the chat.

The browser is now linked to **your player**, not to a single mainframe: it shows every mainframe where you have a role, and you switch between them with the mainframe name in the header. Browsers paired with a mainframe code before keep working for that one mainframe.

## Roles

| Role | May |
|---|---|
| viewer | watch values and alerts – no controls, no coordinates |
| operator | viewer + coordinates, buttons, toggles, sliders, redstone, crafting, enabling rules |
| admin | everything: scripts, terminal, layout editing, rules, devices, access, share links, PIN |
| owner | the player who placed the mainframe: admin + transfer and delete |

Roles are **presets**. Each player or team rank can get single permissions added or removed on top (**Custom** on the Access page) – for example an operator who may also edit the layout, or an admin without script uploads. Removing wins over adding. Without “View the dashboard” a player has no access at all. You can only give permissions you have yourself (the owner can give all of them). A server can switch scripts and the terminal off for everyone with the permission node `cccloud.feature.scripts`.

Roles come from several sources – the **highest one wins**: the owner, roles given to a player (`/cccloud role <player> <role>`, the mainframe screen or the **Access** page), team ranks and server permissions. Things you may not do are hidden or disabled, with the reason shown.

## Teams

A mainframe can be assigned to a team from **FTB Teams**, **Open Parties and Claims** or a vanilla scoreboard team. Each team rank maps to a role on that mainframe (defaults: owner/officer/leader → admin, member → operator, ally → viewer). When someone joins or leaves the team, their access changes right away.

## The Access page

Admins open it from the mainframe menu in the header (**Access & roles**):

- the list of players with their role and where it comes from
- change or remove roles given directly, add a player by name
- assign a team and choose the role for each team rank
- **Custom** next to a role: add or remove single permissions, grouped (watching, controls, storage, scripts & terminal, layout & automation, management), each with a short explanation; the button shows how many differ from the preset (e.g. “−2”)

Who may open it is a permission too (“Manage access”); share links need “Share links”.

## Devices

**Devices** in the header menu lists every browser linked to your player – name, type (computer, tablet, phone), last address and last use. Revoke a lost or old one there or in-game with `/cccloud devices` and `/cccloud revoke`.

## Share links

Admins can share a **read-only** view – for a stream overlay, a friend or a wall display:

1. Mainframe menu → **Share links** → **New link**.
2. Pick whole pages or single cards, the expiry (1 hour, 24 hours, 7 days or never), how many people may watch at once, and optionally **hide exact numbers** (only gauges are shown).
3. Copy the link – it is shown only once.

The secret part after `#` never reaches the server's logs and is removed from the address bar as soon as the page opens. The browser keeps it only in memory – after a reload or from a bookmark, open the full link again. People with the link see **"Shared by …"** and cannot press anything: there are no controls at all, and the server refuses every action from a share link. Never included: coordinates, item search, the network map and history export. Revoking a link disconnects everyone viewing it immediately.

## Server permissions

Server admins control CC: Cloud with **LuckPerms** or **FTB Ranks** (or the op level when no permission mod is installed):

| Node | Default | Allows |
|---|---|---|
| `cccloud.use` | everyone | placing and using the blocks, linking browsers |
| `cccloud.admin.view` | op | viewing any mainframe |
| `cccloud.admin.operate` | op | operating any mainframe |
| `cccloud.admin.manage` | op | managing devices and roles of any mainframe |
| `cccloud.limit.mainframes` | 3 | mainframes per player |

When an admin looks at someone else's mainframe, the owner sees it in the log.

## Keyboard

Tab moves through everything, Enter or Space activates, Escape closes dialogs and menus, the arrow keys move between page tabs and menu entries, and "Skip to content" jumps past the header.

