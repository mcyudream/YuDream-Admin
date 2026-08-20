import { ARCO_SCRIPT_API_MAP, ARCO_TAG_TO_FA_MAP, ARCO_TO_FA_MAP } from './fa-component-map.mjs'

const TAG_PATTERN = /<\s*a-([a-z][a-z0-9]*(?:-[a-z0-9]+)*)/g
const PASCAL_TAG_PATTERN = /<\s*A([A-Z][A-Za-z0-9]*)/g

/** AI 对话场景路径片段，命中时追加 Yd* 元件提示 */
const AI_SCENE_PATTERN = /(?:^|[/\\_-])(?:ai|chat|agent)(?:[/\\_-]|$)/i

function ydHintOf(filename) {
  return AI_SCENE_PATTERN.test(filename)
    ? 'AI 对话场景请优先考虑 Yd* 元件；'
    : ''
}

export default {
  meta: {
    type: 'suggestion',
    docs: {
      description: '存在 Fa 等价组件时禁止直接使用 Arco 原生组件，保持组件体系与主题统一',
    },
    schema: [],
    messages: {
      preferFaTemplate: '优先使用 {{fa}} 替代 Arco 原生组件 <a-{{tag}}>，保持组件体系与主题统一。{{ydHint}}如确需 Arco 原生组件，请 eslint-disable 本行并注明原因。',
      preferFaImport: '优先使用 {{fa}} 替代从 @arco-design/web-vue 导入的 {{name}}。{{ydHint}}如确需 Arco 原生能力，请 eslint-disable 本行并注明原因。',
    },
  },
  create(context) {
    const filename = context.filename ?? ''
    const ydHint = ydHintOf(filename)

    function reportTemplateTag(index, tag, fa) {
      context.report({
        loc: context.sourceCode.getLocFromIndex(index),
        messageId: 'preferFaTemplate',
        data: { tag, fa, ydHint },
      })
    }

    return {
      ImportDeclaration(node) {
        if (node.source.value !== '@arco-design/web-vue') {
          return
        }
        for (const specifier of node.specifiers) {
          if (specifier.type !== 'ImportSpecifier') {
            continue
          }
          const name = specifier.imported.name ?? specifier.imported.value
          const fa = ARCO_TO_FA_MAP[name] ?? ARCO_SCRIPT_API_MAP[name]
          if (fa) {
            context.report({
              node: specifier,
              messageId: 'preferFaImport',
              data: { name, fa, ydHint },
            })
          }
        }
      },
      'Program:exit': function scanTemplate() {
        if (!filename.endsWith('.vue')) {
          return
        }
        const text = context.sourceCode.getText()
        for (const match of text.matchAll(TAG_PATTERN)) {
          const tag = match[1]
          const fa = ARCO_TAG_TO_FA_MAP[tag]
          if (fa) {
            reportTemplateTag(match.index, tag, fa)
          }
        }
        for (const match of text.matchAll(PASCAL_TAG_PATTERN)) {
          const name = match[1]
          const fa = ARCO_TO_FA_MAP[name]
          if (fa) {
            reportTemplateTag(match.index, name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase(), fa)
          }
        }
      },
    }
  },
}
