-- An interactive Lua prompt: type an expression or a statement, see the result. exit() ends it.

local running = true
local history = {}
local env = setmetatable({
  exit = function() running = false end,
  _echo = function(...) return ... end,
}, { __index = _ENV })

local function show(value)
  if type(value) == "table" then
    local ok, text = pcall(textutils.serialize, value)
    if ok then return text end
  end
  return tostring(value)
end

if term.isColour() then term.setTextColour(colors.yellow) end
print("Interactive Lua prompt. Call exit() to leave.")
if term.isColour() then term.setTextColour(colors.white) end

while running do
  if term.isColour() then term.setTextColour(colors.yellow) end
  write("lua> ")
  if term.isColour() then term.setTextColour(colors.white) end
  local ok, line = pcall(read, nil, history)
  if not ok then
    if line == "Terminated" then
      print()
      break
    end
    printError(line)
  elseif line:match("%S") then
    if history[#history] ~= line then history[#history + 1] = line end
    if #history > 50 then table.remove(history, 1) end
    -- an expression first (so "1 + 2" prints 3), then a statement
    local fn, err = load("return _echo(" .. line .. "\n)", "=lua", "t", env)
    if not fn then fn, err = load(line, "=lua", "t", env) end
    if not fn then
      printError(err)
    else
      local results = table.pack(pcall(fn))
      if not results[1] then
        printError(results[2])
      else
        for i = 2, results.n do
          if term.isColour() then term.setTextColour(colors.lightGray) end
          print(show(results[i]))
        end
        if term.isColour() then term.setTextColour(colors.white) end
      end
    end
  end
end
