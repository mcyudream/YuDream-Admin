import { babelParse, parse as parseSfc } from 'vue/compiler-sfc'

const NATIVE_TAGS = new Set(['button', 'input', 'select', 'textarea', 'option'])
const STRUCTURE_TAGS = new Set(['div', 'span', 'li', 'p', 'section', 'article'])
const EVENTS = new Set(['click', 'keydown', 'keyup', 'keypress'])
const FA_NAME = /^(?:Fa[A-Z]|useFa[A-Z])/
const YD_NAME = /^Yd[A-Z]/

function walk(node, visit, parent) {
  if (!node || typeof node !== 'object') {
    return
  }
  if (typeof node.type === 'string') {
    visit(node, parent)
  }
  for (const [key, child] of Object.entries(node)) {
    if (['parent', 'tokens', 'comments', 'loc', 'extra'].includes(key)) {
      continue
    }
    if (Array.isArray(child)) {
      child.forEach(item => walk(item, visit, node))
    }
    else if (child && typeof child === 'object' && typeof child.type === 'string') {
      walk(child, visit, node)
    }
  }
}

/** Parse scripts and the complete SFC template, including nested slots. */
export function scanUiSyntax(text, file, { publicNames, thirdPartySources, requireResponsiveTables = false }) {
  const findings = []
  const scripts = []
  const localComponents = new Set()
  const renderedFunctions = new Set(['h', 'createVNode', 'createElement'])
  const faNames = new Set()
  let template
  const add = (index, rule, message) => findings.push({ index, rule, message })
  const sourceIndex = (node, offset) => (node.start ?? 0) + offset
  const checkSource = (source, node, offset, typeOnly = false) => {
    if (thirdPartySources.some(prefix => source === prefix || source.startsWith(prefix))) {
      add(sourceIndex(node, offset), typeOnly ? 'yd-only-type-import' : 'yd-only', `业务代码禁止直接依赖通用 UI 包 ${source}；请使用 YD 公开组件及类型。`)
    }
  }
  const checkFa = (name, index) => {
    if (FA_NAME.test(name) && !faNames.has(name)) {
      faNames.add(name)
      add(index, 'yd-only', `禁止业务依赖 ${name}；请使用 @yudream/components 公开 YD 入口。`)
    }
  }
  const checkTag = (name, index, interactive = false) => {
    const tag = name.toLowerCase()
    checkFa(name, index)
    if (NATIVE_TAGS.has(tag)) {
      add(index, 'yd-only-native-control', `业务模板禁止原生通用交互 <${name}>；请使用 YD 组件。`)
    }
    if (requireResponsiveTables && tag === 'table') {
      add(index, 'yd-only-native-table', '插件管理表格必须使用 YdResponsiveTable 并提供卡片视图。')
    }
    if (/^a-[a-z]/i.test(name) || /^A[A-Z]/.test(name)) {
      add(index, 'yd-only-third-party-component', `业务模板禁止第三方通用控件 <${name}>；请使用 YD 组件。`)
    }
    if (YD_NAME.test(name) && !publicNames.has(name) && !localComponents.has(name)) {
      add(index, 'yd-only-unknown-entry', `${name} 不是已公开的 YD 组件；请补齐共享组件及公开契约。`)
    }
    if (interactive && STRUCTURE_TAGS.has(tag)) {
      add(index, 'yd-only-clickable-structure', `<${name}> 不得模拟可交互控件；请使用 YdAction/YdButton 等语义组件。`)
    }
  }

  try {
    if (file.endsWith('.vue')) {
      const result = parseSfc(text, { filename: file })
      if (result.errors.length) {
        throw result.errors[0]
      }
      template = result.descriptor.template?.ast
      for (const block of [result.descriptor.script, result.descriptor.scriptSetup]) {
        if (block) {
          scripts.push({ content: block.content, offset: block.loc.start.offset, jsx: block.lang === 'tsx' || block.lang === 'jsx' })
        }
      }
    }
    else {
      scripts.push({ content: text, offset: 0, jsx: /\.[jt]sx$/.test(file) })
    }
    for (const script of scripts) {
      script.ast = babelParse(script.content, { sourceType: 'unambiguous', plugins: ['typescript', ...(script.jsx ? ['jsx'] : [])] })
      walk(script.ast, (node) => {
        if (node.type === 'ImportDeclaration') {
          const source = node.source.value
          checkSource(source, node, script.offset, node.importKind === 'type')
          for (const spec of node.specifiers) {
            const imported = spec.imported?.name ?? spec.imported?.value ?? 'default'
            const local = spec.local.name
            if (node.importKind === 'type' || spec.importKind === 'type') {
              continue
            }
            checkFa(imported, sourceIndex(spec, script.offset))
            if (source === '@yudream/components' || source === '@yudream/components/ai') {
              if (YD_NAME.test(imported) && !publicNames.has(imported)) {
                add(sourceIndex(spec, script.offset), 'yd-only-unknown-entry', `${imported} 不是已公开的 YD 入口；请补齐共享组件公开契约。`)
              }
              localComponents.add(local)
            }
            else if (source.startsWith('.') || source.startsWith('@/') || source.startsWith('@yudream/')) {
              localComponents.add(local)
            }
            if (source === 'vue' && ['h', 'createVNode', 'createElementVNode'].includes(imported)) {
              renderedFunctions.add(local)
            }
          }
        }
        if (node.type === 'VariableDeclarator' && node.id.type === 'Identifier' && YD_NAME.test(node.id.name)) {
          localComponents.add(node.id.name)
        }
      })
    }
    for (const { ast, offset } of scripts) {
      walk(ast, (node, parent) => {
        if (node.type === 'Identifier' && !parent?.type?.startsWith('Import')) {
          checkFa(node.name, sourceIndex(node, offset))
        }
        if ((node.type === 'ExportNamedDeclaration' || node.type === 'ExportAllDeclaration') && node.source) {
          checkSource(node.source.value, node, offset, node.exportKind === 'type')
        }
        if (node.type === 'CallExpression') {
          if ((node.callee.type === 'Import' || node.callee.name === 'require') && node.arguments[0]?.type === 'StringLiteral') {
            checkSource(node.arguments[0].value, node, offset)
          }
          const renderName = node.callee.name ?? (node.callee.type === 'MemberExpression' ? node.callee.property.name : undefined)
          const tag = node.arguments[0]
          if (renderedFunctions.has(renderName) && tag?.type === 'StringLiteral') {
            if (NATIVE_TAGS.has(tag.value)) {
              add(sourceIndex(node, offset), 'yd-only-native-render', `render 函数禁止创建原生通用交互 <${tag.value}>；请使用 YD 组件。`)
            }
            else {
              const props = node.arguments[1]?.properties ?? []
              checkTag(tag.value, sourceIndex(node, offset), props.some(prop => /^on(?:Click|Keydown|Keyup|Keypress)$/.test(prop.key?.name ?? prop.key?.value ?? '')))
            }
          }
        }
        if (node.type === 'ImportExpression' && node.source.type === 'StringLiteral') {
          checkSource(node.source.value, node, offset)
        }
        if (node.type === 'JSXOpeningElement' && node.name.type === 'JSXIdentifier') {
          checkTag(node.name.name, sourceIndex(node, offset), node.attributes.some(attr => /^on(?:Click|Keydown|Keyup|Keypress)$/.test(attr.name?.name ?? '')))
        }
      })
    }
    const visitTemplate = (node) => {
      if (node.type === 1) {
        const events = node.props.filter(prop => prop.type === 7 && prop.name === 'on' && prop.exp?.content?.trim())
        // A rendered article can delegate link clicks without being a control itself.
        // Keyboard/role/tabindex handlers still indicate a simulated control.
        const delegatesArticleLinks = node.props.some(prop => prop.type === 7 && prop.name === 'html')
          && events.every(prop => prop.arg?.content === 'click')
          && !node.props.some(prop => (prop.type === 6 && ['role', 'tabindex'].includes(prop.name))
            || (prop.type === 7 && prop.name === 'bind' && ['role', 'tabindex'].includes(prop.arg?.content)))
        const navigatesDataGroup = node.props.some(prop => prop.type === 6 && prop.name === 'role' && prop.value?.content === 'group')
          && !events.some(prop => prop.arg?.content === 'click')
        checkTag(node.tag, node.loc.start.offset, !delegatesArticleLinks && !navigatesDataGroup && events.some(prop => EVENTS.has(prop.arg?.content)))
        if (node.tag.toLowerCase() === 'a' && events.some(prop => prop.arg?.content === 'click')
          && !node.props.some(prop => (prop.type === 6 && prop.name === 'href')
            || (prop.type === 7 && prop.name === 'bind' && prop.arg?.content === 'href'))) {
          add(node.loc.start.offset, 'yd-only-clickable-structure', '<a> 缺少 href 时不得模拟动作按钮；请使用 YdAction。')
        }
        if (requireResponsiveTables && node.tag === 'YdResponsiveTable'
          && !node.children?.some(child => child.type === 1 && child.props.some(prop => prop.type === 7 && prop.name === 'slot' && prop.arg?.content === 'card'))) {
          add(node.loc.start.offset, 'yd-only-table-card', 'YdResponsiveTable 管理视图必须提供 #card 插槽。')
        }
        for (const prop of node.props) {
          if (prop.type === 7 && prop.name === 'loading') {
            add(prop.loc.start.offset, 'yd-only-third-party-directive', '业务不使用第三方 v-loading；请使用 YdLoadingOverlay 表达受影响容器的加载状态。')
          }
          if (prop.type === 7 && prop.name === 'bind' && prop.arg?.content === 'is' && prop.exp?.content) {
            for (const match of prop.exp.content.matchAll(/\b(?:Fa[A-Z][A-Za-z0-9]*|useFa[A-Z][A-Za-z0-9]*)\b/g)) {
              checkFa(match[0], prop.exp.loc.start.offset + match.index)
            }
          }
        }
      }
      node.children?.forEach(visitTemplate)
    }
    if (template) {
      visitTemplate(template)
    }
  }
  catch (error) {
    add(error.pos ?? error.loc?.start?.offset ?? 0, 'yd-only-parse-error', `无法完成 UI 契约审计：${error.message ?? String(error)}`)
  }
  return findings.sort((left, right) => left.index - right.index)
}
