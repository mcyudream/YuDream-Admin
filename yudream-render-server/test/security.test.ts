import { describe, expect, it } from "vitest";
import { assertSafeExternalUrl, decodeUrlB64, isAllowedPageResource, isPrivateAddress, resolveRequestUrl, sameRegistrableSite, validateMarkup } from "../src/security.js";

describe("render security", () => {
  it("allows html up to 2 MiB and rejects larger payloads", () => {
    const allowed = "x".repeat(2 * 1024 * 1024);
    expect(validateMarkup(allowed)).toBe(allowed);
    expect(() => validateMarkup(allowed + "y")).toThrow(/html exceeds the 2097152 byte limit/);
  });

  it("recognises private addresses", () => {
    expect(isPrivateAddress("127.0.0.1")).toBe(true);
    expect(isPrivateAddress("10.0.0.1")).toBe(true);
    expect(isPrivateAddress("8.8.8.8")).toBe(false);
  });

  it("rejects local url targets", async () => {
    await expect(assertSafeExternalUrl("file:///etc/passwd")).rejects.toThrow("scheme");
    await expect(assertSafeExternalUrl("http://127.0.0.1:3000")).rejects.toThrow("private");
  });

  it("decodes standard and url-safe base64 urls", () => {
    const url = "https://www.chsi.com.cn/xlcx/bg.do?vcode=APEVUKH9C8SSGS5D&srcid=bgcx";
    const standard = Buffer.from(url, "utf8").toString("base64");
    const urlSafe = Buffer.from(url, "utf8").toString("base64url");
    expect(decodeUrlB64(standard)).toBe(url);
    expect(decodeUrlB64(urlSafe)).toBe(url);
  });

  it("prefers urlB64 over raw url", () => {
    const encoded = Buffer.from("https://example.com/a?q=1", "utf8").toString("base64url");
    expect(resolveRequestUrl({ url: "https://ignored.example", urlB64: encoded })).toBe("https://example.com/a?q=1");
    expect(resolveRequestUrl({ url: "https://example.com/plain" })).toBe("https://example.com/plain");
  });

  it("rejects missing or invalid urlB64", () => {
    expect(() => resolveRequestUrl({})).toThrow("url or urlB64 is required");
    expect(() => decodeUrlB64("@@@")).toThrow("urlB64 is invalid");
  });

  it("allows same-site scripts and blocks other hosts", () => {
    const origin = new URL("https://www.chsi.com.cn/xlcx/bg.do");
    expect(sameRegistrableSite(origin, new URL("https://www.chsi.com.cn/static/app.js"))).toBe(true);
    expect(sameRegistrableSite(origin, new URL("https://static.chsi.com.cn/app.js"))).toBe(true);
    expect(sameRegistrableSite(origin, new URL("https://chsi.com.cn/app.js"))).toBe(true);
    expect(sameRegistrableSite(origin, new URL("https://evilchsi.com.cn/app.js"))).toBe(false);
    expect(sameRegistrableSite(origin, new URL("https://evil.example/app.js"))).toBe(false);
    expect(sameRegistrableSite(origin, new URL("http://www.chsi.com.cn/app.js"))).toBe(false);
    expect(isAllowedPageResource("script")).toBe(true);
    expect(isAllowedPageResource("stylesheet")).toBe(true);
    expect(isAllowedPageResource("xhr")).toBe(true);
    expect(isAllowedPageResource("other")).toBe(true);
    expect(isAllowedPageResource("image")).toBe(true);
    expect(isAllowedPageResource("font")).toBe(true);
    expect(isAllowedPageResource("media")).toBe(false);
  });
});
