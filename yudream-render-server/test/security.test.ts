import { describe, expect, it } from "vitest";
import { assertSafeExternalUrl, isPrivateAddress, sanitizeMarkup } from "../src/security.js";

describe("render security", () => {
  it("removes scripts and event attributes", () => {
    expect(sanitizeMarkup('<script>alert(1)</script><p onclick="alert(2)">safe</p>')).toBe("<p>safe</p>");
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
});
