import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'unocss'
import hostUnoConfig from '../yudream-frontend/uno.config.ts'

const projectRoot = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  ...hostUnoConfig,
  content: {
    pipeline: {
      include: [
        path.resolve(projectRoot, 'docs/**/*.{md,vue,ts}'),
        path.resolve(projectRoot, '../yudream-frontend/packages/components/**/*.{vue,ts}'),
      ],
    },
  },
})
