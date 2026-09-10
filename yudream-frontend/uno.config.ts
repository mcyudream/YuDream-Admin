import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { darkTheme, lightTheme } from '@fantastic-admin/themes'
import { entriesToCss, toArray } from '@unocss/core'
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
import { presetAnimations } from 'unocss-preset-animations'

const projectRoot = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  // 级联层架构：preflight（wind4 reset 的 base 层 + shadcn 的 preflights 层）降入 yudream-base，
  // 插件 style.css 经 SDK 输出进 @layer yudream-plugin，宿主工具类保持未分层（最高优先）。
  // 层序：yudream-base < yudream-plugin < 宿主未分层工具类——插件可覆写 reset 与原生元素样式，
  // 但永远不能覆盖宿主同名工具类/响应式变体（如 sm:max-w-lg 被插件 max-w-full 顶掉的问题）。
  outputToCssLayers: {
    cssLayerName: layer => (layer === 'preflights' || layer === 'base') ? 'yudream-base' : null,
  },
  content: {
    pipeline: {
      include: [
        /\.(vue|svelte|[jt]sx|mdx?|astro|elm|php|phtml|html)($|\?)/,
        'src/**/*.{js,ts}',
        path.resolve(projectRoot, 'packages/components/**/*.{vue,js,ts}'),
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
    presetWind4(),
    presetAnimations(),
    presetAttributify(),
    presetIcons({
      collectionsNodeResolvePath: projectRoot,
      extraProperties: {
        'display': 'inline-block',
        'vertical-align': 'middle',
      },
    }),
    presetTypography(),
    {
      name: 'unocss-preset-shadcn',
      preflights: [
        {
          getCSS: () => {
            const returnCss = [
              toArray(':root').map(root => `${root}{color-scheme:light;${entriesToCss(Object.entries(lightTheme))}}`).join(''),
              toArray('.dark').map(root => `${root}{color-scheme:dark;${entriesToCss(Object.entries(darkTheme))}}`).join(''),
            ]
            return `
${returnCss.join('\n')}

::selection {
  color: oklch(var(--primary-foreground));
  background-color: oklch(var(--primary));
}

* {
  border-color: oklch(var(--border));
  scrollbar-color: oklch(var(--border)) transparent;
  scrollbar-width: thin;
}

body {
  color: oklch(var(--foreground));
  background: oklch(var(--background));
}

button:not(:disabled),
[role="button"]:not(:disabled) {
  cursor: pointer;
}
`
          },
        },
      ],
      theme: {
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
      },
    },
  ],
  transformers: [
    transformerDirectives(),
    transformerVariantGroup(),
    transformerCompileClass(),
  ],
  configDeps: [
    path.resolve(projectRoot, 'packages/themes/index.ts'),
  ],
})
