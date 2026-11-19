-- Deletes files or folders.
local args = { ... }
if #args < 1 then
  print("Usage: rm <path> ...")
  return
end
for _, name in ipairs(args) do
  local path = shell.resolve(name)
  if not fs.exists(path) then
    printError(name .. ": not found")
  elseif fs.isReadOnly(path) then
    printError(name .. ": cannot delete read-only files")
  else
    local ok, err = pcall(fs.delete, path)
    if not ok then printError(err) end
  end
end
