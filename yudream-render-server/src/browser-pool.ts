import { chromium, type Browser } from "playwright";
import { limits } from "./config.js";

export class RenderQueueFullError extends Error {}

export class BrowserPool {
  private browser?: Browser;
  private starting?: Promise<Browser>;
  private active = 0;
  private readonly waiters: Array<() => void> = [];

  async acquire(): Promise<Browser> {
    if (this.active >= limits.maxConcurrent && this.waiters.length >= limits.maxQueue) throw new RenderQueueFullError("render queue is full");
    if (this.active >= limits.maxConcurrent) await new Promise<void>((resolve) => this.waiters.push(resolve));
    this.active++;
    try {
      return await this.getBrowser();
    } catch (error) {
      this.release();
      throw error;
    }
  }

  release(): void {
    this.active--;
    this.waiters.shift()?.();
  }

  async healthy(): Promise<boolean> {
    try {
      const browser = await this.getBrowser();
      return browser.isConnected();
    } catch {
      return false;
    }
  }

  async close(): Promise<void> {
    await this.browser?.close();
    this.browser = undefined;
    this.starting = undefined;
  }

  private async getBrowser(): Promise<Browser> {
    if (this.browser?.isConnected()) return this.browser;
    // Full Chromium (not chrome-headless-shell). Aliyun WAF rejects the
    // HeadlessChrome client hint that Playwright's default headless binary sends.
    this.starting ??= chromium.launch({
      channel: "chromium",
      headless: true,
      ignoreDefaultArgs: ["--enable-automation"],
      args: [
        "--disable-dev-shm-usage",
        "--disable-blink-features=AutomationControlled",
        "--no-first-run",
        "--no-default-browser-check",
        "--no-sandbox",
        "--disable-crash-reporter",
        "--crash-dumps-dir=/tmp"
      ]
    });
    try { this.browser = await this.starting; return this.browser; }
    finally { this.starting = undefined; }
  }
}
