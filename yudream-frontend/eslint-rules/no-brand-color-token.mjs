/**
 * 禁止业务页面直接使用 Arco 品牌色阶梯令牌（--primary-1 ~ --primary-10）。
 * 主题与主色由后台主题配置和组件体系统一控制，业务样式只能用中性语义变量
 * （--color-bg-* / --color-text-* / --color-border-* / --color-fill-*）。
 *
 * 注意：--primary / --primary-foreground 属于组件主题系统的合法令牌，不在此列；
 * 该令牌以 oklch(var(--primary)) 形式参与主题组合，而 Arco 阶梯令牌必然带数字后缀。
 */
const ARCO_PRIMARY_STEP_PATTERN = /--primary-\d/g

export default {
  meta: {
    type: 'suggestion',
    docs: {
      description: '业务页面禁止直接使用 Arco 品牌色阶梯令牌（--primary-N）',
    },
    schema: [],
    messages: {
      noBrandColor: '业务样式禁止直接使用品牌色令牌 {{token}}，主题与主色由后台主题配置统一控制，请改用中性语义变量（--color-bg-* / --color-text-* / --color-border-* / --color-fill-*）。如确属主题组件场景，请 eslint-disable 本行并注明原因。',
    },
  },
  create(context) {
    const filename = context.filename ?? ''
    if (!filename.endsWith('.vue') && !filename.endsWith('.ts') && !filename.endsWith('.tsx')) {
      return {}
    }
    return {
      'Program:exit': function scanBrandTokens() {
        const text = context.sourceCode.getText()
        for (const match of text.matchAll(ARCO_PRIMARY_STEP_PATTERN)) {
          context.report({
            loc: context.sourceCode.getLocFromIndex(match.index),
            messageId: 'noBrandColor',
            data: { token: match[0] },
          })
        }
      },
    }
  },
}
