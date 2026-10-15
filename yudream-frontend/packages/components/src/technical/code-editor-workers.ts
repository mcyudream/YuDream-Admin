// 显式带 .js：monaco 0.57 的 exports 只暴露 `./*.js` 与 `./*`，带查询串的深层子路径在 Vite 5（VitePress 文档站）里只有 .js 形式能解析。
import EditorWorker from 'monaco-editor/editor/editor.worker.js?worker'
import CssWorker from 'monaco-editor/languages/features/css/css.worker.js?worker'
import HtmlWorker from 'monaco-editor/languages/features/html/html.worker.js?worker'
import JsonWorker from 'monaco-editor/languages/features/json/json.worker.js?worker'
import TsWorker from 'monaco-editor/languages/features/typescript/ts.worker.js?worker'

// 独立模块承载 `?worker` 导入，使 code-editor-model.ts 这类纯函数模块可被 node:test 直接引用。
export function ensureYdMonacoWorkers() {
  const scope = globalThis as { MonacoEnvironment?: unknown }
  if (scope.MonacoEnvironment) {
    return
  }
  scope.MonacoEnvironment = {
    getWorker(_moduleId: string, label: string) {
      if (label === 'json') {
        return new JsonWorker()
      }
      if (label === 'css' || label === 'scss' || label === 'less') {
        return new CssWorker()
      }
      if (label === 'html' || label === 'handlebars' || label === 'razor') {
        return new HtmlWorker()
      }
      if (label === 'typescript' || label === 'javascript') {
        return new TsWorker()
      }
      return new EditorWorker()
    },
  }
}
