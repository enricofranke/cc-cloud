// "Pair in browser" chat links: /pair#code=XXXX-XXXX[&account=1]
import { describe, expect, it } from "vitest";
import { deviceNameFor, takePairLink } from "../web/src/lib/pairlink.ts";

describe("pair links", () => {
  it("reads the code and the account flag from the fragment", () => {
    expect(takePairLink({ pathname: "/pair", hash: "#code=ABCD-EFGH" })).toEqual({ link: { code: "ABCDEFGH", account: false }, scrub: true });
    expect(takePairLink({ pathname: "/pair/", hash: "#code=abcd-efgh&account=1" })).toEqual({ link: { code: "ABCDEFGH", account: true }, scrub: true });
  });

  it("ignores invalid codes but still clears the fragment", () => {
    for (const hash of ["#code=ABCD-EFG", "#code=ABCD-EFG0", "#code=<script>", "#", ""]) expect(takePairLink({ pathname: "/pair", hash })).toEqual({ link: null, scrub: true });
  });

  it("only applies to the /pair path", () => {
    expect(takePairLink({ pathname: "/", hash: "#code=ABCD-EFGH" })).toEqual({ link: null, scrub: false });
    expect(takePairLink({ pathname: "/s/abc", hash: "#code=ABCD-EFGH" })).toEqual({ link: null, scrub: false });
    expect(takePairLink({ pathname: "/pairing", hash: "#code=ABCD-EFGH" })).toEqual({ link: null, scrub: false });
  });
});

describe("default device name", () => {
  const MAC_CHROME = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
  const IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";
  const WIN_EDGE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0";
  const ANDROID_TAB = "Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
  it("names device and browser, with the owner when known", () => {
    expect(deviceNameFor(MAC_CHROME, "Enrico")).toBe("Enrico's Mac – Chrome");
    expect(deviceNameFor(MAC_CHROME)).toBe("Mac – Chrome");
    expect(deviceNameFor(IPHONE, "Lars")).toBe("Lars' iPhone – Safari");
    expect(deviceNameFor(WIN_EDGE)).toBe("Windows PC – Edge");
    expect(deviceNameFor(ANDROID_TAB)).toBe("Android tablet – Chrome");
    expect(deviceNameFor("curl/8")).toBe("Browser");
    expect(deviceNameFor(MAC_CHROME, "A".repeat(60)).length).toBe(40);
  });
});
