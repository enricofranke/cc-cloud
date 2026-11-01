// Loaded after the shims: importing the dashboard reads its storage and opens its connections.
import { render } from "preact";
import { App } from "../../web/src/app.tsx";
import "../../web/src/styles.css";
import "./demo.css";

export function mount() {
  render(<App />, document.getElementById("app")!);
  document.getElementById("demo-boot")?.remove();
}
