import path from 'node:path'
import { ComposablesAutoImports as FantasticAdminComposablesAutoImports } from '@fantastic-admin/composables/resolver'
import { ComponentsAutoImports as FantasticAdminComponentsAutoImports, ComponentsResolver as FantasticAdminComponentsResolver, ComponentsType as FantasticAdminComponentsType } from '@yudream/components/resolver'
import vue from '@vitejs/plugin-vue'
import Unocss from 'unocss/vite'
import autoImport from 'unplugin-auto-import/vite'
import components from 'unplugin-vue-components/vite'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [
    vue(),

    // https://github.com/unplugin/unplugin-auto-import
    autoImport({
      imports: [
        'vue',
        FantasticAdminComponentsAutoImports,
        FantasticAdminComposablesAutoImports,
      ],
      dts: './src/types/auto-imports.d.ts',
    }),

    // https://github.com/unplugin/unplugin-vue-components
    components({
      dts: './src/types/components.d.ts',
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
  server: {
    port: 5188,
    strictPort: true,
  },
})
