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
    this.starting ??= chromium.launch({ headless: true, args: ["--disable-dev-shm-usage"] });
    try { this.browser = await this.starting; return this.browser; }
    finally { this.starting = undefined; }
  }
}
