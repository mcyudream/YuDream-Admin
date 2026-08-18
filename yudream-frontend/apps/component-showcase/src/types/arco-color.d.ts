// @arco-design/color 没有自带类型声明，这里按实际使用到的 API 补充
declare module '@arco-design/color' {
  export interface GenerateOptions {
    /** 取色板中的第几级（1-10） */
    index?: number
    /** 返回完整 10 级色板数组 */
    list?: boolean
    /** 生成暗色模式色板 */
    dark?: boolean
  }

  export function generate(color: string, options: GenerateOptions & { list: true }): string[]
  export function generate(color: string, options?: GenerateOptions): string

  /** hex 色值转 RGB 三元组字符串，如 '#165DFF' -> '22,93,255' */
  export function getRgbStr(color: string): string

  export function getPresetColors(): Record<string, { label: string, colors: string[] }>
}
