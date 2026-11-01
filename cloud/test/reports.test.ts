// Crash reports, usage stats and feedback: the client helpers against the mock's protocol.
import { afterEach, describe, expect, it } from "vitest";
import { createMockServer, type MockServer } from "../dev/mock-server.ts";
import { STRINGS, type Key } from "../web/src/lib/i18n.ts";
import {
  FEEDBACK_MAX,
  decideReport,
  dismissReports,
  feedbackProblem,
  getPreview,
  getReports,
  parsePreview,
  parseReports,
  pretty,
  previewFeedback,
  privacyUrl,
  sendFeedback,
} from "../web/src/lib/reports.ts";

let mock: MockServer | undefined;
afterEach(async () => {
  await mock?.close();
  mock = undefined;
});
const base = () => `http://127.0.0.1:${mock!.port}`;
async function start() {
  mock = await createMockServer({ port: 0, host: "127.0.0.1", log: () => {} });
  return mock.base.id;
}
const token = async (name: string, canControl = true) =>
  (await (await fetch(`${base()}/dev/token`, { method: "POST", body: JSON.stringify({ mainframeId: mock!.base.id, name, canControl }) })).json()).token as string;

describe("the owner's decision", () => {
  it("starts undecided and prompts the owner", async () => {
    const id = await start();
    const doc = await getReports(base(), await token("Owner"), id);
    expect(doc.prompt).toBe(true);
    expect(doc.canDecide).toBe(true);
    expect(doc.channels).toEqual({ crash: "ask", stats: "ask" });
  });

  it("does not prompt a read-only browser and hides the state from it", async () => {
    const id = await start();
    const doc = await getReports(base(), await token("Viewer", false), id);
    expect(doc.prompt).toBe(false);
    expect(doc.canDecide).toBe(false);
    expect(doc.channels).toBeNull();
    expect(doc.feedback).toBe(false);
  });

  it("refuses decisions and the preview to others", async () => {
    const id = await start();
    const viewer = await token("Viewer", false);
    await expect(decideReport(base(), viewer, id, "crash", true)).rejects.toThrow("not_owner");
    await expect(getPreview(base(), viewer, id)).rejects.toThrow("not_owner");
    expect(mock!.reports.channels.crash).toBe("ask");
  });

  it("decides each channel on its own and a yes can be taken back", async () => {
    const id = await start();
    const owner = await token("Owner");
    let doc = await decideReport(base(), owner, id, "crash", true);
    expect(doc.channels).toEqual({ crash: "active", stats: "ask" });
    expect(doc.prompt).toBe(true);
    doc = await decideReport(base(), owner, id, "stats", false);
    expect(doc.channels).toEqual({ crash: "active", stats: "declined" });
    expect(doc.prompt).toBe(false);
    doc = await decideReport(base(), owner, id, "crash", false);
    expect(doc.channels).toEqual({ crash: "declined", stats: "declined" });
  });

  it("does not come back after it was put aside, and nothing was decided", async () => {
    const id = await start();
    const owner = await token("Owner");
    const doc = await dismissReports(base(), owner, id);
    expect(doc.prompt).toBe(false);
    expect(doc.channels).toEqual({ crash: "ask", stats: "ask" });
    expect((await getReports(base(), owner, id)).prompt).toBe(false);
  });

  it("works on servers without the routes: the helpers just fail", async () => {
    await start();
    await expect(getReports(base(), "nope", "00000000-0000-0000-0000-000000000000")).rejects.toThrow();
  });
});

