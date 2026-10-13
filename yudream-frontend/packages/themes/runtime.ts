export function normalizeHexColor(value?: string) {
  if (!value) {
    return ''
  }
  const color = value.trim()
  if (/^#[0-9a-f]{6}$/i.test(color)) {
    return color.toLowerCase()
  }
  if (/^#[0-9a-f]{3}$/i.test(color)) {
    return `#${color[1]}${color[1]}${color[2]}${color[2]}${color[3]}${color[3]}`.toLowerCase()
  }
  return ''
}

export function hexToOklch(hex?: string) {
  const color = normalizeHexColor(hex)
  if (!color) {
    return null
  }
  const r = Number.parseInt(color.slice(1, 3), 16) / 255
  const g = Number.parseInt(color.slice(3, 5), 16) / 255
  const b = Number.parseInt(color.slice(5, 7), 16) / 255
  const toLinear = (value: number) => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  const lr = toLinear(r)
  const lg = toLinear(g)
  const lb = toLinear(b)
  const l = 0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb
  const m = 0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb
  const s = 0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb
  const lRoot = Math.cbrt(l)
  const mRoot = Math.cbrt(m)
  const sRoot = Math.cbrt(s)
  const okL = 0.2104542553 * lRoot + 0.793617785 * mRoot - 0.0040720468 * sRoot
  const okA = 1.9779984951 * lRoot - 2.428592205 * mRoot + 0.4505937099 * sRoot
  const okB = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.808675766 * sRoot
  const chroma = Math.sqrt(okA ** 2 + okB ** 2)
  const hue = (Math.atan2(okB, okA) * 180 / Math.PI + 360) % 360
  return {
    primary: `${okL.toFixed(3)} ${chroma.toFixed(3)} ${hue.toFixed(3)}`,
    foreground: okL > 0.65 ? '0.145 0 0' : '0.985 0 0',
  }
}

function hexToRgb(hex?: string) {
  const color = normalizeHexColor(hex)
  if (!color) {
    return null
  }
  return [
    Number.parseInt(color.slice(1, 3), 16),
    Number.parseInt(color.slice(3, 5), 16),
    Number.parseInt(color.slice(5, 7), 16),
  ] as const
}

function mixRgb(source: readonly number[], target: readonly number[], amount: number) {
  return source.map((channel, index) => Math.round(channel + (target[index] - channel) * amount)).join(' ')
}

function applyPrimaryScale(value?: string) {
  const primary = hexToRgb(value || '')
  if (!primary) {
    return
  }
  const white = [255, 255, 255]
  const black = [0, 0, 0]
  const lightness = [0.92, 0.78, 0.62, 0.45, 0.24]
  const darkness = [0.12, 0.24, 0.38, 0.52]
  lightness.forEach((amount, index) => {
    document.documentElement.style.setProperty(`--primary-${index + 1}`, mixRgb(primary, white, amount))
  })
  document.documentElement.style.setProperty('--primary-6', primary.join(' '))
  darkness.forEach((amount, index) => {
    document.documentElement.style.setProperty(`--primary-${index + 7}`, mixRgb(primary, black, amount))
  })
}

export function applyPrimaryColor(value?: string) {
  const color = hexToOklch(value || '')
  if (!color) {
    return
  }
  applyPrimaryScale(value)
  document.documentElement.style.setProperty('--primary', color.primary)
  document.documentElement.style.setProperty('--ring', color.primary)
  document.documentElement.style.setProperty('--primary-foreground', color.foreground)
}
