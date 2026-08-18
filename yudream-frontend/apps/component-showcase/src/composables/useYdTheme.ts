import { generate, getRgbStr } from '@arco-design/color'
import { computed, ref, watch } from 'vue'

/**
 * 示例 app 主题管理，与主应用 theme 机制完全对齐：
 * - 明暗：documentElement 的 `.dark` class + `color-scheme`（shadcn），
 *   body 的 `arco-theme="dark"`（Arco），对应主应用 `store/modules/app/settings.ts` 与 `ui/provider/index.vue`。
 * - 主色：hex 转 oklch 写入 `--primary` / `--ring` / `--primary-foreground`（shadcn），
 *   同时用 `@arco-design/color` 生成 10 级 `--primary-1..10`（Arco RGB 三元组），
 *   使 Arco 与 Fa 两套组件在示例中跟随同一主色。
 * - 圆角：0..1 系数写入 shadcn `--radius: {n}rem`，并同步 Arco `--border-radius-*`。
 * - 色弱：对应主应用 `theme.colorAmblyopia`，写入 `filter: invert(80%)`。
 */

export type ThemeColorScheme = 'light' | 'dark' | ''

export interface PrimaryColorPreset {
  name: string
  label: string
  value: string
}

export const themeColorSchemeOptions: { label: string, value: ThemeColorScheme }[] = [
  { label: '亮色', value: 'light' },
  { label: '暗色', value: 'dark' },
  { label: '跟随系统', value: '' },
]

export const primaryColorPresets: PrimaryColorPreset[] = [
  { name: 'arcoblue', label: 'Arco 蓝', value: '#165DFF' },
  { name: 'green', label: '绿', value: '#00B42A' },
  { name: 'purple', label: '紫', value: '#722ED1' },
  { name: 'orange', label: '橙', value: '#FF7D00' },
  { name: 'red', label: '红', value: '#F53F3F' },
]

const STORAGE_KEY = 'component-showcase:theme'

interface ThemeSetting {
  colorScheme: ThemeColorScheme
  primaryColor: string
  radius: number
  colorAmblyopia: boolean
}

const defaultSetting: ThemeSetting = {
  colorScheme: 'light',
  primaryColor: '#165DFF',
  radius: 0.5,
  colorAmblyopia: false,
}

function normalizeHex(color: string): string | null {
  const value = color.trim()
  if (/^#[0-9a-fA-F]{6}$/.test(value)) {
    return value.toUpperCase()
  }
  if (/^#[0-9a-fA-F]{3}$/.test(value)) {
    return `#${value[1]}${value[1]}${value[2]}${value[2]}${value[3]}${value[3]}`.toUpperCase()
  }
  return null
}

function clampRadius(value: number): number {
  if (!Number.isFinite(value)) {
    return defaultSetting.radius
  }
  return Math.min(1, Math.max(0, value))
}

function loadSetting(): ThemeSetting {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return { ...defaultSetting }
    }
    const parsed = JSON.parse(raw) as Partial<ThemeSetting>
    const primaryColor = typeof parsed.primaryColor === 'string' ? normalizeHex(parsed.primaryColor) : null
    return {
      colorScheme: ['light', 'dark', ''].includes(parsed.colorScheme as string)
        ? parsed.colorScheme as ThemeColorScheme
        : defaultSetting.colorScheme,
      primaryColor: primaryColor ?? defaultSetting.primaryColor,
      radius: clampRadius(Number(parsed.radius)),
      colorAmblyopia: typeof parsed.colorAmblyopia === 'boolean' ? parsed.colorAmblyopia : defaultSetting.colorAmblyopia,
    }
  }
  catch {
    return { ...defaultSetting }
  }
}

const setting = loadSetting()

const colorScheme = ref<ThemeColorScheme>(setting.colorScheme)
const primaryColor = ref<string>(setting.primaryColor)
const radius = ref<number>(setting.radius)
const colorAmblyopia = ref<boolean>(setting.colorAmblyopia)

const prefersDark = window.matchMedia('(prefers-color-scheme: dark)')
const systemIsDark = ref(prefersDark.matches)
prefersDark.addEventListener('change', (event) => {
  systemIsDark.value = event.matches
})

const resolvedScheme = computed<'light' | 'dark'>(() => {
  if (colorScheme.value === '') {
    return systemIsDark.value ? 'dark' : 'light'
  }
  return colorScheme.value
})

/** 与主应用 settings.ts 相同的 hex → oklch 转换 */
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

function applyColorScheme() {
  const isDark = resolvedScheme.value === 'dark'
  document.documentElement.classList.toggle('dark', isDark)
  document.documentElement.setAttribute('color-scheme', isDark ? 'dark' : 'light')

  // Arco 明暗（与主应用 ui/provider/index.vue 一致）
  if (isDark) {
    document.body.setAttribute('arco-theme', 'dark')
  }
  else {
    document.body.removeAttribute('arco-theme')
  }
}

function applyPrimaryColor() {
  const oklch = hexToOklch(primaryColor.value)
  if (oklch) {
    document.documentElement.style.setProperty('--primary', oklch.primary)
    document.documentElement.style.setProperty('--ring', oklch.primary)
    document.documentElement.style.setProperty('--primary-foreground', oklch.foreground)
  }

  // Arco 10 级色板（暗色模式使用暗色色板）
  const palette = generate(primaryColor.value, { list: true, dark: resolvedScheme.value === 'dark' })
  palette.forEach((hex, index) => {
    document.body.style.setProperty(`--primary-${index + 1}`, getRgbStr(hex))
  })
}

function applyRadius() {
  document.documentElement.style.setProperty('--radius', `${radius.value}rem`)

  // 与 Arco 默认 2/4/8px 对齐（0.5 时正好是默认值）
  const small = (radius.value * 4).toFixed(1)
  const medium = (radius.value * 8).toFixed(1)
  const large = (radius.value * 16).toFixed(1)
  document.body.style.setProperty('--border-radius-small', `${small}px`)
  document.body.style.setProperty('--border-radius-medium', `${medium}px`)
  document.body.style.setProperty('--border-radius-large', `${large}px`)
}

function applyColorAmblyopia() {
  if (colorAmblyopia.value) {
    document.documentElement.style.setProperty('filter', 'invert(80%)')
  }
  else {
    document.documentElement.style.removeProperty('filter')
  }
}

function applyTheme() {
  applyColorScheme()
  applyPrimaryColor()
  applyRadius()
  applyColorAmblyopia()
}

function persistSetting() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    colorScheme: colorScheme.value,
    primaryColor: primaryColor.value,
    radius: radius.value,
    colorAmblyopia: colorAmblyopia.value,
  }))
}

watch([colorScheme, primaryColor, radius, colorAmblyopia, systemIsDark], () => {
  applyTheme()
  persistSetting()
}, { immediate: true })

export function useYdTheme() {
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

  return {
    colorScheme,
    resolvedScheme,
    primaryColor,
    radius,
    colorAmblyopia,
    setPrimaryColor,
    reset,
  }
}
