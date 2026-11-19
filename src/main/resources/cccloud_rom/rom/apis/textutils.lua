-- Turning values into text and back: Lua table syntax, JSON, time and simple tables.

local keywords = {}
for word in ("and break do else elseif end false for function goto if in local nil not or repeat return then true until while"):gmatch("%a+") do
  keywords[word] = true
end

local function sorted_keys(t)
  local list = {}
  for k in pairs(t) do list[#list + 1] = k end
  table.sort(list, function(a, b)
    local ta, tb = type(a), type(b)
    if ta ~= tb then return ta < tb end
    if ta == "number" or ta == "string" then return a < b end
    return tostring(a) < tostring(b)
  end)
  return list
end

local function quote(s)
  local text = ("%q"):format(s):gsub("\\\n", "\\n")
  return text
end

local function number_text(n)
  if n ~= n then return "0/0" end
  if n == math.huge then return "1/0" end
  if n == -math.huge then return "-1/0" end
  if math.floor(n) == n and math.abs(n) < 2 ^ 53 then return ("%d"):format(n) end
  return ("%.17g"):format(n)
end

local function serialize_value(value, indent, compact, seen, depth)
  local t = type(value)
  if t == "string" then return quote(value) end
  if t == "number" then return number_text(value) end
  if t == "boolean" or t == "nil" then return tostring(value) end
  if t ~= "table" then error("cannot serialize type " .. t, 0) end
  if seen[value] then error("cannot serialize a table that contains itself", 0) end
  if depth > 64 then error("table nested too deeply", 0) end
  if next(value) == nil then return "{}" end
  seen[value] = true
  local n = #value
  local inner = indent .. (compact and "" or "  ")
  local parts = {}
  for i = 1, n do parts[#parts + 1] = serialize_value(value[i], inner, compact, seen, depth + 1) end
  for _, k in ipairs(sorted_keys(value)) do
    if not (type(k) == "number" and k >= 1 and k <= n and math.floor(k) == k) then
      local key
      if type(k) == "string" and k:match("^[%a_][%w_]*$") and not keywords[k] then
        key = k
      else
        key = "[" .. serialize_value(k, inner, compact, seen, depth + 1) .. "]"
      end
      parts[#parts + 1] = key .. (compact and "=" or " = ") .. serialize_value(value[k], inner, compact, seen, depth + 1)
    end
  end
  seen[value] = nil
  if compact then return "{" .. table.concat(parts, ",") .. "}" end
  return "{\n" .. inner .. table.concat(parts, ",\n" .. inner) .. ",\n" .. indent .. "}"
end

-- a value as Lua source text; opts.compact = true writes it on one line
function serialize(value, opts)
  local compact = type(opts) == "table" and opts.compact or false
  return serialize_value(value, "", compact, {}, 0)
end
serialise = serialize

-- the value of text made by serialize, or nil (it is loaded without access to anything)
function unserialize(text)
  if type(text) ~= "string" then error("bad argument #1 (string expected, got " .. type(text) .. ")", 2) end
  local fn = load("return " .. text, "=unserialize", "t", {})
  if not fn then return nil end
  local ok, result = pcall(fn)
  if ok then return result end
  return nil
end
unserialise = unserialize

-- ---- JSON -------------------------------------------------------------------------------------------------------------

local escapes = { ['"'] = '\\"', ["\\"] = "\\\\", ["\b"] = "\\b", ["\f"] = "\\f", ["\n"] = "\\n", ["\r"] = "\\r", ["\t"] = "\\t" }

local function json_string(s)
  local text = s:gsub('[%c"\\]', function(c) return escapes[c] or ("\\u%04x"):format(c:byte()) end)
  return '"' .. text .. '"'
end

local function json_value(value, seen, depth)
  local t = type(value)
  if t == "nil" then return "null" end
  if t == "boolean" then return tostring(value) end
  if t == "number" then
    if value ~= value or value == math.huge or value == -math.huge then error("cannot write " .. tostring(value) .. " as JSON", 0) end
    return number_text(value)
  end
  if t == "string" then return json_string(value) end
  if t ~= "table" then error("cannot write type " .. t .. " as JSON", 0) end
  if seen[value] then error("cannot write a table that contains itself", 0) end
  if depth > 64 then error("table nested too deeply", 0) end
  seen[value] = true
  local parts = {}
  local n = #value
  local count = 0
  for _ in pairs(value) do count = count + 1 end
  local out
  if n > 0 and count == n then
    for i = 1, n do parts[i] = json_value(value[i], seen, depth + 1) end
    out = "[" .. table.concat(parts, ",") .. "]"
  elseif count == 0 then
    out = value == emptyArray and "[]" or "{}"
  else
    for _, k in ipairs(sorted_keys(value)) do
      if type(k) == "string" or type(k) == "number" then
        parts[#parts + 1] = json_string(tostring(k)) .. ":" .. json_value(value[k], seen, depth + 1)
      end
    end
    out = "{" .. table.concat(parts, ",") .. "}"
  end
  seen[value] = nil
  return out
end

emptyArray = setmetatable({}, { __newindex = function() error("emptyArray cannot be changed", 2) end })

-- a value as JSON text
function serializeJSON(value)
  return json_value(value, {}, 0)
end
serialiseJSON = serializeJSON

local function json_error(text, pos, message)
  error(("JSON: %s at position %d"):format(message, pos), 0)
end

local function skip(text, pos)
  return text:find("[^ \t\r\n]", pos) or #text + 1
end

local decode

local function decode_string(text, pos)
  local out = {}
  local i = pos + 1
  while true do
    local c = text:sub(i, i)
    if c == "" then json_error(text, i, "unfinished string") end
    if c == '"' then return table.concat(out), i + 1 end
    if c == "\\" then
      local e = text:sub(i + 1, i + 1)
      local map = { b = "\b", f = "\f", n = "\n", r = "\r", t = "\t", ['"'] = '"', ["\\"] = "\\", ["/"] = "/" }
      if map[e] then
        out[#out + 1] = map[e]
        i = i + 2
      elseif e == "u" then
        local hex = text:sub(i + 2, i + 5)
        if not hex:match("^%x%x%x%x$") then json_error(text, i, "bad escape") end
        local code = tonumber(hex, 16)
        out[#out + 1] = code < 256 and string.char(code) or "?"
        i = i + 6
      else
        json_error(text, i, "bad escape")
      end
    else
      out[#out + 1] = c
      i = i + 1
    end
  end
end

decode = function(text, pos, depth)
  if depth > 64 then json_error(text, pos, "nested too deeply") end
  pos = skip(text, pos)
  local c = text:sub(pos, pos)
  if c == "{" then
    local result = {}
    pos = skip(text, pos + 1)
    if text:sub(pos, pos) == "}" then return result, pos + 1 end
    while true do
      pos = skip(text, pos)
      if text:sub(pos, pos) ~= '"' then json_error(text, pos, "name expected") end
      local key
      key, pos = decode_string(text, pos)
      pos = skip(text, pos)
      if text:sub(pos, pos) ~= ":" then json_error(text, pos, "':' expected") end
      result[key], pos = decode(text, pos + 1, depth + 1)
      pos = skip(text, pos)
      local d = text:sub(pos, pos)
      if d == "}" then return result, pos + 1 end
      if d ~= "," then json_error(text, pos, "',' or '}' expected") end
      pos = pos + 1
    end
  elseif c == "[" then
    local result = {}
    pos = skip(text, pos + 1)
    if text:sub(pos, pos) == "]" then return result, pos + 1 end
    while true do
      result[#result + 1], pos = decode(text, pos, depth + 1)
      pos = skip(text, pos)
      local d = text:sub(pos, pos)
      if d == "]" then return result, pos + 1 end
      if d ~= "," then json_error(text, pos, "',' or ']' expected") end
      pos = pos + 1
    end
  elseif c == '"' then
    return decode_string(text, pos)
  elseif text:sub(pos, pos + 3) == "true" then
    return true, pos + 4
  elseif text:sub(pos, pos + 4) == "false" then
    return false, pos + 5
  elseif text:sub(pos, pos + 3) == "null" then
    return nil, pos + 4
  else
    local number = text:match("^-?%d+%.?%d*[eE]?[-+]?%d*", pos)
    if not number or number == "" then json_error(text, pos, "unexpected character") end
    local value = tonumber(number)
    if not value then json_error(text, pos, "bad number") end
    return value, pos + #number
  end
end

-- the value of JSON text, or nil and the problem
function unserializeJSON(text)
  if type(text) ~= "string" then error("bad argument #1 (string expected, got " .. type(text) .. ")", 2) end
  local ok, value, pos = pcall(decode, text, 1, 0)
  if not ok then return nil, value end
  if skip(text, pos) <= #text then return nil, "JSON: trailing characters" end
  return value
end
unserialiseJSON = unserializeJSON

-- ---- time and tables --------------------------------------------------------------------------------------------------

-- an hour value (as os.time gives it) as "6:30 AM" or, with twentyFour, "6:30"
function formatTime(time, twentyFour)
  local hours = math.floor(time) % 24
  local minutes = math.floor((time - math.floor(time)) * 60)
  if twentyFour then return ("%d:%02d"):format(hours, minutes) end
  local suffix = hours >= 12 and "PM" or "AM"
  local h = hours % 12
  if h == 0 then h = 12 end
  return ("%d:%02d %s"):format(h, minutes, suffix)
end

-- prints lists in columns; a number argument sets the text colour of the lists after it
function tabulate(...)
  local w = term.getSize()
  local items, longest = {}, 0
  for i = 1, select("#", ...) do
    local list = select(i, ...)
    if type(list) == "table" then
      for _, v in ipairs(list) do
        local s = tostring(v)
        items[#items + 1] = { s, items.colour }
        if #s > longest then longest = #s end
      end
    elseif type(list) == "number" then
      items.colour = list
    end
  end
  if #items == 0 then return end
  local column = longest + 2
  local columns = math.max(1, math.floor(w / column))
  for i, item in ipairs(items) do
    if item[2] and term.isColour() then term.setTextColour(item[2]) end
    if i % columns == 0 or i == #items then
      print(item[1])
    else
      write(item[1] .. (" "):rep(column - #item[1]))
    end
  end
  if term.isColour() then term.setTextColour(colors.white) end
end
pagedTabulate = tabulate

-- prints text, waiting for a key after each screen full; returns the lines printed
function pagedPrint(text)
  local _, h = term.getSize()
  local lines = 0
  for line in (tostring(text) .. "\n"):gmatch("(.-)\n") do
    lines = lines + write(line .. "\n")
    if lines >= h - 1 then
      write("Press any key to continue")
      os.pullEvent("key")
      term.clearLine()
      local _, y = term.getCursorPos()
      term.setCursorPos(1, y)
      lines = 0
    end
  end
  return lines
end
