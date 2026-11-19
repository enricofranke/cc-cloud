-- Prints the content of files.
local args = { ... }
if #args < 1 then
  print("Usage: cat <file> ...")
  return
end
for _, name in ipairs(args) do
  local path = shell.resolve(name)
  if fs.isDir(path) then
    printError(name .. ": is a folder")
  else
    local file, err = fs.open(path, "r")
    if not file then
      printError(err)
    else
      local text = file.readAll() or ""
      file.close()
      write(text)
      if text ~= "" and text:sub(-1) ~= "\n" then print() end
    end
  end
end
