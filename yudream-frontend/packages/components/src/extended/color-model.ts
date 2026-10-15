/**
 * Pure color math for YdColorPicker: parsing, conversion and formatting.
 * Implements real sRGB <-> HSL <-> OKLCH conversion per CSS Color 4
 * (OKLab matrices from Björn Ottosson's reference implementation).
 * Alpha is carried end to end as 0..1.
 */

export interface YdRgbaColor {
  /** 0..255 */
  r: number
  /** 0..255 */
  g: number
  /** 0..255 */
  b: number
  /** 0..1 */
  a: number
}

export interface YdHslaColor {
  /** 0..360 */
  h: number
  /** 0..100 */
  s: number
  /** 0..100 */
  l: number
  /** 0..1 */
  a: number
}

export interface YdOklchColor {
  /** 0..1 */
  l: number
  /** 0..0.4 typical */
  c: number
  /** 0..360 */
  h: number
  /** 0..1 */
  a: number
}

export type YdColorFormat = 'hex' | 'rgb' | 'hsl' | 'oklch'

export const YD_COLOR_FORMATS: YdColorFormat[] = ['hex', 'rgb', 'hsl', 'oklch']

export function clampYd(value: number, min: number, max: number) {
  if (!Number.isFinite(value)) {
    return min
  }
  return Math.min(max, Math.max(min, value))
}

export function clampYdAlpha(value: number) {
  return clampYd(Number.isFinite(value) ? value : 1, 0, 1)
}

export function clampYdChannel(value: number) {
  return clampYd(Number.isFinite(value) ? Math.round(value) : 0, 0, 255)
}

function srgbToLinear(channel: number) {
  const normalized = channel / 255
  return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4
}

function linearToSrgb(channel: number) {
  const raw = channel <= 0.0031308 ? channel * 12.92 : 1.055 * channel ** (1 / 2.4) - 0.055
  return clampYdChannel(raw * 255)
}

export function rgbToHsl({ r, g, b, a = 1 }: YdRgbaColor): YdHslaColor {
  const rn = r / 255
  const gn = g / 255
  const bn = b / 255
  const max = Math.max(rn, gn, bn)
  const min = Math.min(rn, gn, bn)
  const delta = max - min
  const lightness = (max + min) / 2
  let hue = 0
  if (delta !== 0) {
    if (max === rn) {
      hue = ((gn - bn) / delta) % 6
    }
    else if (max === gn) {
      hue = (bn - rn) / delta + 2
    }
    else {
      hue = (rn - gn) / delta + 4
    }
    hue *= 60
    if (hue < 0) {
      hue += 360
    }
  }
  const saturation = delta === 0 ? 0 : delta / (1 - Math.abs(2 * lightness - 1))
  return { h: clampYd(hue, 0, 360), s: clampYd(saturation * 100, 0, 100), l: clampYd(lightness * 100, 0, 100), a: clampYdAlpha(a) }
}

export function hslToRgb({ h, s, l, a = 1 }: YdHslaColor): YdRgbaColor {
  const hue = ((Number.isFinite(h) ? h : 0) % 360 + 360) % 360
  const saturation = clampYd(s, 0, 100) / 100
  const lightness = clampYd(l, 0, 100) / 100
  const c = (1 - Math.abs(2 * lightness - 1)) * saturation
  const x = c * (1 - Math.abs((hue / 60) % 2 - 1))
  const m = lightness - c / 2
  const segment = Math.floor(hue / 60) % 6
  const rgbSegments: Array<[number, number, number]> = [
    [c, x, 0],
    [x, c, 0],
    [0, c, x],
    [0, x, c],
    [x, 0, c],
    [c, 0, x],
  ]
  const [rs, gs, bs] = rgbSegments[segment] ?? [0, 0, 0]
  return {
    r: clampYdChannel((rs + m) * 255),
    g: clampYdChannel((gs + m) * 255),
    b: clampYdChannel((bs + m) * 255),
    a: clampYdAlpha(a),
  }
}

