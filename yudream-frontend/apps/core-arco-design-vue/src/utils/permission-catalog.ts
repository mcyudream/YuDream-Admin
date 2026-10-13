import type { PermissionItem } from '@/api/modules/system-role'

export type PermissionCategoryVariant = 'default' | 'secondary' | 'outline' | 'destructive'
export interface PermissionCategoryMeta {
  key: string
  label: string
  actions: readonly string[]
  variant: PermissionCategoryVariant
}

// 权限动作词表唯一前端入口；规范见 docs/system/permission-management.md。
export const permissionCategoryMeta: readonly PermissionCategoryMeta[] = [
  { key: 'view', label: '查看', actions: ['view', 'export', 'download'], variant: 'secondary' },
  { key: 'operate', label: '操作', actions: ['create', 'edit', 'import', 'upload', 'use', 'generate', 'publish', 'connect', 'send', 'test', 'run', 'invoke', 'execute', 'enable', 'disable', 'assign-*', 'dataset', 'report', 'accept'], variant: 'outline' },
  { key: 'manage', label: '管理', actions: ['manage', 'config'], variant: 'default' },
  { key: 'danger', label: '危险', actions: ['delete', 'kickout', 'revoke', 'impersonate'], variant: 'destructive' },
  { key: 'other', label: '其他', actions: [], variant: 'outline' },
]

const domains: Record<string, string> = { system: '系统管理', platform: '平台能力', plugin: '插件', mobile: '移动端' }
const resourceLabels: Record<string, string> = {
  'system:user': '用户管理',
  'system:role': '角色管理',
  'system:dept': '部门管理',
  'system:file': '文件管理',
  'system:menu': '菜单管理',
  'system:backup': '数据备份',
  'system:about': '关于系统',
  'system:command': '系统指令',
  'system:dashboard': '工作台',
  'system:runtime-log': '运行日志',
  'system:setting': '系统配置',
  'system:setting:theme': '后台主题配置',
  'system:security': '安全设置',
  'system:security:api-key': 'API Key',
  'system:security:external-login': '外部登录',
  'system:security:oauth': 'OAuth 应用',
  'system:security:passkey': 'Passkey',
  'system:monitor:agent-trace': 'Agent 执行流',
  'system:monitor:api-log': '接口日志',
  'system:monitor:login-log': '登录日志',
  'system:monitor:online': '在线用户',
  'system:monitor:redis': 'Redis 监控',
  'system:monitor:resource': '资源监控',
  'platform:agent': 'Agent 应用',
  'platform:agent:code': 'Agent 代码节点',
  'platform:agent:tool': 'Agent 工具',
  'platform:agent-skill': 'Agent 技能',
  'platform:ai': 'AI 生成',
  'platform:ai:tool': 'AI 工具',
  'platform:capability': '平台能力管理',
  'platform:chat': '聊天服务',
  'platform:chat:quota': '聊天配额',
  'platform:chat:session': '聊天会话',
  'platform:cms': '内容站点',
  'platform:theme-center': '主题中心',
  'platform:dataviz': '数据可视化',
  'platform:docs': 'API 文档',
  'platform:document': '文档生成',
  'platform:document:log': '文档生成日志',
  'platform:form': '表单管理',
  'platform:form:statistics': '表单统计',
  'platform:form:submission': '表单提交记录',
  'platform:graph': '知识图谱',
  'platform:graph:log': '图谱执行日志',
  'platform:inbound-mail': '收件服务',
  'platform:integration': 'HTTP 集成',
  'platform:integration:log': 'HTTP 集成日志',
  'platform:milky': 'QQ 消息平台',
  'platform:mobile-config': '移动端配置',
  'platform:plugin': '插件管理',
  'platform:plugin-devtools': '插件开发工具',
  'platform:plugin-market-source': '本机插件市场源',
  'platform:plugin-marketplace': '插件市场',
  'platform:render': '消息渲染',
  'platform:wiki': '知识库',
  'platform:wiki:tool': '知识库工具',
  'mobile:device': '移动设备',
}

// SystemMenuModule 的入口码与直属按钮实际权限归属；只归一展示资源，叶权限码继续独立保存。
const resourceAliases: Record<string, string> = {
  'system:agent-trace': 'system:monitor:agent-trace',
  'system:api-log': 'system:monitor:api-log',
  'system:login-log': 'system:monitor:login-log',
  'system:online-user': 'system:monitor:online',
  'system:redis-monitor': 'system:monitor:redis',
  'system:resource-monitor': 'system:monitor:resource',
  'system:theme': 'system:setting:theme',
}

export function permissionCategoryOf(code: string): PermissionCategoryMeta {
  const action = code.split(':').at(-1) || ''
  return permissionCategoryMeta.find(item => item.actions.includes(action) || (item.key === 'operate' && /^assign-[a-z0-9]+(?:-[a-z0-9]+)*$/.test(action)))!
    || permissionCategoryMeta[permissionCategoryMeta.length - 1]!
}

function permissionResourceIdentity(permission: Pick<PermissionItem, 'code'>) {
  const parts = permission.code.split(':')
  const valid = parts.length >= 3 && parts.every(part => /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(part))
    && (parts[0] !== 'plugin' || parts.length === 3)
  const domainKey = domains[parts[0]!] ? parts[0]! : 'other'
  // 原始导航码是独立授权叶子，但与同资源的动作码归在一起；插件历史嵌套码仍归属稳定插件 owner。
  const key = parts[0] === 'plugin' && parts[1]
    ? `plugin:${parts[1]}`
    : resourceAliases[permission.code]
      || (Object.hasOwn(resourceLabels, permission.code)
        ? permission.code
        : valid ? resourceAliases[parts.slice(0, -1).join(':')] || parts.slice(0, -1).join(':') : `legacy:${permission.code}`)
  return { key, domainKey, valid }
}

