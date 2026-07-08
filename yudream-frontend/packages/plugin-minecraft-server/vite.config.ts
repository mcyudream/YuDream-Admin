import vue from '@vitejs/plugin-vue'
import { yuDreamPluginSharedAliases } from '@yudream/plugin-sdk/vite-shared'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: yuDreamPluginSharedAliases(),
  },
  build: {
    lib: {
      entry: 'src/index.ts',
      formats: ['es'],
      fileName: () => 'remoteEntry.js',
    },
    rollupOptions: {
      external: ['vue', 'vue-router', '@yudream/plugin-sdk', '@fantastic-admin/components'],
    },
    emptyOutDir: true,
  },
})
