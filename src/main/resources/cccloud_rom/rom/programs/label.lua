-- Shows, sets or clears the computer's label.
local args = { ... }
local command = args[1]
if command == "get" or command == nil then
  local label = os.getComputerLabel()
  if label then print("Label: \"" .. label .. "\"") else print("No label") end
  if command == nil then print("Usage: label get | label set <text> | label clear") end
elseif command == "set" then
  local text = table.concat(args, " ", 2)
  if text == "" then
    print("Usage: label set <text>")
    return
  end
  local ok, err = pcall(os.setComputerLabel, text)
  if ok then print("Label set to \"" .. os.getComputerLabel() .. "\"") else printError(err) end
elseif command == "clear" then
  os.setComputerLabel(nil)
  print("Label cleared")
else
  print("Usage: label get | label set <text> | label clear")
end
