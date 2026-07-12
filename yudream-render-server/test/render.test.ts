import { describe, expect, it } from "vitest";
import { buildServer } from "../src/server.js";

describe("render http contract", () => {
  it("declares a health endpoint", async () => {
    const app = buildServer();
    const response = await app.inject({ method: "GET", url: "/health" });
    expect(response.statusCode).toBe(200);
    await app.close();
  });

  it("captures the selected element at its exact box size", async () => {
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
  });

  it.todo("renders markdown into a nonblank png");
  it.todo("rejects queue saturation and render timeouts");
});
