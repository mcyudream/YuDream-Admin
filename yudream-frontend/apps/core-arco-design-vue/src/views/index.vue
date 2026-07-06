<script setup lang="ts">
import DashboardChartStatsCard from './dashboard/DashboardChartStatsCard.vue'
import DashboardFlowGraphCard from './dashboard/DashboardFlowGraphCard.vue'

type DashboardKey = 'administrator' | 'people' | 'content' | 'platform' | 'monitor' | 'personal'

interface DashboardMetric {
  label: string
  value: string | (() => string)
  description: string
  icon: string
  tone: 'blue' | 'green' | 'amber' | 'rose'
}

interface DashboardAction {
  title: string
  description: string
  icon: string
  path: string
  permission?: string
  status?: string
}

interface DashboardTemplate {
  key: DashboardKey
  title: string
  subtitle: string
  icon: string
  accent: string
  metrics: DashboardMetric[]
  primaryActions: DashboardAction[]
  focus: string[]
  placeholders: DashboardAction[]
}

const router = useRouter()
const accountStore = useAppAccountStore()

const permissions = computed(() => accountStore.permissions || [])
const currentRole = computed(() => accountStore.currentRole)
const currentDept = computed(() => accountStore.currentDept)
const isSuperAdmin = computed(() => currentRole.value?.code === 'super_admin' || permissions.value.includes('*'))
const permissionCount = computed(() => isSuperAdmin.value ? '全部' : String(permissions.value.length))
const todayText = computed(() => new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date()))

