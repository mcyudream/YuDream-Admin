import Fastify, { type FastifyInstance } from "fastify";
import sensible from "@fastify/sensible";
import { BrowserPool, RenderQueueFullError } from "./browser-pool.js";
import { RenderService, type RenderRequest } from "./render-service.js";
import { RenderInputError } from "./security.js";

export function buildServer(pool = new BrowserPool()): FastifyInstance {
  const app = Fastify({ logger: true, bodyLimit: 300 * 1024 });
  const service = new RenderService(pool);
  void app.register(sensible);

  app.setErrorHandler((error, _request, reply) => {
    if (error instanceof RenderInputError || error.message === "html is required" || error.message === "markdown is required" || error.message === "url is required") return reply.code(400).send({ message: error.message });
    if (error instanceof RenderQueueFullError) return reply.code(429).send({ message: error.message });
    if (error.name === "TimeoutError") return reply.code(504).send({ message: "render timed out" });
    app.log.error(error);
    return reply.code(500).send({ message: "render failed" });
  });

  app.get("/health", async () => ({ status: (await pool.healthy()) ? "ok" : "unavailable" }));
  app.post<{ Body: RenderRequest }>("/v1/render/html", async (request) => service.renderHtml(request.body));
  app.post<{ Body: RenderRequest }>("/v1/render/markdown", async (request) => service.renderMarkdown(request.body));
  app.post<{ Body: RenderRequest }>("/v1/render/url", async (request) => service.renderUrl(request.body));

  app.addHook("onClose", async () => pool.close());
  return app;
}
