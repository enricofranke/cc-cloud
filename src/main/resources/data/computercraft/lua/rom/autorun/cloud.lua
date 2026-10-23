-- CC: Cloud script import helper (runs at boot on every computer; does nothing without a Cloud Uplink).
-- 1. A file uploaded with "restart and run" (.cloud/run_next) runs once after boot.
-- 2. Advanced computers with an uplink get a background "cloud" tab that runs uploaded files on request
--    (event cloud_run). Switch it off with: set cloud.runner false
if not peripheral.find("cloud_uplink") then return end

if fs.exists(".cloud/run_next") then
    local handle = fs.open(".cloud/run_next", "r")
    local file = handle and handle.readAll()
    if handle then handle.close() end
    fs.delete(".cloud/run_next")
    if file and file:match("^[%w_.-]+$") or file and file:match("^[%w_.-]+/[%w_.-]+$") then
        if multishell then
            local id = multishell.launch({ shell = shell, multishell = multishell }, "rom/programs/shell.lua", file)
            multishell.setTitle(id, file)
        else
            shell.run(file)
        end
    end
end

if multishell and settings.get("cloud.runner", true) ~= false then
    local id = multishell.launch({ shell = shell, multishell = multishell }, "rom/programs/cloud-run.lua", "--listen")
    multishell.setTitle(id, "cloud")
end
