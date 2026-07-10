import { describe, expect, it } from "vitest";
import { buildServer } from "../src/server.js";

describe("render http contract", () => {
  it("declares a health endpoint", async () => {
    const app = buildServer();
    const response = await app.inject({ method: "GET", url: "/health" });
    expect(response.statusCode).toBe(200);
    await app.close();
  });

  it.todo("renders markdown into a nonblank png");
  it.todo("rejects queue saturation and render timeouts");
});