const dashboardTemplates: Record<DashboardKey, DashboardTemplate> = {
  administrator: {
    key: 'administrator',
    title: '系统总控台',
    subtitle: '面向超级管理员和系统管理员，聚合人员、权限、安全、平台能力与运行状态。',
    icon: 'dashboard',
    accent: 'admin',
    metrics: [
      { label: '权限范围', value: () => permissionCount.value, description: '当前角色可访问的权限集合', icon: 'verified_user', tone: 'blue' },
      { label: '治理模块', value: '4 项', description: '用户、角色、部门、菜单', icon: 'manage_accounts', tone: 'green' },
      { label: '监控入口', value: '4 处', description: '缓存、接口、登录、在线用户', icon: 'monitoring', tone: 'amber' },
      { label: '待接入统计', value: '6 类', description: '后续可接真实趋势与告警', icon: 'bar_chart', tone: 'rose' },
    ],
    primaryActions: [
      { title: '用户管理', description: '维护账号、多部门、角色与伪装访问', icon: 'group', path: '/system/user', permission: 'system:user:view', status: '人员' },
      { title: '角色管理', description: '配置部门下角色与权限范围', icon: 'supervisor_account', path: '/system/role', permission: 'system:role:view', status: '权限' },
      { title: '安全中心', description: '管理 API 加密、Passkey、OAuth 与 API Key', icon: 'admin_panel_settings', path: '/system/security', permission: 'system:security:view', status: '安全' },
      { title: '平台能力', description: '启停 SSE、WS、MQ、CMS、AI 等可选能力', icon: 'account_tree', path: '/platform/capability', permission: 'platform:capability:view', status: '能力' },
    ],
    focus: ['检查新建角色是否绑定正确部门', '确认高权限账号的 API Key 使用范围', '关注接口异常与登录失败趋势'],
    placeholders: [
      { title: '系统设置', description: '站点名称、Logo 与基础信息配置', icon: 'settings', path: '/system/setting', status: '配置' },
      { title: '菜单管理', description: '维护后台路由、按钮权限与菜单图标', icon: 'menu_open', path: '/system/menu', permission: 'system:menu:view', status: '导航' },
    ],
  },
  people: {
    key: 'people',
    title: '组织人员工作台',
    subtitle: '聚焦成员入离、部门边界、角色授权与日常账号治理。',
    icon: 'manage_accounts',
    accent: 'people',
    metrics: [
      { label: '成员入口', value: () => hasAnyPermission(['system:user:view']) ? '可用' : '受限', description: '用户生命周期管理', icon: 'group', tone: 'blue' },
      { label: '部门边界', value: () => hasAnyPermission(['system:dept:view']) ? '可用' : '占位', description: '多部门组织结构', icon: 'business', tone: 'green' },
      { label: '角色授权', value: () => hasAnyPermission(['system:role:view']) ? '可用' : '占位', description: '按部门分配角色', icon: 'supervisor_account', tone: 'amber' },
      { label: '模拟访问', value: () => hasAnyPermission(['system:user:impersonate']) ? '已开放' : '未开放', description: '用于排查用户视角问题', icon: 'login', tone: 'rose' },
    ],
    primaryActions: [
      { title: '用户管理', description: '创建用户、维护资料、分配部门与角色', icon: 'group', path: '/system/user', permission: 'system:user:view', status: '核心' },
      { title: '部门管理', description: '维护组织树、默认部门与导入导出', icon: 'business', path: '/system/dept', permission: 'system:dept:view', status: '组织' },
      { title: '角色管理', description: '在部门范围内维护角色和权限', icon: 'supervisor_account', path: '/system/role', permission: 'system:role:view', status: '权限' },
    ],
    focus: ['新用户先确认默认部门', '跨部门账号需要检查角色来源', '敏感操作前建议使用伪装访问复核'],
    placeholders: [
      { title: '人员质量卡片', description: '后续接入未验证邮箱、长期未登录、无默认部门统计', icon: 'checklist', path: '/system/user', status: '占位' },
      { title: '组织变更流', description: '后续展示部门迁移与授权变更时间线', icon: 'receipt_long', path: '/system/dept', status: '占位' },
    ],
  },
  content: {
    key: 'content',
    title: '内容运营工作台',
    subtitle: '面向 CMS、动态表单、媒体资源和公开站点维护。',
    icon: 'dashboard_customize',
    accent: 'content',
    metrics: [
      { label: '内容页面', value: () => hasAnyPermission(['platform:cms:view']) ? '可维护' : '占位', description: '页面库与首页构建器', icon: 'article', tone: 'blue' },
      { label: '动态表单', value: () => hasAnyPermission(['platform:form:view']) ? '可维护' : '占位', description: '设计、发布、统计闭环', icon: 'dynamic_form', tone: 'green' },
      { label: '媒体资源', value: '待统计', description: 'RustFS/S3 资产库后续接入', icon: 'image', tone: 'amber' },
      { label: 'AI 辅助', value: () => hasAnyPermission(['platform:ai:generate']) ? '已接入' : '可扩展', description: '构建器智能编辑', icon: 'smart_toy', tone: 'rose' },
    ],
    primaryActions: [
      { title: '内容定制', description: '维护页面库、首页、导航、媒体和公开预览', icon: 'dashboard_customize', path: '/platform/cms', permission: 'platform:cms:view', status: 'CMS' },
      { title: '动态表单', description: '可视化设计表单、发布链接并查看结果统计', icon: 'dynamic_form', path: '/platform/form', permission: 'platform:form:view', status: '表单' },
      { title: '站点预览', description: '查看公开站点当前渲染状态', icon: 'open_in_new', path: '/site', status: '公开' },
    ],
    focus: ['发布前确认首页布局与导航链接', '表单发布后复制公开链接给业务方', '媒体资产建议补充命名和用途'],
    placeholders: [
      { title: '发布质量', description: '后续展示草稿数、未发布页面、表单转化率', icon: 'bar_chart', path: '/platform/cms', status: '占位' },
      { title: '素材池状态', description: '后续展示容量、最近上传、未引用文件', icon: 'inventory_2', path: '/platform/cms', status: '占位' },
    ],
  },
  platform: {
    key: 'platform',
    title: '平台能力工作台',
    subtitle: '面向动态能力、接口文档、集成调用、文档生成和图数据库。',
    icon: 'account_tree',
    accent: 'platform',
    metrics: [
      { label: '能力开关', value: () => hasAnyPermission(['platform:capability:view']) ? '可管理' : '受限', description: '按项目启停能力', icon: 'account_tree', tone: 'blue' },
      { label: '接口文档', value: () => hasAnyPermission(['platform:docs:view']) ? '可查看' : '占位', description: 'API 文档入口', icon: 'description', tone: 'green' },
      { label: '集成调用', value: () => hasAnyPermission(['platform:integration:view']) ? '可维护' : '占位', description: 'HTTP 与脚本运行记录', icon: 'terminal', tone: 'amber' },
      { label: '图数据库', value: () => hasAnyPermission(['platform:graph:view']) ? '可维护' : '占位', description: 'Neo4j 连接与查询', icon: 'hub', tone: 'rose' },
    ],
    primaryActions: [
      { title: '能力管理', description: '声明依赖、配置能力并按需启停', icon: 'account_tree', path: '/platform/capability', permission: 'platform:capability:view', status: '运行时' },
      { title: '集成调用', description: '配置 HTTP 连接器、脚本运行与调用日志', icon: 'terminal', path: '/platform/integration', permission: 'platform:integration:view', status: '集成' },
      { title: 'API 文档', description: '配置文档入口、安全策略与接口说明', icon: 'description', path: '/platform/api-doc', permission: 'platform:docs:view', status: '文档' },
      { title: '图数据库', description: '管理 Neo4j 连接、查询和查询日志', icon: 'hub', path: '/platform/graph', permission: 'platform:graph:view', status: '图谱' },
    ],
    focus: ['能力启用前先确认依赖能力已启用', '中间件连接只在使用时创建', '集成调用异常优先查看日志'],
    placeholders: [
      { title: '文档生成', description: 'Word 模板上传、生成和记录查询', icon: 'docs', path: '/platform/document', permission: 'platform:document:view', status: '模板' },
      { title: '插件扩展', description: '后续展示动态插件加载、前端模块和权限贡献', icon: 'extension', path: '/platform/capability', status: '占位' },
    ],
  },
  monitor: {
    key: 'monitor',
    title: '运行监控工作台',
    subtitle: '聚合 Redis、接口日志、登录日志与在线用户，适合运维和审计角色。',
    icon: 'monitoring',
    accent: 'monitor',
    metrics: [
      { label: '在线会话', value: () => hasAnyPermission(['system:monitor:online:view']) ? '可查看' : '受限', description: '会话列表和强制下线', icon: 'groups', tone: 'blue' },
      { label: '缓存监控', value: () => hasAnyPermission(['system:monitor:redis:view']) ? '可查看' : '占位', description: 'Redis key 与内存状态', icon: 'database', tone: 'green' },
      { label: '接口日志', value: () => hasAnyPermission(['system:monitor:api-log:view']) ? '可查看' : '占位', description: '请求、耗时、异常', icon: 'plagiarism', tone: 'amber' },
      { label: '登录日志', value: () => hasAnyPermission(['system:monitor:login-log:view']) ? '可查看' : '占位', description: '登录成功与失败记录', icon: 'login', tone: 'rose' },
    ],
    primaryActions: [
      { title: 'Redis 监控', description: '查看连接、内存、Key 样本和命中率', icon: 'database', path: '/system/redis-monitor', permission: 'system:monitor:redis:view', status: '缓存' },
      { title: '接口日志', description: '查询请求耗时、状态码、用户和异常', icon: 'plagiarism', path: '/system/api-log', permission: 'system:monitor:api-log:view', status: '接口' },
      { title: '登录日志', description: '审计账号登录成功、失败和客户端来源', icon: 'login', path: '/system/login-log', permission: 'system:monitor:login-log:view', status: '登录' },
      { title: '在线用户', description: '查看当前会话并按需强制下线', icon: 'groups', path: '/system/online-user', permission: 'system:monitor:online:view', status: '会话' },
    ],
    focus: ['接口日志支持一键清空，清空请求不会再次写入日志', '登录失败异常上升时优先检查安全策略', 'Redis Key 样本只用于排查，不建议暴露敏感命名'],
    placeholders: [
      { title: '告警中心', description: '后续接入慢接口、失败登录和缓存异常阈值', icon: 'warning', path: '/system/api-log', status: '占位' },
      { title: '审计日报', description: '后续生成登录、接口、在线会话日报', icon: 'receipt_long', path: '/system/login-log', status: '占位' },
    ],
  },
  personal: {
    key: 'personal',
    title: '个人工作台',
    subtitle: '展示当前身份、可用入口和后续待办。权限较少时也能有清晰的落点。',
    icon: 'home',
    accent: 'personal',
    metrics: [
      { label: '当前账号', value: () => accountStore.account || '-', description: '登录用户名', icon: 'person', tone: 'blue' },
      { label: '当前部门', value: () => currentDept.value?.name || '未选择', description: '请求会按当前部门上下文执行', icon: 'business', tone: 'green' },
      { label: '当前角色', value: () => currentRole.value?.name || '未选择', description: '菜单和权限来自当前角色', icon: 'supervisor_account', tone: 'amber' },
      { label: '权限数量', value: () => permissionCount.value, description: '当前可访问功能数', icon: 'verified_user', tone: 'rose' },
    ],
    primaryActions: [
      { title: '公开站点', description: '访问当前发布的站点首页', icon: 'open_in_new', path: '/site', status: '公开' },
      { title: '内容页面', description: '如果已授权，可进入内容定制工作区', icon: 'dashboard_customize', path: '/platform/cms', permission: 'platform:cms:view', status: '内容' },
      { title: '动态表单', description: '如果已授权，可进入表单设计和提交统计', icon: 'dynamic_form', path: '/platform/form', permission: 'platform:form:view', status: '表单' },
    ],
    focus: ['若菜单较少，请联系管理员确认角色权限', '切换部门或角色后首页会按新上下文重算', '个人资料、头像和 API Key 可从右上角账号菜单维护'],
    placeholders: [
      { title: '个人待办', description: '后续可接入审批、草稿、任务和消息提醒', icon: 'checklist', path: '/', status: '占位' },
      { title: '最近访问', description: '后续记录常用模块和最近操作入口', icon: 'history', path: '/', status: '占位' },
    ],
  },
}

