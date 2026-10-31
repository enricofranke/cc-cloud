-- One canvas with the numbers of all your machine cards: reads the other cards on the mainframe, the same ones a viewer sees.
-- Needs a Cloud Mainframe next to the computer or on its wired network.
-- Usage: cloud-overview [uplink]

local ui = require("cloud.ui")
local dash = ui.connect(...)
local mainframe = ui.mainframe()
if not mainframe then error("cloud-overview reads cards from the mainframe: put this computer next to a Cloud Mainframe or on its wired network.", 0) end

local ROW, W, H = 14, 320, 180

dash:clear()
dash:title("Overview")
dash:group("all", "All machines", { w = 12, color = "gold", icon = "chart" })
local canvas = dash:canvas("all", "", { width = W, height = H, bg = "panel", group = "all", h = 18 })

local function draw()
    local cards, err = mainframe.getCards()
    if not cards then return printError(err) end
    canvas:clear()
    local y = 4
    for _, row in ipairs(cards) do
        if y > H - ROW then break end
        if row.title ~= "Overview" and row.widgets > 0 then
            local card = mainframe.getCard(row.id)
            for _, widget in ipairs(card and card.widgets or {}) do
                if y <= H - ROW and type(widget.value) == "number" and widget.max and widget.max > (widget.min or 0) then
                    local min = widget.min or 0
                    local share = math.max(0, math.min(1, (widget.value - min) / (widget.max - min)))
                    local color = widget.crit and widget.value >= widget.crit and "red" or widget.warn and widget.value >= widget.warn and "yellow" or "green"
                    canvas:text(4, y + 2, row.title .. " " .. (widget.label or widget.id), row.online and "text" or "dim", { size = 7 })
                    canvas:rect(190, y + 2, 100, 8, "bg"):rect(190, y + 2, 100 * share, 8, color)
                    canvas:text(316, y + 2, tostring(math.floor(widget.value)) .. (widget.unit and " " .. widget.unit or ""), "dim", { size = 7, align = "r" })
                    y = y + ROW
                end
            end
        end
    end
    if y == 4 then canvas:text(W / 2, H / 2 - 4, "No machine cards with numbers yet", "dim", { align = "c" }) end
    canvas:draw()
end

dash:every(2, draw)
print("Overview is on the dashboard. Ctrl+T ends it.")
dash:run()
