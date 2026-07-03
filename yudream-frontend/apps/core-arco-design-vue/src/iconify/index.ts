import { addCollection } from '@iconify/vue'
import data from './data.json'

export async function downloadAndInstall(name: string) {
  const collection = data.find(item => item.prefix === name)
  if (collection) {
    addCollection(collection)
  }
}

export const icons = data.sort((a, b) => a.info.name.localeCompare(b.info.name))
