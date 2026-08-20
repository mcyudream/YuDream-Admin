<script setup lang="ts">
import type {
  EndpointTestResult,
  PluginCommandAsset,
  PluginCommandTestResult,
  PluginDevPlugin,
  PluginHttpEndpointAsset,
  PluginRuntimeAssets,
} from '@/api/modules/platform-devtools'
import apiDevtools from '@/api/modules/platform-devtools'
import AssetSection from './AssetSection.vue'

const toast = useFaToast()

const plugins = ref<PluginDevPlugin[]>([])
const pluginsLoading = ref(false)
const selectedCode = ref<string>()

const assets = ref<PluginRuntimeAssets | null>(null)
const assetsLoading = ref(false)

const selectedPlugin = computed(() => plugins.value.find(item => item.code === selectedCode.value))

onMounted(loadPlugins)

async function loadPlugins() {
  pluginsLoading.value = true
  try {
    const res = await apiDevtools.plugins()
    plugins.value = res.data || []
  }
  finally {
    pluginsLoading.value = false
  }
}

function openPlugin(code: string) {
  selectedCode.value = code
}

function backToList() {
  selectedCode.value = undefined
  assets.value = null
}

watch(selectedCode, async (code) => {
  assets.value = null
  if (!code) {
    return
  }
  assetsLoading.value = true
  try {
    const res = await apiDevtools.assets(code)
    assets.value = res.data
  }
  finally {
    assetsLoading.value = false
  }
})

function statusVariant(plugin: PluginDevPlugin) {
  if (plugin.status === 'ERROR') {
    return 'destructive' as const
  }
  if (plugin.status === 'ENABLED') {
    return 'default' as const
  }
  return 'secondary' as const
}

const reloading = ref(false)
async function reloadPlugin(code?: string) {
  const target = code || selectedCode.value
  if (!target) {
    return
  }
  reloading.value = true
  try {
    await apiDevtools.reload(target)
    toast.success('重载指令已提交')
  }
  catch {
    // 拦截器已提示
  }
  finally {
    reloading.value = false
  }
}

// ---------- 端点测试器 ----------
const endpointTesterOpen = ref(false)
const endpointTarget = ref<PluginHttpEndpointAsset | null>(null)
const endpointPathParams = ref<Record<string, string>>({})
const endpointQuery = ref('')
const endpointBody = ref('')
const endpointTesting = ref(false)
const endpointResult = ref<EndpointTestResult | null>(null)

function openEndpointTester(endpoint: PluginHttpEndpointAsset) {
  endpointTarget.value = endpoint
  endpointPathParams.value = {}
  for (const match of endpoint.fullPath.matchAll(/\{(\w+)\}/g)) {
    endpointPathParams.value[match[1]] = ''
  }
  endpointQuery.value = ''
  endpointBody.value = ''
  endpointResult.value = null
  endpointTesterOpen.value = true
}

const endpointResolvedPath = computed(() => {
  let path = endpointTarget.value?.fullPath || ''
  for (const [key, value] of Object.entries(endpointPathParams.value)) {
    path = path.replaceAll(`{${key}}`, encodeURIComponent(value || `{${key}}`))
  }
  const query = endpointQuery.value.trim()
  return query ? `${path}${path.includes('?') ? '&' : '?'}${query.replace(/^\?/, '')}` : path
})

const endpointMethodSupportsBody = computed(() => !['GET', 'HEAD'].includes((endpointTarget.value?.method || 'GET').toUpperCase()))

async function runEndpointTest() {
  if (!endpointTarget.value) {
    return
  }
  endpointTesting.value = true
  endpointResult.value = null
  try {
    endpointResult.value = await apiDevtools.testEndpoint(endpointTarget.value.method, endpointResolvedPath.value, endpointBody.value)
  }
  finally {
    endpointTesting.value = false
  }
}

function formatEndpointBody(body: string) {
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  }
  catch {
    return body
  }
}

// ---------- 指令模拟器 ----------
const commandTestOpen = ref(false)
const commandTarget = ref<PluginCommandAsset | null>(null)
const commandInput = ref('')
const commandArguments = ref('')
const commandContent = ref('')
const commandTesting = ref(false)
const commandResult = ref<PluginCommandTestResult | null>(null)

