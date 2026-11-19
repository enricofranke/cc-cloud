-- Copies a file or folder.
local args = { ... }
if #args < 2 then
  print("Usage: cp <from> <to>")
  return
end
local from, to = shell.resolve(args[1]), shell.resolve(args[2])
if fs.isDir(to) then to = fs.combine(to, fs.getName(from)) end
local ok, err = pcall(fs.copy, from, to)
if not ok then printError(err) end