describe("the preview", () => {
  it("shows the real payloads, the crash marked as an example", async () => {
    const id = await start();
    const preview = await getPreview(base(), await token("Owner"), id);
    expect(preview.crash.example).toBe(true);
    expect(pretty(preview.crash.payload)).toContain("fingerprint");
    expect(pretty(preview.stats.payload)).toContain("integrations");
  });

  it("parses defensively", () => {
    expect(() => parseReports(null)).toThrow();
    expect(() => parseReports({})).toThrow();
    expect(parseReports({ version: 1, channels: { crash: "weird" } }).channels).toEqual({ crash: "ask", stats: "ask" });
    expect(parseReports({ version: 1 }).prompt).toBe(false);
    expect(parsePreview({}).crash.example).toBe(true);
    expect(parsePreview({}).stats.payload).toBeNull();
    expect(() => parsePreview(null)).toThrow();
  });
});

describe("feedback", () => {
  const input = { category: "bug" as const, text: "It broke at /home/steve/world" };

  it("shows the cleaned message and sends nothing", async () => {
    const id = await start();
    const payload = (await previewFeedback(base(), await token("Owner"), id, input)) as Record<string, unknown>;
    expect(payload).toEqual({ v: 1, category: "bug", text: "It broke at <path>" });
    expect(mock!.reports.feedback).toEqual([]);
  });

  it("adds contact and technical info only when given", async () => {
    const id = await start();
    const owner = await token("Owner");
    expect(await previewFeedback(base(), owner, id, { ...input, contact: "  " })).not.toHaveProperty("contact");
    expect(await previewFeedback(base(), owner, id, input)).not.toHaveProperty("env");
    const full = await previewFeedback(base(), owner, id, { ...input, contact: "steve#1", technical: true });
    expect(full).toHaveProperty("contact", "steve#1");
    expect(full).toHaveProperty("env");
  });

  it("sends once after the confirmation", async () => {
    const id = await start();
    expect(await sendFeedback(base(), await token("Owner"), id, input)).toBe(true);
    expect(mock!.reports.feedback).toHaveLength(1);
  });

  it("names the limit", async () => {
    const id = await start();
    const owner = await token("Owner");
    for (let i = 0; i < 3; i++) await sendFeedback(base(), owner, id, input);
    await expect(sendFeedback(base(), owner, id, input)).rejects.toThrow("rate_limited");
  });

  it("refuses a read-only browser", async () => {
    const id = await start();
    await expect(previewFeedback(base(), await token("Viewer", false), id, input)).rejects.toThrow("not_allowed");
  });

  it("closes the Send step for empty and oversized messages", () => {
    expect(feedbackProblem({ category: "bug", text: "   " })).toBe("empty");
    expect(feedbackProblem({ category: "bug", text: "ok" })).toBeNull();
    expect(feedbackProblem({ category: "bug", text: "x".repeat(FEEDBACK_MAX) })).toBeNull();
    expect(feedbackProblem({ category: "bug", text: "x".repeat(FEEDBACK_MAX + 1) })).toBe("too_long");
    expect(feedbackProblem({ category: "bug", text: "ok", contact: "x".repeat(121) })).toBe("too_long");
    expect(feedbackProblem({ category: "bug", text: "語".repeat(1300) })).toBe("too_long");
  });
});

describe("the wording", () => {
  const strings = (l: "en" | "de", k: string) => STRINGS[l][k as Key];

  it("links the policy in the reader's language", () => {
    expect(privacyUrl("en")).toBe("https://cccloud.dev/en/legal/privacy/");
    expect(privacyUrl("de")).toBe("https://cccloud.dev/de/legal/privacy/");
  });

  it("says who it is for and asks minors to check first, in both languages", () => {
    expect(strings("en", "rp.operator")).toMatch(/operator/);
    expect(strings("en", "rp.operator")).toMatch(/under 16/);
    expect(strings("de", "rp.operator")).toMatch(/Betreiber/);
    expect(strings("de", "rp.operator")).toMatch(/unter 16/);
  });

  it("warns about personal data in feedback in both languages", () => {
    expect(strings("en", "rp.fb.warning")).toMatch(/personal data/);
    expect(strings("de", "rp.fb.warning")).toMatch(/persönliche Daten/);
  });
});
