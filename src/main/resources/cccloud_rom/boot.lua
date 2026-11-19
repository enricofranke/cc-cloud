-- CC: Cloud computer boot code. Sets up the Lua side of the APIs, then runs the shell
-- in a coroutine and feeds it events.

local native = __native
_G.__native = nil

local type, error, tostring, select, pairs, ipairs = type, error, tostring, select, pairs, ipairs
local co_create, co_resume, co_status, co_yield = coroutine.create, coroutine.resume, coroutine.status, coroutine.yield
local pack, unpack = table.pack, table.unpack

local function expect(n, value, ...)
  local t = type(value)
  for i = 1, select("#", ...) do
    if t == select(i, ...) then return value end
  end
  local names = table.concat({ ... }, " or ")
  error(("bad argument #%d (%s expected, got %s)"):format(n, names, t), 3)
end
_G.expect = expect

-- ---- os ---------------------------------------------------------------------------------------------------------------

local std_time = os.time

function os.version()
  return "CloudOS 1.0"
end

function os.pullEventRaw(filter)
  return co_yield(filter)
end

function os.pullEvent(filter)
  local event = pack(co_yield(filter))
  if event[1] == "terminate" then error("Terminated", 0) end
  return unpack(event, 1, event.n)
end

function os.time(kind)
  if type(kind) == "table" then return std_time(kind) end
  if kind == nil or kind == "ingame" then return native.gameTime() end
  if kind == "utc" or kind == "local" then return os.epoch("utc") / 3600000 % 24 end
  error("bad argument #1 to 'os.time' (unsupported operation)", 2)
end

function os.sleep(seconds)
  local timer = os.startTimer(seconds or 0)
  repeat
    local _, id = os.pullEvent("timer")
  until id == timer
end
sleep = os.sleep

-- ---- term: a redirectable front for the screen --------------------------------------------------------------------------

local screen = {}
for name, fn in pairs(term) do screen[name] = fn end
local current = screen

term = {}
for name in pairs(screen) do
  term[name] = function(...) return current[name](...) end
end
function term.native() return screen end
function term.current() return current end
function term.redirect(target)
  expect(1, target, "table")
  local old = current
  current = target
  return old
end

-- ---- output -----------------------------------------------------------------------------------------------------------

