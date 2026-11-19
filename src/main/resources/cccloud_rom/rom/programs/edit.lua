-- A small text editor. Arrows, Home/End, Page Up/Down move; Ctrl opens the menu (Save, Exit).

local args = { ... }
if #args < 1 then
  print("Usage: edit <file>")
  return
end
local path = shell.resolve(args[1])
if fs.isDir(path) then
  printError("Cannot edit a folder")
  return
end
local read_only = fs.isReadOnly(path)

local lines = {}
if fs.exists(path) then
  local file = fs.open(path, "r")
  local text = file.readAll() or ""
  file.close()
  for line in (text .. "\n"):gmatch("(.-)\r?\n") do lines[#lines + 1] = line end
  if #lines > 1 and lines[#lines] == "" and text:sub(-1) == "\n" then lines[#lines] = nil end
end
if #lines == 0 then lines[1] = "" end

local w, h = term.getSize()
local x, y = 1, 1 -- cursor in the text (x: 1 = before the first character)
local scroll_x, scroll_y = 0, 0
local menu = false
local menu_item = 1
local items = read_only and { "Exit" } or { "Save", "Exit" }
local status = read_only and "Read only. Press Ctrl for the menu" or "Press Ctrl for the menu"
local changed = false
local running = true
local colour = term.isColour()

local function set_colour(c)
  if colour then term.setTextColour(c) end
end

local function draw_line(row)
  local index = row + scroll_y
  term.setCursorPos(1, row)
  term.clearLine()
  local line = lines[index]
  if line then
    set_colour(colors.white)
    term.write(line:sub(scroll_x + 1, scroll_x + w))
  end
end

local function draw_status()
  term.setCursorPos(1, h)
  term.clearLine()
  if menu then
    for i, item in ipairs(items) do
      if i == menu_item then
        set_colour(colors.yellow)
        term.write("[" .. item .. "]")
      else
        set_colour(colors.white)
        term.write(" " .. item .. " ")
      end
    end
  else
    set_colour(colors.yellow)
    term.write(status)
    local pos = ("Ln %d"):format(y)
    term.setCursorPos(w - #pos + 1, h)
    term.write(pos)
  end
  set_colour(colors.white)
end

local function place_cursor()
  term.setCursorPos(x - scroll_x, y - scroll_y)
  term.setCursorBlink(not menu)
end

local function draw()
  for row = 1, h - 1 do draw_line(row) end
  draw_status()
  place_cursor()
end

-- keeps the cursor on the screen; true when the view moved
local function follow()
  local moved = false
  if x - scroll_x > w then scroll_x = x - w; moved = true end
  if x - scroll_x < 1 then scroll_x = x - 1; moved = true end
  if y - scroll_y > h - 1 then scroll_y = y - (h - 1); moved = true end
  if y - scroll_y < 1 then scroll_y = y - 1; moved = true end
  return moved
end

local function update(full)
  if follow() or full then draw() else draw_line(y - scroll_y); draw_status(); place_cursor() end
end

local function save()
  local text = table.concat(lines, "\n") .. "\n"
  local file, err = fs.open(path, "w")
  if not file then return false, err end
  local ok, problem = pcall(function()
    file.write(text)
    file.close()
  end)
  if not ok then return false, problem end
  return true
end

local function clamp_x()
  x = math.min(x, #lines[y] + 1)
end

local function do_menu()
  local item = items[menu_item]
  menu = false
  if item == "Save" then
    local ok, err = save()
    if ok then
      changed = false
      status = "Saved to /" .. path
    else
      status = "Not saved: " .. tostring(err)
    end
  elseif item == "Exit" then
    running = false
  end
end

draw()
while running do
  local event, a, b, c = os.pullEvent()
  if event == "key" then
    if menu then
      if a == keys.left then
        menu_item = menu_item > 1 and menu_item - 1 or #items
      elseif a == keys.right then
        menu_item = menu_item < #items and menu_item + 1 or 1
      elseif a == keys.enter or a == keys.numPadEnter then
        do_menu()
      elseif a == keys.leftCtrl or a == keys.rightCtrl then
        menu = false
      end
      if running then update(true) end
    elseif a == keys.leftCtrl or a == keys.rightCtrl then
      menu = true
      menu_item = 1
      draw_status()
      place_cursor()
    elseif a == keys.up then
      if y > 1 then y = y - 1; clamp_x(); update() end
    elseif a == keys.down then
      if y < #lines then y = y + 1; clamp_x(); update() end
    elseif a == keys.left then
      if x > 1 then
        x = x - 1
      elseif y > 1 then
        y = y - 1
        x = #lines[y] + 1
      end
      update()
    elseif a == keys.right then
      if x <= #lines[y] then
        x = x + 1
      elseif y < #lines then
        y = y + 1
        x = 1
      end
      update()
    elseif a == keys.home then
      x = 1; update()
    elseif a == keys["end"] then
      x = #lines[y] + 1; update()
    elseif a == keys.pageUp then
      y = math.max(1, y - (h - 1)); clamp_x(); update(true)
    elseif a == keys.pageDown then
      y = math.min(#lines, y + (h - 1)); clamp_x(); update(true)
    elseif not read_only and (a == keys.enter or a == keys.numPadEnter) then
      local line = lines[y]
      local indent = line:match("^%s*")
      table.insert(lines, y + 1, indent .. line:sub(x))
      lines[y] = line:sub(1, x - 1)
      y = y + 1
      x = #indent + 1
      changed = true
      update(true)
    elseif not read_only and a == keys.backspace then
      if x > 1 then
        lines[y] = lines[y]:sub(1, x - 2) .. lines[y]:sub(x)
        x = x - 1
        changed = true
        update()
      elseif y > 1 then
        x = #lines[y - 1] + 1
        lines[y - 1] = lines[y - 1] .. lines[y]
        table.remove(lines, y)
        y = y - 1
        changed = true
        update(true)
      end
    elseif not read_only and a == keys.delete then
      if x <= #lines[y] then
        lines[y] = lines[y]:sub(1, x - 1) .. lines[y]:sub(x + 1)
        changed = true
        update()
      elseif y < #lines then
        lines[y] = lines[y] .. lines[y + 1]
        table.remove(lines, y + 1)
        changed = true
        update(true)
      end
    elseif not read_only and a == keys.tab then
      lines[y] = lines[y]:sub(1, x - 1) .. "  " .. lines[y]:sub(x)
      x = x + 2
      changed = true
      update()
    end
  elseif (event == "char" or event == "paste") and not menu and not read_only then
    lines[y] = lines[y]:sub(1, x - 1) .. a .. lines[y]:sub(x)
    x = x + #a
    changed = true
    update()
  elseif event == "mouse_click" and not menu and c < h then
    y = math.max(1, math.min(#lines, c + scroll_y))
    x = math.max(1, math.min(#lines[y] + 1, b + scroll_x))
    update()
  elseif event == "mouse_scroll" and not menu then
    scroll_y = math.max(0, math.min(#lines - 1, scroll_y + a))
    y = math.max(scroll_y + 1, math.min(scroll_y + h - 1, y, #lines))
    clamp_x()
    draw()
  elseif event == "term_resize" then
    w, h = term.getSize()
    update(true)
  end
end

term.clear()
term.setCursorPos(1, 1)
term.setCursorBlink(false)