function permissionModuleLabel(module: string | undefined) {
  const label = module?.trim().replace(/\s+/g, ' ')
  return label && !Object.values(domains).includes(label) && label !== 'default' ? label : undefined
}

/** 按完整目录生成统一资源名，优先可用 owner 模块，再取短名和字典序；过滤或输入顺序不改变标签。 */
export function permissionResourceLabels(permissions: readonly Pick<PermissionItem, 'code' | 'module' | 'status'>[]): Map<string, string> {
  const candidates = new Map<string, { label: string, active: boolean, owner: number }[]>()
  const resourceDomains = new Map<string, string>()
  for (const permission of permissions) {
    const { key, domainKey } = permissionResourceIdentity(permission)
    resourceDomains.set(key, domainKey)
    if (!candidates.has(key)) {
      candidates.set(key, [])
    }
    const label = permissionModuleLabel(permission.module)
    if (label) {
      const owner = permission.code === key ? 2 : key.startsWith('plugin:') && permission.code.split(':').length === 3 ? 1 : 0
      candidates.get(key)!.push({ label, active: permission.status === 'ACTIVE', owner })
    }
  }
  const labels = new Map([...candidates].sort(([left], [right]) => left.localeCompare(right)).map(([key, names]) => {
    const preferred = [...names].sort((left, right) => Number(right.active) - Number(left.active)
      || right.owner - left.owner
      || left.label.length - right.label.length
      || left.label.localeCompare(right.label, 'zh-CN'))[0]?.label
    return [key, resourceLabels[key] || preferred || key]
  }))
  const namesByDomain = new Map<string, Map<string, string[]>>()
  for (const [key, label] of labels) {
    const domain = resourceDomains.get(key)!
    if (!namesByDomain.has(domain)) {
      namesByDomain.set(domain, new Map())
    }
    const names = namesByDomain.get(domain)!
    const keys = names.get(label) || []
    keys.push(key)
    names.set(label, keys)
  }
  for (const names of namesByDomain.values()) {
    for (const [label, keys] of names) {
      if (keys.length > 1) {
        keys.forEach(key => labels.set(key, `${label}（${key}）`))
      }
    }
  }
  return labels
}

export function permissionResourceOf(permission: Pick<PermissionItem, 'code' | 'module'>, labels?: ReadonlyMap<string, string>) {
  const { key, domainKey, valid } = permissionResourceIdentity(permission)
  return {
    key,
    label: labels?.get(key) || resourceLabels[key] || permissionModuleLabel(permission.module) || key,
    domainKey,
    domainLabel: domains[domainKey] || '其他来源',
    valid,
  }
}

export interface PermissionTreeNode {
  key: string
  title: string
  code?: string
  variant?: PermissionCategoryVariant
  children?: PermissionTreeNode[]
}

export function buildPermissionTree(permissions: PermissionItem[], keyword = ''): PermissionTreeNode[] {
  const query = keyword.trim().toLocaleLowerCase()
  const labels = permissionResourceLabels(permissions)
  const grouped = new Map<string, { label: string, resources: Map<string, { label: string, items: PermissionItem[] }> }>()
  for (const item of [...permissions].sort((a, b) => a.code.localeCompare(b.code))) {
    if (item.status !== 'ACTIVE') {
      continue
    }
    const resource = permissionResourceOf(item, labels)
    const category = permissionCategoryOf(item.code)
    if (query && ![item.name, item.code, item.module, item.desc, resource.label, resource.domainLabel, category.label].some(value => value?.toLocaleLowerCase().includes(query))) {
      continue
    }
    if (!grouped.has(resource.domainKey)) {
      grouped.set(resource.domainKey, { label: resource.domainLabel, resources: new Map() })
    }
    const group = grouped.get(resource.domainKey)!
    if (!group.resources.has(resource.key)) {
      group.resources.set(resource.key, { label: resource.label, items: [] })
    }
    group.resources.get(resource.key)!.items.push(item)
  }
  const order = ['system', 'platform', 'plugin', 'mobile', 'other']
  return [...grouped].sort(([a], [b]) => order.indexOf(a) - order.indexOf(b)).map(([domain, group]) => ({
    key: `domain:${domain}`,
    title: `${group.label}（${[...group.resources.values()].reduce((total, resource) => total + resource.items.length, 0)}）`,
    children: [...group.resources].map(([key, resource]) => ({
      key: `resource:${key}`,
      title: `${resource.label}（${resource.items.length}）`,
      children: permissionCategoryMeta.map((category) => {
        const items = resource.items.filter(item => permissionCategoryOf(item.code).key === category.key)
        return {
          key: `category:${key}:${category.key}`,
          title: `${category.label}（${items.length}）`,
          variant: category.variant,
          children: items.map(item => ({ key: item.code, title: item.name, code: item.code })),
        }
      }).filter(category => category.children.length > 0),
    })),
  }))
}

export function permissionTreeKeys(nodes: PermissionTreeNode[], leaves = false): string[] {
  return nodes.flatMap(node => [
    ...((leaves ? !!node.code : !node.code) ? [node.key] : []),
    ...permissionTreeKeys(node.children || [], leaves),
  ])
}

export function mergeVisiblePermissions(previous: string[], checked: (string | number)[], visible: Set<string>): string[] {
  return [...new Set([
    ...previous.filter(code => !visible.has(code)),
    ...checked.filter((code): code is string => typeof code === 'string' && visible.has(code)),
  ])]
}
