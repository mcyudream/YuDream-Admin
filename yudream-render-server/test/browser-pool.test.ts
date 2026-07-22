import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("playwright", () => ({
  chromium: { launch: vi.fn() }
}));

import { chromium } from "playwright";
import { BrowserPool } from "../src/browser-pool.js";

const launch = vi.mocked(chromium.launch);

describe("BrowserPool", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("releases its acquired slot when browser startup fails", async () => {
    const startupError = new Error("browser startup failed");
    const browser = { isConnected: () => true };
    launch.mockRejectedValueOnce(startupError).mockResolvedValueOnce(browser as never);
    const pool = new BrowserPool();

    await expect(pool.acquire()).rejects.toBe(startupError);
    await expect(pool.acquire()).resolves.toBe(browser);

    pool.release();
    expect(launch).toHaveBeenCalledTimes(2);
  });
});
