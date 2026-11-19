-- The command line: runs programs by name, keeps the current folder and runs "startup" at boot.

local parent = shell
local dir = parent and parent.dir() or ""
local path = parent and parent.path() or ".:/rom/programs"
local aliases = parent and parent.aliases() or { ls = "list", dir = "list", cp = "copy", mv = "move", rm = "delete", del = "delete", h = "help" }
local running = {}
local history = {}
local quit = false

local function split(line)
  local words = {}
  local quoted = false
  for part in (line .. '"'):gmatch('(.-)"') do
    if quoted then
      words[#words + 1] = part
    else
      for word in part:gmatch("%S+") do words[#words + 1] = word end
    end
    quoted = not quoted
  end
  return words
end

local api = {}

function api.dir()
  return dir
end

function api.setDir(new)
  expect(1, new, "string")
  local resolved = fs.combine(new)
  if not fs.isDir(resolved) then error("Not a directory", 2) end
  dir = resolved
end

function api.path()
  return path
end

function api.setPath(new)
  expect(1, new, "string")
  path = new
end

function api.aliases()
  local copy = {}
  for k, v in pairs(aliases) do copy[k] = v end
  return copy
end

function api.setAlias(name, program)
  aliases[name] = program
end

function api.clearAlias(name)
  aliases[name] = nil
end

-- a path relative to the current folder ("/" at the start means from the top)
function api.resolve(p)
  expect(1, p, "string")
  if p:sub(1, 1) == "/" or p:sub(1, 1) == "\\" then return fs.combine(p) end
  return fs.combine(dir, p)
end

-- the file of a program name, or nil
function api.resolveProgram(name)
  expect(1, name, "string")
  name = aliases[name] or name
  if name:find("[/\\]") then
    local p = api.resolve(name)
    if fs.exists(p) and not fs.isDir(p) then return p end
    if fs.exists(p .. ".lua") and not fs.isDir(p .. ".lua") then return p .. ".lua" end
    if fs.exists(p .. ".wasm") and not fs.isDir(p .. ".wasm") then return p .. ".wasm" end
    return nil
  end
  for entry in path:gmatch("[^:]+") do
    local base = entry == "." and dir or fs.combine(entry)
    local p = fs.combine(base, name)
    if fs.exists(p) and not fs.isDir(p) then return p end
    if fs.exists(p .. ".lua") and not fs.isDir(p .. ".lua") then return p .. ".lua" end
    if fs.exists(p .. ".wasm") and not fs.isDir(p .. ".wasm") then return p .. ".wasm" end
  end
  return nil
end

-- the names of every program on the path
function api.programs(hidden)
  local seen, list = {}, {}
  for entry in path:gmatch("[^:]+") do
    local base = entry == "." and dir or fs.combine(entry)
    if fs.isDir(base) then
      for _, file in ipairs(fs.list(base)) do
        local p = fs.combine(base, file)
        if not fs.isDir(p) and (hidden or file:sub(1, 1) ~= ".") then
          local name = file:gsub("%.lua$", ""):gsub("%.wasm$", "")
          if not seen[name] then
            seen[name] = true
            list[#list + 1] = name
          end
        end
      end
    end
  end
  table.sort(list)
  return list
end

function api.getRunningProgram()
  return running[#running]
end

-- runs a program with arguments; returns whether it worked
function api.run(...)
  local words = split(table.concat({ ... }, " "))
  local name = words[1]
  if not name then return false end
  local file = api.resolveProgram(name)
  if not file then
    printError("No such program")
    return false
  end
  running[#running + 1] = file
  local env = { shell = api, arg = { [0] = name, table.unpack(words, 2) } }
  local ok = os.run(env, file, table.unpack(words, 2))
  running[#running] = nil
  return ok
end

-- ends this shell after the current program
function api.exit()
  quit = true
end

shell = api

local function prompt()
  if term.isColour() then term.setTextColour(colors.yellow) end
  write("/" .. dir .. "> ")
  if term.isColour() then term.setTextColour(colors.white) end
end

if not parent then
  term.setBackgroundColour(colors.black)
  term.setTextColour(colors.white)
  term.clear()
  term.setCursorPos(1, 1)
  if term.isColour() then term.setTextColour(colors.lightBlue) end
  print(os.version())
  if term.isColour() then term.setTextColour(colors.white) end
  for _, name in ipairs({ "startup.lua", "startup" }) do
    if fs.exists(name) and not fs.isDir(name) then
      api.run("/" .. name)
      break
    end
  end
end

local args = { ... }
if #args > 0 then
  api.run(table.unpack(args))
  return
end

while not quit do
  prompt()
  local ok, line = pcall(read, nil, history)
  if not ok then
    if line == "Terminated" then
      print()
    else
      printError(line)
    end
  elseif line:match("%S") then
    if history[#history] ~= line then history[#history + 1] = line end
    if #history > 100 then table.remove(history, 1) end
    api.run(line)
  end
end
