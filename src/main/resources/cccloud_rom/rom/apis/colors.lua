-- The 16 terminal colours as bit values, and helpers to combine them.

white = 0x1
orange = 0x2
magenta = 0x4
lightBlue = 0x8
yellow = 0x10
lime = 0x20
pink = 0x40
gray = 0x80
lightGray = 0x100
cyan = 0x200
purple = 0x400
blue = 0x800
brown = 0x1000
green = 0x2000
red = 0x4000
black = 0x8000

local digits = "0123456789abcdef"

-- all given colours as one set
function combine(...)
  local result = 0
  for i = 1, select("#", ...) do
    result = bit32.bor(result, (select(i, ...)))
  end
  return result
end

-- the set without the given colours
function subtract(set, ...)
  local result = set
  for i = 1, select("#", ...) do
    result = bit32.band(result, bit32.bnot((select(i, ...))))
  end
  return result
end

-- whether a set holds a colour
function test(set, colour)
  return bit32.band(set, colour) == colour
end

-- 0xRRGGBB from three parts between 0 and 1
function packRGB(r, g, b)
  local function part(v) return math.floor(math.max(0, math.min(1, v)) * 255 + 0.5) end
  return part(r) * 65536 + part(g) * 256 + part(b)
end

-- three parts between 0 and 1 from 0xRRGGBB
function unpackRGB(rgb)
  return math.floor(rgb / 65536) % 256 / 255, math.floor(rgb / 256) % 256 / 255, rgb % 256 / 255
end

-- the blit digit of a colour
function toBlit(colour)
  local index = math.floor(math.log(colour, 2) + 0.5)
  if index < 0 or index > 15 or 2 ^ index ~= colour then error("invalid colour " .. tostring(colour), 2) end
  return digits:sub(index + 1, index + 1)
end

-- the colour of a blit digit, or nil
function fromBlit(digit)
  local index = digits:find(tostring(digit):lower(), 1, true)
  if not index or #tostring(digit) ~= 1 then return nil end
  return math.floor(2 ^ (index - 1))
end
