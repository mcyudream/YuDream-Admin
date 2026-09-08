import Fastify, { type FastifyInstance, type FastifyRequest } from "fastify";
import sensible from "@fastify/sensible";
import { BrowserPool, RenderQueueFullError } from "./browser-pool.js";
import { RenderService, type RenderRequest } from "./render-service.js";
import { RenderInputError } from "./security.js";

type UrlQuery = { url?: string; urlB64?: string };

function mergeUrlParams(request: FastifyRequest<{ Body: RenderRequest; Querystring: UrlQuery }>): RenderRequest {
  const body = request.body ?? {};
  const query = request.query ?? {};
  return {
    ...body,
    url: typeof query.url === "string" && query.url.trim() ? query.url : body.url,
    urlB64: typeof query.urlB64 === "string" && query.urlB64.trim() ? query.urlB64 : body.urlB64
  };
}

export function buildServer(pool = new BrowserPool()): FastifyInstance {
  const app = Fastify({ logger: true, bodyLimit: 3 * 1024 * 1024 });
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
  app.post<{ Body: RenderRequest; Querystring: UrlQuery }>("/v1/render/url", async (request) => service.renderUrl(mergeUrlParams(request)));
  app.post<{ Body: RenderRequest; Querystring: UrlQuery }>("/v1/render/url-html", async (request) => service.renderUrlHtml(mergeUrlParams(request)));

  app.addHook("onClose", async () => pool.close());
  return app;
}
