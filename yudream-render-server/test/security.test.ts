import { describe, expect, it } from "vitest";
import { assertSafeExternalUrl, isPrivateAddress, validateMarkup } from "../src/security.js";

describe("render security", () => {
  it("preserves style tags and inline styles", () => {
    const markup = '<style>.menu { color: red; }</style><p style="color: blue">safe</p>';
    expect(validateMarkup(markup)).toBe(markup);
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