export function rgbToOklch({ r, g, b, a = 1 }: YdRgbaColor): YdOklchColor {
  const lr = srgbToLinear(r)
  const lg = srgbToLinear(g)
  const lb = srgbToLinear(b)
  const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
  const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
  const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
  const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
  const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
  const chroma = Math.sqrt(A * A + B * B)
  const hue = chroma < 1e-8 ? 0 : (Math.atan2(B, A) * 180 / Math.PI + 360) % 360
  return { l: clampYd(L, 0, 1), c: Math.max(0, chroma), h: clampYd(hue, 0, 360), a: clampYdAlpha(a) }
}

export function oklchToRgb({ l, c, h, a = 1 }: YdOklchColor): YdRgbaColor {
  const L = clampYd(l, 0, 1)
  const C = Math.max(0, Number.isFinite(c) ? c : 0)
  const hueRad = ((Number.isFinite(h) ? h : 0) % 360) * Math.PI / 180
  const A = C * Math.cos(hueRad)
  const B = C * Math.sin(hueRad)
  const l_ = L + 0.3963377774 * A + 0.2158037573 * B
  const m_ = L - 0.1055613458 * A - 0.0638541728 * B
  const s_ = L - 0.0894841775 * A - 1.291485548 * B
  const ll = l_ ** 3
  const mm = m_ ** 3
  const ss = s_ ** 3
  return {
    r: linearToSrgb(4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss),
    g: linearToSrgb(-1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss),
    b: linearToSrgb(-0.0041960863 * ll - 0.7034186147 * mm + 1.707614701 * ss),
    a: clampYdAlpha(a),
  }
}

export function normalizeHexYdColor(hex: string) {
  return `#${hex.replace(/^#/, '').toLowerCase()}`
}