const templateKey = computed<DashboardKey>(() => {
  const roleCode = currentRole.value?.code || ''
  const roleName = currentRole.value?.name || ''
  if (isSuperAdmin.value || roleCode === 'admin' || roleName.includes('管理员')) {
    return 'administrator'
  }
  if (hasAnyPermission(['system:monitor:api-log:view', 'system:monitor:login-log:view', 'system:monitor:online:view', 'system:monitor:redis:view'])) {
    return 'monitor'
  }
  if (hasAnyPermission(['platform:capability:view', 'platform:integration:view', 'platform:docs:view', 'platform:graph:view', 'platform:document:view'])) {
    return 'platform'
  }
  if (hasAnyPermission(['platform:cms:view', 'platform:form:view', 'platform:ai:generate'])) {
    return 'content'
  }
  if (hasAnyPermission(['system:user:view', 'system:dept:view', 'system:role:view'])) {
    return 'people'
  }
  return 'personal'
})

const dashboard = computed(() => dashboardTemplates[templateKey.value])
const visiblePrimaryActions = computed(() => dashboard.value.primaryActions)
const availableActionCount = computed(() => visiblePrimaryActions.value.filter(action => canOpen(action)).length)
const roleSummary = computed(() => currentRole.value ? `${currentRole.value.name} / ${currentRole.value.code}` : '未选择角色')
const deptSummary = computed(() => currentDept.value ? currentDept.value.name : '未选择部门')

