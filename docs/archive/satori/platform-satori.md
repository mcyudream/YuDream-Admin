# Satori Platform

Enable the project gates before configuring connections:

```env
PLATFORM_SATORI_ENABLED=true
PLATFORM_MESSAGE_RENDER_ENABLED=true
YUDREAM_SATORI_CREDENTIAL_KEY=<base64 AES key>
```

Create Satori connections from **Platform > Satori Message Platform**. Tokens are encrypted at rest and never returned through the management API. Use the connection test action to request `meta`; it does not open a long-running event socket.

Each connection supports Satori v1 HTTP APIs, WebSocket events and passive WebHook events. WebHook endpoints accept only `EVENT` and `META`, require `Authorization: Bearer <connection-token>`, and use the `Satori-Opcode` header.

Messages may be sent as plain text, Satori elements, Markdown, HTML or media. When a target adapter does not support rich text, Markdown and HTML are rendered to an image by the internal render server, uploaded through Satori and sent as an `img` element. The original `referrer` is kept for replies.

Plugins receive the same capabilities only through `yudream-plugin-spi`: `framework().messaging()`, `framework().satoriRaw()`, `framework().render()` and `context.interactions()`. Plugin event, command and button handlers are removed automatically when a plugin is disabled or unloaded.
