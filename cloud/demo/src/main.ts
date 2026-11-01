// Entry of the live demo. Order matters: the stand-ins for storage, fetch and WebSocket have to be in place before the
// dashboard's modules load, so it is imported dynamically. The worker and the dashboard load side by side.
import { installBridge } from "./bridge.ts";
import { installShims } from "./shims.ts";
import { lang, s } from "./strings.ts";

function failed() {
  const boot = document.getElementById("demo-boot");
  if (!boot) return;
  boot.classList.add("failed");
  boot.querySelector("[data-boot-text]")!.textContent = s.failed;
}

document.documentElement.lang = lang;
installShims(lang);
installBridge(failed);
import("./app.tsx")
  .then((m) => m.mount())
  .catch(failed);
