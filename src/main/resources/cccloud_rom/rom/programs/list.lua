-- Lists the files and folders of a folder (default: the current one).
local args = { ... }
local target = shell.resolve(args[1] or ".")
if not fs.isDir(target) then
  printError("Not a directory")
  return
end
local show_hidden = args[2] == "-a" or args[1] == "-a"
local dirs, files = {}, {}
for _, name in ipairs(fs.list(target)) do
  if show_hidden or name:sub(1, 1) ~= "." then
    if fs.isDir(fs.combine(target, name)) then dirs[#dirs + 1] = name else files[#files + 1] = name end
  end
end
if term.isColour() then
  textutils.tabulate(colors.green, dirs, colors.white, files)
else
  textutils.tabulate(dirs, files)
end