function write(text)
  text = tostring(text)
  local w, h = term.getSize()
  local x, y = term.getCursorPos()
  local lines = 0
  local function newline()
    if y >= h then
      term.scroll(1)
      term.setCursorPos(1, h)
    else
      term.setCursorPos(1, y + 1)
    end
    x, y = term.getCursorPos()
    lines = lines + 1
  end
  while #text > 0 do
    local space = text:match("^[ \t]+")
    if space then
      term.write(space)
      x, y = term.getCursorPos()
      text = text:sub(#space + 1)
    elseif text:sub(1, 1) == "\n" then
      newline()
      text = text:sub(2)
    else
      local word = text:match("^[^ \t\n]+")
      text = text:sub(#word + 1)
      if #word > w then
        while #word > 0 do
          if x > w then newline() end
          local part = word:sub(1, w - x + 1)
          term.write(part)
          word = word:sub(#part + 1)
          x, y = term.getCursorPos()
        end
      else
        if x > 1 and x + #word - 1 > w then newline() end
        term.write(word)
        x, y = term.getCursorPos()
      end
    end
  end
  return lines
end

function print(...)
  local n = select("#", ...)
  local parts = {}
  for i = 1, n do parts[i] = tostring((select(i, ...))) end
  return write(table.concat(parts, " ") .. "\n")
end

function printError(...)
  local old
  if term.isColour() then
    old = term.getTextColour()
    term.setTextColour(16384)
  end
  print(...)
  if old then term.setTextColour(old) end
end

-- read a line with editing, history and an optional replacement character
function read(replace, history, complete, default)
  if replace ~= nil then replace = tostring(replace):sub(1, 1) end
  local line = default and tostring(default) or ""
  local pos = #line
  local hist = history and #history + 1 or nil
  local w = term.getSize()
  local sx, sy = term.getCursorPos()
  term.setCursorBlink(true)

  local function redraw(clear)
    local scroll = 0
    if sx + pos >= w then scroll = sx + pos - w end
    term.setCursorPos(sx, sy)
    local shown = replace and replace:rep(#line) or line
    local visible = shown:sub(scroll + 1, scroll + w - sx + 1)
    if clear then
      term.write((" "):rep(#visible + 1))
    else
      term.write(visible)
      term.write(" ")
    end
    term.setCursorPos(sx + pos - scroll, sy)
  end

  local function set(text)
    redraw(true)
    line = text
    pos = #line
    redraw()
  end

  redraw()
  while true do
    local event, a = os.pullEvent()
    if event == "char" then
      line = line:sub(1, pos) .. a .. line:sub(pos + 1)
      pos = pos + 1
      redraw()
    elseif event == "paste" then
      line = line:sub(1, pos) .. a .. line:sub(pos + 1)
      pos = pos + #a
      redraw()
    elseif event == "key" then
      if a == keys.enter or a == keys.numPadEnter then
        break
      elseif a == keys.left then
        if pos > 0 then pos = pos - 1; redraw() end
      elseif a == keys.right then
        if pos < #line then pos = pos + 1; redraw() end
      elseif a == keys.home then
        pos = 0; redraw()
      elseif a == keys["end"] then
        pos = #line; redraw()
      elseif a == keys.backspace then
        if pos > 0 then
          redraw(true)
          line = line:sub(1, pos - 1) .. line:sub(pos + 1)
          pos = pos - 1
          redraw()
        end
      elseif a == keys.delete then
        if pos < #line then
          redraw(true)
          line = line:sub(1, pos) .. line:sub(pos + 2)
          redraw()
        end
      elseif (a == keys.up or a == keys.down) and history and #history > 0 then
        if a == keys.up and hist > 1 then
          hist = hist - 1
        elseif a == keys.down and hist <= #history then
          hist = hist + 1
        end
        set(history[hist] or "")
      elseif a == keys.tab and complete then
        local options = complete(line)
        if options and options[1] then set(line .. options[1]) end
      end
    elseif event == "term_resize" then
      w = term.getSize()
      redraw()
    end
  end
  local _, cy = term.getCursorPos()
  term.setCursorBlink(false)
  local _, h = term.getSize()
  if cy >= h then
    term.scroll(1)
    term.setCursorPos(1, h)
  else
    term.setCursorPos(1, cy + 1)
  end
  return line
end

-- ---- fs.open: file handles as tables ----------------------------------------------------------------------------------

function fs.open(path, mode)
  expect(1, path, "string")
  expect(2, mode, "string")
  local id, err = native.open(path, mode)
  if not id then return nil, err end
  local handle = {}
  local closed = false
  local function check()
    if closed then error("attempt to use a closed file", 3) end
  end
  if mode:sub(1, 1) == "r" then
    function handle.read(count) check() return native.read(id, count) end
    function handle.readLine(withEnd) check() return native.readLine(id, withEnd) end
    function handle.readAll() check() return native.readAll(id) end
    function handle.seek(whence, offset) check() return native.seek(id, whence, offset) end
  else
    local binary = mode:find("b", 1, true) ~= nil
    function handle.write(value)
      check()
      if binary and type(value) == "number" then value = string.char(value % 256) end
      native.write(id, tostring(value))
    end
    function handle.writeLine(value) check() native.write(id, tostring(value) .. "\n") end
    function handle.flush() check() native.flush(id) end
  end
  function handle.close()
    if closed then return end
    closed = true
    native.close(id)
  end
  return handle
end

-- ---- loading programs -------------------------------------------------------------------------------------------------

local function read_file(path)
  return native.readFile(path)
end

function loadfile(path, mode, env)
  if type(mode) == "table" and env == nil then env, mode = mode, nil end
  local source, err = read_file(path)
  if not source then return nil, err end
  return load(source, "@/" .. fs.combine(path), mode or "t", env or _G)
end

function dofile(path)
  local fn, err = loadfile(path)
  if not fn then error(err, 2) end
  return fn()
end

function os.run(env, path, ...)
  expect(1, env, "table")
  expect(2, path, "string")
  setmetatable(env, { __index = _G })
  local fn, err = loadfile(path, nil, env)
  if not fn then
    printError(err)
    return false
  end
  local ok, problem = pcall(fn, ...)
  if not ok then
    if problem and problem ~= "" then printError(problem) end
    return false
  end
  return true
end

-- ---- peripheral.call: waits for the answer of the machine ------------------------------------------------------------

do
  local call_async = peripheral.callAsync
  function peripheral.call(side, widget, value)
    expect(1, side, "string")
    expect(2, widget, "string")
    local id = call_async(side, widget, value)
    while true do
      local _, answer, ok, err = os.pullEvent("peripheral_result")
      if answer == id then return ok, err end
    end
  end
end

-- ---- APIs written in Lua ----------------------------------------------------------------------------------------------

for _, name in ipairs({ "colors", "keys", "textutils" }) do
  local env = setmetatable({}, { __index = _G })
  local fn, err = loadfile("rom/apis/" .. name .. ".lua", nil, env)
  if not fn then error(err, 0) end
  fn()
  local api = {}
  for k, v in pairs(env) do api[k] = v end
  _G[name] = api
end
colours = {}
for k, v in pairs(colors) do colours[k] = v end
colours.grey, colours.lightGrey = colors.gray, colors.lightGray
rs = redstone

-- ---- run the shell ----------------------------------------------------------------------------------------------------

local function show(message)
  term.redirect(screen)
  if term.isColour() then term.setTextColour(colors.red) end
  print(message)
  term.setTextColour(colors.white)
end

local shell = co_create(function()
  local ok, err = pcall(function()
    os.run({}, "rom/programs/shell.lua")
  end)
  if not ok then show(err) end
end)

local filter
local event = { n = 0 }
while true do
  local ok, result = co_resume(shell, unpack(event, 1, event.n))
  if not ok then
    show(result)
    break
  end
  if co_status(shell) == "dead" then break end
  filter = result
  repeat
    event = pack(native.pull())
  until filter == nil or event[1] == filter or event[1] == "terminate"
end
-- the shell ended: the computer turns off
