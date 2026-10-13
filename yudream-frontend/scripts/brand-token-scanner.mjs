const BRAND_TOKEN = /--primary-\d/g
const SITE_SCOPE_CLASSES = new Set([
  'site-chrome', 'site-page', 'neco-chrome', 'login-page', 'wiki-public', 'wiki-home',
  'wiki-search-page', 'discover', 'plugin', 'public-form-page', 'tl-page', 'ev-page',
  'world-map-viewer', 'qb-screen', 'qb-shared', 'mc-wiki-public', 'dark',
])
const SITE_SCOPE_ANCHORS = new Set([...SITE_SCOPE_CLASSES].filter(name => name !== 'dark'))

function splitSelectorList(selector) {
  const result = []
  let start = 0
  let depth = 0
  let quote = ''
  for (let index = 0; index < selector.length; index++) {
    const char = selector[index]
    if (quote) {
      if (char === '\\') {
        index++
      }
      else if (char === quote) {
        quote = ''
      }
    }
    else if (char === '"' || char === '\'') {
      quote = char
    }
    else if (char === '(' || char === '[') {
      depth++
    }
    else if (char === ')' || char === ']') {
      depth = Math.max(0, depth - 1)
    }
    else if (char === ',' && depth === 0) {
      result.push(selector.slice(start, index).trim())
      start = index + 1
    }
  }
  result.push(selector.slice(start).trim())
  return result.filter(Boolean)
}

function isAllowedThemeSelector(selector, scope) {
  if (scope === 'ADMIN') {
    return splitSelectorList(selector).length > 0
      && splitSelectorList(selector).every(branch => branch === ':root' || branch === '.dark')
  }
  const branches = splitSelectorList(selector)
  return branches.length > 0 && branches.every((branch) => {
    if (/:(?:not|has)\s*\(|[+~]/i.test(branch)) {
      return false
    }
    let invalidGroup = false
    const target = branch.replace(/\[[^\]]*\]/g, '').replace(/:(?:is|where)\(([^()]*)\)/g, (_group, choices) => {
      if (!splitSelectorList(choices).every(choice => isAllowedThemeSelector(choice, 'SITE'))) {
        invalidGroup = true
      }
      return '.site-page'
    })
    if (invalidGroup || /:(?:is|where)\s*\(/i.test(target)) {
      return false
    }
    const classes = [...target.matchAll(/\.([_a-zA-Z][\w-]*)/g)].map(match => match[1])
    const targetClasses = [...target.matchAll(/\.([_a-zA-Z][\w-]*)/g)].map(match => match[1])
    return targetClasses.some(name => SITE_SCOPE_ANCHORS.has(name))
      && classes.every(name => SITE_SCOPE_CLASSES.has(name))
  })
}

function isSiteDeclarationBlock(css, start, end) {
  const declaration = css.slice(start, end)
  return /^\s*--primary-\d+\s*:/i.test(declaration)
}

/** Return declaration offsets permitted by the verified scope of a plugin theme CSS asset. */
export function findThemeBrandDeclarationOffsets(css, scope) {
  if (scope !== 'SITE' && scope !== 'ADMIN') {
    return new Set()
  }
  const offsets = new Set()
  const stack = []
  let segmentStart = 0
  let quote = ''
  let comment = false

  const recordDeclaration = (start, end, allowedScope) => {
    if (!allowedScope || !isSiteDeclarationBlock(css, start, end)) {
      return
    }
    const text = css.slice(start, end)
    const token = BRAND_TOKEN.exec(text)
    BRAND_TOKEN.lastIndex = 0
    if (token) {
      offsets.add(start + token.index)
    }
  }

  for (let index = 0; index < css.length; index++) {
    const char = css[index]
    const next = css[index + 1]
    if (comment) {
      if (char === '*' && next === '/') {
        comment = false
        index++
      }
      continue
    }
    if (quote) {
      if (char === '\\') {
        index++
      }
      else if (char === quote) {
        quote = ''
      }
      continue
    }
    if (char === '/' && next === '*') {
      comment = true
      index++
      continue
    }
    if (char === '"' || char === '\'') {
      quote = char
      continue
    }
    if (char === '{') {
      const prelude = css.slice(segmentStart, index).trim()
      const parentAllowed = stack.at(-1)?.allowed ?? true
      const allowed = parentAllowed && (prelude.startsWith('@') || isAllowedThemeSelector(prelude, scope))
      stack.push({ allowed })
      segmentStart = index + 1
    }
    else if (char === ';') {
      recordDeclaration(segmentStart, index, stack.at(-1)?.allowed === true)
      segmentStart = index + 1
    }
    else if (char === '}') {
      recordDeclaration(segmentStart, index, stack.at(-1)?.allowed === true)
      stack.pop()
      segmentStart = index + 1
    }
  }
  return offsets
}

/** Shared by host and plugin audits. Only exact metadata-derived offsets may be exempted. */
export function scanBrandTokenUsage(text, { allowedDeclarationOffsets = new Set() } = {}) {
  const findings = []
  for (const match of text.matchAll(BRAND_TOKEN)) {
    if (!allowedDeclarationOffsets.has(match.index)) {
      findings.push({ index: match.index, token: match[0] })
    }
  }
  return findings
}

/** Resolve the single source CSS entry that produces a theme's declared `style.css` asset. */
export function resolvePluginThemeCssSources({ pluginCode, javaSources, entrySource, viteConfigSource }) {
  const scopes = new Set()
  javaSources.forEach((source) => {
    for (const match of source.matchAll(/@PluginTheme\s*\(([^)]*)\)/g)) {
      const args = match[1]
      const code = args.match(/\bcode\s*=\s*"([^"]+)"/)?.[1]
      const declaredScopes = args.match(/\bscopes\s*=\s*\{([^}]*)\}/)?.[1] ?? ''
      const scopeNames = [...declaredScopes.matchAll(/PluginThemeScope\.(\w+)/g)].map(item => item[1])
      const styles = args.match(/\bstyles\s*=\s*\{([^}]*)\}/)?.[1] ?? ''
      if (code === pluginCode && scopeNames.length === 1 && ['SITE', 'ADMIN'].includes(scopeNames[0])
        && [...styles.matchAll(/"([^"]+)"/g)].some(item => item[1] === 'style.css')) {
        scopes.add(scopeNames[0])
      }
    }
  })
  // If metadata declares both scopes, this scanner cannot prove asset-to-scope isolation.
  if (scopes.size !== 1 || !/cssFileName\s*:\s*['"]style['"]/.test(viteConfigSource)) {
    return []
  }
  const paths = []
  for (const match of entrySource.matchAll(/^\s*import\s+['"]([^'"]+\/theme\.css|\.\/theme\.css)['"]\s*;?\s*$/gm)) {
    if (match[1].startsWith('./')) {
      paths.push(`src/${match[1].slice(2)}`)
    }
  }
  return [...new Set(paths)].map(path => ({ path, scope: [...scopes][0] }))
}
