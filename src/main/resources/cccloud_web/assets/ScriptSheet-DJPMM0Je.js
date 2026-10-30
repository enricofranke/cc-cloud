const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["assets/Editor-Cz9kRZAB.js","assets/jsxRuntime.module-DMgpA-GX.js","assets/Overlays-BB9Kks4e.js","assets/scriptstore-L1h0OKAj.js","assets/scripts-pNe2VorL.js","assets/index-xp9Qh7bO.js","assets/index-DHkI7YHB.css"])))=>i.map(i=>d[i]);
import{c as e,d as t,h as n,s as r,t as i,u as a}from"./jsxRuntime.module-DMgpA-GX.js";import{A as o,n as s,r as c,t as ee}from"./Overlays-BB9Kks4e.js";import{_ as l,k as te}from"./index-xp9Qh7bO.js";import{a as u,c as d,i as f,l as p,n as m,o as h,s as ne,u as g}from"./scripts-pNe2VorL.js";var _=Object.assign({"../../../../examples/cloud-test.lua":`-- CC: Cloud test program: an uplink next to this computer, a redstone lamp on the back.
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
`}),v={"cloud-demo.lua":`Every widget type in one program – the quickest way to see what a dashboard can do. Also built into the computer as the program cloud-demo.`},y=Object.entries(_).map(([e,t])=>{let n=e.split(`/`).pop(),r=f(t);return{name:n,title:g(n),text:v[n]??r.text,...r.run?{run:r.run}:{},code:t,lines:t.split(`
`).length}}).sort((e,t)=>e.name.localeCompare(t.name)),b=null;function x(e){let[n,r]=a(()=>b);return t(()=>{n||l(()=>import(`./Editor-Cz9kRZAB.js`).then(e=>r(()=>b=e.default)),__vite__mapDeps([0,1,2,3,4,5,6]))},[]),n?i(n,{...e}):null}function S({conn:l,uplinkId:f,title:g,onClose:_}){let v=e(()=>ne(l.baseUrl.replace(/\/$/,``),l.token,l.info.value?.id??``,f),[l,f]),[b,S]=a(`upload`),[C,re]=a(null),[w,T]=a(null),[E,D]=a(``),[O,k]=a(``),[A,ie]=a(!0),[j,ae]=a(!1),[M,N]=a(!1),[P,F]=a(null),[oe,I]=a(!1),[L,R]=a(null),[z,B]=a(null),[V,H]=a(null),[U,W]=a(null),G=r(void 0),K=r(null),q=()=>v.list().then(e=>{re(e),T(null)}).catch(e=>T(d(e)));t(()=>{q()},[v]);let J=e=>new Promise(t=>B({...e,resolve:t}));async function Y(e){for(;;)try{let t=await e(G.current);return H(null),t}catch(e){if(!(e instanceof m)||![`pin_required`,`wrong_pin`,`pin_locked`].includes(e.code))throw H(null),e;e.code!==`pin_required`&&(G.current=void 0);let t=e.code===`pin_locked`?{kind:`locked`,text:d(e)}:e.code===`wrong_pin`?{kind:`error`,text:`Wrong PIN${e.attemptsLeft===void 0?``:` – ${e.attemptsLeft} attempts left`}.`}:{kind:`idle`},n=await new Promise(e=>H({state:t,resolve:e}));if(n===null)throw new m(0,`cancelled`);G.current=n}}let X=async e=>{if(F(null),e.size>262144)return F(`The file is too large (max 256 KB).`);let t=await e.text();D(t),k(p(e.name)),R(null)},Z=e=>{D(e.code),k(e.name),S(`upload`),R(null),F(null)},se=async e=>{e?.preventDefault();let t=O.trim(),n=h(t)??(E.trim()?new Blob([E]).size>262144?`The file is too large (max 256 KB).`:null:`Paste some code or drop a .lua file first.`);if(n)return F(n);F(null),N(!0);try{let e=!1;if(C?.files.some(e=>e.path===t)&&(e=await J({title:`Replace ${t}?`,text:`${t} already exists on ${C.computer.label||`Computer #${C.computer.id}`}. The old version is overwritten.`,label:`Replace`,color:`yellow`}),!e)||j&&C?.files.some(e=>e.path===`startup.lua`)&&t!==`startup.lua`&&!await J({title:`Replace startup.lua?`,text:`The computer's startup.lua will only run ${t}.`,label:`Replace startup`,color:`yellow`}))return;try{await Y(n=>v.write(t,E,{overwrite:e,startup:j,pin:n}))}catch(e){if(e instanceof m&&e.code===`exists`&&await J({title:`Replace ${t}?`,text:`${t} already exists.`,label:`Replace`,color:`yellow`}))await Y(e=>v.write(t,E,{overwrite:!0,startup:j,pin:e}));else throw e}let n=`${t} saved on ${C?.computer.label||`the computer`}.`;if(A)try{let e=await Y(e=>v.run(t,`auto`,e));n+=e===`event`?` It runs in a new tab.`:``}catch(e){if(!(e instanceof m)||e.code!==`needs_reboot`)throw e;await J({title:`Restart the computer?`,text:`This computer cannot start programs in the background (it is not an advanced computer). Restart it to run the script once? A program running now is stopped.`,label:`Restart & run`,color:`yellow`})&&(await Y(e=>v.run(t,`reboot`,e)),n+=` The computer restarts and runs it.`)}R(n),o({level:`info`,title:`Script uploaded`,text:n}),q()}catch(e){e instanceof m&&e.code===`cancelled`||F(d(e))}finally{N(!1)}},ce=async e=>{try{let t=await v.read(e),n=URL.createObjectURL(new Blob([t],{type:`text/plain`})),r=document.createElement(`a`);r.href=n,r.download=e.split(`/`).pop(),r.click(),setTimeout(()=>URL.revokeObjectURL(n),1e3)}catch(e){o({level:`warn`,title:`Download failed`,text:d(e)})}},le=async e=>{if(await J({title:`Delete ${e}?`,text:`The file is removed from the computer. This cannot be undone.`,label:`Delete`,color:`red`}))try{await Y(t=>v.remove(e,t)),o({level:`info`,title:`Deleted`,text:`${e} was deleted.`}),q()}catch(e){e instanceof m&&e.code===`cancelled`||o({level:`warn`,title:`Delete failed`,text:d(e)})}},Q=C?.computer,$=O?h(O.trim()):null;return i(c,{title:`Scripts – ${g}`,onClose:_,children:[i(`div`,{class:`scripts`,children:[i(`div`,{class:`scripts-comp`,children:Q?i(n,{children:[i(`span`,{class:`led ${Q.on?`led-on`:`led-off`}`,"aria-hidden":`true`}),i(`b`,{children:Q.label||`Computer #${Q.id}`}),i(`span`,{class:`muted small`,children:[`#`,Q.id,` · `,Q.advanced?`advanced`:`basic`,` · `,u(C.used),` of `,u(C.capacity),` used`]})]}):i(`span`,{class:`muted small`,children:w??`Connecting to the computer…`})}),i(`button`,{type:`button`,class:`btn btn-small open-editor`,onClick:()=>W({}),children:`Open the editor`}),i(`div`,{class:`nd-tabs`,role:`tablist`,"aria-label":`Scripts`,children:[[`upload`,`Upload`],[`examples`,`Examples (${y.length})`],[`files`,`Files${C?` (${C.files.filter(e=>!e.dir).length})`:``}`]].map(([e,t])=>i(`button`,{type:`button`,role:`tab`,"aria-selected":b===e,class:`nd-tab ${b===e?`on`:``}`,onClick:()=>S(e),children:t},e))}),b===`upload`&&i(`form`,{class:`form scripts-upload`,onSubmit:se,novalidate:!0,children:[i(`div`,{class:`dropzone ${oe?`drag`:``}`,onDragOver:e=>{e.preventDefault(),I(!0)},onDragLeave:()=>I(!1),onDrop:e=>{e.preventDefault(),I(!1);let t=e.dataTransfer?.files?.[0];t&&X(t)},children:[i(`span`,{children:`Drop a .lua file here or`}),i(`button`,{type:`button`,class:`btn`,onClick:()=>K.current?.click(),children:`Choose file`}),i(`input`,{ref:K,type:`file`,accept:`.lua,text/plain,text/x-lua`,hidden:!0,onChange:e=>{let t=e.target.files?.[0];t&&X(t),e.target.value=``}})]}),i(`label`,{class:`field`,children:[i(`span`,{class:`field-label`,children:`Code`}),i(`textarea`,{class:`code-area`,value:E,onInput:e=>{D(e.target.value),R(null)},placeholder:`-- paste Lua here
local ui = require("cloud.ui")
local dash = ui.connect()
…`,spellcheck:!1,autoCapitalize:`off`,autoCorrect:`off`,rows:12}),i(`span`,{class:`field-hint`,children:[E?`${E.split(`
`).length} lines · ${u(new Blob([E]).size)}`:`Max 256 KB.`,` The full editor comes later.`]})]}),i(`label`,{class:`field`,children:[i(`span`,{class:`field-label`,children:`File name on the computer`}),i(`input`,{class:`input`,value:O,onInput:e=>k(e.target.value),onBlur:()=>O&&!/\.lua$/i.test(O)&&!O.includes(`/`)&&k(p(O)),placeholder:`control-room.lua`,maxLength:129,"aria-invalid":!!$,spellcheck:!1,autoCapitalize:`off`}),$?i(`span`,{class:`field-hint err`,children:$}):C?.files.some(e=>e.path===O.trim())&&i(`span`,{class:`field-hint warn`,children:`Exists – you will be asked before it is replaced.`})]}),i(`label`,{class:`check-row`,children:[i(`input`,{type:`checkbox`,checked:A,onChange:e=>ie(e.target.checked)}),`Run it now`,Q&&!Q.advanced?` (restarts this basic computer)`:` (in a new tab)`]}),i(`label`,{class:`check-row`,children:[i(`input`,{type:`checkbox`,checked:j,onChange:e=>ae(e.target.checked)}),`Start it automatically (startup.lua)`]}),P&&i(`p`,{class:`form-error`,role:`alert`,children:P}),L&&i(`p`,{class:`form-ok`,role:`status`,children:[`✓ `,L]}),i(`button`,{type:`submit`,class:`pxbtn pxbtn-gold pxbtn-wide`,disabled:M||!E.trim()||!O.trim()||!!$,children:i(`span`,{children:M?`Uploading…`:A?`Upload & run`:`Upload`})}),i(`p`,{class:`muted small`,children:[`The code runs on this CC computer only, inside CC's sandbox – not on the Minecraft server. Admins only; the mainframe PIN is asked if one is set.`,` `,i(te,{to:`/docs/lua#upload`,children:`More`})]})]}),b===`examples`&&i(`ul`,{class:`ex-grid`,children:y.map(e=>i(`li`,{class:`ex-card`,children:[i(`div`,{class:`ex-head`,children:[i(`b`,{children:e.title}),i(`span`,{class:`mono small muted`,children:e.name})]}),i(`p`,{class:`ex-text`,children:e.text}),i(`pre`,{class:`ex-code`,"aria-hidden":`true`,children:e.code.split(`
`).filter(e=>e.trim()&&!e.trim().startsWith(`--`)).slice(0,4).join(`
`)}),i(`div`,{class:`ex-foot`,children:[i(`span`,{class:`muted small`,children:[e.lines,` lines`]}),i(`button`,{type:`button`,class:`btn`,onClick:()=>Z(e),children:`Use this`})]})]},e.name))}),b===`files`&&i(`div`,{class:`files`,children:[C&&i(`div`,{class:`files-usage`,role:`img`,"aria-label":`${u(C.used)} of ${u(C.capacity)} used`,children:i(`span`,{ref:e=>{e&&(e.style.width=`${Math.min(100,Math.round(C.used/Math.max(1,C.capacity)*100))}%`)}})}),w&&i(`p`,{class:`form-error`,children:w}),C&&C.files.length===0&&i(`p`,{class:`muted`,children:`No files yet. Upload a script or pick an example.`}),i(`ul`,{class:`file-list`,children:C?.files.map(e=>e.dir?i(`li`,{class:`file-dir`,children:[e.path,`/`]},e.path):i(`li`,{class:`file-row ${e.path.includes(`/`)?`nested`:``}`,children:[i(`span`,{class:`file-name mono`,children:e.path.includes(`/`)?e.path.split(`/`)[1]:e.path}),i(`span`,{class:`muted small`,children:u(e.size)}),i(`span`,{class:`file-actions`,children:[i(`button`,{type:`button`,class:`btn btn-s`,onClick:()=>W({path:e.path}),children:`Edit`}),i(`button`,{type:`button`,class:`btn btn-s`,onClick:()=>ce(e.path),"aria-label":`Download ${e.path}`,children:`Download`}),i(`button`,{type:`button`,class:`btn btn-s btn-danger-ghost`,onClick:()=>le(e.path),"aria-label":`Delete ${e.path}`,children:`Delete`})]})]},e.path))})]})]}),z&&i(ee,{title:z.title,text:z.text,confirmLabel:z.label,color:z.color,onCancel:()=>{z.resolve(!1),B(null)},onConfirm:()=>{z.resolve(!0),B(null)}}),V&&i(s,{title:`Mainframe PIN`,text:`Changing files on a computer needs the mainframe PIN.`,color:`gold`,state:V.state,onCancel:()=>{V.resolve(null),H(null)},onSubmit:e=>{H({...V,state:{kind:`checking`}}),V.resolve(e)}}),U&&i(x,{conn:l,uplinkId:f,title:g,initialPath:U.path,onClose:()=>(W(null),q())})]})}export{S as default};