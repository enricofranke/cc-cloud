--- A declarative builder and event loop for CC: Cloud dashboards.
--
-- Shipped with CC: Cloud in the ROM, so every computer can use it:
--
--     local ui = require("cloud.ui")
--     local dash = ui.connect()
--     dash:page("reactor", "Reactor", { icon = "flame" })
--     local temp = dash:gauge("temp", "Temperature", { max = 1200, unit = "K", warn = 900, crit = 1100, page = "reactor" })
--     dash:button("scram", "SCRAM", { color = "red", confirm = true, pin = true, page = "reactor" },
--       function(user) reactor.scram(); dash:notify(user .. " pressed SCRAM", "crit") end)
--     dash:every(1, function() temp:set(reactor.getTemperature()) end)
--     dash:run()
--
-- See `help cloud` for every widget and option.
--
-- @module cloud.ui

local expect = require("cc.expect").expect

local ui = {}

local Dash = {}
Dash.__index = Dash

local Widget = {}
Widget.__index = Widget

--- Sets the widget's value (gauges, charts, labels, toggles, ...).
function Widget:set(value)
    self.dash.uplink.set(self.id, value)
    self.value = value
    return self
end

--- Removes the widget from the dashboard.
function Widget:remove()
    self.dash.uplink.remove(self.id)
    self.dash.handlers[self.id] = nil
end

--- Registers (or replaces) the handler called when the widget is used on the dashboard.
function Widget:on(handler)
    expect(1, handler, "function")
    self.dash.handlers[self.id] = handler
    return self
end

local function wrap_widget(dash, id, initial)
    return setmetatable({ dash = dash, id = id, value = initial }, Widget)
end

--- Finds the Cloud Uplink and returns a dashboard builder.
-- @tparam[opt] string|table uplink A peripheral name, a wrapped uplink or nil to search for one.
-- @treturn table The dashboard.
function ui.connect(uplink)
    expect(1, uplink, "string", "table", "nil")
    if type(uplink) == "string" then
        local name = uplink
        uplink = peripheral.wrap(name)
        if not uplink or peripheral.getType(uplink) ~= "cloud_uplink" then error("no cloud_uplink named '" .. name .. "'", 2) end
    elseif uplink == nil then
        uplink = peripheral.find("cloud_uplink")
        if not uplink then error("No Cloud Uplink found. Place one next to this computer (or on its wired network).", 2) end
    end
    return setmetatable({
        uplink = uplink,
        side = peripheral.getName(uplink),
        handlers = {},
        widgets = {},
        timers = {},
        events = {},
        running = false,
    }, Dash)
end

--- The raw `cloud_uplink` peripheral.
function Dash:peripheral()
    return self.uplink
end

--- Sets the section title.
function Dash:title(text)
    self.uplink.setTitle(text)
    return self
end

--- Declares a page (tab). With pages an uplink may have up to 64 widgets.
function Dash:page(id, title, opts)
    self.uplink.page(id, title, opts)
    return self
end

--- Declares a group (card); widgets join it with `opts.group`.
function Dash:group(id, title, opts)
    self.uplink.group(id, title, opts)
    return self
end

local function bridge_name(opts)
    if opts and type(opts.bridge) == "table" then
        local copy = {}
        for k, v in pairs(opts) do copy[k] = v end
        copy.bridge = peripheral.getName(opts.bridge)
        return copy
    end
    return opts
end

-- Widget constructors: dash:<type>(id, label, [opts], [handler]).
local function constructor(method, initial_key)
    return function(self, id, label, opts, handler)
        expect(1, id, "string")
        expect(3, opts, "table", "function", "nil")
        if type(opts) == "function" then opts, handler = nil, opts end
        expect(4, handler, "function", "nil")
        opts = bridge_name(opts)
        self.uplink[method](id, label, opts)
        self.handlers[id] = handler
        local widget = wrap_widget(self, id, opts and opts[initial_key or "value"])
        self.widgets[id] = widget
        return widget
    end
end

Dash.button = constructor("button")
Dash.toggle = constructor("toggle")
Dash.slider = constructor("slider")
Dash.gauge = constructor("gauge")
Dash.chart = constructor("chart")
Dash.progress = constructor("progress")
Dash.indicator = constructor("indicator")
Dash.select = constructor("select")
Dash.number = constructor("number")
Dash.text = constructor("text")
Dash.buttons = constructor("buttons")
Dash.itemSearch = constructor("itemSearch")
Dash.watchlist = constructor("watchlist")
Dash.jobs = constructor("jobs")

--- A read-only text line: dash:label(id, text, [opts]).
function Dash:label(id, text, opts)
    self.uplink.label(id, text, opts)
    local widget = wrap_widget(self, id, text)
    self.widgets[id] = widget
    return widget
end

--- Sets a widget value by id.
function Dash:set(id, value)
    self.uplink.set(id, value)
    if self.widgets[id] then self.widgets[id].value = value end
end

--- Sets several values at once: dash:setMany({ temp = 812, running = true }).
function Dash:setMany(values)
    expect(1, values, "table")
    self.uplink.setMany(values)
    for id, value in pairs(values) do
        if self.widgets[id] then self.widgets[id].value = value end
    end
