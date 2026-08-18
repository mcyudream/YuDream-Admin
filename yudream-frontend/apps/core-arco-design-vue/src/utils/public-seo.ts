export interface PublicSeoInput {
  title: string
  description?: string
  canonicalPath?: string
  image?: string
  type?: 'article' | 'website'
  siteName?: string
  publishedAt?: string
  updatedAt?: string
  breadcrumbs?: Array<{ name: string, path: string }>
}

const SEO_MARKER = 'data-yudream-seo'
let previousTitle: string | null = null
const previousElements = new Map<Element, string | null>()

export function applyPublicSeo(input: PublicSeoInput) {
  if (previousTitle === null) previousTitle = document.title
  const siteName = input.siteName?.trim()
  const title = siteName && input.title !== siteName ? `${input.title} - ${siteName}` : input.title
  const description = compactDescription(input.description)
  const canonical = new URL(input.canonicalPath || window.location.pathname, window.location.origin).toString()
  const image = input.image ? new URL(input.image, window.location.origin).toString() : ''

  document.title = title
  setMeta('name', 'description', description)
  setMeta('name', 'robots', 'index,follow,max-image-preview:large')
  setMeta('property', 'og:title', title)
  setMeta('property', 'og:description', description)
  setMeta('property', 'og:type', input.type || 'website')
  setMeta('property', 'og:url', canonical)
  setMeta('property', 'og:site_name', siteName || input.title)
  setMeta('name', 'twitter:card', image ? 'summary_large_image' : 'summary')
  setMeta('name', 'twitter:title', title)
  setMeta('name', 'twitter:description', description)
  setOptionalMeta('property', 'og:image', image)
  setOptionalMeta('name', 'twitter:image', image)
  setOptionalMeta('property', 'article:published_time', input.publishedAt)
  setOptionalMeta('property', 'article:modified_time', input.updatedAt)
  setCanonical(canonical)
  setStructuredData(input, title, description, canonical, image)
}

export function clearPublicSeo() {
  document.head.querySelectorAll(`[${SEO_MARKER}]`).forEach(element => element.remove())
  previousElements.forEach((value, element) => {
    if (!element.isConnected) return
    if (element instanceof HTMLMetaElement) element.content = value || ''
    if (element instanceof HTMLLinkElement) element.href = value || ''
  })
  previousElements.clear()
  if (previousTitle !== null) document.title = previousTitle
  previousTitle = null
}

function compactDescription(value?: string) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, 180)
}

function setMeta(attribute: 'name' | 'property', key: string, content: string) {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attribute, key)
    element.setAttribute(SEO_MARKER, '')
    document.head.append(element)
  }
  else if (!previousElements.has(element)) {
    previousElements.set(element, element.content)
  }
  element.content = content
}

function setOptionalMeta(attribute: 'name' | 'property', key: string, content?: string) {
  const element = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)
  if (!content) {
    if (element?.hasAttribute(SEO_MARKER)) {
      element.remove()
    }
    else if (element && previousElements.has(element)) {
      element.content = previousElements.get(element) || ''
    }
    return
  }
  setMeta(attribute, key, content)
}

function setCanonical(url: string) {
  let element = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
  if (!element) {
    element = document.createElement('link')
    element.rel = 'canonical'
    element.setAttribute(SEO_MARKER, '')
    document.head.append(element)
  }
  else if (!previousElements.has(element)) {
    previousElements.set(element, element.href)
  }
  element.href = url
}

function setStructuredData(input: PublicSeoInput, title: string, description: string, canonical: string, image: string) {
  document.head.querySelectorAll(`script[type="application/ld+json"][${SEO_MARKER}]`).forEach(element => element.remove())
  const graph: Record<string, unknown>[] = [{
    '@type': input.type === 'article' ? 'Article' : 'WebPage',
    'headline': title,
    'description': description,
    'url': canonical,
    ...(image ? { image } : {}),
    ...(input.publishedAt ? { datePublished: input.publishedAt } : {}),
    ...(input.updatedAt ? { dateModified: input.updatedAt } : {}),
  }]
  if (input.breadcrumbs?.length) {
    graph.push({
      '@type': 'BreadcrumbList',
      'itemListElement': input.breadcrumbs.map((item, index) => ({
        '@type': 'ListItem',
        position: index + 1,
        name: item.name,
        item: new URL(item.path, window.location.origin).toString(),
      })),
    })
  }
  const script = document.createElement('script')
  script.type = 'application/ld+json'
  script.setAttribute(SEO_MARKER, '')
  script.textContent = JSON.stringify({ '@context': 'https://schema.org', '@graph': graph })
  document.head.append(script)
}
