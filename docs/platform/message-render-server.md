# Message Render Server

`yudream-render-server` is an independent Fastify service that uses Playwright Chromium in headless mode. It exposes `/health` and four rendering endpoints:

- `POST /v1/render/html`
- `POST /v1/render/markdown`
- `POST /v1/render/url`
- `POST /v1/render/url-html`

Image endpoints return `{ contentType, data, width, height }`, where `data` is Base64 only on the internal service boundary. The Java API returns image bytes to callers.

`POST /v1/render/url-html` returns `{ html, finalUrl }` after a single isolated navigation. URL targets may be supplied as `url` or as escaped Base64 in `urlB64` (body or query). Query parameters take precedence. The host SPI encodes the raw URL as URL-safe Base64 before calling this endpoint.

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
- HTML and Markdown screenshot rendering still disable JavaScript and block every subresource.
- `POST /v1/render/url` screenshot rendering still permits only a single validated public HTTP(S) navigation. Redirects and all subresources are blocked.
- `POST /v1/render/url-html` uses a browser-like context: JavaScript is enabled, launches full Chromium (not `chrome-headless-shell`), and uses CDP `Emulation.setUserAgentOverride` so `sec-ch-ua` does not advertise `HeadlessChrome`. It does **not** patch `navigator.webdriver` / `window.chrome` and does **not** install Playwright `page.route` interception — Aliyun WAF treats both as a failed challenge and returns HTTP 400 with an empty document. Private hosts are still rejected before navigation. Default fetch timeout is 30s, hard cap 60s.
- Fetched HTML is capped at 2 MiB.
- Screenshot height may exceed the 1080px layout viewport. The service first asks Chromium for `captureBeyondViewport`; if that fails it stitches PNG tiles. The documented `maxHeight` cap remains 10_000px (default 4_000).
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
