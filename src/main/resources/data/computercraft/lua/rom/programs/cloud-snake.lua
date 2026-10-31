-- Snake on the dashboard: click on the side of the snake you want it to turn to. Everyone with the dashboard plays the same game.
-- Usage: cloud-snake [uplink]

local ui = require("cloud.ui")
local dash = ui.connect(...)

local CELL, COLS, ROWS = 6, 30, 20
local W, H = COLS * CELL, ROWS * CELL
local snake, dir, food, score, best, over, player

dash:clear()
dash:title("Snake")
dash:group("game", "Snake", { w = 6, color = "green", icon = "star" })
local board = dash:label("score", "Click the board to start", { group = "game" })

local function place_food()
    repeat
        food = { x = math.random(0, COLS - 1), y = math.random(0, ROWS - 1) }
        local taken = false
        for _, part in ipairs(snake) do taken = taken or (part.x == food.x and part.y == food.y) end
    until not taken
end

local function start(user)
    snake, dir, score, over, player = { { x = 15, y = 10 }, { x = 14, y = 10 }, { x = 13, y = 10 } }, { x = 1, y = 0 }, 0, false, user
    place_food()
end

local canvas = dash:canvas("board", "", { width = W, height = H, input = "click", bg = "bg", group = "game", h = 10 }, function(user, ev)
    if not snake or over then return start(user) end
    player = user
    local head = snake[1]
    local dx, dy = ev.x / CELL - head.x, ev.y / CELL - head.y
    -- turn only: a snake cannot reverse into itself
    if dir.x ~= 0 then dir = { x = 0, y = dy < 0 and -1 or 1 } else dir = { x = dx < 0 and -1 or 1, y = 0 } end
end)

local function step()
    if not snake or over then return end
    local head = { x = snake[1].x + dir.x, y = snake[1].y + dir.y }
    if head.x < 0 or head.y < 0 or head.x >= COLS or head.y >= ROWS then over = true end
    for _, part in ipairs(snake) do over = over or (part.x == head.x and part.y == head.y) end
    if over then
        if score > (best or 0) then best = score end
        board:set(player .. " scored " .. score .. " (best " .. (best or 0) .. "). Click to play again")
        return
    end
    table.insert(snake, 1, head)
    if head.x == food.x and head.y == food.y then
        score = score + 1
        board:set(player .. ": " .. score)
        place_food()
    else
        table.remove(snake)
    end
end

local function draw()
    canvas:clear()
    canvas:box(0, 0, W, H, "line", 1)
    if snake then
        canvas:rect(food.x * CELL + 1, food.y * CELL + 1, CELL - 2, CELL - 2, "red")
        for i, part in ipairs(snake) do canvas:rect(part.x * CELL, part.y * CELL, CELL - 1, CELL - 1, i == 1 and "yellow" or "green") end
    end
    if not snake or over then canvas:text(W / 2, H / 2 - 6, over and "GAME OVER" or "CLICK TO START", "text", { size = 12, align = "c" }) end
    canvas:draw()
end

dash:every(0.25, function()
    step()
    draw()
end)
print("Snake is on the dashboard. Ctrl+T ends it.")
dash:run()
