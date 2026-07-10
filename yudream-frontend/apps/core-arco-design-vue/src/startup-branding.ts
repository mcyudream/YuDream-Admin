interface StartupBrandingDocument {
  title: string
  querySelector: (selector: string) => { textContent: string | null } | null
}

export async function initializeStartupBranding(
  loadSiteSettings: () => Promise<unknown>,
  applyBranding: () => void,
  timeoutMs = 1500,
) {
  let timeoutId: ReturnType<typeof setTimeout> | undefined
  const timeout = new Promise<void>((resolve) => {
    timeoutId = setTimeout(resolve, timeoutMs)
  })
  const loading = Promise.resolve()
    .then(loadSiteSettings)
    .catch(() => undefined)

  try {
    await Promise.race([loading, timeout])
  }
  finally {
    if (timeoutId) {
      clearTimeout(timeoutId)
    }
  }
  applyBranding()
}

export function applyStartupBranding(document: StartupBrandingDocument, siteName: string) {
  document.title = siteName
  const loadingName = document.querySelector('.loading-container .name')
  if (loadingName) {
    loadingName.textContent = siteName
  }
}
