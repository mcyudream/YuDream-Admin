# Message Render Server

`yudream-render-server` is an independent Fastify service that uses Playwright Chromium in headless mode. It exposes `/health` and three rendering endpoints:

- `POST /v1/render/html`
- `POST /v1/render/markdown`
- `POST /v1/render/url`

The service returns `{ contentType, data, width, height }`, where `data` is Base64 only on the internal service boundary. The Java API returns image bytes to callers.

## Docker Compose

Set a Base64 AES key for Satori credentials and optional render token in `.env`:

```env
YUDREAM_SATORI_CREDENTIAL_KEY=<base64-16-24-or-32-byte-key>
MESSAGE_RENDER_TOKEN=<internal-shared-token>
MESSAGE_RENDER_BASE_URL=http://render-server:3000
```

Run the complete stack with `docker compose up -d`. The render server is internal-only and is not published on a host port.

## Headless Security

- Chromium always runs headless in a fresh browser context.
- HTML and Markdown reject scripts, inline event handlers, CSS imports and external subresources.
- URL rendering permits only a single validated public HTTP(S) navigation. Redirects and all subresources are blocked.
- Docker runs read-only, without Linux capabilities, with a bounded tmpfs, CPU and memory limits.

## Standalone Server

On Linux or Windows Server, install Node.js 22 and Chromium dependencies, then run:

```bash
cd yudream-render-server
corepack enable
pnpm install
pnpm exec playwright install --with-deps chromium
pnpm build
RENDER_HOST=127.0.0.1 RENDER_PORT=3000 pnpm start
```

Run it behind an internal reverse proxy or systemd/Windows service. Do not expose it publicly without network policy and an authentication token.
