import { beforeEach, describe, expect, it, vi } from "vitest";
import { PNG } from "pngjs";
import type { Browser, Request, Route } from "playwright";
import { limits } from "../src/config.js";
import { RenderService } from "../src/render-service.js";

type RouteHandler = (route: Route) => Promise<void> | void;

function createHarness() {
  let routeHandler: RouteHandler | undefined;
  const page = {
    url: vi.fn(() => "https://www.chsi.com.cn/xlcx/bg.do?vcode=APEVUKH9C8SSGS5D&srcid=bgcx"),
    content: vi.fn(async () => `<html><body>${"教育部学籍在线验证报告 郭金龙 西南科技大学 ".repeat(20)}</body></html>`),
    goto: vi.fn(async () => ({ status: () => 200 })),
    waitForLoadState: vi.fn(async () => undefined),
    waitForURL: vi.fn(async () => undefined),
    waitForTimeout: vi.fn(async () => {
      throw new Error("waitForTimeout should not loop forever in unit tests");
    }),
    evaluate: vi.fn(async () => "教育部学籍在线验证报告 郭金龙 西南科技大学"),
    route: vi.fn(async (_pattern: string, handler: RouteHandler) => {
      routeHandler = handler;
    }),
    screenshot: vi.fn(),
    setContent: vi.fn(),
    setViewportSize: vi.fn(),
    locator: vi.fn(),
    context: vi.fn(() => ({
      newCDPSession: vi.fn(async () => ({
        send: vi.fn(async (method: string) => {
          if (method === "Page.captureScreenshot") {
            return { data: PNG.sync.write(new PNG({ width: 320, height: 1_200 })).toString("base64") };
          }
          return {};
        })
      }))
    }))
  };
  const cdp = {
    send: vi.fn(async () => undefined)
  };
  const context = {
    newPage: vi.fn(async () => page),
    addInitScript: vi.fn(async () => undefined),
    newCDPSession: vi.fn(async () => cdp),
    close: vi.fn(async () => undefined)
  };
  const browser = {
    newContext: vi.fn(async () => context),
    version: vi.fn(() => "148.0.7778.96")
  };
  const pool = {
    acquire: vi.fn(async () => browser as unknown as Browser),
    release: vi.fn()
  };
  const continueFn = vi.fn(async () => undefined);
  const abortFn = vi.fn(async () => undefined);
  const route = (request: Request): Route => ({
    request: () => request,
    continue: continueFn,
    abort: abortFn
  } as unknown as Route);

  return {
    service: new RenderService(pool as never),
    page,
    context,
    browser,
    cdp,
    pool,
    continueFn,
    abortFn,
    route,
    dispatch: async (request: Request) => {
      if (!routeHandler) throw new Error("route handler was not registered");
      await routeHandler(route(request));
    }
  };
}

