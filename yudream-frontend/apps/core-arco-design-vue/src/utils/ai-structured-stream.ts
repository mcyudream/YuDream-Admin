/** Read only values fully present in the model's real JSON deltas. Never repair, fabricate, or replay characters. */
function stringEnd(source: string, start: number): number | undefined {
  let escaped = false
  for (let index = start + 1; index < source.length; index++) {
    const character = source[index]
    if (escaped) {
      escaped = false
    }
    else if (character === '\\') {
      escaped = true
    }
    else if (character === '"') {
      return index + 1
    }
  }
  return undefined
}

function valueEnd(source: string, start: number): number | undefined {
  if (source[start] === '"') {
    return stringEnd(source, start)
  }
  if (source[start] === '{' || source[start] === '[') {
    const stack: string[] = []
    for (let index = start; index < source.length; index++) {
      const character = source[index]
      if (character === '"') {
        const end = stringEnd(source, index)
        if (end === undefined) {
          return undefined
        }
        index = end - 1
      }
      else if (character === '{' || character === '[') {
        stack.push(character)
      }
      else if (character === '}' || character === ']') {
        if (stack.pop() !== (character === '}' ? '{' : '[')) {
          return undefined
        }
        if (!stack.length) {
          return index + 1
        }
      }
    }
    return undefined
  }
  const delimiter = source.slice(start).search(/[,}\]\s]/)
  return delimiter >= 0 ? start + delimiter : undefined
}

function propertyStart(source: string, key: string): number | undefined {
  let index = source.indexOf('{') + 1
  if (index === 0) {
    return undefined
  }
  while (index < source.length) {
    while (/[\s,]/.test(source[index] || '')) {
      index++
    }
    if (source[index] !== '"') {
      return undefined
    }
    const end = stringEnd(source, index)
    if (end === undefined) {
      return undefined
    }
    let candidate: unknown
    try {
      candidate = JSON.parse(source.slice(index, end))
    }
    catch {
      return undefined
    }
    index = end
    while (/\s/.test(source[index] || '')) {
      index++
    }
    if (source[index] !== ':') {
      return undefined
    }
    index++
    while (/\s/.test(source[index] || '')) {
      index++
    }
    if (candidate === key) {
      return index
    }
    const next = valueEnd(source, index)
    if (next === undefined) {
      return undefined
    }
    index = next
  }
  return undefined
}

export function readCompletedAiJsonField(source: string, key: string): unknown {
  const start = propertyStart(source, key)
  if (start === undefined) {
    return undefined
  }
  const end = valueEnd(source, start)
  if (end === undefined) {
    return undefined
  }
  try {
    return JSON.parse(source.slice(start, end))
  }
  catch {
    return undefined
  }
}

export function readCompletedAiJsonItems(source: string, key: string): unknown[] {
  const start = propertyStart(source, key)
  if (start === undefined || source[start] !== '[') {
    return []
  }
  const items: unknown[] = []
  let index = start + 1
  while (index < source.length) {
    while (/[\s,]/.test(source[index] || '')) {
      index++
    }
    if (source[index] === ']') {
      break
    }
    const end = valueEnd(source, index)
    if (end === undefined) {
      break
    }
    try {
      items.push(JSON.parse(source.slice(index, end)))
    }
    catch {
      break
    }
    index = end
  }
  return items
}
