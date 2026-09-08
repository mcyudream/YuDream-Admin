export const limits = {
  maxHtmlBytes: 2 * 1024 * 1024,
  maxMarkdownBytes: 2 * 1024 * 1024,
  maxFetchedHtmlBytes: 2 * 1024 * 1024,
  maxCssBytes: 50 * 1024,
  minWidth: 320,
  maxWidth: 1920,
  minHeight: 200,
  maxHeight: 10_000,
  defaultMaxHeight: 4_000,
  viewportCap: 1_080,
  maxScale: 2,
  minTimeoutMs: 1_000,
  maxTimeoutMs: 60_000,
  defaultTimeoutMs: 10_000,
  defaultFetchedHtmlTimeoutMs: 30_000,
  maxQueue: Number.parseInt(process.env.RENDER_MAX_QUEUE ?? "32", 10),
  maxConcurrent: Number.parseInt(process.env.RENDER_MAX_CONCURRENT ?? "2", 10)
} as const;

export const serverConfig = {
  host: process.env.RENDER_HOST ?? "127.0.0.1",
  port: Number.parseInt(process.env.RENDER_PORT ?? "3000", 10)
} as const;
