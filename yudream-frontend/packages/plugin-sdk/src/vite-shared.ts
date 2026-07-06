import { fileURLToPath } from 'node:url'

export function yuDreamPluginSharedAliases() {
  return {
    vue: fileURLToPath(new URL('./host-vue.ts', import.meta.url)),
    'vue-router': fileURLToPath(new URL('./host-vue-router.ts', import.meta.url)),
    '@fantastic-admin/components': fileURLToPath(new URL('./host-components.ts', import.meta.url)),
  }
}