function hasPermission(permission?: string) {
  if (!permission) {
    return true
  }
  return isSuperAdmin.value || permissions.value.includes(permission)
}

function hasAnyPermission(items: string[]) {
  return isSuperAdmin.value || items.some(item => permissions.value.includes(item))
}

function canOpen(action: DashboardAction) {
  return hasPermission(action.permission)
}

function metricValue(metric: DashboardMetric) {
  return typeof metric.value === 'function' ? metric.value() : metric.value
}

function openAction(action: DashboardAction) {
  if (!canOpen(action)) {
    useFaToast().warning('暂无权限', { description: `当前角色缺少 ${action.permission}` })
    return
  }
  router.push(action.path)
}
</script>

<template>
  <div class="dashboard-page">
    <div class="dashboard-shell" :class="`accent-${dashboard.accent}`">
      <header class="dashboard-hero">
        <div class="hero-main">
          <div class="hero-icon">
            <span class="material-symbols-outlined" aria-hidden="true">{{ dashboard.icon }}</span>
          </div>
          <div>
            <p class="hero-date">
              {{ todayText }}
            </p>
            <h1>{{ dashboard.title }}</h1>
            <p>{{ dashboard.subtitle }}</p>
          </div>
        </div>
        <div class="hero-context">
          <div>
            <span>账号</span>
            <strong>{{ accountStore.account || '-' }}</strong>
          </div>
          <div>
            <span>部门</span>
            <strong>{{ deptSummary }}</strong>
          </div>
          <div>
            <span>角色</span>
            <strong>{{ roleSummary }}</strong>
          </div>
          <div v-if="accountStore.isImpersonating" class="impersonating">
            <span>伪装访问</span>
            <strong>来自 {{ accountStore.impersonatorAccount }}</strong>
          </div>
        </div>
      </header>

      <section class="metric-grid">
        <article v-for="metric in dashboard.metrics" :key="metric.label" class="metric-card" :class="`tone-${metric.tone}`">
          <div class="metric-icon">
            <span class="material-symbols-outlined" aria-hidden="true">{{ metric.icon }}</span>
          </div>
          <div>
            <span>{{ metric.label }}</span>
            <strong>{{ metricValue(metric) }}</strong>
            <p>{{ metric.description }}</p>
          </div>
        </article>
      </section>

      <section class="dashboard-grid">
        <main class="work-panel">
          <div class="section-head">
            <div>
              <h2>核心工作区</h2>
              <p>根据当前角色和权限推荐最常用入口。</p>
            </div>
            <span>{{ availableActionCount }} / {{ visiblePrimaryActions.length }} 可用</span>
          </div>
          <div class="action-grid">
            <button
              v-for="action in visiblePrimaryActions"
              :key="action.title"
              type="button"
              class="action-card"
              :class="{ locked: !canOpen(action) }"
              @click="openAction(action)"
            >
              <span class="action-status">{{ canOpen(action) ? action.status : '无权限' }}</span>
              <span class="action-icon">
                <span class="material-symbols-outlined" aria-hidden="true">{{ action.icon }}</span>
              </span>
              <strong>{{ action.title }}</strong>
              <p>{{ action.description }}</p>
            </button>
          </div>
        </main>

        <aside class="side-panel">
          <div class="section-head compact">
            <div>
              <h2>今日关注</h2>
              <p>先用固定建议占位，后续可接真实任务。</p>
            </div>
          </div>
          <ol class="focus-list">
            <li v-for="item in dashboard.focus" :key="item">
              {{ item }}
            </li>
          </ol>
        </aside>
      </section>

      <section class="placeholder-panel">
        <div class="section-head">
          <div>
            <h2>预留模块</h2>
            <p>暂时没有实时数据的模块先以占位卡片呈现，避免首页空白。</p>
          </div>
          <span>权限驱动布局</span>
        </div>
        <div class="placeholder-grid">
          <button
            v-for="item in dashboard.placeholders"
            :key="item.title"
            type="button"
            class="placeholder-card"
            :class="{ locked: !canOpen(item) }"
            @click="openAction(item)"
          >
            <span class="action-icon">
              <span class="material-symbols-outlined" aria-hidden="true">{{ item.icon }}</span>
            </span>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
            </div>
            <span class="placeholder-status">{{ canOpen(item) ? item.status : '无权限' }}</span>
          </button>
        </div>
      </section>

      <section class="dataviz-panel">
        <div class="section-head">
          <div>
            <h2>数据可视化演示</h2>
            <p>通过 Dataviz 组件演示图表与关系图能力。</p>
          </div>
          <span>演示卡片</span>
        </div>
        <div class="dataviz-grid">
          <DashboardChartStatsCard :card="{ cardCode: 'dataviz-bar-capability', title: '能力分类统计', description: '按类型展示平台能力分布' }" />
          <DashboardFlowGraphCard :card="{ cardCode: 'dataviz-graph-demo', title: '关系图谱演示', description: '示例节点与关系网络' }" />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  position: absolute;
  inset: 0;
  overflow: auto;
  background: var(--color-bg-1);
}

