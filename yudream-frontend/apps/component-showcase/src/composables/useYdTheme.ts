import { settingsDefault } from '@fantastic-admin/settings'
import { applyPrimaryColor as applyFaPrimaryColor } from '@fantastic-admin/themes/runtime'
import { computed, ref, watch } from 'vue'

/**
 * 示例 app 主题管理，与主应用 theme 机制完全对齐：
 * - 明暗：documentElement 的 `.dark` class + `color-scheme`（shadcn），
 *   body 的 `arco-theme="dark"`（Arco），对应主应用 `store/modules/app/settings.ts` 与 `ui/provider/index.vue`。
 * - 主色：hex 转 oklch 写入 `--primary` / `--ring` / `--primary-foreground`（shadcn），
 *   共用 @fantastic-admin/themes/runtime 的主色转换与色阶生成。
 *   YD 控件和内部基础能力消费同一组 FA 语义变量。
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
  { name: 'default', label: 'Fa 默认', value: settingsDefault.theme.primaryColor },
  { name: 'blue', label: '湖蓝', value: '#165DFF' },
  { name: 'green', label: '绿', value: '#00B42A' },
  { name: 'purple', label: '紫', value: '#722ED1' },
  { name: 'orange', label: '橙', value: '#FF7D00' },
  { name: 'red', label: '红', value: '#F53F3F' },
]

const STORAGE_KEY = 'component-showcase:theme:v2'

interface ThemeSetting {
  colorScheme: ThemeColorScheme
  primaryColor: string
  radius: number
  colorAmblyopia: boolean
}

const defaultSetting: ThemeSetting = {
  colorScheme: settingsDefault.theme.colorScheme,
  primaryColor: settingsDefault.theme.primaryColor,
  radius: settingsDefault.theme.radius,
  colorAmblyopia: settingsDefault.theme.colorAmblyopia,
}

function normalizeHex(color: string): string | null {
  const value = color.trim()
  if (/^#[0-9a-f]{6}$/i.test(value)) {
    return value.toUpperCase()
  }
  if (/^#[0-9a-f]{3}$/i.test(value)) {
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
  applyFaPrimaryColor(primaryColor.value)
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
