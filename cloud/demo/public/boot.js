// Before first paint: the website's theme (external file: the site's CSP allows no stray inline script).
try {
  var t = localStorage.getItem("cccloud.theme") || "dark";
  if (t === "system") t = matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
  document.documentElement.setAttribute("data-theme", t);
} catch (e) {}