.dashboard-shell {
  display: grid;
  gap: 16px;
  width: min(1440px, calc(100% - 32px));
  margin: 0 auto;
  padding: 20px 0 32px;
}

.dashboard-hero,
.metric-card,
.work-panel,
.side-panel,
.placeholder-panel,
.action-card,
.placeholder-card {
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
}

.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 420px);
  gap: 20px;
  padding: 22px;
  overflow: hidden;
  position: relative;
}

.dashboard-hero::before {
  position: absolute;
  inset: 0;
  content: "";
  pointer-events: none;
  background: linear-gradient(110deg, var(--accent-soft), transparent 42%);
}

.hero-main,
.hero-context {
  position: relative;
  z-index: 1;
}

.hero-main {
  display: flex;
  gap: 16px;
  align-items: center;
}

.hero-icon,
.metric-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 8px;
}

.hero-icon {
  width: 54px;
  height: 54px;
  background: var(--accent);
  color: white;
  font-size: 28px;
}

.material-symbols-outlined {
  overflow: hidden;
  font-size: 1em;
  font-variation-settings: "FILL" 0, "wght" 500, "GRAD" 0, "opsz" 24;
  line-height: 1;
}

.hero-date {
  margin: 0 0 6px;
  color: var(--color-text-3);
  font-size: 13px;
}

.dashboard-hero h1,
.section-head h2 {
  margin: 0;
  color: var(--color-text-1);
}

.dashboard-hero h1 {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.dashboard-hero p,
.section-head p,
.metric-card p,
.action-card p,
.placeholder-card p {
  margin: 0;
  color: var(--color-text-3);
}

.dashboard-hero h1 + p {
  margin-top: 8px;
  max-width: 760px;
  line-height: 1.7;
}

.hero-context {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.hero-context div {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--accent) 18%, var(--color-border-2));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-bg-2) 84%, white);
}

