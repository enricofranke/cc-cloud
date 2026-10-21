import { render } from "preact";
import { App } from "./app.tsx";
import "./styles.css";

render(<App />, document.getElementById("app")!);

if ("serviceWorker" in navigator && import.meta.env.PROD) {
  // Service workers need a secure context (https or localhost); on plain-HTTP LAN
  // addresses this silently does nothing and the app still works.
  navigator.serviceWorker.register("/sw.js").catch(() => {});
}
