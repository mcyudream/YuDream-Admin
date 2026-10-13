import { getCurrentScope, onScopeDispose } from 'vue'

export interface YdFilePickerOptions {
  accept?: string
  multiple?: boolean
  directory?: boolean
}

/** Imperative picker for existing import actions; open must run in a user gesture. */
export function useYdFilePicker(defaults: YdFilePickerOptions = {}) {
  let pending: (() => void) | undefined
  let disposed = false
  function cancel() {
    pending?.()
  }
  function dispose() {
    disposed = true
    cancel()
  }
  function open(options: YdFilePickerOptions = {}): Promise<File[]> {
    cancel()
    if (disposed || typeof document === 'undefined') {
      return Promise.resolve([])
    }
    return new Promise((resolve, reject) => {
      const input = document.createElement('input')
      input.type = 'file'
      input.accept = options.accept ?? defaults.accept ?? ''
      input.multiple = options.multiple ?? defaults.multiple ?? false
      if (options.directory ?? defaults.directory) {
        input.setAttribute('webkitdirectory', '')
      }
      input.hidden = true
      const finish = (files: File[]) => {
        input.removeEventListener('change', change)
        input.removeEventListener('cancel', cancelled)
        input.remove()
        pending = undefined
        resolve(files)
      }
      function change() {
        finish([...input.files ?? []])
      }
      function cancelled() {
        finish([])
      }
      pending = cancelled
      input.addEventListener('change', change, { once: true })
      input.addEventListener('cancel', cancelled, { once: true })
      document.body.appendChild(input)
      try {
        input.click()
      }
      catch (error) {
        input.removeEventListener('change', change)
        input.removeEventListener('cancel', cancelled)
        input.remove()
        pending = undefined
        reject(error)
      }
    })
  }
  if (getCurrentScope()) {
    onScopeDispose(dispose)
  }
  return { open, cancel, dispose }
}
