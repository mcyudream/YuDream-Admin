import { PNG } from "pngjs";
import MarkdownIt from "markdown-it";
import type { Browser, BrowserContext, Page } from "playwright";
import { BrowserPool } from "./browser-pool.js";
import { limits } from "./config.js";
import { assertRenderOptions, assertSafeExternalUrl, assertTextLimit, RenderInputError, resolveRequestUrl, sameRegistrableSite, validateMarkup } from "./security.js";

// Kept alongside themes/default.css for deployments that mount or replace the stylesheet.
const defaultCss = `:root{color-scheme:light}*{box-sizing:border-box}html,body{margin:0;padding:0}body{width:fit-content;min-width:100%;padding:32px;color:#1f2937;background:#fff;font-family:"Noto Sans CJK SC","Microsoft YaHei","PingFang SC",Arial,sans-serif;font-size:16px;line-height:1.65;overflow-wrap:anywhere}pre,code{font-family:"Noto Sans Mono CJK SC","Cascadia Mono",Consolas,monospace}pre{padding:16px;overflow:auto;color:#e5e7eb;background:#111827;border-radius:4px}code{padding:1px 4px;background:#f3f4f6;border-radius:3px}pre code{padding:0;color:inherit;background:transparent}table{width:100%;border-collapse:collapse}th,td{padding:8px 12px;border:1px solid #d1d5db;text-align:left}blockquote{margin-left:0;padding-left:16px;color:#4b5563;border-left:4px solid #9ca3af}img,video{max-width:100%;height:auto}`;

function chromeClientHints(version: string): {
  userAgent: string;
  userAgentMetadata: {
    brands: Array<{ brand: string; version: string }>;
    fullVersionList: Array<{ brand: string; version: string }>;
    fullVersion: string;
    platform: string;
    platformVersion: string;
    architecture: string;
    model: string;
    mobile: boolean;
    bitness: string;
    wow64: boolean;
  };
} {
  const major = version.split(".")[0] || version;
  return {
    userAgent: `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${version} Safari/537.36`,
    userAgentMetadata: {
      brands: [
        { brand: "Not(A:Brand", version: "8" },
        { brand: "Chromium", version: major },
        { brand: "Google Chrome", version: major }
      ],
      fullVersionList: [
        { brand: "Not(A:Brand", version: "10.0.0.0" },
        { brand: "Chromium", version: version },
        { brand: "Google Chrome", version: version }
      ],
      fullVersion: version,
      platform: "Windows",
      platformVersion: "15.0.0",
      architecture: "x86",
      model: "",
      mobile: false,
      bitness: "64",
      wow64: false
    }
  };
}

export type RenderFormat = "png" | "jpeg";

export interface RenderRequest {
  html?: string;
  markdown?: string;
  url?: string;
  urlB64?: string;
  css?: string;
  width?: number;
  maxHeight?: number;
  timeoutMs?: number;
  deviceScaleFactor?: number;
  format?: RenderFormat;
  quality?: number;
  transparent?: boolean;
  selector?: string;
}

export interface RenderResult {
  contentType: string;
  data: string;
  width: number;
  height: number;
}

export interface UrlHtmlResult {
  html: string;
  finalUrl: string;
}

const markdown = new MarkdownIt({ html: false, linkify: false, typographer: true }).enable("table");

function imageType(format: RenderFormat): string {
  return format === "jpeg" ? "image/jpeg" : "image/png";
}

function buildDocument(body: string, css: string, transparent: boolean): string {
  if (/^\s*(?:<!doctype\s+html>\s*)?<html[\s>]/i.test(body)) {
    if (!css && !transparent) return body;
    const overrides = `<style>${css}\n${transparent ? "body { background: transparent; }" : ""}</style>`;
    return /<\/head\s*>/i.test(body) ? body.replace(/<\/head\s*>/i, `${overrides}</head>`) : `${overrides}${body}`;
  }
  return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https:; style-src 'unsafe-inline'"><style>${defaultCss}\n${css}\n${transparent ? "body { background: transparent; }" : ""}</style></head><body>${body}</body></html>`;
}

export class RenderService {
  public constructor(private readonly pool: BrowserPool) {}

  async renderHtml(input: RenderRequest): Promise<RenderResult> {
    if (typeof input.html !== "string") throw new Error("html is required");
    return this.renderDocument(buildDocument(validateMarkup(input.html), input.css ?? "", input.transparent === true), input);
  }

  async renderMarkdown(input: RenderRequest): Promise<RenderResult> {
    if (typeof input.markdown !== "string") throw new Error("markdown is required");
    assertTextLimit(input.markdown, limits.maxMarkdownBytes, "markdown");
    return this.renderDocument(buildDocument(validateMarkup(markdown.render(input.markdown)), input.css ?? "", input.transparent === true), input);
  }

