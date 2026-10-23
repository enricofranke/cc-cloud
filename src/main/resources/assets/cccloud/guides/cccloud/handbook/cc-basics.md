---
# Generated from the dashboard docs by tools/gen_handbook.ts – edit cloud/web/src/docs/content instead.
navigation:
  title: "CC basics"
  position: 1
  icon: computercraft:computer_advanced
---

# CC basics for beginners

New to ComputerCraft? This page explains the few things you need to run a dashboard program.

## Shell vs. Lua

A computer starts in the **shell** – a command line like a terminal. The shell runs **programs** by name (`edit`, `ls`, `cloud-demo`). It does **not** understand Lua code.

> **Warning:** If you type Lua directly into the shell, e.g. `cloud.setTitle("x")`, you get **"No such program"**. The shell looks for a program called `cloud.setTitle("x")`. Put Lua code into a file (below) or use the `lua` prompt.

## Write a program with edit

```
edit startup.lua
```

1. Type `edit startup.lua` and press Enter. An editor opens.
2. Type or paste your code. (Pasting from the dashboard: copy with the **Copy** button, then Ctrl+V in Minecraft.)
3. Press **Ctrl**, choose **Save** with the arrow keys and Enter, then **Ctrl** → **Exit**.

A file named `startup.lua` runs automatically whenever the computer boots – perfect for dashboards.

## Run a program

Type its name without `.lua`:

```
startup
```

Stop a running program with **Ctrl+T** (hold it for a second). Reboot with **Ctrl+R**.

## Drag files onto the computer

Write the code on your PC in any editor, save it as `startup.lua` and **drag the file onto the open computer screen** in Minecraft. CC: Tweaked uploads it into the current folder.

## Quick tests with the lua prompt

`lua` opens an interactive Lua prompt – handy to try one line at a time:

```
lua
lua> peripheral.find("cloud_uplink").getClients()
1
lua> exit()
```

## Useful commands

| Command | Does |
|---|---|
| `ls` | list files |
| `edit <file>` | edit or create a file |
| `rm <file>` | delete a file |
| `help cloud` | CC: Cloud quick reference |
| `cloud-demo` | shows every dashboard widget |
| `cloud-storage` | custom storage dashboard for an ME / RS bridge |
| `peripheral` | in Lua: `peripheral.getNames()` lists attached blocks |

Next: [the Lua reference](lua.md) or the [cloud.ui helper](ui-library.md).