describe("RenderService url-html", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("enables javascript and waits for a browser-like load", async () => {
    const harness = createHarness();
    const result = await harness.service.renderUrlHtml({
      url: "https://www.chsi.com.cn/xlcx/bg.do?vcode=APEVUKH9C8SSGS5D&srcid=bgcx"
    });

    expect(harness.browser.newContext).toHaveBeenCalledWith(expect.objectContaining({
      javaScriptEnabled: true,
      locale: "zh-CN",
      timezoneId: "Asia/Shanghai",
      userAgent: expect.stringContaining("Chrome/148.0.7778.96")
    }));
    expect(harness.page.goto).toHaveBeenCalledWith(
      "https://www.chsi.com.cn/xlcx/bg.do?vcode=APEVUKH9C8SSGS5D&srcid=bgcx",
      { waitUntil: "commit", timeout: limits.defaultFetchedHtmlTimeoutMs }
    );
    expect(harness.page.route).not.toHaveBeenCalled();
    expect(harness.context.addInitScript).not.toHaveBeenCalled();
    expect(harness.context.newCDPSession).toHaveBeenCalled();
    expect(harness.cdp.send).toHaveBeenCalledWith("Emulation.setUserAgentOverride", expect.objectContaining({
      platform: "Win32",
      userAgentMetadata: expect.objectContaining({
        brands: expect.arrayContaining([
          expect.objectContaining({ brand: "Google Chrome", version: "148" })
        ])
      })
    }));
    expect(harness.page.waitForLoadState).toHaveBeenCalled();
    expect(result.html).toContain("郭金龙");
    expect(result.finalUrl).toContain("chsi.com.cn");
    expect(harness.pool.release).toHaveBeenCalledTimes(1);
    expect(harness.context.close).toHaveBeenCalledTimes(1);
  });

  it("does not intercept url-html subresources", async () => {
    const harness = createHarness();
    await harness.service.renderUrlHtml({
      urlB64: Buffer.from("https://www.chsi.com.cn/xlcx/bg.do?vcode=APEVUKH9C8SSGS5D&srcid=bgcx", "utf8").toString("base64url")
    });
    expect(harness.page.route).not.toHaveBeenCalled();
  });

  it("keeps screenshot url rendering javascript-off and locked to one navigation", async () => {
    const harness = createHarness();
    harness.page.evaluate.mockResolvedValue(200);
    harness.page.screenshot.mockResolvedValue(Buffer.from("png"));
    await harness.service.renderUrl({ url: "https://example.com/card" });

    expect(harness.browser.newContext).toHaveBeenCalledWith(expect.objectContaining({
      javaScriptEnabled: false
    }));
    expect(harness.page.goto).toHaveBeenCalledWith("https://example.com/card", {
      waitUntil: "domcontentloaded",
      timeout: limits.defaultTimeoutMs
    });
  });

  it("captures pages taller than the viewport through CDP", async () => {
    const harness = createHarness();
    harness.page.evaluate.mockResolvedValue(2_400);
    harness.page.screenshot.mockRejectedValue(new Error("clip beyond viewport"));
    const cdpSend = vi.fn(async (method: string, params?: { clip?: { height?: number }; captureBeyondViewport?: boolean }) => {
      if (method === "Page.captureScreenshot") {
        expect(params?.captureBeyondViewport).toBe(true);
        expect(params?.clip?.height).toBe(2_400);
        return { data: PNG.sync.write(new PNG({ width: 640, height: 2_400 })).toString("base64") };
      }
      return {};
    });
    harness.page.context.mockReturnValue({
      newCDPSession: vi.fn(async () => ({ send: cdpSend }))
    });

    const result = await harness.service.renderHtml({
      html: "<p>tall</p>",
      width: 640,
      maxHeight: 4_000
    });

    expect(result.height).toBe(2_400);
    expect(result.width).toBe(640);
    expect(cdpSend).toHaveBeenCalledWith("Page.captureScreenshot", expect.objectContaining({
      captureBeyondViewport: true
    }));
  });

  it("stitches png tiles when CDP capture is unavailable", async () => {
    const harness = createHarness();
    harness.page.evaluate.mockResolvedValue(1_500);
    harness.page.context.mockReturnValue({
      newCDPSession: vi.fn(async () => ({
        send: vi.fn(async () => {
          throw new Error("cdp unavailable");
        })
      }))
    });
    harness.page.screenshot.mockImplementation(async ({ clip }: { clip: { y: number; height: number; width: number } }) => (
      PNG.sync.write(new PNG({ width: clip.width, height: clip.height }))
    ));

    const result = await harness.service.renderHtml({
      html: "<p>tall</p>",
      width: 400,
      maxHeight: 4_000
    });

    expect(result.height).toBe(1_500);
    expect(harness.page.screenshot).toHaveBeenCalledTimes(2);
    expect(harness.page.screenshot).toHaveBeenNthCalledWith(1, expect.objectContaining({
      clip: { x: 0, y: 0, width: 400, height: 1_080 }
    }));
    expect(harness.page.screenshot).toHaveBeenNthCalledWith(2, expect.objectContaining({
      clip: { x: 0, y: 1_080, width: 400, height: 420 }
    }));
  });
});
