// Empty state after pairing: no uplinks linked yet (v0.3 "Web empty state").
import { CodeBlock } from "../components/CodeBlock.tsx";

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
  return (
    <div class="setup">
      <h2 class="px-heading setup-title">Connect your first computer</h2>
      <p class="muted setup-lead">No code needed – a linked computer shows up here on its own.</p>
      <ol class="setup-steps">
        <li>
          <span class="setup-num">1</span>
          <div>
            <b>Place a Cloud Uplink</b> on a side of a computer (normal or advanced).
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
            <b>Done.</b> The computer appears here as a <b>Computer card</b>: power, reboot and its redstone sides.
          </div>
        </li>
      </ol>
      <p class="muted small setup-note">This page updates by itself – keep it open while you build.</p>
      <details class="setup-more">
        <summary>Build your own dashboard</summary>
        <p class="muted small">
          Run <code>cloud-demo</code> on the computer to see every widget, or start from this script (save it with <code>edit startup.lua</code>):
        </p>
        <CodeBlock code={STARTER} label="startup.lua" />
        <p class="muted small">
          In-game help: <code>help cloud</code>. As soon as a program defines widgets, they replace the automatic Computer card.
        </p>
      </details>
    </div>
  );
}
