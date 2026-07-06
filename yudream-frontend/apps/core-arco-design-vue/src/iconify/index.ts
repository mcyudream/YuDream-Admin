import { addCollection } from '@iconify/vue'
import data from './data.json'

export async function downloadAndInstall(name: string) {
  const collection = data.find(item => item.prefix === name)
  if (collection) {
    // data.json 中 icons 为 string[]（离线图标名称列表），与 IconifyJSON 的 Record 形状不同，
    // 但运行时 @iconify/vue 的 addCollection 在此处按现有行为工作，故先做类型断言。
    addCollection(collection as any)
  }
}

export const icons = data.sort((a, b) => a.info.name.localeCompare(b.info.name))