end

--- Removes all widgets, pages and groups.
function Dash:clear()
    self.uplink.clear()
    self.handlers, self.widgets = {}, {}
end

--- Sends a toast/push: level "info" (default), "warn" or "crit". At most one per second.
function Dash:notify(text, level)
    return self.uplink.notify(text, level)
end
Dash.alert = Dash.notify

--- Number of connected dashboards.
function Dash:clients()
    return self.uplink.getClients()
end

--- True if the mainframe is online and the dashboard is enabled.
function Dash:isOnline()
    return self.uplink.isOnline()
end

--- Registers a handler for a widget: dash:on("scram", function(user, value) ... end).
function Dash:on(id, handler)
    expect(1, id, "string")
    expect(2, handler, "function")
    self.handlers[id] = handler
    return self
end

--- Runs fn every `seconds` seconds while dash:run() is active (first call right away).
function Dash:every(seconds, fn)
    expect(1, seconds, "number")
    expect(2, fn, "function")
    if seconds < 0.05 then error("interval must be at least 0.05 seconds", 2) end
    table.insert(self.timers, { interval = seconds, fn = fn })
    return self
end

--- Handles any other event while dash:run() is active: dash:onEvent("redstone", function(...) end).
function Dash:onEvent(event, fn)
    expect(1, event, "string")
    expect(2, fn, "function")
    self.events[event] = self.events[event] or {}
    table.insert(self.events[event], fn)
    return self
end

local function call(fn, ...)
    local ok, err = pcall(fn, ...)
    if not ok then
        printError(err)
    end
end

--- Stops dash:run().
function Dash:stop()
    self.running = false
    os.queueEvent("cloud_ui_stop")
end

--- Handlers run in their own coroutines (at most this many at once, the rest wait in a queue), so a handler that
-- calls sleep() or waits for an event does not block other dashboard actions or the timers.
ui.MAX_HANDLERS = 8
local MAX_QUEUED = 64

--- The event loop: dispatches dashboard actions to the handlers and runs the timers. Each handler runs in its own
-- coroutine (up to ui.MAX_HANDLERS at once), so sleep() inside a handler only pauses that handler. Errors inside
-- handlers are printed and do not stop the loop. Press Ctrl+T to terminate.
function Dash:run()
    self.running = true
    local tasks, queue = {}, {}

    local function spawn(packed)
        local co = coroutine.create(function() call(table.unpack(packed, 1, packed.n)) end)
        local ok, filter = coroutine.resume(co)
        if not ok then
            printError(filter)
        elseif coroutine.status(co) ~= "dead" then
            tasks[#tasks + 1] = { co = co, filter = filter }
        end
    end

    local function start(fn, ...)
        local packed = table.pack(fn, ...)
        if #tasks >= ui.MAX_HANDLERS then
            if #queue < MAX_QUEUED then queue[#queue + 1] = packed else printError("cloud.ui: too many busy handlers, an action was dropped") end
            return
        end
        spawn(packed)
    end

    local function resume_all(event)
        local current = tasks
        tasks = {}
        for _, task in ipairs(current) do
            if task.filter == nil or task.filter == event[1] or event[1] == "terminate" then
                local ok, filter = coroutine.resume(task.co, table.unpack(event, 1, event.n))
                if not ok then
                    printError(filter)
                elseif coroutine.status(task.co) ~= "dead" then
                    task.filter = filter
                    tasks[#tasks + 1] = task
                end
            else
                tasks[#tasks + 1] = task
            end
        end
        while #tasks < ui.MAX_HANDLERS and #queue > 0 do spawn(table.remove(queue, 1)) end
    end

    local by_timer = {}
    for _, task in ipairs(self.timers) do
        start(task.fn)
        by_timer[os.startTimer(task.interval)] = task
    end
    while self.running do
        local event = table.pack(os.pullEvent())
        local name = event[1]
        -- Handlers that are waiting (sleep, pullEvent) get the event first; new handlers start afterwards.
        resume_all(event)
        if name == "cloud_action" then
            local id, value, user, side = event[2], event[3], event[4], event[5]
            local widget = self.widgets[id]
            if side == self.side and widget then widget.value = value end
            local handler = side == self.side and self.handlers[id]
            if handler then start(handler, user, value) end
        elseif name == "cloud_run" and not multishell and type(event[2]) == "string" and fs.exists(event[2]) then
            -- Script import "Run now" on computers without multishell: run the uploaded file as a handler.
            local file = event[2]
            if file:match("^[%w_.-]+$") or file:match("^[%w_.-]+/[%w_.-]+$") then start(shell.run, file) end
        elseif name == "timer" and by_timer[event[2]] then
            local task = by_timer[event[2]]
            by_timer[event[2]] = nil
            start(task.fn)
            by_timer[os.startTimer(task.interval)] = task
        end
        local listeners = self.events[name]
        if listeners then
            for _, fn in ipairs(listeners) do start(fn, table.unpack(event, 2, event.n)) end
        end
    end
end

return ui
