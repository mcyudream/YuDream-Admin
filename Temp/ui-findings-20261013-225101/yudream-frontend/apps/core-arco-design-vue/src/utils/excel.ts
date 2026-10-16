export interface ExcelImportResult {
  total: number
  success: number
  failed: number
  errors: string[]
}

export interface ExcelBlobResponse {
  data: Blob
  headers: Record<string, string>
}

export function saveExcelResponse(response: ExcelBlobResponse, fallbackName: string) {
  const filename = resolveFilename(response.headers?.['content-disposition'], fallbackName)
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export function pickExcelFile(onPick: (file: File) => void) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = () => {
    const file = input.files?.[0]
    if (file) {
      onPick(file)
    }
  }
  input.click()
}

export function excelForm(file: File) {
  const form = new FormData()
  form.append('file', file)
  return form
}

export function importResultMessage(result: ExcelImportResult) {
  if (!result.failed) {
    return `共 ${result.total} 行，成功 ${result.success} 行`
  }
  return `共 ${result.total} 行，成功 ${result.success} 行，失败 ${result.failed} 行：${result.errors.slice(0, 3).join('；')}`
}

function resolveFilename(disposition: string | undefined, fallbackName: string) {
  if (!disposition) {
    return fallbackName
  }
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }
  const match = disposition.match(/filename="?([^";]+)"?/i)
  return match?.[1] ? decodeURIComponent(match[1]) : fallbackName
}