/** Hex digits only; accepts 3/4/6/8 digits with or without the leading '#'. */
export function parseHexYdColor(input: string): YdRgbaColor | null {
  const raw = input.trim().replace(/^#/, '')
  if (!/^[0-9a-f]+$/i.test(raw) || ![3, 4, 6, 8].includes(raw.length)) {
    return null
  }
  const expand = raw.length <= 4 ? [...raw].map(ch => ch + ch).join('') : raw
  return {
    r: Number.parseInt(expand.slice(0, 2), 16),
    g: Number.parseInt(expand.slice(2, 4), 16),
    b: Number.parseInt(expand.slice(4, 6), 16),
    a: expand.length === 8 ? Number.parseInt(expand.slice(6, 8), 16) / 255 : 1,
  }
}

function tokenizeYdColorBody(body: string) {
  const alphaPart = body.includes('/') ? body.slice(body.indexOf('/') + 1) : ''
  const main = body.includes('/') ? body.slice(0, body.indexOf('/')) : body
  const numbers = main.split(/[\s,]+/).filter(Boolean).map((token) => {
    const percent = token.endsWith('%')
    const value = Number.parseFloat(token)
    if (!Number.isFinite(value)) {
      return Number.NaN
    }
    return percent ? value / 100 : value
  })
  const alpha = alphaPart
    ? Number.parseFloat(alphaPart.trim().endsWith('%') ? String(Number.parseFloat(alphaPart) / 100) : alphaPart.trim())
    : 1
  return { numbers, alpha: clampYdAlpha(alpha) }
}

function parseFunctionYdColor(input: string): YdRgbaColor | null {
  const match = input.match(/^(rgb|rgba|hsl|hsla|oklch|oklab)\((.*)\)$/i)
  if (!match) {
    return null
  }
  const kind = match[1].toLowerCase()
  const { numbers, alpha } = tokenizeYdColorBody(match[2].replace(/\s*\/\s*/, '/'))
  if (numbers.some(value => !Number.isFinite(value))) {
    return null
  }
  if (kind === 'rgb' || kind === 'rgba') {
    // Channel values may be 0..255 numbers or 0..1 percentages (already scaled).
    const scale = numbers.some((value, index) => index < 3 && value <= 1 && `${value}`.length <= 4 && value !== 0) && numbers.slice(0, 3).every(value => value >= 0 && value <= 1) ? 255 : 1
    return { r: clampYdChannel((numbers[0] ?? 0) * scale), g: clampYdChannel((numbers[1] ?? 0) * scale), b: clampYdChannel((numbers[2] ?? 0) * scale), a: alpha }
  }
  if (kind === 'hsl' || kind === 'hsla') {
    return hslToRgb({ h: numbers[0] ?? 0, s: (numbers[1] ?? 0) <= 1 ? (numbers[1] ?? 0) * 100 : numbers[1] ?? 0, l: (numbers[2] ?? 0) <= 1 ? (numbers[2] ?? 0) * 100 : numbers[2] ?? 0, a: alpha })
  }
  if (kind === 'oklch') {
    const lightness = numbers[0] ?? 0
    return oklchToRgb({ l: lightness <= 1 ? lightness : lightness / 100, c: numbers[1] ?? 0, h: numbers[2] ?? 0, a: alpha })
  }
  return null
}

/** Parse any of: hex (3/4/6/8), rgb()/rgba(), hsl()/hsla(), oklch(), 'transparent'. */
export function parseYdColor(input: string | null | undefined): YdRgbaColor | null {
  if (!input) {
    return null
  }
  const text = input.trim().toLowerCase()
  if (!text) {
    return null
  }
  if (text === 'transparent') {
    return { r: 0, g: 0, b: 0, a: 0 }
  }
  if (text.startsWith('#') || /^[0-9a-f]{3,8}$/i.test(text)) {
    return parseHexYdColor(text)
  }
  return parseFunctionYdColor(text)
}

export function isValidYdColor(input: string | null | undefined) {
  return parseYdColor(input) !== null
}

function toHexPair(value: number) {
  return clampYdChannel(value).toString(16).padStart(2, '0')
}

/** Format a color for display/model output; alpha only appended when requested. */
export function formatYdColor(color: YdRgbaColor, format: YdColorFormat = 'hex', showAlpha = false) {
  const safe: YdRgbaColor = { r: clampYdChannel(color.r), g: clampYdChannel(color.g), b: clampYdChannel(color.b), a: clampYdAlpha(color.a) }
  if (format === 'hex') {
    const base = `#${toHexPair(safe.r)}${toHexPair(safe.g)}${toHexPair(safe.b)}`
    return showAlpha || safe.a < 1 ? `${base}${toHexPair(safe.a * 255)}` : base
  }
  if (format === 'rgb') {
    const base = `rgb(${clampYdChannel(safe.r)} ${clampYdChannel(safe.g)} ${clampYdChannel(safe.b)}`
    return showAlpha || safe.a < 1 ? `${base} / ${roundYd(safe.a, 3)})` : `${base})`
  }
  if (format === 'hsl') {
    const { h, s, l } = rgbToHsl(safe)
    const base = `hsl(${roundYd(h, 1)} ${roundYd(s, 1)}% ${roundYd(l, 1)}%`
    return showAlpha || safe.a < 1 ? `${base} / ${roundYd(safe.a, 3)})` : `${base})`
  }
  const oklch = rgbToOklch(safe)
  const base = `oklch(${roundYd(oklch.l, 3)} ${roundYd(oklch.c, 3)} ${roundYd(oklch.h, 1)}`
  return showAlpha || safe.a < 1 ? `${base} / ${roundYd(safe.a, 3)})` : `${base})`
}

export function roundYd(value: number, digits: number) {
  const factor = 10 ** digits
  return Math.round(value * factor) / factor
}

/** Parse arbitrary user text; falls back to `fallback` when invalid. */
export function coerceYdColor(input: string | null | undefined, fallback: YdRgbaColor) {
  return parseYdColor(input) ?? fallback
}

/** Pick the color's own hex for `<input type="color">` (which has no alpha). */
export function ydColorToHexInput(color: YdRgbaColor) {
  return formatYdColor(color, 'hex').slice(0, 7)
}

/** Component-wise blend used by the SV pad gradients and preview stripes. */
export function mixYdColor(base: YdRgbaColor, override: Partial<YdRgbaColor>) {
  return {
    r: clampYdChannel(override.r ?? base.r),
    g: clampYdChannel(override.g ?? base.g),
    b: clampYdChannel(override.b ?? base.b),
    a: clampYdAlpha(override.a ?? base.a),
  }
}
