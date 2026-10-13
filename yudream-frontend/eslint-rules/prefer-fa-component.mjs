import { scanUiSource } from './fa-component-map.mjs'

export default {
  meta: {
    type: 'problem',
    docs: { description: 'Enforce public YD components for all business UI interactions' },
    schema: [],
    messages: {
      violation: '{{message}}',
    },
  },
  create(context) {
    return {
      'Program:exit': function scanUi() {
        const source = context.sourceCode.getText()
        for (const violation of scanUiSource(source, context.filename ?? '')) {
          context.report({
            loc: context.sourceCode.getLocFromIndex(violation.index),
            messageId: 'violation',
            data: { message: violation.message },
          })
        }
      },
    }
  },
}
