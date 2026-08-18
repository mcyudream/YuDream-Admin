import type { InjectionKey } from 'vue'
import { inject, provide } from 'vue'

/** YuDreamAdmin AI 对话全局配置（对齐 antd-x XProvider / tdesign ChatProvider） */
export interface YdChatConfig {
  /** 输入框占位文案 */
  placeholder?: string
  /** 全局建议问题 */
  suggestions?: string[]
  /** 等待首字节提示 */
  thinkingText?: string
  /** 深度思考标题 */
  reasoningTitle?: string
  /** 输入区免责声明 */
  disclaimer?: string
  /** 助手头像图标（iconify 名称），传空字符串隐藏 */
  assistantAvatar?: string
  /** 渲染前的内容转换（如 wikilink → markdown 链接） */
  transformContent?: (content: string) => string
}

export const ydChatConfigKey: InjectionKey<YdChatConfig> = Symbol('yd-chat-config')

/** 在应用或页面根部提供 AI 对话默认配置 */
export function provideYdChatConfig(config: YdChatConfig) {
  provide(ydChatConfigKey, config)
}

/** 组件内部读取全局配置，未提供时回退空对象 */
export function useYdChatConfig(): YdChatConfig {
  return inject(ydChatConfigKey, {})
}
