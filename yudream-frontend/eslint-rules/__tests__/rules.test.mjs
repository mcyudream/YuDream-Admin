import assert from 'node:assert'
import test from 'node:test'
import { RuleTester } from 'eslint'
import vueParser from 'vue-eslint-parser'
import noBrandColorToken from '../no-brand-color-token.mjs'
import preferFaComponent from '../prefer-fa-component.mjs'

const vueTester = new RuleTester({
  languageOptions: {
    parser: vueParser,
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
})

test('prefer-fa-component', () => {
  vueTester.run('prefer-fa-component', preferFaComponent, {
    valid: [
      {
        filename: 'valid.vue',
        code: '<template><FaButton>保存</FaButton><FaSelect :options="[]" /></template>',
      },
      {
        filename: 'valid.vue',
        code: '<template><a-form><a-form-item label="名称" /></a-form></template>',
      },
      {
        filename: 'valid.ts',
        code: 'import { FaButton } from \'@yudream/components\'',
      },
      {
        filename: 'valid.ts',
        code: 'import { Tree } from \'@arco-design/web-vue\'',
      },
    ],
    invalid: [
      {
        filename: 'invalid.vue',
        code: '<template><a-button type="primary">保存</a-button></template>',
        errors: [{ messageId: 'preferFaTemplate' }],
      },
      {
        filename: 'invalid.vue',
        code: '<template><div><ATable :columns="[]" /></div></template>',
        errors: [{ messageId: 'preferFaTemplate' }],
      },
      {
        filename: 'invalid.vue',
        code: '<template><a-modal /><a-drawer /><a-switch /></template>',
        errors: [
          { messageId: 'preferFaTemplate' },
          { messageId: 'preferFaTemplate' },
          { messageId: 'preferFaTemplate' },
        ],
      },
      {
        filename: 'invalid.ts',
        code: 'import { Message, Modal } from \'@arco-design/web-vue\'',
        errors: [
          { messageId: 'preferFaImport' },
          { messageId: 'preferFaImport' },
        ],
      },
    ],
  })
})

test('no-brand-color-token', () => {
  vueTester.run('no-brand-color-token', noBrandColorToken, {
    valid: [
      {
        filename: 'valid.vue',
        code: '<template><div class="text-primary" :style="{ color: \'var(--color-text-1)\' }" /></template>',
      },
      {
        filename: 'valid.vue',
        code: '<template><div /></template><style scoped>.a { background: oklch(var(--primary)); color: var(--primary-foreground); }</style>',
      },
      {
        filename: 'valid.ts',
        code: 'const cls = \'text-primary\'',
      },
    ],
    invalid: [
      {
        filename: 'invalid.vue',
        code: '<template><div :style="{ color: \'rgb(var(--primary-6))\' }" /></template>',
        errors: [{ messageId: 'noBrandColor' }],
      },
      {
        filename: 'invalid.vue',
        code: '<template><div /></template><style scoped>.a { color: rgb(var(--primary-6)); background: rgba(var(--primary-1), 0.1); }</style>',
        errors: [
          { messageId: 'noBrandColor' },
          { messageId: 'noBrandColor' },
        ],
      },
    ],
  })
})

test('Arco → Fa 映射表覆盖 25 项以上', async () => {
  const { ARCO_TO_FA_MAP } = await import('../fa-component-map.mjs')
  assert.ok(Object.keys(ARCO_TO_FA_MAP).length >= 25)
})
