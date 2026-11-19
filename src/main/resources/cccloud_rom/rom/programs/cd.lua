-- Changes the current folder.
local args = { ... }
if #args < 1 then
  print("Usage: cd <folder>")
  return
end
local target = shell.resolve(args[1])
if fs.isDir(target) then
  shell.setDir(target)
else
  printError("Not a directory")
end
