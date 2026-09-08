import type { UserConfig } from 'unocss'
import { fileURLToPath } from 'node:url'
import { presetAnimations } from 'unocss-preset-animations'
import {
  defineConfig,
  presetAttributify,
  presetIcons,
  presetTypography,
  presetWind4,
  transformerCompileClass,
  transformerDirectives,
  transformerVariantGroup,
} from 'unocss'
import UnoCSS from 'unocss/vite'

export interface YuDreamPluginUnoCssOptions {
  /**
   * 额外纳入 UnoCSS 扫描的内容来源。
   * 默认已覆盖插件包内的 `src/**`（vue/ts 等），仅当模板字符串来自其他目录时才需要补充。
   */
  extraContent?: Array<string | RegExp>
}

// 与 yudream-frontend/uno.config.ts 的 unocss-preset-shadcn theme 保持一致。
// 插件侧只引用宿主 preflight 已注入 :root/.dark 的 CSS 变量，绝不重复定义变量或全局 reset，
// 主色、深浅模式与圆角始终由宿主主题配置统一控制。
const shadcnTheme = {
  colors: {
    background: 'oklch(var(--background))',
    foreground: 'oklch(var(--foreground))',
    card: {
      DEFAULT: 'oklch(var(--card))',
      foreground: 'oklch(var(--card-foreground))',
    },
    popover: {
      DEFAULT: 'oklch(var(--popover))',
      foreground: 'oklch(var(--popover-foreground))',
    },
    primary: {
      DEFAULT: 'oklch(var(--primary))',
      foreground: 'oklch(var(--primary-foreground))',
    },
    secondary: {
      DEFAULT: 'oklch(var(--secondary))',
      foreground: 'oklch(var(--secondary-foreground))',
    },
    muted: {
      DEFAULT: 'oklch(var(--muted))',
      foreground: 'oklch(var(--muted-foreground))',
    },
    accent: {
      DEFAULT: 'oklch(var(--accent))',
      foreground: 'oklch(var(--accent-foreground))',
    },
    destructive: 'oklch(var(--destructive))',
    border: 'oklch(var(--border))',
    input: 'oklch(var(--input))',
    ring: 'oklch(var(--ring))',
  },
  radius: {
    xl: 'calc(var(--radius) + 4px)',
    lg: 'var(--radius)',
    md: 'calc(var(--radius) - 2px)',
    sm: 'calc(var(--radius) - 4px)',
  },
}

/**
 * 生成与宿主一致的 UnoCSS 配置（ presetWind4/attributify/icons/typography/animations、
 * flex 快捷方式与 shadcn 主题色板 ）。差异仅两处：
 * - 关闭全局 reset preflight（插件 DOM 与宿主同文档，宿主已提供 reset 与主题变量）；
 * - 扫描范围指向插件自身 src。
 * 图标按需从 @iconify/json 解析（随 SDK 安装，构建期本地读取，不访问网络）。
 */
export function yuDreamPluginUnoConfig(options: YuDreamPluginUnoCssOptions = {}): UserConfig {
  return defineConfig({
    content: {
      pipeline: {
        include: [
          /\.(vue|svelte|[jt]sx|mdx?|astro|elm|php|phtml|html)($|\?)/,
          'src/**/*.{js,ts}',
          ...(options.extraContent ?? []),
        ],
      },
    },
    shortcuts: [
      [/^flex-?(col)?-(start|end|center|baseline|stretch)-?(start|end|center|between|around|evenly|left|right)?$/, ([, col, items, justify]) => {
        const cls = ['flex']
        if (col === 'col') {
          cls.push('flex-col')
        }
        if (items === 'center' && !justify) {
          cls.push('items-center')
          cls.push('justify-center')
        }
        else {
          cls.push(`items-${items}`)
          if (justify) {
            cls.push(`justify-${justify}`)
          }
        }
        return cls.join(' ')
      }],
    ],
    presets: [
      presetWind4({
        preflights: {
          reset: false,
        },
      }),
      presetAnimations(),
      presetAttributify(),
      presetIcons({
        collectionsNodeResolvePath: fileURLToPath(new URL('.', import.meta.url)),
        extraProperties: {
          'display': 'inline-block',
          'vertical-align': 'middle',
        },
      }),
      presetTypography(),
      {
        name: 'yudream-plugin-shadcn-theme',
        theme: shadcnTheme,
      },
    ],
    transformers: [
      transformerDirectives(),
      transformerVariantGroup(),
      transformerCompileClass(),
    ],
  })
}

/**
 * 插件 vite.config.ts 一行接入：`plugins: [vue(), ...yuDreamPluginUnoCss()]`，
 * 并在入口 `import 'virtual:uno.css'`；产物 CSS 在 `@PluginFrontend(styles = ...)` 中声明后由宿主加载。
 */
export function yuDreamPluginUnoCss(options: YuDreamPluginUnoCssOptions = {}) {
  return UnoCSS(yuDreamPluginUnoConfig(options))
}
