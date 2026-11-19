-- Lists the programs or shows the help text of one.
local args = { ... }
if #args == 0 then
  print("Programs (type help <name> for more):")
  textutils.tabulate(shell.programs())
  return
end
local name = args[1]
local path = "rom/help/" .. name .. ".txt"
if fs.exists(path) then
  local file = fs.open(path, "r")
  local text = file.readAll()
  file.close()
  textutils.pagedPrint(text)
else
  print("No help for " .. name)
end