  async renderUrl(input: RenderRequest): Promise<RenderResult> {
    const url = await assertSafeExternalUrl(resolveRequestUrl(input));
    assertRenderOptions(input);
    return this.withPage(input, { javaScriptEnabled: false }, async (page) => {
      await this.openLockedUrl(page, url, input.timeoutMs);
      return this.capture(page, input);
    });
  }

  async renderUrlHtml(input: RenderRequest): Promise<UrlHtmlResult> {
    const url = await assertSafeExternalUrl(resolveRequestUrl(input));
    const timeoutMs = input.timeoutMs ?? limits.defaultFetchedHtmlTimeoutMs;
    assertRenderOptions({ ...input, timeoutMs });
    return this.withPage(input, { javaScriptEnabled: true }, async (page) => {
      await this.openBrowserUrl(page, url, timeoutMs);
      const html = await this.readDocument(page);
      assertTextLimit(html, limits.maxFetchedHtmlBytes, "html");
      return { html, finalUrl: page.url() };
    });
  }

  private async openLockedUrl(page: Page, url: URL, timeoutMs?: number): Promise<void> {
    // Screenshot URL rendering still permits only the validated document navigation.
    await page.route("**/*", (route) => route.request().isNavigationRequest() && route.request().url() === url.toString() ? route.continue() : route.abort());
    await page.goto(url.toString(), { waitUntil: "domcontentloaded", timeout: timeoutMs ?? limits.defaultTimeoutMs });
  }

  private async openBrowserUrl(page: Page, url: URL, timeoutMs?: number): Promise<void> {
    const budget = timeoutMs ?? limits.defaultFetchedHtmlTimeoutMs;
    const started = Date.now();
    const remaining = () => Math.max(1_000, budget - (Date.now() - started));
    // Do not install page.route here. Aliyun WAF treats request interception as
    // a failed challenge even when every request is continued.
    const response = await page.goto(url.toString(), { waitUntil: "commit", timeout: budget });
    await this.waitForSettledDocument(page, remaining());
    let finalUrl: URL;
    try {
      finalUrl = new URL(page.url());
    } catch {
      throw new RenderInputError("url host is not allowed");
    }
    if (!sameRegistrableSite(url, finalUrl)) {
      throw new RenderInputError("url host is not allowed");
    }
    if (response && response.status() >= 400 && response.status() !== 412 && response.status() !== 400) {
      throw new RenderInputError(`url responded with HTTP ${response.status()}`);
    }
  }

