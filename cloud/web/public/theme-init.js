// Applies the saved theme before first paint (external file so a strict CSP without 'unsafe-inline' allows it).
try {
  var t = localStorage.getItem("cccloud.theme") || "dark";
  if (t === "system") t = matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
  document.documentElement.setAttribute("data-theme", t);
} catch (e) {}
