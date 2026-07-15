import type { Editor, Plugin } from 'grapesjs'
import blocksBasic from 'grapesjs-blocks-basic'
import customCode from 'grapesjs-custom-code'
import navbar from 'grapesjs-navbar'
import forms from 'grapesjs-plugin-forms'
import styleBg from 'grapesjs-style-bg'

export const cmsGrapesPluginOptions = {
  blocksBasic: {
    category: '基础组件',
    flexGrid: true,
    blocks: ['column1', 'column2', 'column3', 'text', 'link', 'image', 'video', 'map'],
    labelColumn1: '单列布局',
    labelColumn2: '双列布局',
    labelColumn3: '三列布局',
    labelText: '文本',
    labelLink: '链接',
    labelImage: '图片',
    labelVideo: '视频',
    labelMap: '地图',
  },
  forms: {
    category: '表单',
  },
  navbar: {
    label: '响应式导航',
    block: {
      category: '导航',
    },
  },
  customCode: {
    blockCustomCode: {
      category: '高级',
      label: '自定义代码',
    },
    modalTitle: '编辑自定义代码',
    buttonLabel: '应用',
    placeholderScript: '脚本已隐藏，仅在页面预览或发布后执行。',
  },
  styleBg: {},
} as const

export const cmsPluginBlockLabels: Record<string, string> = {
  form: '表单容器',
  input: '输入框',
  textarea: '多行文本',
  select: '下拉选择',
  button: '提交按钮',
  label: '字段标签',
  checkbox: '复选框',
  radio: '单选框',
}

export const cmsGrapesPluginDefinitions: Array<{
  id: string
  plugin: Plugin<any>
  options: Record<string, any>
}> = [
  { id: 'blocks-basic', plugin: blocksBasic, options: cmsGrapesPluginOptions.blocksBasic },
  { id: 'forms', plugin: forms, options: cmsGrapesPluginOptions.forms },
  { id: 'navbar', plugin: navbar, options: cmsGrapesPluginOptions.navbar },
  { id: 'custom-code', plugin: customCode, options: cmsGrapesPluginOptions.customCode },
  { id: 'style-bg', plugin: styleBg, options: cmsGrapesPluginOptions.styleBg },
]

export function cmsGrapesPlugins() {
  return cmsGrapesPluginDefinitions.map(item => item.plugin)
}

export function cmsGrapesPluginsOpts() {
  return Object.fromEntries(cmsGrapesPluginDefinitions.map(item => [String(item.plugin), item.options]))
}

export function localizeCmsPluginBlocks(editor: Editor) {
  Object.entries(cmsPluginBlockLabels).forEach(([id, label]) => {
    editor.BlockManager.get(id)?.set('label', label)
  })
}
