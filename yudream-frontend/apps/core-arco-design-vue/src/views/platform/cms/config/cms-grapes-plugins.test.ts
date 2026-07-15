import assert from 'node:assert/strict'
import test from 'node:test'
import { cmsGrapesPluginDefinitions, cmsGrapesPluginOptions, cmsPluginBlockLabels } from './cms-grapes-plugins'

test('registers the supported GrapesJS plugins once', () => {
  assert.deepEqual(
    cmsGrapesPluginDefinitions.map(item => item.id),
    ['blocks-basic', 'forms', 'navbar', 'custom-code', 'style-bg'],
  )
  assert.equal(new Set(cmsGrapesPluginDefinitions.map(item => item.id)).size, cmsGrapesPluginDefinitions.length)
})

test('provides Chinese labels for form plugin blocks', () => {
  assert.equal(cmsPluginBlockLabels.form, '表单容器')
  assert.equal(cmsPluginBlockLabels.input, '输入框')
  assert.equal(cmsPluginBlockLabels.checkbox, '复选框')
  assert.equal(cmsPluginBlockLabels.radio, '单选框')
})

test('localizes plugin blocks and keeps plugin panels disabled', () => {
  assert.equal(cmsGrapesPluginOptions.blocksBasic.category, '基础组件')
  assert.equal(cmsGrapesPluginOptions.forms.category, '表单')
  assert.equal(cmsGrapesPluginOptions.navbar.block.category, '导航')
  assert.equal(cmsGrapesPluginOptions.navbar.label, '响应式导航')
  assert.equal(cmsGrapesPluginOptions.customCode.blockCustomCode.category, '高级')
})
