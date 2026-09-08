import { describe, expect, it } from "vitest";
import { buildServer } from "../src/server.js";

describe("render http contract", () => {
  it("declares a health endpoint", async () => {
    const app = buildServer();
    const response = await app.inject({ method: "GET", url: "/health" });
    expect(response.statusCode).toBe(200);
    await app.close();
  });

  it.skipIf(!process.env.PLAYWRIGHT_BROWSERS_PATH && process.platform === "win32")(
    "captures the selected element at its exact box size",
    async () => {
      const app = buildServer();
      const response = await app.inject({
        method: "POST",
        url: "/v1/render/html",
        payload: {
          html: "<!doctype html><html><body><main id='target' style='box-sizing:border-box;width:320px;height:120px;padding:20px;background:#eee'><div style='height:100%;background:#fff'></div></main></body></html>",
          selector: "#target"
        }
      });
      expect(response.statusCode).toBe(200);
      expect(response.json()).toMatchObject({ width: 320, height: 120, contentType: "image/png" });
      await app.close();
    }
  );

  it("rejects url-html without a target", async () => {
    const app = buildServer();
    const response = await app.inject({
      method: "POST",
      url: "/v1/render/url-html",
      payload: {}
    });
    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({ message: "url or urlB64 is required" });
    await app.close();
  });

  it("accepts urlB64 as a query param for url-html", async () => {
    const app = buildServer();
    const urlB64 = Buffer.from("http://127.0.0.1/", "utf8").toString("base64url");
    const response = await app.inject({
      method: "POST",
      url: `/v1/render/url-html?urlB64=${encodeURIComponent(urlB64)}`,
      payload: {}
    });
    expect(response.statusCode).toBe(400);
    expect(String(response.json().message)).toMatch(/private|not allowed/);
    await app.close();
  });

  it.todo("renders markdown into a nonblank png");
  it.todo("rejects queue saturation and render timeouts");
});
