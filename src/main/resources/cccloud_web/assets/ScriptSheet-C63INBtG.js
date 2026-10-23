import{a as e,l as t,n,o as r,r as i,t as a}from"./jsxRuntime.module-BRbsxUMc.js";import{k as o,n as ee,r as s,t as c}from"./Overlays-CjKeW_-z.js";import{u as l}from"./index-Cob01lYh.js";var u=/^[A-Za-z0-9_.-]{1,64}$/,d=/^(rom|disk\d*|\.cloud)$/;function f(e){if(!e)return`Enter a file name.`;if(e.startsWith(`/`)||e.includes(`\\`))return`Use a relative name like control-room.lua.`;let t=e.split(`/`);if(t.length>2)return`At most one folder (folder/name.lua).`;for(let e of t){if(e===`.`||e===`..`)return`“.” and “..” are not allowed.`;if(!u.test(e))return`Letters, digits, “_”, “-” and “.” only (max 64 per part).`}return d.test(t[0])?`“${t[0]}” is reserved by the computer.`:null}function p(e){let t=(e.split(/[\\/]/).pop()??``).trim().replace(/\s+/g,`-`).replace(/[^A-Za-z0-9_.-]/g,``).replace(/^\.+/,``).slice(0,60);return t||=`program`,/\.lua$/i.test(t)||(t=`${t.replace(/\.[^.]*$/,``)}.lua`),t.replace(/\.LUA$/,`.lua`)}function m(e){let t=[];for(let n of e.split(/\r?\n/)){let e=/^\s*--+\s?(.*)$/.exec(n);if(!e)break;t.push(e[1].trim())}let n=e=>/^(run|usage):/i.test(e),r=t.find(n)?.replace(/^(run|usage):\s*/i,``),i=t.filter(e=>e&&!n(e)).join(` `).replace(/^(CC: Cloud (example|test program|demo)|[a-z0-9-]+):\s*/i,``);return{text:i.charAt(0).toUpperCase()+i.slice(1),...r?{run:r}:{}}}var h=e=>{let t=e.replace(/\.lua$/,``).replace(/[-_]+/g,` `);return t.charAt(0).toUpperCase()+t.slice(1)},g=class extends Error{code;status;attemptsLeft;retryAfter;constructor(e,t,n={}){super(t),this.code=t,this.status=e,this.attemptsLeft=n.attemptsLeft,this.retryAfter=n.retryAfter}},_={not_allowed:`Only admins of this mainframe can manage scripts.`,no_access:`This browser has no access to this mainframe.`,unknown_computer:`There is no computer behind this card (or its chunk is not loaded).`,offline:`The mainframe is offline.`,invalid_path:`That file name is not allowed.`,exists:`A file with that name already exists.`,too_large:`The file is too large (max 256 KB).`,quota:`The computer's disk is full.`,not_found:`The file does not exist (any more).`,rate_limited:`Too many changes – wait a minute.`,needs_reboot:`This computer cannot start programs in the background.`,pin_required:`This needs the mainframe PIN.`,wrong_pin:`Wrong PIN.`,pin_locked:`Too many wrong PINs – locked for a few minutes.`,unreachable:`Server not reachable.`},v=e=>e instanceof g?_[e.code]??`Error: ${e.code}`:`Something went wrong. Try again.`,y=e=>e.split(`/`).map(encodeURIComponent).join(`/`);function te(e,t,n,r){let i=`${e}/api/mainframes/${encodeURIComponent(n)}/computers/${encodeURIComponent(r)}`,a=async(e,n={})=>{let r={authorization:`Bearer ${t}`};n.pin&&(r[`x-cloud-pin`]=n.pin);let a;try{a=await fetch(i+e,{...n,headers:{...r,...n.headers},cache:`no-store`})}catch{throw new g(0,`unreachable`)}if(!a.ok){let e={};try{e=await a.json()}catch{}throw new g(a.status,e.error??`http_${a.status}`,e)}return a};return{list:async()=>(await a(`/files`)).json(),read:async e=>(await a(`/files/${y(e)}`)).text(),write:async(e,t,n)=>{let r=new URLSearchParams;return n.overwrite&&r.set(`overwrite`,`true`),n.startup&&r.set(`startup`,`true`),(await a(`/files/${y(e)}${r.size?`?${r}`:``}`,{method:`PUT`,body:t,pin:n.pin,headers:{"content-type":`text/plain; charset=utf-8`}})).json()},remove:async(e,t)=>{await a(`/files/${y(e)}`,{method:`DELETE`,pin:t})},run:async(e,t,n)=>(await(await a(`/run`,{method:`POST`,body:JSON.stringify({path:e,mode:t}),pin:n,headers:{"content-type":`application/json`}})).json()).started}}var b=e=>e<1024?`${e} B`:e<1048576?`${(e/1024).toFixed(+(e<10240))} KB`:`${(e/1024/1024).toFixed(1)} MB`,x=Object.assign({"../../../../examples/cloud-test.lua":`-- CC: Cloud test program: an uplink next to this computer, a redstone lamp on the back.
local cloud = peripheral.find("cloud_uplink")
if not cloud then error("No Cloud Uplink found. Attach one to this computer.", 0) end

cloud.clear()
cloud.setTitle("Testlabor")
cloud.toggle("lamp", "Lampe (hinten)", { value = false })
cloud.button("count", "Zaehler +1", { color = "cyan" })
cloud.label("counter", "Klicks: 0")
cloud.gauge("time", "Ingame-Zeit", { min = 0, max = 24, unit = "h", warn = 20, crit = 22 })
cloud.slider("level", "Redstone-Staerke", { min = 0, max = 15, step = 1, value = 0 })
cloud.button("alarm", "Test-Alarm", { color = "red", confirm = true })

local clicks = 0
local timer = os.startTimer(1)
print("Cloud test running. Online: " .. tostring(cloud.isOnline()))

while true do
  local event, a, b, c = os.pullEvent()
  if event == "cloud_action" then
    local id, value, user = a, b, c
    print(("%s -> %s = %s"):format(tostring(user), id, tostring(value)))
    if id == "lamp" then
      redstone.setOutput("back", value)
      cloud.set("lamp", value)
    elseif id == "count" then
      clicks = clicks + 1
      cloud.set("counter", "Klicks: " .. clicks)
    elseif id == "level" then
      redstone.setAnalogOutput("back", value)
      cloud.set("level", value)
    elseif id == "alarm" then
      cloud.alert("Test-Alarm von " .. tostring(user), "crit")
    end
  elseif event == "timer" and a == timer then
    cloud.set("time", math.floor(os.time() * 10) / 10)
    timer = os.startTimer(1)
  end
end
`,"../../../../examples/control-room.lua":`-- CC: Cloud example: a small control room for one computer.
-- Needs a Cloud Uplink on this computer. Lamp/door on the back side, optional speaker.
-- Run: control-room   (stop with Ctrl+T)

local ui = require("cloud.ui")
local dash = ui.connect()
dash:clear()
dash:title("Kontrollraum")

-- Lamp on the back side: switch from the dashboard
dash:toggle("lamp", "Lampe hinten", { value = redstone.getOutput("back") }, function(user, on)
  redstone.setOutput("back", on)
  dash:set("lamp", on)
  print(user .. " -> Lampe " .. (on and "an" or "aus"))
end)

-- Open a door on the left side for 5 seconds.
-- No sleep() inside handlers: it would block all other dashboard actions. Use a timer instead.
local doorTimer
dash:button("door", "Tuer oeffnen", { color = "cyan" }, function(user)
  redstone.setOutput("left", true)
  doorTimer = os.startTimer(5)
  dash:notify(user .. " hat die Tuer geoeffnet", "info")
end)
dash:onEvent("timer", function(id)
  if id == doorTimer then
    redstone.setOutput("left", false)
    doorTimer = nil
  end
end)

-- Redstone strength 0-15 on the right side (e.g. machine speed)
dash:slider("power", "Redstone rechts", { min = 0, max = 15, step = 1, value = 0 }, function(_, level)
  redstone.setAnalogOutput("right", level)
  dash:set("power", level)
end)

-- Start a program on this computer in a new tab (advanced computer)
local programs = { "cloud-demo", "worm", "adventure" }
local chosen = programs[1]
dash:select("program", "Programm", { options = programs, value = chosen }, function(_, value)
  chosen = value
  dash:set("program", value)
end)
dash:button("start", "Starten", { color = "green", confirm = true }, function(user)
  if multishell then
    shell.openTab(chosen)
    dash:notify(user .. " startet " .. chosen, "info")
  else
    dash:notify("Programme starten braucht einen Advanced Computer", "warn")
  end
end)

-- Live data: in-game time as gauge + chart
local clock = dash:gauge("time", "Ingame-Uhrzeit", { min = 0, max = 24, unit = "h", warn = 19, crit = 22 })
local day = dash:chart("day", "Tageszeit-Verlauf", { min = 0, max = 24, points = 60 })
dash:every(1, function()
  local t = math.floor(os.time() * 10) / 10
  clock:set(t)
  day:set(t)
end)

-- Emergency button with PIN (set the PIN in-game: /cccloud pin set 1234)
dash:button("alarm", "ALARM", { color = "red", confirm = true, pin = true }, function(user)
  redstone.setOutput("top", true)
  dash:notify("ALARM ausgeloest von " .. user, "crit")
end)
dash:button("reset", "Alarm aus", { color = "gray" }, function()
  redstone.setOutput("top", false)
end)

print("Kontrollraum laeuft. Strg+T zum Beenden.")
dash:run()
`,"../../../../src/main/resources/data/computercraft/lua/rom/programs/cloud-demo.lua":`-- cloud-demo: shows every CC: Cloud widget on the dashboard.
-- Usage: cloud-demo [uplink]

local ui = require("cloud.ui")
local dash = ui.connect(...)

dash:clear()
dash:title("Cloud Demo")
dash:page("overview", "Overview", { icon = "home" })
dash:page("controls", "Controls", { icon = "gear" })
dash:group("plant", "Power plant", { page = "overview", cols = 2 })
dash:group("inputs", "Inputs", { page = "controls", cols = 2 })

-- Read-only widgets
local temp = dash:gauge("temp", "Core temperature", { min = 0, max = 1200, unit = "K", warn = 900, crit = 1100, group = "plant", icon = "flame" })
local power = dash:chart("power", "Power output", { min = 0, max = 100, unit = "MW", points = 60, interval = 1, group = "plant", span = 2 })
local fuel = dash:progress("fuel", "Fuel", { min = 0, max = 100, unit = "%", group = "plant" })
local running = dash:indicator("running", "Running", { value = true, onColor = "green", offColor = "red", group = "plant" })
local status = dash:label("status", "Starting...", { page = "overview" })

-- Inputs
local enabled = true
local mode = "mid"
dash:toggle("enabled", "Reactor enabled", { value = true, group = "inputs" }, function(user, value)
    enabled = value
    dash:notify(user .. (value and " started" or " stopped") .. " the reactor", value and "info" or "warn")
end)
dash:slider("rods", "Control rods", { min = 0, max = 100, step = 5, value = 50, unit = "%", group = "inputs" })
dash:select("mode", "Mode", { options = { "low", "mid", "high" }, value = "mid", group = "inputs" }, function(user, value) mode = value end)
dash:number("target", "Target output", { min = 0, max = 100, step = 1, value = 60, unit = "MW", group = "inputs" })
dash:text("note", "Shift note", { maxLength = 64, placeholder = "Anything to report?", page = "controls" }, function(user, value)
    print(user .. " wrote: " .. value)
end)
dash:buttons("preset", "Presets", { options = { "Eco", "Normal", "Boost" }, page = "controls" }, function(user, value)
    dash:notify(user .. " chose preset " .. value)
end)
dash:button("scram", "SCRAM", { color = "red", confirm = true, page = "controls", icon = "warning" }, function(user)
    enabled = false
    dash:set("enabled", false)
    dash:notify(user .. " pressed SCRAM", "crit")
end)

local factor = { low = 0.5, mid = 1, high = 1.5 }
local t, level = 0, 100
dash:every(1, function()
    t = t + 1
    local out = enabled and (40 + 20 * math.sin(t / 6)) * factor[mode] or 0
    level = enabled and math.max(0, level - 0.2) or level
    dash:setMany({
        temp = math.floor(300 + out * 8),
        power = math.floor(out * 10) / 10,
        fuel = math.floor(level),
        running = enabled,
    })
    status:set(("%s, %d dashboard(s) connected"):format(enabled and "Running" or "Stopped", dash:clients()))
end)

print("Cloud demo is running on " .. peripheral.getName(dash:peripheral()) .. ". Press Ctrl+T to stop.")
dash:run()
`}),S={"cloud-demo.lua":`Every widget type in one program – the quickest way to see what a dashboard can do. Also built into the computer as the program cloud-demo.`},C=Object.entries(x).map(([e,t])=>{let n=e.split(`/`).pop(),r=m(t);return{name:n,title:h(n),text:S[n]??r.text,...r.run?{run:r.run}:{},code:t,lines:t.split(`
`).length}}).sort((e,t)=>e.name.localeCompare(t.name));function w({conn:u,uplinkId:d,title:m,onClose:h}){let _=i(()=>te(u.baseUrl.replace(/\/$/,``),u.token,u.info.value?.id??``,d),[u,d]),[y,x]=e(`upload`),[S,w]=e(null),[T,E]=e(null),[D,O]=e(``),[k,A]=e(``),[j,M]=e(!0),[N,P]=e(!1),[F,I]=e(!1),[L,R]=e(null),[ne,z]=e(!1),[B,V]=e(null),[H,U]=e(null),[W,G]=e(null),K=n(void 0),q=n(null),J=()=>_.list().then(e=>{w(e),E(null)}).catch(e=>E(v(e)));r(()=>{J()},[_]);let Y=e=>new Promise(t=>U({...e,resolve:t}));async function X(e){for(;;)try{let t=await e(K.current);return G(null),t}catch(e){if(!(e instanceof g)||![`pin_required`,`wrong_pin`,`pin_locked`].includes(e.code))throw G(null),e;e.code!==`pin_required`&&(K.current=void 0);let t=e.code===`pin_locked`?{kind:`locked`,text:v(e)}:e.code===`wrong_pin`?{kind:`error`,text:`Wrong PIN${e.attemptsLeft===void 0?``:` – ${e.attemptsLeft} attempts left`}.`}:{kind:`idle`},n=await new Promise(e=>G({state:t,resolve:e}));if(n===null)throw new g(0,`cancelled`);K.current=n}}let Z=async e=>{if(R(null),e.size>262144)return R(`The file is too large (max 256 KB).`);let t=await e.text();O(t),A(p(e.name)),V(null)},re=e=>{O(e.code),A(e.name),x(`upload`),V(null),R(null)},ie=async e=>{e?.preventDefault();let t=k.trim(),n=f(t)??(D.trim()?new Blob([D]).size>262144?`The file is too large (max 256 KB).`:null:`Paste some code or drop a .lua file first.`);if(n)return R(n);R(null),I(!0);try{let e=!1;if(S?.files.some(e=>e.path===t)&&(e=await Y({title:`Replace ${t}?`,text:`${t} already exists on ${S.computer.label||`Computer #${S.computer.id}`}. The old version is overwritten.`,label:`Replace`,color:`yellow`}),!e)||N&&S?.files.some(e=>e.path===`startup.lua`)&&t!==`startup.lua`&&!await Y({title:`Replace startup.lua?`,text:`The computer's startup.lua will only run ${t}.`,label:`Replace startup`,color:`yellow`}))return;try{await X(n=>_.write(t,D,{overwrite:e,startup:N,pin:n}))}catch(e){if(e instanceof g&&e.code===`exists`&&await Y({title:`Replace ${t}?`,text:`${t} already exists.`,label:`Replace`,color:`yellow`}))await X(e=>_.write(t,D,{overwrite:!0,startup:N,pin:e}));else throw e}let n=`${t} saved on ${S?.computer.label||`the computer`}.`;if(j)try{let e=await X(e=>_.run(t,`auto`,e));n+=e===`event`?` It runs in a new tab.`:``}catch(e){if(!(e instanceof g)||e.code!==`needs_reboot`)throw e;await Y({title:`Restart the computer?`,text:`This computer cannot start programs in the background (it is not an advanced computer). Restart it to run the script once? A program running now is stopped.`,label:`Restart & run`,color:`yellow`})&&(await X(e=>_.run(t,`reboot`,e)),n+=` The computer restarts and runs it.`)}V(n),o({level:`info`,title:`Script uploaded`,text:n}),J()}catch(e){e instanceof g&&e.code===`cancelled`||R(v(e))}finally{I(!1)}},ae=async e=>{try{let t=await _.read(e),n=URL.createObjectURL(new Blob([t],{type:`text/plain`})),r=document.createElement(`a`);r.href=n,r.download=e.split(`/`).pop(),r.click(),setTimeout(()=>URL.revokeObjectURL(n),1e3)}catch(e){o({level:`warn`,title:`Download failed`,text:v(e)})}},oe=async e=>{try{O(await _.read(e)),A(e),x(`upload`),V(null)}catch(e){o({level:`warn`,title:`Could not open`,text:v(e)})}},se=async e=>{if(await Y({title:`Delete ${e}?`,text:`The file is removed from the computer. This cannot be undone.`,label:`Delete`,color:`red`}))try{await X(t=>_.remove(e,t)),o({level:`info`,title:`Deleted`,text:`${e} was deleted.`}),J()}catch(e){e instanceof g&&e.code===`cancelled`||o({level:`warn`,title:`Delete failed`,text:v(e)})}},Q=S?.computer,$=k?f(k.trim()):null;return a(s,{title:`Scripts – ${m}`,onClose:h,children:[a(`div`,{class:`scripts`,children:[a(`div`,{class:`scripts-comp`,children:Q?a(t,{children:[a(`span`,{class:`led ${Q.on?`led-on`:`led-off`}`,"aria-hidden":`true`}),a(`b`,{children:Q.label||`Computer #${Q.id}`}),a(`span`,{class:`muted small`,children:[`#`,Q.id,` · `,Q.advanced?`advanced`:`basic`,` · `,b(S.used),` of `,b(S.capacity),` used`]})]}):a(`span`,{class:`muted small`,children:T??`Connecting to the computer…`})}),a(`div`,{class:`nd-tabs`,role:`tablist`,"aria-label":`Scripts`,children:[[`upload`,`Upload`],[`examples`,`Examples (${C.length})`],[`files`,`Files${S?` (${S.files.filter(e=>!e.dir).length})`:``}`]].map(([e,t])=>a(`button`,{type:`button`,role:`tab`,"aria-selected":y===e,class:`nd-tab ${y===e?`on`:``}`,onClick:()=>x(e),children:t},e))}),y===`upload`&&a(`form`,{class:`form scripts-upload`,onSubmit:ie,novalidate:!0,children:[a(`div`,{class:`dropzone ${ne?`drag`:``}`,onDragOver:e=>{e.preventDefault(),z(!0)},onDragLeave:()=>z(!1),onDrop:e=>{e.preventDefault(),z(!1);let t=e.dataTransfer?.files?.[0];t&&Z(t)},children:[a(`span`,{children:`Drop a .lua file here or`}),a(`button`,{type:`button`,class:`btn`,onClick:()=>q.current?.click(),children:`Choose file`}),a(`input`,{ref:q,type:`file`,accept:`.lua,text/plain,text/x-lua`,hidden:!0,onChange:e=>{let t=e.target.files?.[0];t&&Z(t),e.target.value=``}})]}),a(`label`,{class:`field`,children:[a(`span`,{class:`field-label`,children:`Code`}),a(`textarea`,{class:`code-area`,value:D,onInput:e=>{O(e.target.value),V(null)},placeholder:`-- paste Lua here
local ui = require("cloud.ui")
local dash = ui.connect()
…`,spellcheck:!1,autoCapitalize:`off`,autoCorrect:`off`,rows:12}),a(`span`,{class:`field-hint`,children:[D?`${D.split(`
`).length} lines · ${b(new Blob([D]).size)}`:`Max 256 KB.`,` The full editor comes later.`]})]}),a(`label`,{class:`field`,children:[a(`span`,{class:`field-label`,children:`File name on the computer`}),a(`input`,{class:`input`,value:k,onInput:e=>A(e.target.value),onBlur:()=>k&&!/\.lua$/i.test(k)&&!k.includes(`/`)&&A(p(k)),placeholder:`control-room.lua`,maxLength:129,"aria-invalid":!!$,spellcheck:!1,autoCapitalize:`off`}),$?a(`span`,{class:`field-hint err`,children:$}):S?.files.some(e=>e.path===k.trim())&&a(`span`,{class:`field-hint warn`,children:`Exists – you will be asked before it is replaced.`})]}),a(`label`,{class:`check-row`,children:[a(`input`,{type:`checkbox`,checked:j,onChange:e=>M(e.target.checked)}),`Run it now`,Q&&!Q.advanced?` (restarts this basic computer)`:` (in a new tab)`]}),a(`label`,{class:`check-row`,children:[a(`input`,{type:`checkbox`,checked:N,onChange:e=>P(e.target.checked)}),`Start it automatically (startup.lua)`]}),L&&a(`p`,{class:`form-error`,role:`alert`,children:L}),B&&a(`p`,{class:`form-ok`,role:`status`,children:[`✓ `,B]}),a(`button`,{type:`submit`,class:`pxbtn pxbtn-gold pxbtn-wide`,disabled:F||!D.trim()||!k.trim()||!!$,children:a(`span`,{children:F?`Uploading…`:j?`Upload & run`:`Upload`})}),a(`p`,{class:`muted small`,children:[`The code runs on this CC computer only, inside CC's sandbox – not on the Minecraft server. Admins only; the mainframe PIN is asked if one is set.`,` `,a(l,{to:`/docs/lua#upload`,children:`More`})]})]}),y===`examples`&&a(`ul`,{class:`ex-grid`,children:C.map(e=>a(`li`,{class:`ex-card`,children:[a(`div`,{class:`ex-head`,children:[a(`b`,{children:e.title}),a(`span`,{class:`mono small muted`,children:e.name})]}),a(`p`,{class:`ex-text`,children:e.text}),a(`pre`,{class:`ex-code`,"aria-hidden":`true`,children:e.code.split(`
`).filter(e=>e.trim()&&!e.trim().startsWith(`--`)).slice(0,4).join(`
`)}),a(`div`,{class:`ex-foot`,children:[a(`span`,{class:`muted small`,children:[e.lines,` lines`]}),a(`button`,{type:`button`,class:`btn`,onClick:()=>re(e),children:`Use this`})]})]},e.name))}),y===`files`&&a(`div`,{class:`files`,children:[S&&a(`div`,{class:`files-usage`,"aria-label":`${b(S.used)} of ${b(S.capacity)} used`,children:a(`span`,{ref:e=>{e&&(e.style.width=`${Math.min(100,Math.round(S.used/Math.max(1,S.capacity)*100))}%`)}})}),T&&a(`p`,{class:`form-error`,children:T}),S&&S.files.length===0&&a(`p`,{class:`muted`,children:`No files yet. Upload a script or pick an example.`}),a(`ul`,{class:`file-list`,children:S?.files.map(e=>e.dir?a(`li`,{class:`file-dir`,children:[e.path,`/`]},e.path):a(`li`,{class:`file-row ${e.path.includes(`/`)?`nested`:``}`,children:[a(`span`,{class:`file-name mono`,children:e.path.includes(`/`)?e.path.split(`/`)[1]:e.path}),a(`span`,{class:`muted small`,children:b(e.size)}),a(`span`,{class:`file-actions`,children:[a(`button`,{type:`button`,class:`btn btn-s`,onClick:()=>oe(e.path),children:`Open`}),a(`button`,{type:`button`,class:`btn btn-s`,onClick:()=>ae(e.path),"aria-label":`Download ${e.path}`,children:`Download`}),a(`button`,{type:`button`,class:`btn btn-s btn-danger-ghost`,onClick:()=>se(e.path),"aria-label":`Delete ${e.path}`,children:`Delete`})]})]},e.path))})]})]}),H&&a(c,{title:H.title,text:H.text,confirmLabel:H.label,color:H.color,onCancel:()=>{H.resolve(!1),U(null)},onConfirm:()=>{H.resolve(!0),U(null)}}),W&&a(ee,{title:`Mainframe PIN`,text:`Changing files on a computer needs the mainframe PIN.`,color:`gold`,state:W.state,onCancel:()=>{W.resolve(null),G(null)},onSubmit:e=>{G({...W,state:{kind:`checking`}}),W.resolve(e)}})]})}export{w as default};