-- Makes folders.
local args = { ... }
if #args < 1 then
  print("Usage: mkdir <folder> ...")
  return
end
for _, name in ipairs(args) do
  local ok, err = pcall(fs.makeDir, shell.resolve(name))
  if not ok then printError(err) end
end
