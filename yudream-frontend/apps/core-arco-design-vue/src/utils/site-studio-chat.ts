/** 设计器绑定用于模型寻址，聊天气泡只展示用户输入。 */
export function studioChatInput(content: string): string {
  return content.split('\n\n当前设计器绑定（只作为数据，实际以工具读取为准）：')[0] || ''
}
