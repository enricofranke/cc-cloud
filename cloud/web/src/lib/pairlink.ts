// "Pair in browser" chat link from the mod: <dashboard>/pair#code=XXXX-XXXX[&account=1].
// The code only lives in the URL fragment (never sent to the server in a URL,
// log or Referer). It is read once at start-up, the fragment is removed with
// history.replaceState, and the code is only ever sent in the POST body of /api/pair.

export interface PairLink {
  code: string;
  account: boolean;
}

const CODE = /^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$/;

/** Reads a pairing link. `scrub` tells the caller to clear the fragment (also for invalid links). */
export function takePairLink(loc: { pathname: string; hash: string }): { link: PairLink | null; scrub: boolean } {
  if (!/^\/pair\/?$/.test(loc.pathname)) return { link: null, scrub: false };
  const params = new URLSearchParams(loc.hash.replace(/^#/, ""));
  const raw = (params.get("code") ?? "").toUpperCase().replace(/[-\s]/g, "");
  return { link: CODE.test(raw) ? { code: raw, account: params.get("account") === "1" } : null, scrub: true };
}

/** "Enrico's Mac – Chrome" (owner known from a linked account) or "Mac – Chrome". */
export function deviceNameFor(ua: string, owner?: string) {
  const device = /iPhone/.test(ua)
    ? "iPhone"
    : /iPad/.test(ua)
      ? "iPad"
      : /Android/.test(ua)
        ? /Mobile/.test(ua)
          ? "Android phone"
          : "Android tablet"
        : /CrOS/.test(ua)
          ? "Chromebook"
          : /Mac/.test(ua)
            ? "Mac"
            : /Windows/.test(ua)
              ? "Windows PC"
              : /Linux/.test(ua)
                ? "Linux PC"
                : "Browser";
  const browser = /Edg\//.test(ua) ? "Edge" : /OPR\//.test(ua) ? "Opera" : /Firefox\//.test(ua) ? "Firefox" : /Chrome\//.test(ua) ? "Chrome" : /Safari\//.test(ua) ? "Safari" : "";
  const name = browser ? `${device} – ${browser}` : device;
  const o = owner?.trim();
  return (o ? `${o}${/s$/i.test(o) ? "'" : "'s"} ${name}` : name).slice(0, 40);
}
