import { lookup } from "node:dns/promises";
import net from "node:net";
import sanitizeHtml from "sanitize-html";
import { limits } from "./config.js";

const privateIpv4Ranges: ReadonlyArray<readonly [number, number]> = [
  [0x00000000, 0x00ffffff], [0x0a000000, 0x0affffff], [0x64400000, 0x647fffff],
  [0x7f000000, 0x7fffffff], [0xa9fe0000, 0xa9feffff], [0xac100000, 0xac1fffff],
  [0xc0a80000, 0xc0a8ffff], [0xe0000000, 0xffffffff]
];

export class RenderInputError extends Error {}

export function assertTextLimit(value: string, maximum: number, name: string): void {
  if (Buffer.byteLength(value, "utf8") > maximum) {
    throw new RenderInputError(`${name} exceeds the ${maximum} byte limit`);
  }
}

export function sanitizeMarkup(html: string): string {
  assertTextLimit(html, limits.maxHtmlBytes, "html");
  return sanitizeHtml(html, {
    allowedTags: ["a", "abbr", "b", "blockquote", "br", "code", "del", "div", "em", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "i", "img", "li", "ol", "p", "pre", "span", "strong", "sub", "sup", "table", "tbody", "td", "th", "thead", "tr", "u", "ul"],
    allowedAttributes: { a: ["href", "title"], img: ["alt", "height", "src", "title", "width"], "*": ["class"] },
    allowedSchemes: ["https"],
    allowedSchemesByTag: { img: ["https"] },
    allowProtocolRelative: false,
    disallowedTagsMode: "discard",
    allowedStyles: {}
  });
}

function ipv4ToNumber(address: string): number {
  return address.split(".").reduce((result, part) => (result << 8) + Number(part), 0) >>> 0;
}

export function isPrivateAddress(address: string): boolean {
  const family = net.isIP(address);
  if (family === 4) {
    const numeric = ipv4ToNumber(address);
    return privateIpv4Ranges.some(([start, end]) => numeric >= start && numeric <= end);
  }
  if (family === 6) {
    const value = address.toLowerCase();
    return value === "::1" || value === "::" || value.startsWith("fc") || value.startsWith("fd") || value.startsWith("fe80:") || value.startsWith("::ffff:127.") || value.startsWith("::ffff:10.") || value.startsWith("::ffff:192.168.");
  }
  return true;
}

export async function assertSafeExternalUrl(value: string): Promise<URL> {
  let url: URL;
  try { url = new URL(value); } catch { throw new RenderInputError("url must be absolute"); }
  if (url.protocol !== "https:" && url.protocol !== "http:") throw new RenderInputError("url scheme is not allowed");
  if (url.username || url.password || url.hostname === "localhost" || url.hostname.endsWith(".localhost")) throw new RenderInputError("url host is not allowed");
  if (net.isIP(url.hostname)) {
    if (isPrivateAddress(url.hostname)) throw new RenderInputError("url resolves to a private address");
    return url;
  }
  let addresses: { address: string }[];
  try { addresses = await lookup(url.hostname, { all: true, verbatim: true }); } catch { throw new RenderInputError("url host cannot be resolved"); }
  if (!addresses.length || addresses.some(({ address }) => isPrivateAddress(address))) throw new RenderInputError("url resolves to a private address");
  return url;
}

export function assertRenderOptions(input: { width?: number; maxHeight?: number; timeoutMs?: number; deviceScaleFactor?: number; css?: string }): void {
  const { width = 900, maxHeight = 4_000, timeoutMs = limits.defaultTimeoutMs, deviceScaleFactor = 1, css = "" } = input;
  if (!Number.isInteger(width) || width < limits.minWidth || width > limits.maxWidth) throw new RenderInputError("width is outside the allowed range");
  if (!Number.isInteger(maxHeight) || maxHeight < limits.minHeight || maxHeight > limits.maxHeight) throw new RenderInputError("maxHeight is outside the allowed range");
  if (!Number.isInteger(timeoutMs) || timeoutMs < limits.minTimeoutMs || timeoutMs > limits.maxTimeoutMs) throw new RenderInputError("timeoutMs is outside the allowed range");
  if (!Number.isFinite(deviceScaleFactor) || deviceScaleFactor < 1 || deviceScaleFactor > limits.maxScale) throw new RenderInputError("deviceScaleFactor is outside the allowed range");
  assertTextLimit(css, limits.maxCssBytes, "css");
  if (/@import\b|url\s*\(|<\s*\/\s*style/i.test(css)) throw new RenderInputError("css contains a forbidden construct");
}