.hero-context span,
.metric-card > div > span,
.action-status,
.placeholder-status {
  color: var(--color-text-3);
  font-size: 12px;
}

.hero-context strong {
  overflow: hidden;
  color: var(--color-text-1);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero-context .impersonating {
  grid-column: 1 / -1;
  border-color: rgba(245, 63, 63, 0.28);
  background: rgba(245, 63, 63, 0.08);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  display: flex;
  gap: 12px;
  min-height: 118px;
  padding: 16px;
}

.metric-icon {
  width: 38px;
  height: 38px;
  font-size: 20px;
}

.metric-card strong {
  display: block;
  margin-top: 6px;
  color: var(--color-text-1);
  font-size: 24px;
  line-height: 1.2;
}

.metric-card p {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
}

.tone-blue .metric-icon {
  background: rgba(22, 93, 255, 0.12);
  color: #165dff;
}

.tone-green .metric-icon {
  background: rgba(0, 180, 42, 0.12);
  color: #00a870;
}

.tone-amber .metric-icon {
  background: rgba(247, 114, 52, 0.13);
  color: #d25f00;
}

.tone-rose .metric-icon {
  background: rgba(245, 63, 63, 0.12);
  color: #f53f3f;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
}

.work-panel,
.side-panel,
.placeholder-panel {
  padding: 18px;
}

.section-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-head h2 {
  font-size: 16px;
  font-weight: 700;
}

.section-head p {
  margin-top: 4px;
  font-size: 13px;
}

.section-head > span {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 12px;
}

.section-head.compact {
  margin-bottom: 10px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.action-card,
.placeholder-card {
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, background 0.2s ease;
}

.action-card {
  display: grid;
  gap: 10px;
  min-height: 150px;
  padding: 16px;
}

.action-card:hover,
.placeholder-card:hover {
  border-color: var(--accent);
  transform: translateY(-1px);
}

.action-icon {
  color: var(--accent);
  font-size: 24px;
}

.action-card strong,
.placeholder-card strong {
  color: var(--color-text-1);
  font-size: 15px;
}

.action-card p,
.placeholder-card p {
  font-size: 13px;
  line-height: 1.6;
}

.action-status {
  justify-self: start;
  padding: 3px 7px;
  border-radius: 999px;
  background: var(--color-fill-2);
}

.locked {
  cursor: not-allowed;
  opacity: 0.58;
}

.locked:hover {
  border-color: var(--color-border-2);
  transform: none;
}

.focus-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.focus-list li {
  position: relative;
  padding: 12px 12px 12px 32px;
  border-radius: 8px;
  background: var(--color-fill-1);
  color: var(--color-text-2);
  font-size: 13px;
  line-height: 1.6;
}

.focus-list li::before {
  position: absolute;
  top: 18px;
  left: 14px;
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--accent);
  content: "";
}

.placeholder-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.placeholder-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-height: 92px;
  padding: 14px;
}

.placeholder-status {
  padding: 3px 7px;
  border-radius: 999px;
  background: var(--color-fill-2);
}

.accent-admin {
  --accent: #165dff;
  --accent-soft: rgba(22, 93, 255, 0.12);
}

.accent-people {
  --accent: #00a870;
  --accent-soft: rgba(0, 180, 42, 0.12);
}

.accent-content {
  --accent: #7b61ff;
  --accent-soft: rgba(123, 97, 255, 0.12);
}

.accent-platform {
  --accent: #0e7490;
  --accent-soft: rgba(14, 116, 144, 0.12);
}

.accent-monitor {
  --accent: #d25f00;
  --accent-soft: rgba(247, 114, 52, 0.13);
}

.accent-personal {
  --accent: #475569;
  --accent-soft: rgba(71, 85, 105, 0.12);
}

.dataviz-panel {
  padding: 18px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
}

.dataviz-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .dataviz-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 780px) {
  .dashboard-shell {
    width: min(100% - 20px, 1440px);
    padding-top: 10px;
  }

  .dashboard-hero,
  .hero-context,
  .metric-grid,
  .action-grid,
  .placeholder-grid {
    grid-template-columns: 1fr;
  }

  .hero-main {
    align-items: flex-start;
  }

  .dashboard-hero {
    padding: 16px;
  }

  .dashboard-hero h1 {
    font-size: 24px;
  }

  .placeholder-card {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .placeholder-status {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
