import antfu from '@antfu/eslint-config'
import yudream from './eslint-rules/index.mjs'

export default antfu(
  {
    vue: true,
    unocss: true,
    markdown: false,
    ignores: [
      '**/public',
      '**/dist*',
    ],
  },
  {
    rules: {
      'e18e/prefer-static-regex': 'off',
      'eslint-comments/no-unlimited-disable': 'off',
      'curly': ['error', 'all'],
      'ts/no-unused-expressions': ['error', {
        allowShortCircuit: true,
        allowTernary: true,
      }],
    },
  },
  {
    files: [
      'src/**/*.vue',
    ],
    rules: {
      'vue/block-order': ['error', {
        order: ['script', 'template', 'style'],
      }],
    },
  },
  {
    files: [
      'pnpm-workspace.yaml',
    ],
    rules: {
      'pnpm/yaml-enforce-settings': 'off',
    },
  },
  // Strict YD-only gate; the separate audit report remains visible in devtools.
  {
    files: [
      'apps/core-arco-design-vue/src/**/*.vue',
      'apps/core-arco-design-vue/src/**/*.ts',
      'apps/core-arco-design-vue/src/**/*.tsx',
      'apps/component-showcase/src/**/*.vue',
      'apps/component-showcase/src/**/*.ts',
      'apps/component-showcase/src/**/*.tsx',
    ],
    plugins: {
      yudream,
    },
    rules: {
      'yudream/prefer-fa-component': 'error',
      'yudream/no-brand-color-token': 'error',
    },
  },
  // 本地规则与宿主单测使用 node:test（仓库无 vitest 依赖）。
  {
    files: [
      'eslint-rules/**/*.mjs',
      'apps/core-arco-design-vue/src/**/*.test.ts',
    ],
    rules: {
      'test/no-import-node-test': 'off',
    },
  },
)
