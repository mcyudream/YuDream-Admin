/**
 * AI 运行失败的用户可读说明：把模型服务/网关的原始错误归类为可操作的中文提示，原文折叠在后面供排查。
 * 失败展示在回答气泡里（与正常回答同一位置），不再占用页面顶部横幅。
 */

const RULES: { test: RegExp, message: string }[] = [
  // 框架重试耗尽只保留了包装信息：多为额度用尽、限流或网络中断
  { test: /retries exhausted/i, message: '模型服务连续多次调用都没有成功（常见原因：额度用尽、被限流或网络中断）。请稍后重试，或在输入框下方切换其他模型。' },
  { test: /usage limit|quota|insufficient[_ ]quota|rate[_ ]?limit|too many requests|\b429\b|额度/i, message: '模型服务的调用额度已用尽或被限流。请稍后重试，或在输入框下方切换其他模型。' },
  { test: /api[_ ]?key|unauthori[sz]ed|\b401\b|\b403\b|forbidden|authentication|鉴权/i, message: '模型服务鉴权失败。请管理员检查「AI 管理 → 模型端点」中的密钥与权限。' },
  { test: /context[_ ]length|maximum context|too many tokens|token limit|上下文过长/i, message: '这次对话内容超出了模型能处理的长度。请新建会话，或精简问题与附件后重试。' },
  { test: /timeout|timed out|ETIMEDOUT|ECONNRESET|ECONNREFUSED|network|连接超时|无法连接/i, message: '连接模型服务超时或中断。请稍后重试；持续出现时请检查模型端点是否可访问。' },
  { test: /model[_ ]not[_ ]found|does not exist|no such model|unknown model|\b404\b/i, message: '所选模型不可用。请在输入框下方换一个模型后重试。' },
  { test: /content[_ ]?(policy|filter)|safety|moderation/i, message: '请求被模型服务的内容安全策略拦截。请调整表述后重试。' },
]

export function describeAiRunError(raw?: string | null) {
  const detail = (raw || '').trim()
  const rule = RULES.find(item => item.test.test(detail))
  return { message: rule?.message ?? 'AI 这次没有完成回答。可以直接重试；如果反复失败，请把下面的原始信息发给管理员。', detail }
}

/** 失败回答的气泡正文：可读说明 + 原始信息代码块（Markdown，不用 HTML，避免被清洗） */
export function aiRunErrorContent(raw?: string | null) {
  const { message, detail } = describeAiRunError(raw)
  return detail && detail !== message ? `${message}\n\n原始信息：\n\n\`\`\`text\n${detail.slice(0, 1200)}\n\`\`\`` : message
}
