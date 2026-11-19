-- Key codes of the "key" and "key_up" events (the keyboard numbers the game client uses).

local names = {}

local function add(name, code)
  _ENV[name] = code
  names[code] = names[code] or name
end

add("space", 32)
add("apostrophe", 39)
add("comma", 44)
add("minus", 45)
add("period", 46)
add("slash", 47)
local numbers = { "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine" }
for i, name in ipairs(numbers) do add(name, 47 + i) end
add("semicolon", 59)
add("equals", 61)
for i = 0, 25 do add(string.char(97 + i), 65 + i) end
add("leftBracket", 91)
add("backslash", 92)
add("rightBracket", 93)
add("grave", 96)
add("enter", 257)
add("tab", 258)
add("backspace", 259)
add("insert", 260)
add("delete", 261)
add("right", 262)
add("left", 263)
add("down", 264)
add("up", 265)
add("pageUp", 266)
add("pageDown", 267)
add("home", 268)
add("end", 269)
add("capsLock", 280)
add("scrollLock", 281)
add("numLock", 282)
add("printScreen", 283)
add("pause", 284)
for i = 1, 25 do add("f" .. i, 289 + i) end
for i = 0, 9 do add("numPad" .. i, 320 + i) end
add("numPadDecimal", 330)
add("numPadDivide", 331)
add("numPadMultiply", 332)
add("numPadSubtract", 333)
add("numPadAdd", 334)
add("numPadEnter", 335)
add("numPadEqual", 336)
add("leftShift", 340)
add("leftCtrl", 341)
add("leftAlt", 342)
add("leftSuper", 343)
add("rightShift", 344)
add("rightCtrl", 345)
add("rightAlt", 346)
add("rightSuper", 347)
add("menu", 348)

-- the name of a key code, or nil
function getName(code)
  return names[code]
end
