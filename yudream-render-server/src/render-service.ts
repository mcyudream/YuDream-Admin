import MarkdownIt from "markdown-it";
import type { Page } from "playwright";
import { BrowserPool } from "./browser-pool.js";
import { limits } from "./config.js";
import { assertRenderOptions, assertSafeExternalUrl, assertTextLimit, sanitizeMarkup } from "./security.js";

// Kept alongside themes/default.css for deployments that mount or replace the stylesheet.
const defaultCss = `:root{color-scheme:light}*{box-sizing:border-box}html,body{margin:0;padding:0}body{width:fit-content;min-width:100%;padding:32px;color:#1f2937;background:#fff;font-family:"Noto Sans CJK SC","Microsoft YaHei","PingFang SC",Arial,sans-serif;font-size:16px;line-height:1.65;overflow-wrap:anywhere}pre,code{font-family:"Noto Sans Mono CJK SC","Cascadia Mono",Consolas,monospace}pre{padding:16px;overflow:auto;color:#e5e7eb;background:#111827;border-radius:4px}code{padding:1px 4px;background:#f3f4f6;border-radius:3px}pre code{padding:0;color:inherit;background:transparent}table{width:100%;border-collapse:collapse}th,td{padding:8px 12px;border:1px solid #d1d5db;text-align:left}blockquote{margin-left:0;padding-left:16px;color:#4b5563;border-left:4px solid #9ca3af}img,video{max-width:100%;height:auto}`;

export type RenderFormat = "png" | "jpeg" | "webp";

export interface RenderRequest {
  html?: string;
  markdown?: string;
  url?: string;
  css?: string;
  width?: number;
  maxHeight?: number;
  timeoutMs?: number;
  deviceScaleFactor?: number;
  format?: RenderFormat;
  quality?: number;
  transparent?: boolean;
}

export interface RenderResult {
  contentType: string;
  data: string;
  width: number;
  height: number;
}

const markdown = new MarkdownIt({ html: false, linkify: false, typographer: true }).enable("table");

function imageType(format: RenderFormat): string {
  return format === "jpeg" ? "image/jpeg" : `image/${format}`;
}

function buildDocument(body: string, css: string, transparent: boolean): string {
  return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https:; style-src 'unsafe-inline'"><style>${defaultCss}\n${css}\n${transparent ? "body { background: transparent; }" : ""}</style></head><body>${body}</body></html>`;
}

export class RenderService {
  public constructor(private readonly pool: BrowserPool) {}

  async renderHtml(input: RenderRequest): Promise<RenderResult> {
    if (typeof input.html !== "string") throw new Error("html is required");
    return this.renderDocument(buildDocument(sanitizeMarkup(input.html), input.css ?? "", input.transparent === true), input);
  }

  async renderMarkdown(input: RenderRequest): Promise<RenderResult> {
    if (typeof input.markdown !== "string") throw new Error("markdown is required");
    assertTextLimit(input.markdown, limits.maxMarkdownBytes, "markdown");
    return this.renderDocument(buildDocument(sanitizeMarkup(markdown.render(input.markdown)), input.css ?? "", input.transparent === true), input);
  }

  async renderUrl(input: RenderRequest): Promise<RenderResult> {
    if (typeof input.url !== "string") throw new Error("url is required");
    const url = await assertSafeExternalUrl(input.url);
    assertRenderOptions(input);
    return this.withPage(input, async (page) => {
      // Permit the validated document navigation only. Redirects and every subresource are denied.
      await page.route("**/*", (route) => route.request().isNavigationRequest() && route.request().url() === url.toString() ? route.continue() : route.abort());
      await page.goto(url.toString(), { waitUntil: "domcontentloaded", timeout: input.timeoutMs ?? limits.defaultTimeoutMs });
      return this.capture(page, input);
    });
  }

  private async renderDocument(document: string, input: RenderRequest): Promise<RenderResult> {
    assertRenderOptions(input);
    return this.withPage(input, async (page) => {
      await page.route("**/*", (route) => route.abort());
      await page.setContent(document, { waitUntil: "domcontentloaded", timeout: input.timeoutMs ?? limits.defaultTimeoutMs });
      return this.capture(page, input);
    });
  }

  private async withPage(input: RenderRequest, action: (page: Page) => Promise<RenderResult>): Promise<RenderResult> {
    const browser = await this.pool.acquire();
    try {
      const context = await browser.newContext({
        javaScriptEnabled: false,
        viewport: { width: input.width ?? 900, height: Math.min(input.maxHeight ?? 4_000, 1_080) },
        deviceScaleFactor: input.deviceScaleFactor ?? 1
      });
      try { return await action(await context.newPage()); }
      finally { await context.close(); }
    } finally { this.pool.release(); }
  }

  private async capture(page: Page, input: RenderRequest): Promise<RenderResult> {
    const maxHeight = input.maxHeight ?? 4_000;
    const measured = await page.evaluate(() => Math.max(document.documentElement.scrollHeight, document.body.scrollHeight));
    const height = Math.min(Math.max(measured, limits.minHeight), maxHeight);
    await page.setViewportSize({ width: input.width ?? 900, height: Math.min(height, 1_080) });
    const format = input.format ?? "png";
    const screenshot = await page.screenshot({
      type: format,
      clip: { x: 0, y: 0, width: input.width ?? 900, height },
      omitBackground: input.transparent === true && format === "png",
      quality: format === "png" ? undefined : input.quality ?? 90
    });
    return { contentType: imageType(format), data: screenshot.toString("base64"), width: input.width ?? 900, height };
  }
}