function openCommandTest(command: PluginCommandAsset) {
  commandTarget.value = command
  commandInput.value = command.command
  commandArguments.value = ''
  commandContent.value = ''
  commandResult.value = null
  commandTestOpen.value = true
}

async function runCommandTest() {
  if (!selectedCode.value || !commandInput.value.trim()) {
    return
  }
  commandTesting.value = true
  commandResult.value = null
  try {
    const res = await apiDevtools.commandTest(selectedCode.value, {
      command: commandInput.value.trim(),
      arguments: commandArguments.value.split(/\s+/).filter(Boolean),
      content: commandContent.value.trim() || undefined,
    })
    commandResult.value = res.data
  }
  catch {
    // 拦截器已提示
  }
  finally {
    commandTesting.value = false
  }
}
</script>

<template>
  <div class="plugins-panel">
    <!-- 插件清单 -->
    <template v-if="!selectedPlugin">
      <div class="plugins-toolbar">
        <span class="plugins-toolbar__title">已安装插件</span>
        <FaTag variant="secondary" class="text-xs">
          {{ plugins.length }}
        </FaTag>
        <div class="flex-1" />
        <FaButton variant="outline" size="icon" :loading="pluginsLoading" title="刷新插件列表" @click="loadPlugins">
          <FaIcon name="i-ri:refresh-line" />
        </FaButton>
      </div>

      <div
        v-for="plugin in plugins"
        :key="plugin.code"
        class="plugin-row"
        @click="openPlugin(plugin.code)"
      >
        <div class="plugin-row__main">
          <div class="plugin-row__title">
            <span class="font-medium">{{ plugin.name }}</span>
            <FaTag :variant="statusVariant(plugin)" class="text-xs">
              {{ plugin.status }}
            </FaTag>
            <FaTag v-if="plugin.devMode" class="text-xs">
              开发模式
            </FaTag>
            <FaTag v-if="plugin.devMode && plugin.devProject" variant="outline" class="text-xs">
              {{ plugin.devProject.source === 'CONFIG' ? '配置文件' : '面板登记' }}
            </FaTag>
          </div>
          <div class="plugin-row__sub">
            {{ plugin.code }}<span v-if="plugin.version"> · v{{ plugin.version }}</span>
          </div>
        </div>
        <FaIcon name="i-ri:arrow-right-s-line" class="text-secondary-foreground/60 shrink-0 size-4" />
      </div>
      <div v-if="!plugins.length && !pluginsLoading" class="panel-empty">
        暂无已安装插件
      </div>
    </template>

    <!-- 插件详情 -->
    <template v-else>
      <div class="plugins-toolbar">
        <FaButton variant="ghost" size="sm" @click="backToList">
          <FaIcon name="i-ri:arrow-left-s-line" />
          插件清单
        </FaButton>
        <div class="flex-1" />
        <FaTooltip text="对开发模式插件执行 disable→unload→load→enable 重载" side="bottom">
          <FaButton
            variant="outline"
            size="sm"
            :disabled="!selectedPlugin.devMode"
            :loading="reloading"
            @click="reloadPlugin()"
          >
            <FaIcon name="i-ri:restart-line" />
            重载
          </FaButton>
        </FaTooltip>
      </div>

      <div class="plugin-brief">
        <div class="plugin-brief__title">
          <span class="font-medium">{{ selectedPlugin.name }}</span>
          <FaTag :variant="statusVariant(selectedPlugin)" class="text-xs">
            {{ selectedPlugin.status }}
          </FaTag>
          <FaTag v-if="selectedPlugin.devMode" class="text-xs">
            开发模式
          </FaTag>
        </div>
        <div v-if="selectedPlugin.devProject" class="plugin-brief__path">
          {{ selectedPlugin.devProject.path }}
        </div>
      </div>

      <div v-if="assetsLoading" class="panel-empty">
        正在加载运行时资产…
      </div>
      <template v-else-if="assets">
        <AssetSection title="HTTP 端点" icon="i-ri:global-line" :count="assets.httpEndpoints.length" default-open>
          <div v-for="endpoint in assets.httpEndpoints" :key="`${endpoint.method}:${endpoint.fullPath}`" class="asset-row">
            <FaTag variant="outline" class="method-tag text-xs">
              {{ endpoint.method }}
            </FaTag>
            <span class="asset-row__main text-xs font-mono">{{ endpoint.fullPath }}</span>
            <FaButton variant="ghost" size="sm" class="shrink-0" @click="openEndpointTester(endpoint)">
              试用
            </FaButton>
          </div>
          <div v-if="!assets.httpEndpoints.length" class="asset-empty">
            该插件未注册 HTTP 端点
          </div>
        </AssetSection>

        <AssetSection title="QQ 指令" icon="i-ri:terminal-box-line" :count="assets.commands.length" default-open>
          <div v-for="command in assets.commands" :key="command.code" class="asset-row">
            <span class="text-xs font-mono">/{{ command.command }}</span>
            <span class="asset-row__main text-xs">{{ command.name }}{{ command.description ? `：${command.description}` : '' }}</span>
            <FaButton variant="ghost" size="sm" class="shrink-0" @click="openCommandTest(command)">
              模拟触发
            </FaButton>
          </div>
          <div v-if="!assets.commands.length" class="asset-empty">
            该插件未注册指令
          </div>
        </AssetSection>

        <AssetSection title="前端模块与路由" icon="i-ri:window-line" :count="assets.frontendModules.length">
          <div v-for="module in assets.frontendModules" :key="module.pluginCode" class="frontend-module">
            <div class="asset-row">
              <FaTag variant="outline" class="text-xs">
                {{ module.menuTitle || module.moduleName || '未命名模块' }}
              </FaTag>
              <span class="asset-row__main text-xs font-mono">{{ module.entry }}</span>
            </div>
            <div v-for="route in module.routes" :key="route.path" class="asset-row asset-row--sub">
              <FaIcon v-if="route.icon" :name="route.icon" class="size-3.5" />
              <span class="text-xs">{{ route.title || route.name }}</span>
              <span class="asset-row__main text-xs text-secondary-foreground/60 font-mono">{{ route.path }}</span>
            </div>
          </div>
          <div v-if="!assets.frontendModules.length" class="asset-empty">
            无前端模块
          </div>
        </AssetSection>

        <AssetSection title="权限" icon="i-ri:shield-keyhole-line" :count="assets.permissions.length">
          <div v-for="permission in assets.permissions" :key="permission.code" class="asset-row">
            <span class="text-xs font-mono">{{ permission.code }}</span>
            <span class="asset-row__main text-xs">{{ permission.name }}</span>
          </div>
          <div v-if="!assets.permissions.length" class="asset-empty">
            无注册权限
          </div>
        </AssetSection>

        <AssetSection title="菜单" icon="i-ri:menu-line" :count="assets.menus.length">
          <div v-for="menu in assets.menus" :key="menu.path" class="asset-row">
            <FaIcon v-if="menu.icon" :name="menu.icon" class="size-3.5" />
            <span class="text-xs">{{ menu.title }}</span>
            <span class="asset-row__main text-xs text-secondary-foreground/60 font-mono">{{ menu.path }}</span>
            <FaTag v-if="menu.permission" variant="secondary" class="text-xs shrink-0">
              {{ menu.permission }}
            </FaTag>
          </div>
          <div v-if="!assets.menus.length" class="asset-empty">
            无注册菜单
          </div>
        </AssetSection>

        <AssetSection title="AI 工具" icon="i-ri:tools-line" :count="assets.aiTools.length">
          <div v-for="tool in assets.aiTools" :key="tool.name" class="asset-row">
            <span class="text-xs font-mono">{{ tool.name }}</span>
            <span class="asset-row__main text-xs">{{ tool.title || tool.description }}</span>
            <FaTag v-if="tool.risk" :variant="tool.risk === 'HIGH' ? 'destructive' : 'secondary'" class="text-xs shrink-0">
              {{ tool.risk }}
            </FaTag>
          </div>
          <div v-if="!assets.aiTools.length" class="asset-empty">
            无 AI 工具
          </div>
        </AssetSection>

        <AssetSection title="声明式 Agent" icon="i-ri:robot-2-line" :count="assets.agents.length">
          <div v-for="agent in assets.agents" :key="agent.id" class="asset-row">
            <FaIcon v-if="agent.icon" :name="agent.icon" class="size-3.5" />
            <span class="text-xs">{{ agent.name }}</span>
            <span class="asset-row__main text-xs text-secondary-foreground/60 font-mono">{{ agent.code }}</span>
            <FaTag v-if="agent.status" variant="secondary" class="text-xs shrink-0">
              {{ agent.status }}
            </FaTag>
          </div>
          <div v-if="!assets.agents.length" class="asset-empty">
            无声明式 Agent
          </div>
        </AssetSection>

        <AssetSection title="平台能力" icon="i-ri:apps-2-line" :count="assets.capabilities.length">
          <div v-for="capability in assets.capabilities" :key="capability.code" class="asset-row">
            <span class="text-xs">{{ capability.name }}</span>
            <span class="asset-row__main text-xs text-secondary-foreground/60 font-mono">{{ capability.code }}</span>
            <FaTag v-if="capability.type" variant="secondary" class="text-xs shrink-0">
              {{ capability.type }}
            </FaTag>
          </div>
          <div v-if="!assets.capabilities.length" class="asset-empty">
            无平台能力
          </div>
        </AssetSection>

        <AssetSection title="消息交互" icon="i-ri:message-3-line" :count="assets.messageInteractions.length">
          <div v-for="(interaction, index) in assets.messageInteractions" :key="index" class="asset-row">
            <FaTag variant="outline" class="text-xs">
              {{ interaction.kind }}
            </FaTag>
            <span class="asset-row__main text-xs">{{ interaction.eventTypes.join('、') }}</span>
            <span v-if="interaction.command" class="text-xs font-mono shrink-0">/{{ interaction.command }}</span>
          </div>
          <div v-if="!assets.messageInteractions.length" class="asset-empty">
            无消息交互
          </div>
        </AssetSection>

        <AssetSection title="首页卡片" icon="i-ri:dashboard-line" :count="assets.dashboardCards.length">
          <div v-for="card in assets.dashboardCards" :key="card.code" class="asset-row">
            <span class="text-xs">{{ card.title }}</span>
            <span class="asset-row__main text-xs text-secondary-foreground/60">{{ card.description }}</span>
          </div>
          <div v-if="!assets.dashboardCards.length" class="asset-empty">
            无首页卡片
          </div>
        </AssetSection>

        <AssetSection title="服务导出" icon="i-ri:share-forward-line" :count="assets.exposedServices.length">
          <div v-for="service in assets.exposedServices" :key="service" class="asset-row">
            <span class="text-xs font-mono">{{ service }}</span>
          </div>
          <div v-if="!assets.exposedServices.length" class="asset-empty">
            未导出服务
          </div>
        </AssetSection>
      </template>
      <div v-else class="panel-empty">
        未能获取插件资产
      </div>
    </template>

    <!-- 端点测试器 -->
    <FaModal v-model="endpointTesterOpen" title="端点测试" :footer="false" :z-index="2200" content-class="sm:max-w-xl">
      <div v-if="endpointTarget" class="tester">
        <div class="tester__target">
          <FaTag variant="outline" class="text-xs">
            {{ endpointTarget.method }}
          </FaTag>
          <span class="text-xs font-mono break-all">{{ endpointResolvedPath }}</span>
        </div>

        <template v-if="Object.keys(endpointPathParams).length">
          <div v-for="(_, name) in endpointPathParams" :key="name" class="tester__field">
            <span class="tester__label">路径参数 {{ name }}</span>
            <FaInput v-model="endpointPathParams[name]" :placeholder="`{${name}}`" />
          </div>
        </template>

        <div class="tester__field">
          <span class="tester__label">查询字符串（可选，原样拼接）</span>
          <FaInput v-model="endpointQuery" placeholder="key=value&..." />
        </div>

        <div v-if="endpointMethodSupportsBody" class="tester__field">
          <span class="tester__label">请求体（JSON，可选）</span>
          <FaTextarea v-model="endpointBody" :rows="5" placeholder="{ }" class="text-xs font-mono" />
        </div>

        <div class="tester__actions">
          <FaButton :loading="endpointTesting" @click="runEndpointTest">
            <FaIcon name="i-ri:send-plane-line" />
            发送请求
          </FaButton>
        </div>

        <div v-if="endpointResult" class="tester__result">
          <div class="tester__result-meta">
            <FaTag :variant="endpointResult.ok ? 'default' : 'destructive'" class="text-xs">
              HTTP {{ endpointResult.status }} {{ endpointResult.statusText }}
            </FaTag>
            <span class="text-xs text-secondary-foreground/60">{{ endpointResult.durationMs }}ms</span>
          </div>
          <FaScrollArea class="tester__result-body">
            <pre>{{ formatEndpointBody(endpointResult.body) }}</pre>
          </FaScrollArea>
        </div>
      </div>
    </FaModal>

    <!-- 指令模拟器 -->
    <FaModal v-model="commandTestOpen" title="模拟触发指令" :footer="false" :z-index="2200" content-class="sm:max-w-xl">
      <div v-if="commandTarget" class="tester">
        <div class="tester__target">
          <FaTag variant="outline" class="text-xs">
            {{ commandTarget.pluginCode }}
          </FaTag>
          <span class="text-xs text-secondary-foreground/70">{{ commandTarget.name }}</span>
        </div>

        <div class="tester__field">
          <span class="tester__label">指令名</span>
          <FaInput v-model="commandInput" placeholder="指令名（不带 /）" />
        </div>
        <div class="tester__field">
          <span class="tester__label">参数（空格分隔）</span>
          <FaInput v-model="commandArguments" placeholder="arg1 arg2 ..." />
        </div>
        <div class="tester__field">
          <span class="tester__label">消息原文（可选，缺省按 /指令 参数 构造）</span>
          <FaTextarea v-model="commandContent" :rows="3" />
        </div>

        <div class="tester__actions">
          <FaButton :loading="commandTesting" :disabled="!commandInput.trim()" @click="runCommandTest">
            <FaIcon name="i-ri:play-line" />
            触发
          </FaButton>
          <span class="text-xs text-secondary-foreground/60">在插件作用域内同步调用处理器，绕过权限与匿名检查</span>
        </div>

        <div v-if="commandResult" class="tester__result">
          <div class="tester__result-meta">
            <FaTag :variant="commandResult.success ? 'default' : 'destructive'" class="text-xs">
              {{ commandResult.success ? '执行成功' : '执行失败' }}
            </FaTag>
            <FaTag v-if="!commandResult.matched" variant="secondary" class="text-xs">
              未匹配到处理器
            </FaTag>
            <span class="text-xs text-secondary-foreground/60">{{ commandResult.durationMs ?? 0 }}ms</span>
          </div>
          <div v-if="commandResult.errorMessage" class="tester__result-body tester__result-body--error">
            <pre>{{ commandResult.errorMessage }}</pre>
          </div>
        </div>
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.plugins-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.plugins-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.plugins-toolbar__title {
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 600;
}

