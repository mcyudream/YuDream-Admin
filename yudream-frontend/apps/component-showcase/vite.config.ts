import path from 'node:path'
import { ComposablesAutoImports as FantasticAdminComposablesAutoImports } from '@fantastic-admin/composables/resolver'
import vue from '@vitejs/plugin-vue'
import { ComponentsAutoImports as FantasticAdminComponentsAutoImports, ComponentsResolver as FantasticAdminComponentsResolver, ComponentsType as FantasticAdminComponentsType } from '@yudream/components/resolver'
import Unocss from 'unocss/vite'
import autoImport from 'unplugin-auto-import/vite'
import components from 'unplugin-vue-components/vite'
import { defineConfig } from 'vite'

export default defineConfig(({ command }) => ({
  plugins: [
    vue(),

    // https://github.com/unplugin/unplugin-auto-import
    autoImport({
      imports: [
        'vue',
        FantasticAdminComponentsAutoImports,
        FantasticAdminComposablesAutoImports,
      ],
      dts: command === 'serve' ? './src/types/auto-imports.d.ts' : false,
    }),

    // https://github.com/unplugin/unplugin-vue-components
    components({
      dts: command === 'serve' ? './src/types/components.d.ts' : false,
      resolvers: [
        FantasticAdminComponentsResolver(),
      ],
      types: [
        FantasticAdminComponentsType,
      ],
    }),

    Unocss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  // 组件工作区按源码热更新，新增公开入口无需重建依赖预打包缓存。
  optimizeDeps: {
    exclude: ['@yudream/components'],
  },
  server: {
    port: 5188,
    strictPort: true,
  },
}))
