import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export type ThemeColorScheme = 'light' | 'dark' | ''

export interface PrimaryColorPreset {
  name: string
  label: string
  value: string
}

interface ThemeSetting {
  colorScheme: ThemeColorScheme
  primaryColor: string
  radius: number
  colorAmblyopia: boolean
}

const STORAGE_KEY = 'yd-docs:theme:v2'
const defaultSetting: ThemeSetting = {
  colorScheme: 'light',
  primaryColor: '#18181b',
  radius: 0.5,
  colorAmblyopia: false,
}

export const themeColorSchemeOptions: { label: string, value: ThemeColorScheme }[] = [
  { label: '亮色', value: 'light' },
  { label: '暗色', value: 'dark' },
  { label: '跟随系统', value: '' },
]

export const primaryColorPresets: PrimaryColorPreset[] = [
  { name: 'framework-default', label: '框架默认', value: '#18181b' },
  { name: 'arcoblue', label: 'Arco 蓝', value: '#165DFF' },
  { name: 'green', label: '绿', value: '#00B42A' },
  { name: 'purple', label: '紫', value: '#722ED1' },
  { name: 'orange', label: '橙', value: '#FF7D00' },
  { name: 'red', label: '红', value: '#F53F3F' },
]

const colorScheme = ref<ThemeColorScheme>(defaultSetting.colorScheme)
const primaryColor = ref(defaultSetting.primaryColor)
const radius = ref(defaultSetting.radius)
const colorAmblyopia = ref(defaultSetting.colorAmblyopia)
const systemIsDark = ref(false)
let mediaQuery: MediaQueryList | undefined
let mediaQueryListener: ((event: MediaQueryListEvent) => void) | undefined

const resolvedScheme = computed<'light' | 'dark'>(() => {
  if (colorScheme.value === '') {
    return systemIsDark.value ? 'dark' : 'light'
  }
  return colorScheme.value
})

function normalizeHex(value: string): string | null {
  const color = value.trim()
  if (/^#[0-9a-f]{6}$/i.test(color)) {
    return color.toUpperCase()
  }
  if (/^#[0-9a-f]{3}$/i.test(color)) {
    return `#${color[1]}${color[1]}${color[2]}${color[2]}${color[3]}${color[3]}`.toUpperCase()
  }
  return null
}

function clampRadius(value: number): number {
  return Number.isFinite(value) ? Math.min(1, Math.max(0, value)) : defaultSetting.radius
}

function loadSetting(): ThemeSetting {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return { ...defaultSetting }
    }
    const parsed = JSON.parse(raw) as Partial<ThemeSetting>
    const color = typeof parsed.primaryColor === 'string' ? normalizeHex(parsed.primaryColor) : null
    return {
      colorScheme: ['light', 'dark', ''].includes(parsed.colorScheme as string) ? parsed.colorScheme as ThemeColorScheme : defaultSetting.colorScheme,
      primaryColor: color ?? defaultSetting.primaryColor,
      radius: clampRadius(Number(parsed.radius)),
      colorAmblyopia: typeof parsed.colorAmblyopia === 'boolean' ? parsed.colorAmblyopia : defaultSetting.colorAmblyopia,
    }
  }
  catch {
    return { ...defaultSetting }
  }
}

function hexToOklch(hex: string) {
  const color = normalizeHex(hex)
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

function applyPrimaryScale(color: string) {
  const normalized = normalizeHex(color)
  if (!normalized) {
    return
  }
  const primary = [
    Number.parseInt(normalized.slice(1, 3), 16),
    Number.parseInt(normalized.slice(3, 5), 16),
    Number.parseInt(normalized.slice(5, 7), 16),
  ]
  const mix = (target: readonly number[], amount: number) => primary.map((channel, index) => Math.round(channel + (target[index] - channel) * amount)).join(' ')
  const root = document.documentElement
  ;[0.92, 0.78, 0.62, 0.45, 0.24].forEach((amount, index) => root.style.setProperty(`--primary-${index + 1}`, mix([255, 255, 255], amount)))
  root.style.setProperty('--primary-6', primary.join(' '))
  ;[0.12, 0.24, 0.38, 0.52].forEach((amount, index) => root.style.setProperty(`--primary-${index + 7}`, mix([0, 0, 0], amount)))
}

function applyTheme() {
  const root = document.documentElement
  const isDark = resolvedScheme.value === 'dark'
  root.classList.toggle('dark', isDark)
  root.setAttribute('color-scheme', isDark ? 'dark' : 'light')
  document.body.toggleAttribute('arco-theme', isDark)
  if (isDark) {
    document.body.setAttribute('arco-theme', 'dark')
  }

  const color = hexToOklch(primaryColor.value)
  if (color) {
    root.style.setProperty('--primary', color.primary)
    root.style.setProperty('--ring', color.primary)
    root.style.setProperty('--primary-foreground', color.foreground)
  }
  applyPrimaryScale(primaryColor.value)

  root.style.setProperty('--radius', `${radius.value}rem`)
  document.body.style.setProperty('--border-radius-small', `${(radius.value * 4).toFixed(1)}px`)
  document.body.style.setProperty('--border-radius-medium', `${(radius.value * 8).toFixed(1)}px`)
  document.body.style.setProperty('--border-radius-large', `${(radius.value * 16).toFixed(1)}px`)
  if (colorAmblyopia.value) {
    root.style.setProperty('filter', 'invert(80%)')
  }
  else {
    root.style.removeProperty('filter')
  }
}

function persistSetting() {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
    colorScheme: colorScheme.value,
    primaryColor: primaryColor.value,
    radius: radius.value,
    colorAmblyopia: colorAmblyopia.value,
  }))
}

export function useYdDocsTheme(options: { initialize?: boolean } = {}) {
  let stopWatch: (() => void) | undefined

  if (options.initialize !== false) {
    onMounted(() => {
      const setting = loadSetting()
      colorScheme.value = setting.colorScheme
      primaryColor.value = setting.primaryColor
      radius.value = setting.radius
      colorAmblyopia.value = setting.colorAmblyopia
      mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
      systemIsDark.value = mediaQuery.matches
      mediaQueryListener = event => systemIsDark.value = event.matches
      mediaQuery.addEventListener('change', mediaQueryListener)
      stopWatch = watch([colorScheme, primaryColor, radius, colorAmblyopia, systemIsDark], () => {
        applyTheme()
        persistSetting()
      }, { immediate: true })
    })

    onBeforeUnmount(() => {
      stopWatch?.()
      if (mediaQuery && mediaQueryListener) {
        mediaQuery.removeEventListener('change', mediaQueryListener)
      }
    })
  }

  function setPrimaryColor(color: string): boolean {
    const normalized = normalizeHex(color)
    if (!normalized) {
      return false
    }
    primaryColor.value = normalized
    return true
  }

  function reset() {
    colorScheme.value = defaultSetting.colorScheme
    primaryColor.value = defaultSetting.primaryColor
    radius.value = defaultSetting.radius
    colorAmblyopia.value = defaultSetting.colorAmblyopia
  }

  return { colorScheme, resolvedScheme, primaryColor, radius, colorAmblyopia, setPrimaryColor, reset }
}
