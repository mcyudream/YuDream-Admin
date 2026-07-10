export const limits = {
  maxHtmlBytes: 200 * 1024,
  maxMarkdownBytes: 200 * 1024,
  maxCssBytes: 50 * 1024,
  minWidth: 320,
  maxWidth: 1920,
  minHeight: 200,
  maxHeight: 10_000,
  maxScale: 2,
  minTimeoutMs: 1_000,
  maxTimeoutMs: 30_000,
  defaultTimeoutMs: 10_000,
  maxQueue: Number.parseInt(process.env.RENDER_MAX_QUEUE ?? "32", 10),
  maxConcurrent: Number.parseInt(process.env.RENDER_MAX_CONCURRENT ?? "2", 10)
} as const;

export const serverConfig = {
  host: process.env.RENDER_HOST ?? "127.0.0.1",
  port: Number.parseInt(process.env.RENDER_PORT ?? "3000", 10)
} as const;
