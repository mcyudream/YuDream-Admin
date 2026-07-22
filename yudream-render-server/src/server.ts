import Fastify, { type FastifyInstance } from "fastify";
import sensible from "@fastify/sensible";
import { BrowserPool, RenderQueueFullError } from "./browser-pool.js";
import { RenderService, type RenderRequest } from "./render-service.js";
import { RenderInputError } from "./security.js";

export function buildServer(pool = new BrowserPool()): FastifyInstance {
  const app = Fastify({ logger: true, bodyLimit: 300 * 1024 });
  const service = new RenderService(pool);
  void app.register(sensible);

  app.setErrorHandler((error, request, reply) => {
    const message = error instanceof Error ? error.message : "render failed";
    const name = error instanceof Error ? error.name : "";
    const statusCode = error instanceof RenderInputError || message === "html is required" || message === "markdown is required" || message === "url is required"
      ? 400
      : error instanceof RenderQueueFullError
        ? 429
        : name === "TimeoutError"
          ? 504
          : 500;

    request.log.error({ err: error, method: request.method, route: request.routeOptions.url, statusCode }, "render request failed");

    if (statusCode === 400) return reply.code(statusCode).send({ message });
    if (statusCode === 429) return reply.code(statusCode).send({ message });
    if (statusCode === 504) return reply.code(statusCode).send({ message: "render timed out" });
    return reply.code(statusCode).send({ message: "render failed" });
  });

  app.get("/health", async () => ({ ok: await pool.healthy() }));
  app.post<{ Body: RenderRequest }>("/v1/render/html", async (request) => service.renderHtml(request.body));
  app.post<{ Body: RenderRequest }>("/v1/render/markdown", async (request) => service.renderMarkdown(request.body));
  app.post<{ Body: RenderRequest }>("/v1/render/url", async (request) => service.renderUrl(request.body));

  app.addHook("onClose", async () => pool.close());
  return app;
}
