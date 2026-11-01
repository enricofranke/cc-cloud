// Before first paint: the website's theme and the language of the loading text.
// (An external file: the site's CSP allows no stray inline script.)
try {
  var t = localStorage.getItem("cccloud.theme") || "dark";
  if (t === "system") t = matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
  document.documentElement.setAttribute("data-theme", t);
} catch (e) {}
var l = /[?&]lang=(de|en)\b/.exec(location.search);
document.documentElement.lang = l ? l[1] : (navigator.language || "").slice(0, 2) === "de" ? "de" : "en";