  private async waitForSettledDocument(page: Page, timeoutMs: number): Promise<void> {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      try {
        await page.waitForLoadState("domcontentloaded", { timeout: Math.min(1_000, Math.max(1, deadline - Date.now())) });
        const html = await this.readDocument(page);
        const text = await page.evaluate(() => ((document.body && document.body.innerText) || "").trim());
        if (html.length > 200 && text.length > 20 && !html.includes("$_ts=")) {
          return;
        }
      } catch {
        // WAF challenge reloads the document; keep waiting until navigation settles.
      }
      await page.waitForTimeout(400);
    }
  }

  private async readDocument(page: Page): Promise<string> {
    for (let attempt = 0; attempt < 5; attempt++) {
      try {
        return await page.content();
      } catch {
        await page.waitForTimeout(200);
      }
    }
    return "";
  }

  private async renderDocument(document: string, input: RenderRequest): Promise<RenderResult> {
    assertRenderOptions(input);
    return this.withPage(input, { javaScriptEnabled: false }, async (page) => {
      await page.route("**/*", (route) => route.abort());
      await page.setContent(document, { waitUntil: "domcontentloaded", timeout: input.timeoutMs ?? limits.defaultTimeoutMs });
      return this.capture(page, input);
    });
  }

  private async withPage<T>(
    input: RenderRequest,
    options: { javaScriptEnabled: boolean },
    action: (page: Page) => Promise<T>
  ): Promise<T> {
    const browser = await this.pool.acquire();
    try {
      const hints = chromeClientHints(browser.version());
      const context = await browser.newContext({
        javaScriptEnabled: options.javaScriptEnabled,
        userAgent: hints.userAgent,
        locale: "zh-CN",
        timezoneId: "Asia/Shanghai",
        extraHTTPHeaders: { "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8" },
        viewport: { width: input.width ?? 1280, height: Math.min(input.maxHeight ?? limits.defaultMaxHeight, limits.viewportCap) },
        deviceScaleFactor: input.deviceScaleFactor ?? 1
      });
      try {
        // Do not patch navigator.webdriver / window.chrome. Aliyun WAF treats
        // those getter replacements as a failed challenge and returns HTTP 400.
        const page = await context.newPage();
        if (options.javaScriptEnabled) {
          await this.applyChromeClientHints(context, page, browser);
        }
        return await action(page);
      }
      finally { await context.close(); }
    } finally { this.pool.release(); }
  }

  private async applyChromeClientHints(context: BrowserContext, page: Page, browser: Browser): Promise<void> {
    const hints = chromeClientHints(browser.version());
    const session = await context.newCDPSession(page);
    await session.send("Emulation.setUserAgentOverride", {
      userAgent: hints.userAgent,
      acceptLanguage: "zh-CN,zh;q=0.9",
      platform: "Win32",
      userAgentMetadata: hints.userAgentMetadata
    });
  }

  private async capture(page: Page, input: RenderRequest): Promise<RenderResult> {
    const maxHeight = input.maxHeight ?? limits.defaultMaxHeight;
    const format = input.format ?? "png";
    if (input.selector) {
      const element = page.locator(input.selector);
      const box = await element.boundingBox();
      if (!box) throw new RenderInputError(`selector did not match a visible element: ${input.selector}`);
      if (box.height > maxHeight) throw new RenderInputError(`selected element exceeds the ${maxHeight}px height limit`);
      const screenshot = await element.screenshot({
        type: format,
        omitBackground: input.transparent === true && format === "png",
        quality: format === "png" ? undefined : input.quality ?? 90
      });
      return {
        contentType: imageType(format),
        data: screenshot.toString("base64"),
        width: Math.ceil(box.width),
        height: Math.ceil(box.height)
      };
    }

    const measured = await page.evaluate(() => Math.max(document.documentElement.scrollHeight, document.body.scrollHeight));
    const captureWidth = input.width ?? 1280;
    const height = Math.min(Math.max(measured, limits.minHeight), maxHeight);
    const viewportHeight = Math.min(height, limits.viewportCap);
    await page.setViewportSize({ width: Math.max(captureWidth, 320), height: viewportHeight });
    const screenshot = height <= viewportHeight
      ? await page.screenshot({
          type: format,
          clip: { x: 0, y: 0, width: captureWidth, height },
          omitBackground: input.transparent === true && format === "png",
          quality: format === "png" ? undefined : input.quality ?? 90
        })
      : await this.captureBeyondViewport(page, captureWidth, height, format, input);
    return { contentType: imageType(format), data: screenshot.toString("base64"), width: captureWidth, height };
  }

  private async captureBeyondViewport(
    page: Page,
    width: number,
    height: number,
    format: RenderFormat,
    input: RenderRequest
  ): Promise<Buffer> {
    try {
      return await this.captureWithCdp(page, width, height, format, input);
    } catch (error) {
      if (format !== "png") throw error;
      return this.stitchPngTiles(page, width, height, input);
    }
  }

  private async captureWithCdp(
    page: Page,
    width: number,
    height: number,
    format: RenderFormat,
    input: RenderRequest
  ): Promise<Buffer> {
    const session = await page.context().newCDPSession(page);
    const transparent = input.transparent === true && format === "png";
    if (transparent) {
      await session.send("Emulation.setDefaultBackgroundColorOverride", { color: { r: 0, g: 0, b: 0, a: 0 } });
    }
    try {
      const result = await session.send("Page.captureScreenshot", {
        format: format === "jpeg" ? "jpeg" : "png",
        quality: format === "jpeg" ? input.quality ?? 90 : undefined,
        fromSurface: true,
        captureBeyondViewport: true,
        clip: {
          x: 0,
          y: 0,
          width,
          height,
          scale: input.deviceScaleFactor ?? 1
        }
      });
      return Buffer.from(result.data, "base64");
    } finally {
      if (transparent) {
        await session.send("Emulation.setDefaultBackgroundColorOverride", {});
      }
    }
  }

  private async stitchPngTiles(page: Page, width: number, height: number, input: RenderRequest): Promise<Buffer> {
    const canvas = new PNG({ width, height });
    for (let y = 0; y < height; y += limits.viewportCap) {
      const tileHeight = Math.min(limits.viewportCap, height - y);
      const raw = await page.screenshot({
        type: "png",
        clip: { x: 0, y, width, height: tileHeight },
        omitBackground: input.transparent === true
      });
      const tile = PNG.sync.read(raw);
      PNG.bitblt(tile, canvas, 0, 0, tile.width, tile.height, 0, y);
    }
    return PNG.sync.write(canvas);
  }
}
