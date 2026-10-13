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
      { filename: 'valid.vue', code: '<template><YdButton>保存</YdButton><YdSelect :options="[]" /></template>' },
      { filename: 'packages/components/src/ui/Inner.vue', code: '<template><button>内部实现</button></template>' },
      {
        filename: 'valid.ts',
        code: 'import { YdButton } from \'@yudream/components\'',
      },
    ],
    invalid: [
      {
        filename: 'invalid.vue',
        code: '<template><a-button type="primary">保存</a-button></template>',
        errors: [{ messageId: 'violation' }],
      },
      {
        filename: 'invalid.vue',
        code: '<template><div><ATable :columns="[]" /></div></template>',
        errors: [{ messageId: 'violation' }],
      },
      {
        filename: 'invalid.vue',
        code: '<template><a-modal /><a-drawer /><a-switch /></template>',
        errors: [
          { messageId: 'violation' },
          { messageId: 'violation' },
          { messageId: 'violation' },
        ],
      },
      {
        filename: 'invalid.ts',
        code: 'import { Message, Modal } from \'@arco-design/web-vue\'',
        errors: [{ messageId: 'violation' }],
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

test('strict YD-only gate rejects Fa aliases, Arco type imports, unknown YD names and native controls', async () => {
  const { scanUiSource } = await import('../fa-component-map.mjs')
  const cases = [
    ['<template><FaInput /></template>', 'screen.vue', 'yd-only'],
    ['import type { SelectProps } from \'@arco-design/web-vue\'', 'screen.ts', 'yd-only-type-import'],
    ['import { SelectRoot } from \'reka-ui\'', 'screen.ts', 'yd-only'],
    ['<template><YdMissing /></template>', 'screen.vue', 'yd-only-unknown-entry'],
    ['import { YdMissing } from \'@yudream/components\'', 'screen.ts', 'yd-only-unknown-entry'],
    ['<template><button @click="run">Run</button></template>', 'screen.vue', 'yd-only-native-control'],
    ['const vnode = h(\'button\', { onClick: run })', 'screen.tsx', 'yd-only-native-render'],
    ['const vnode = createVNode(\'input\')', 'screen.ts', 'yd-only-native-render'],
  ]
  for (const [source, file, expected] of cases) {
    assert.ok(scanUiSource(source, file).some(item => item.rule === expected), `${expected} should be reported`)
  }
})

test('strict YD-only gate permits semantic content and registered public components', async () => {
  const { scanUiSource } = await import('../fa-component-map.mjs')
  assert.deepEqual(scanUiSource('<template><main><p>内容</p><a href="/docs">文档</a><YdButton /></main></template>', 'screen.vue'), [])
})

test('nested slots cannot hide controls in the rest of the SFC', async () => {
  const { scanUiSource } = await import('../fa-component-map.mjs')
  const source = '<template><YdCard><template #header><p>标题</p></template><button @click="save">保存</button><div @click.stop="open">打开</div></YdCard></template>'
  const result = scanUiSource(source, 'screen.vue')
  assert.equal(result.filter(item => item.rule === 'yd-only-native-control').length, 1)
  assert.equal(result.filter(item => item.rule === 'yd-only-clickable-structure').length, 1)
  assert.equal(result[0].index, source.indexOf('<button'))
})

test('comments and displayed strings are not UI imports or controls', async () => {
  const { scanUiSource } = await import('../fa-component-map.mjs')
  assert.deepEqual(scanUiSource('// import { Button } from \'@arco-design/web-vue\'\nconst doc = "FaButton h(\'input\')"', 'screen.ts'), [])
  assert.deepEqual(scanUiSource('<template><!-- <FaButton /><input /> --><p>FaButton 示例</p></template>', 'screen.vue'), [])
})

test('renamed render helpers and JSX cannot bypass the public entry', async () => {
  const { scanUiSource } = await import('../fa-component-map.mjs')
  assert.ok(scanUiSource('import { h as render } from \'vue\'; render(\'button\')', 'screen.ts').some(item => item.rule === 'yd-only-native-render'))
  assert.ok(scanUiSource('const node = <input disabled />', 'screen.tsx').some(item => item.rule === 'yd-only-native-control'))
  assert.ok(scanUiSource('import { FaButton as Save } from \'@yudream/components\'', 'screen.ts').some(item => item.rule === 'yd-only'))
})

test('local business compositions are allowed while malformed source fails closed', async () => {
  const { scanUiSource } = await import('../fa-component-map.mjs')
  assert.deepEqual(scanUiSource('<script setup>import YdLocalPanel from \'./YdLocalPanel.vue\'</script><template><YdLocalPanel /></template>', 'screen.vue'), [])
  assert.ok(scanUiSource('const broken = {', 'screen.ts').some(item => item.rule === 'yd-only-parse-error'))
})

test('rendered article link delegation is distinct from simulated controls', async () => {
  const { scanUiSource } = await import('../fa-component-map.mjs')
  assert.deepEqual(scanUiSource('<template><article v-html="html" @click="delegateLink" /></template>', 'screen.vue'), [])
  assert.deepEqual(scanUiSource('<template><div @click.stop /></template>', 'screen.vue'), [])
  for (const source of [
    '<template><div v-html="html" role="button" @click="save" /></template>',
    '<template><div v-html="html" tabindex="0" @keydown="save" /></template>',
    '<template><div v-html="html" :role="role" @click="save" /></template>',
  ]) {
    assert.ok(scanUiSource(source, 'screen.vue').some(item => item.rule === 'yd-only-clickable-structure'))
  }
})

test('multi-key data navigation is distinct from an action and anchors need href', async () => {
  const { scanUiSource } = await import('../fa-component-map.mjs')
  assert.deepEqual(scanUiSource('<template><div role="group" tabindex="0" @keydown="browseChart" /></template>', 'screen.vue'), [])
  assert.ok(scanUiSource('<template><div role="group" @click="save" /></template>', 'screen.vue').some(item => item.rule === 'yd-only-clickable-structure'))
  assert.ok(scanUiSource('<template><a @click="save">保存</a></template>', 'screen.vue').some(item => item.rule === 'yd-only-clickable-structure'))
  assert.deepEqual(scanUiSource('<template><a href="/docs" @click="navigate">文档</a></template>', 'screen.vue'), [])
})

test('an accessible region may delegate Escape dismissal without becoming a control', async () => {
  const { scanUiSource } = await import('../fa-component-map.mjs')
  assert.deepEqual(scanUiSource('<template><section aria-label="权限关系星图" @keydown.esc.stop="clearSelection"><YdButton>项</YdButton></section></template>', 'screen.vue'), [])
  assert.ok(scanUiSource('<template><section aria-label="权限关系星图" role="button" @keydown.esc="activate"></section></template>', 'screen.vue').some(item => item.rule === 'yd-only-clickable-structure'))
})

test('plugin management tables require public responsive table with card slot', async () => {
  const { scanUiSyntax } = await import('../ui-source-scanner.mjs')
  const options = { publicNames: new Set(['YdResponsiveTable']), thirdPartySources: [], requireResponsiveTables: true }
  assert.ok(scanUiSyntax('<template><table /></template>', 'screen.vue', options).some(item => item.rule === 'yd-only-native-table'))
  assert.ok(scanUiSyntax('<template><YdResponsiveTable /></template>', 'screen.vue', options).some(item => item.rule === 'yd-only-table-card'))
  assert.deepEqual(scanUiSyntax('<template><YdResponsiveTable><template #header>标题</template><template #card="{ row }">{{ row }}</template></YdResponsiveTable></template>', 'screen.vue', options), [])
})

test('legacy v-loading directive is rejected by the UI source scanner', async () => {
  const { scanUiSyntax } = await import('../ui-source-scanner.mjs')
  const options = { publicNames: new Set(['YdLoadingOverlay']), thirdPartySources: [] }

  assert.ok(scanUiSyntax('<template><section v-loading="busy"><p>内容</p></section></template>', 'screen.vue', options).some(item => item.rule === 'yd-only-third-party-directive'))
  assert.deepEqual(scanUiSyntax('<template><YdLoadingOverlay :loading="busy"><p>内容</p></YdLoadingOverlay></template>', 'screen.vue', options), [])
})
