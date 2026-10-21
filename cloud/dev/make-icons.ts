// Renders web/public/icon.svg into the PNG icons the PWA manifest needs.
// Run once after changing the icon: node dev/make-icons.ts
import { chromium } from "playwright";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const pub = fileURLToPath(new URL("../web/public/", import.meta.url));
const svg = readFileSync(pub + "icon.svg", "utf8");
const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "chrome" });
const page = await browser.newPage();
for (const [name, size, pad] of [["icon-192.png", 192, 0], ["icon-512.png", 512, 0], ["icon-maskable-512.png", 512, 0.18]] as const) {
  await page.setViewportSize({ width: size, height: size });
  const inner = Math.round(size * (1 - pad * 2));
  await page.setContent(
    `<body style="margin:0;background:#111;display:grid;place-items:center;width:${size}px;height:${size}px">` +
      svg.replace("<svg ", `<svg width="${inner}" height="${inner}" style="image-rendering:pixelated" `) + "</body>",
  );
  await page.screenshot({ path: pub + name, omitBackground: false });
  console.log("wrote", name);
}
await browser.close();
