import { config } from 'md-editor-v3'

export const YD_MARKDOWN_EDITOR_ID_PREFIX = 'yd-markdown-editor-'

export function shouldEnableYdMarkdownHtml(editorId: string): boolean {
  return editorId.startsWith(YD_MARKDOWN_EDITOR_ID_PREFIX)
}

config({
  markdownItConfig(markdownIt, { editorId }) {
    if (shouldEnableYdMarkdownHtml(editorId)) {
      markdownIt.set({ html: true })
    }
  },
})
