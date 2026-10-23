-- cloud-run <file>      runs a file (like the dashboard's "Run now")
-- cloud-run --listen    background helper: runs files uploaded from the CC: Cloud dashboard (event cloud_run)
local args = { ... }

local function valid(file)
    return type(file) == "string" and (file:match("^[%w_.-]+$") or file:match("^[%w_.-]+/[%w_.-]+$")) ~= nil
end

if args[1] ~= "--listen" then
    if not valid(args[1]) then
        print("Usage: cloud-run <file>")
        return
    end
    shell.run(args[1])
    return
end

print("CC: Cloud runner")
print("Waiting for scripts from the dashboard...")
while true do
    local _, file = os.pullEvent("cloud_run")
    if valid(file) and fs.exists(file) and not fs.isDir(file) then
        print("Starting " .. file)
        if multishell then
            local id = multishell.launch({ shell = shell, multishell = multishell }, "rom/programs/shell.lua", file)
            multishell.setTitle(id, file)
        else
            shell.run(file)
        end
    else
        printError("cloud_run: not a file: " .. tostring(file))
    end
end
