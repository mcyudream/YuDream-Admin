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
  // Fa 组件优先与品牌色令牌审查：仅告警不阻断，供 lint 与开发者工具面板消费
  {
    files: [
      'apps/**/*.vue',
      'apps/**/*.ts',
      'apps/**/*.tsx',
    ],
    plugins: {
      yudream,
    },
    rules: {
      'yudream/prefer-fa-component': 'warn',
      'yudream/no-brand-color-token': 'warn',
    },
  },
  // 本地规则用例使用 node:test 运行（根包无 vitest 依赖），禁止改写为 vitest 导入
  {
    files: [
      'eslint-rules/**/*.mjs',
    ],
    rules: {
      'test/no-import-node-test': 'off',
    },
  },
)
