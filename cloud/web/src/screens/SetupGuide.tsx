// Empty state after pairing: no uplinks linked yet (v0.3 "Web empty state").
import { CodeBlock } from "../components/CodeBlock.tsx";
import { DocsLink } from "../components/DocsLink.tsx";
import { ccTweaked } from "../lib/config.ts";
import { t } from "../lib/i18n.ts";

const STARTER = `local cloud = peripheral.find("cloud_uplink")
cloud.setTitle("My Base")
cloud.gauge("temp", "Temperature", {max=1200, unit="K", warn=900, crit=1100})
cloud.toggle("lights", "Lights", {value=false})
cloud.button("alarm", "Alarm", {color="red", confirm=true})

while true do
  local _, id, value, user = os.pullEvent("cloud_action")
  if id == "lights" then
    redstone.setOutput("back", value)
    cloud.set("lights", value)
  elseif id == "alarm" then
    cloud.alert(user .. " pressed the alarm", "warn")
  end
end`;

export function SetupGuide() {
  // without CC: Tweaked there are no computers: machines, storage and redstone devices instead
  const cc = ccTweaked();
  return (
    <div class="setup">
      <h2 class="px-heading setup-title">{cc ? "Connect your first computer" : "Connect your first machine"}</h2>
      <p class="muted setup-lead">{cc ? "No code needed – a linked computer shows up here on its own." : "No code needed – a linked machine shows up here on its own."}</p>
      <ol class="setup-steps">
        <li>
          <span class="setup-num">1</span>
          <div>
            {cc ? (
              <>
                <b>Place a Cloud Uplink</b> on a side of a computer (normal or advanced).
              </>
            ) : (
              <>
                <b>Place a Cloud Uplink</b> on a machine, a storage block or an energy cell – or place a <b>Cloud Lamp</b> or <b>Cloud Switch</b>.
              </>
            )}
          </div>
        </li>
        <li>
          <span class="setup-num">2</span>
          <div>
            <b>Link it:</b> right-click the Cloud Mainframe with a <b>Cloud Link Card</b>, then right-click the uplink.
          </div>
        </li>
        <li>
          <span class="setup-num">3</span>
          <div>
            {cc ? (
              <>
                <b>Done.</b> The computer appears here as a <b>Computer card</b>: power, reboot and its redstone sides.
              </>
            ) : (
              <>
                <b>Done.</b> It appears here as a card: the machine's values, its storage or a switch.
              </>
            )}
          </div>
        </li>
      </ol>
      <p class="muted small setup-note">
        This page updates by itself – keep it open while you build. Stuck? <DocsLink to="/docs/start#linking">Step-by-step guide</DocsLink>
      </p>
      <p class="muted small setup-note">{t("ps.setupHint")}</p>
      {!cc && (
        <p class="muted small setup-note">
          Computers and Lua programs need CC: Tweaked, which this server does not have. <DocsLink to="/docs/start#cc-tweaked">What it adds</DocsLink>
        </p>
      )}
      {cc && <details class="setup-more">
        <summary>Build your own dashboard</summary>
        <p class="muted small">
          Run <code>cloud-demo</code> on the computer to see every widget, or start from this script (save it with <code>edit startup.lua</code>):
        </p>
        <CodeBlock code={STARTER} label="startup.lua" />
        <p class="muted small">
          In-game help: <code>help cloud</code>. As soon as a program defines widgets, they replace the automatic Computer card. New to ComputerCraft? Read{" "}
          <DocsLink to="/docs/cc-basics">CC basics</DocsLink> (why typing Lua into the shell says "No such program") and the{" "}
          <DocsLink to="/docs/lua">Lua reference</DocsLink>.
        </p>
      </details>}
    </div>
  );
}
