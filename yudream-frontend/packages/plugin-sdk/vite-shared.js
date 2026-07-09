import { fileURLToPath } from 'node:url'

export function yuDreamPluginSharedAliases() {
  return {
    vue: fileURLToPath(new URL('./src/host-vue.ts', import.meta.url)),
    'vue-router': fileURLToPath(new URL('./src/host-vue-router.ts', import.meta.url)),
    '@yudream/components': fileURLToPath(new URL('./src/host-components.ts', import.meta.url)),
  }
}