.plugin-row {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 10px 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  cursor: pointer;
}

.plugin-row:hover {
  background: var(--color-fill-1, var(--color-bg-3));
}

.plugin-row__main {
  flex: 1;
  min-width: 0;
}

.plugin-row__title {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
  color: var(--color-text-1);
  font-size: 13px;
}

.plugin-row__sub {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 12px;
  font-family: monospace;
}

.plugin-brief {
  padding: 12px 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1, var(--color-bg-3));
}

.plugin-brief__title {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
}

.plugin-brief__path {
  margin-top: 6px;
  color: var(--color-text-3);
  font-size: 12px;
  font-family: monospace;
  overflow-wrap: anywhere;
}

.asset-row {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 6px 2px;
  color: var(--color-text-2);
}

.asset-row--sub {
  padding-left: 18px;
}

.asset-row__main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.method-tag {
  min-width: 52px;
  justify-content: center;
  font-family: monospace;
}

.asset-empty,
.panel-empty {
  padding: 12px 0;
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
}

.panel-empty {
  padding: 40px 0;
  font-size: 13px;
}

.frontend-module {
  padding: 2px 0 6px;
  border-bottom: 1px dashed var(--color-border-2);
}

.frontend-module:first-child {
  padding-top: 0;
}

.frontend-module:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.tester {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tester__target {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--color-fill-2);
}

.tester__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tester__label {
  color: var(--color-text-3);
  font-size: 12px;
}

.tester__actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.tester__result {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tester__result-meta {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tester__result-body {
  max-height: 320px;
  padding: 8px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-3);
}

.tester__result-body pre {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.tester__result-body--error {
  color: var(--color-danger-5, var(--color-danger, #f53f3f));
}
</style>
