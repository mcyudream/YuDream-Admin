import mermaid from 'mermaid'
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
  // mermaid 块只让它渲染成「待渲染 div」，由运行期把 SVG 直接写进 DOM。
  // md-editor 的缓存命中分支会把 SVG 字符串内联进 markdown HTML，而我们的消毒是按
  // 白名单逐标签处理的：svg/g/path 之外的 marker/defs/style/text 会被转义成文本，
  // 图表就变成一堆乱码文本（重渲染后才出现，首次渲染正常）。缓存只影响性能，
  // 关掉它比放宽消毒白名单安全得多。
  markdownItPlugins(plugins) {
    return plugins.map(plugin => plugin.type === 'mermaid'
      ? { ...plugin, options: { ...plugin.options, getCached: () => undefined } }
      : plugin)
  },
  // mermaid 用本地实例而不是组件库默认的 CDN 脚本：内网/离线部署同样能渲染图表。
  // 实例来自 mermaid 的 ESM 入口，图表类型分块由打包器按需加载，不进主包。
  // 注意：md-editor 靠块上的 data-mermaid-revision / data-mermaid-block 筛选待渲染块，
  // 这两个属性必须在 HTML 消毒白名单里放行，否则图表会被静默跳过（见 markdown-render.ts）。
  editorExtensions: {
    mermaid: {
      instance: mermaid,
    },
  },
  mermaidConfig: base => ({ ...base, securityLevel: 'strict' }),
})
