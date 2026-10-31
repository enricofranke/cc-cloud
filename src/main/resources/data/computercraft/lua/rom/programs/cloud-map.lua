-- A map of your base on a canvas: every node of the network at its real position, with its links. Click a node for its name.
-- Needs a Cloud Mainframe next to the computer or on its wired network.
-- Usage: cloud-map [uplink]

local ui = require("cloud.ui")
local dash = ui.connect(...)
local mainframe = ui.mainframe()
if not mainframe then error("cloud-map reads the network from the mainframe: put this computer next to a Cloud Mainframe or on its wired network.", 0) end

dash:clear()
dash:title("Base map")
dash:group("map", "Base map", { w = 12, h = 20, color = "cyan", icon = "home" })
local W, H, MARGIN = 320, 180, 16
local selected
local nodes = {}

local canvas
canvas = dash:canvas("map", "Network", { width = W, height = H, input = "click", bg = "bg", group = "map", h = 18 }, function(_, ev)
    local best, distance = nil, 64
    for _, node in ipairs(nodes) do
        local d = (node.sx - ev.x) ^ 2 + (node.sy - ev.y) ^ 2
        if d < distance then best, distance = node, d end
    end
    selected = best
end)

local KIND = { mainframe = "gold", relay = "cyan", uplink = "green", bridge = "purple" }

local function draw()
    local network = mainframe.getNetwork()
    local dimension
    nodes = {}
    for _, node in ipairs(network.nodes or {}) do
        dimension = dimension or node.dim
        if node.pos and node.dim == dimension then nodes[#nodes + 1] = node end
    end
    canvas:clear()
    if #nodes == 0 then
        canvas:text(W / 2, H / 2 - 4, "No network nodes yet", "dim", { align = "c" }):draw()
        return
    end

    local minX, maxX, minZ, maxZ = math.huge, -math.huge, math.huge, -math.huge
    for _, node in ipairs(nodes) do
        minX, maxX = math.min(minX, node.pos.x), math.max(maxX, node.pos.x)
        minZ, maxZ = math.min(minZ, node.pos.z), math.max(maxZ, node.pos.z)
    end
    local scale = math.min((W - 2 * MARGIN) / math.max(16, maxX - minX), (H - 2 * MARGIN) / math.max(16, maxZ - minZ))
    local byId = {}
    for _, node in ipairs(nodes) do
        node.sx = math.floor(MARGIN + (node.pos.x - minX) * scale)
        node.sy = math.floor(MARGIN + (node.pos.z - minZ) * scale)
        byId[node.id] = node
    end

    for x = 0, W, 16 do canvas:line(x, 0, x, H, "#ffffff08") end
    for y = 0, H, 16 do canvas:line(0, y, W, y, "#ffffff08") end
    for _, link in ipairs(network.links or {}) do
        local a, b = byId[link.a], byId[link.b]
        if a and b then canvas:line(a.sx, a.sy, b.sx, b.sy, "line", 1) end
    end
    for _, node in ipairs(nodes) do
        local size = node.type == "mainframe" and 4 or 2.5
        canvas:circle(node.sx, node.sy, size, KIND[node.type] or "gray")
        if node.status and node.status ~= "online" then canvas:ring(node.sx, node.sy, size + 2, "red", 1) end
    end
    if selected and byId[selected.id] then
        local node = byId[selected.id]
        canvas:ring(node.sx, node.sy, 7, "text", 1)
        local label = (node.name and node.name ~= "" and node.name or node.type) .. "  " .. node.pos.x .. " " .. node.pos.y .. " " .. node.pos.z
        canvas:rect(0, H - 12, W, 12, "panel"):text(4, H - 10, label, "text", { size = 7 })
    end
    canvas:draw()
end

dash:every(1, draw)
print("Base map is on the dashboard. Ctrl+T ends it.")
dash:run()
