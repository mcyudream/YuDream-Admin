<script setup lang="ts">
import type { McpConnectionStatus, McpServer, McpServerPayload, McpTool, McpTransport } from '@/api/modules/platform-mcp'
import { computed, onMounted, reactive, ref } from 'vue'
import apiMcp, { MCP_HEADER_MASK } from '@/api/modules/platform-mcp'

const toast = useYdToast()
const modal = useYdModal()

/** 后端默认超时与最小可配值，保存前按此校验。 */
const DEFAULT_TIMEOUT_MILLIS = 10000
const MIN_TIMEOUT_MILLIS = 1000
const MAX_TIMEOUT_MILLIS = 120000
/** 服务编码参与工具模块编码（mcp:<编码>）与平台工具编码，限定为小写字母、数字、下划线与连字符。 */
const CODE_PATTERN = /^[a-z0-9_-]+$/
const HEADER_EXAMPLE = '{"Authorization": "Bearer <token>"}'

const transportOptions: { label: string, value: McpTransport }[] = [
  { label: 'Streamable HTTP', value: 'STREAMABLE_HTTP' },
]

const statusMap: Record<McpConnectionStatus, { text: string, variant: 'default' | 'secondary' | 'destructive', icon: string }> = {
  CONNECTED: { text: '已连接', variant: 'default', icon: 'morph:check' },
  ERROR: { text: '连接失败', variant: 'destructive', icon: 'morph:triangle-alert' },
  UNKNOWN: { text: '未连接', variant: 'secondary', icon: 'morph:minus' },
}

const formHints = {
  code: '小写字母、数字、下划线或连字符；工具模块编码为 mcp:<编码>。',
  transport: '旧版 SSE 与 stdio 暂不支持，只支持 MCP Streamable HTTP。',
  timeout: '单次 initialize 与 tools/list 调用的超时时间。',
  endpointUrl: 'MCP Streamable HTTP 端点，常见形式为 http://host:port/mcp。',
  headers: `认证等请求头。值在读取时以 ${MCP_HEADER_MASK} 掩码返回：保持掩码或留空都表示不修改已保存的值，删除键才移除该请求头。`,
  enabled: '停用后服务不参与工具桥接，工具模块也会随之停用。',
  preload: '开启后 AI 无需 load_tools 即可见这些工具；关闭则由 AI 按需加载。',
  allowPrivateNetwork: '自建 MCP 服务常用；关闭后只允许访问公网地址。',
} as const

const loading = ref(false)
const loadFailed = ref(false)
const servers = ref<McpServer[]>([])
const actionLoading = ref('')
/** 每个服务的工具清单展开状态，按服务 ID 独立保存。 */
const expandedTools = reactive<Record<string, boolean>>({})

const formVisible = ref(false)
const formSaving = ref(false)
const editingServer = ref<McpServer | null>(null)
const isEditing = computed(() => editingServer.value !== null)
const form = reactive<McpServerPayload>({
  name: '',
  code: '',
  description: '',
  transport: 'STREAMABLE_HTTP',
  endpointUrl: '',
  headers: {},
  enabled: true,
  preload: false,
  timeoutMillis: DEFAULT_TIMEOUT_MILLIS,
  allowPrivateNetwork: true,
})
const headersText = ref('')

onMounted(load)

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    const res = await apiMcp.list()
    servers.value = Array.isArray(res.data) ? res.data : []
  }
  catch {
    // 拦截器已提示具体原因，这里只保留可重试的失败状态，不清空已读内容。
    loadFailed.value = true
  }
  finally {
    loading.value = false
  }
}

function openForm(server?: McpServer) {
  editingServer.value = server || null
  Object.assign(form, server
    ? {
        name: server.name,
        code: server.code,
        description: server.description || '',
        transport: server.transport || 'STREAMABLE_HTTP',
        endpointUrl: server.endpointUrl,
        headers: { ...(server.headers || {}) },
        enabled: server.enabled,
        preload: server.preload,
        timeoutMillis: server.timeoutMillis || DEFAULT_TIMEOUT_MILLIS,
        allowPrivateNetwork: server.allowPrivateNetwork !== false,
      }
    : {
        name: '',
        code: '',
        description: '',
        transport: 'STREAMABLE_HTTP',
        endpointUrl: '',
        headers: {},
        enabled: true,
        preload: false,
        timeoutMillis: DEFAULT_TIMEOUT_MILLIS,
        allowPrivateNetwork: true,
      })
  const headers = form.headers || {}
  headersText.value = Object.keys(headers).length ? JSON.stringify(headers, null, 2) : ''
  formVisible.value = true
}

/** 确认时先校验再提交；失败不关闭弹窗，保留已填内容便于修正。 */
function handleBeforeClose(action: 'confirm' | 'cancel' | 'close', done: () => void) {
  if (action !== 'confirm') {
    done()
    return
  }
  void submitForm(done)
}

async function submitForm(done: () => void) {
  if (formSaving.value) {
    return
  }
  const name = form.name.trim()
  if (!name) {
    toast.error('请填写服务名称')
    return
  }
  const code = form.code.trim()
  if (!code) {
    toast.error('请填写服务编码')
    return
  }
  if (!CODE_PATTERN.test(code)) {
    toast.error('服务编码不合法', { description: '只能包含小写字母、数字、下划线和连字符。' })
    return
  }
  const endpointUrl = form.endpointUrl.trim()
  if (!endpointUrl) {
    toast.error('请填写服务地址')
    return
  }
  if (!/^https?:\/\//i.test(endpointUrl)) {
    toast.error('服务地址不合法', { description: '请填写 http:// 或 https:// 开头的完整 MCP Streamable HTTP 端点。' })
    return
  }
  if (!Number.isFinite(form.timeoutMillis) || form.timeoutMillis < MIN_TIMEOUT_MILLIS) {
    toast.error(`超时时间不能小于 ${MIN_TIMEOUT_MILLIS} 毫秒`)
    return
  }
  const headers = parseHeaders(headersText.value)
  if (!headers) {
    return
  }
  const payload: McpServerPayload = {
    name,
    code,
    description: form.description?.trim() || '',
    transport: form.transport,
    endpointUrl,
    headers,
    enabled: form.enabled,
    preload: form.preload,
    timeoutMillis: form.timeoutMillis,
    allowPrivateNetwork: form.allowPrivateNetwork,
  }
  const current = editingServer.value
  formSaving.value = true
  try {
    if (current) {
      await apiMcp.update(current.id, payload)
    }
    else {
      await apiMcp.create(payload)
    }
    done()
    await load()
    toast.success(current ? 'MCP 服务已更新' : 'MCP 服务已创建', {
      description: '连接测试结果不影响保存：连接失败会记录在卡片上，修正后可重试「测试连接」。',
    })
  }
  catch {
    // 拦截器已提示保存失败原因；保留弹窗内容，不关闭也不清空输入。
  }
  finally {
    formSaving.value = false
  }
}

/**
 * 解析请求头 JSON。
 * 值为掩码或空时视为「不修改服务端已保存的凭据」：已有请求头按掩码提交，新增请求头直接忽略。
 */
function parseHeaders(text: string): Record<string, string> | null {
  const raw = text.trim()
  if (!raw) {
    return {}
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  }
  catch {
    toast.error('请求头 JSON 解析失败', { description: `请填写合法的 JSON 对象，例如 ${HEADER_EXAMPLE}` })
    return null
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    toast.error('请求头必须是 JSON 对象', { description: `例如 ${HEADER_EXAMPLE}` })
    return null
  }
  const storedKeys = new Set(Object.keys(editingServer.value?.headers || {}))
  const headers: Record<string, string> = {}
  for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
    const header = key.trim()
    if (!header) {
      continue
    }
    if (value !== null && typeof value === 'object') {
      toast.error(`请求头 ${header} 的值不合法`, { description: '每个请求头的值必须是字符串。' })
      return null
    }
    const text = value === null || value === undefined ? '' : String(value).trim()
    if (!text) {
      if (storedKeys.has(header)) {
        headers[header] = MCP_HEADER_MASK
      }
      continue
    }
    headers[header] = text
  }
  return headers
}

async function testConnection(server: McpServer) {
  actionLoading.value = `test:${server.id}`
  try {
    const res = await apiMcp.test(server.id)
    const result = res.data
    replaceServer(result)
    if (result.lastStatus === 'CONNECTED') {
      toast.success(`连接成功，发现 ${toolsOf(result).length} 个工具`, {
        description: serverLabel(result) || `工具已缓存为模块 ${moduleCodeOf(result)}`,
      })
    }
    else {
      toast.error('连接失败', { description: result.lastError || '请检查服务地址、请求头与网络连通性后重试。' })
    }
  }
  catch {
    // 拦截器已提示失败原因；重新拉取列表以呈现服务端记录的连接状态。
    await load()
  }
  finally {
    actionLoading.value = ''
  }
}

function confirmRemove(server: McpServer) {
  modal.confirm({
    title: '删除 MCP 服务',
    content: `确定删除“${server.name}”吗？同时会移除系统工具模块 ${moduleCodeOf(server)}（含 ${toolsOf(server).length} 个已缓存工具），已加载该模块的 AI 应用将无法再调用这些工具。`,
    onConfirm: async () => {
      await apiMcp.remove(server.id)
      toast.success('已删除 MCP 服务')
      await load()
    },
  })
}

function replaceServer(server: McpServer) {
  const index = servers.value.findIndex(item => item.id === server.id)
  if (index >= 0) {
    servers.value[index] = server
  }
}

/** 后端始终返回 tools 数组，这里兜底旧数据，避免卡片渲染中断。 */
function toolsOf(server: McpServer): McpTool[] {
  return server.tools || []
}

function moduleCodeOf(server: McpServer) {
  return server.moduleCode || `mcp:${server.code}`
}

function statusOf(server: McpServer) {
  return statusMap[server.lastStatus] || statusMap.UNKNOWN
}

function transportText(transport: McpTransport) {
  return transportOptions.find(option => option.value === transport)?.label || 'Streamable HTTP'
}

/** 连接成功后展示服务端自报身份；未连接时不展示推测值。 */
function serverLabel(server: McpServer) {
  if (server.lastStatus !== 'CONNECTED') {
    return ''
  }
  return [server.serverName, server.serverVersion].filter(Boolean).join(' ')
}

function requestHeaderCount(server: McpServer) {
  return Object.keys(server.headers || {}).length
}

function riskText(tool: McpTool) {
  return tool.risk === 'READ' ? '只读' : '写入'
}

/** 检查时间带 7 位小数时截断到秒，避免长串撑破信息行。 */
function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/)
  return match ? `${match[1]} ${match[2]}` : value
}
</script>

<template>
  <div>
    <YdPageHeader title="MCP 服务" class="mb-0">
      <template #description>
        注册外部 MCP（Model Context Protocol）服务。后端按 Streamable HTTP 执行 initialize 与 tools/list，把工具桥接为系统工具模块 mcp:&lt;编码&gt;，AI 用 load_tools 按需加载；开启常驻则无需加载。
      </template>
      <YdButton variant="outline" :loading="loading" @click="load">
        <YdIcon name="morph:refresh-cw" />刷新
      </YdButton>
      <YdButton v-auth="'platform:mcp:server:create'" @click="openForm()">
        <YdIcon name="morph:plus" />新建 MCP 服务
      </YdButton>
    </YdPageHeader>

    <YdPageMain>
      <YdLoadingOverlay :loading="loading">
        <YdEmpty
          v-if="loadFailed"
          title="MCP 服务列表加载失败"
          description="未能读取已注册的 MCP 服务，列表可能不是最新状态。"
          illustration="error"
        >
          <template #action>
            <YdButton variant="outline" @click="load">
              重新加载
            </YdButton>
          </template>
        </YdEmpty>

        <div v-else-if="!loading && !servers.length" class="mcp-empty">
          <YdEmpty
            title="还没有注册 MCP 服务"
            description="服务地址填 MCP Streamable HTTP 端点，通常是 http://host:port/mcp。保存后点「测试连接」即可获取工具清单。"
            illustration="empty"
          >
            <template #action>
              <YdButton v-auth="'platform:mcp:server:create'" @click="openForm()">
                <YdIcon name="morph:plus" />新建 MCP 服务
              </YdButton>
            </template>
          </YdEmpty>
          <ol class="mcp-empty__steps">
            <li>新建时把服务地址填成 MCP 的 Streamable HTTP 端点，常见形式为 <code>http://host:port/mcp</code>；需要鉴权时在请求头里加 <code>Authorization</code> 等条目。</li>
            <li>保存不受连接测试影响；点卡片上的「测试连接」，后端会执行 initialize 与 tools/list 并缓存工具清单，失败原因会记在卡片上。</li>
            <li>连接成功后这些工具以模块 <code>mcp:&lt;编码&gt;</code> 出现在「系统工具」，AI 用 <code>load_tools</code> 加载；开启「常驻加载」则无需加载即可见。</li>
          </ol>
        </div>

        <div v-else class="mcp-grid">
          <article
            v-for="server in servers"
            :key="server.id"
            class="mcp-card"
            :class="{ 'is-disabled': !server.enabled, 'is-error': server.lastStatus === 'ERROR' }"
          >
            <header class="mcp-card__head">
              <span class="mcp-card__icon"><YdIcon name="morph:server" /></span>
              <div class="mcp-card__heading">
                <h3>{{ server.name }}</h3>
                <code class="mcp-code">{{ server.code }}</code>
              </div>
              <YdTag :variant="statusOf(server).variant" :icon="statusOf(server).icon">
                {{ statusOf(server).text }}
              </YdTag>
            </header>

            <div class="mcp-card__chips">
              <YdTag v-if="!server.enabled" variant="secondary" icon="morph:circle-pause">
                已停用
              </YdTag>
              <YdTag :variant="server.preload ? 'outline' : 'secondary'" :icon="server.preload ? 'morph:pin' : 'morph:hand'">
                {{ server.preload ? '常驻' : '按需加载' }}
              </YdTag>
              <YdTag variant="outline" icon="morph:route">
                {{ transportText(server.transport) }}
              </YdTag>
            </div>

            <p v-if="server.description" class="mcp-card__description">
              {{ server.description }}
            </p>

            <div class="mcp-endpoint">
              <code class="mcp-endpoint__url">{{ server.endpointUrl }}</code>
              <YdCopyButton :text="server.endpointUrl" label="" />
            </div>

            <dl class="mcp-facts">
              <div class="mcp-fact">
                <dt>工具</dt>
                <dd>{{ toolsOf(server).length }} 个</dd>
              </div>
              <div class="mcp-fact">
                <dt>工具模块</dt>
                <dd class="mcp-fact__module">
                  <code class="mcp-code">{{ moduleCodeOf(server) }}</code>
                  <YdTag :variant="server.moduleEnabled ? 'default' : 'secondary'" icon="morph:boxes">
                    {{ server.moduleEnabled ? '模块已启用' : '模块未启用' }}
                  </YdTag>
                </dd>
              </div>
              <div class="mcp-fact">
                <dt>最后连接</dt>
                <dd>{{ formatTime(server.lastConnectedAt) }}</dd>
              </div>
              <div v-if="serverLabel(server)" class="mcp-fact">
                <dt>服务端</dt>
                <dd>
                  <span>{{ serverLabel(server) }}</span>
                  <span v-if="server.protocolVersion">协议 {{ server.protocolVersion }}</span>
                </dd>
              </div>
              <div class="mcp-fact">
                <dt>超时</dt>
                <dd>{{ server.timeoutMillis }} ms</dd>
              </div>
              <div class="mcp-fact">
                <dt>请求头</dt>
                <dd>{{ requestHeaderCount(server) ? `${requestHeaderCount(server)} 项` : '无' }}</dd>
              </div>
            </dl>

            <YdAlert
              v-if="server.lastStatus === 'ERROR'"
              variant="destructive"
              icon="morph:triangle-alert"
              title="连接失败"
              :description="server.lastError || '后端未能完成 initialize 或 tools/list，请检查服务地址、请求头与网络连通性。'"
            />

            <YdCollapsible v-model="expandedTools[server.id]" class="mcp-tools">
              <template #trigger="{ open }">
                <span class="mcp-tools__trigger">
                  <span class="mcp-tools__trigger-title">
                    <YdIcon name="morph:wrench" />
                    工具清单
                    <span class="mcp-tools__count">{{ toolsOf(server).length ? `${toolsOf(server).length} 个` : '尚未获取' }}</span>
                  </span>
                  <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
                </span>
              </template>
              <ul v-if="toolsOf(server).length" class="mcp-tools__list">
                <li v-for="tool in toolsOf(server)" :key="tool.name" class="mcp-tool">
                  <div class="mcp-tool__main">
                    <span class="mcp-tool__name">{{ tool.name }}</span>
                    <span v-if="tool.title" class="mcp-tool__title">{{ tool.title }}</span>
                    <p v-if="tool.description" class="mcp-tool__description">
                      {{ tool.description }}
                    </p>
                  </div>
                  <div class="mcp-tool__meta">
                    <YdTag variant="secondary" :icon="tool.risk === 'READ' ? 'morph:eye' : 'morph:pencil'">
                      {{ riskText(tool) }}
                    </YdTag>
                    <YdTag v-if="tool.requiresConfirmation" variant="outline" icon="morph:shield-alert">
                      需确认
                    </YdTag>
                    <code v-if="tool.platformToolCode" class="mcp-code">{{ tool.platformToolCode }}</code>
                  </div>
                </li>
              </ul>
              <p v-else class="mcp-tools__empty">
                尚未获取工具清单，请先测试连接。
              </p>
            </YdCollapsible>

            <footer class="mcp-card__actions">
              <YdButton
                v-auth="'platform:mcp:server:test'"
                variant="outline"
                size="sm"
                :loading="actionLoading === `test:${server.id}`"
                @click="testConnection(server)"
              >
                <YdIcon name="morph:plug-zap" />测试连接
              </YdButton>
              <YdButton v-auth="'platform:mcp:server:edit'" variant="outline" size="sm" @click="openForm(server)">
                <YdIcon name="morph:pencil" />编辑
              </YdButton>
              <YdButton v-auth="'platform:mcp:server:delete'" variant="destructive" size="sm" @click="confirmRemove(server)">
                <YdIcon name="morph:trash-2" />删除
              </YdButton>
            </footer>
          </article>
        </div>
      </YdLoadingOverlay>

      <YdModal
        v-model="formVisible"
        :title="isEditing ? '编辑 MCP 服务' : '新建 MCP 服务'"
        show-cancel-button
        class="sm:max-w-2xl"
        :before-close="handleBeforeClose"
      >
        <YdForm :model="form" layout="vertical">
          <div class="gap-x-4 grid grid-cols-1 md:grid-cols-2">
            <YdFormItem label="名称" required>
              <YdInput v-model="form.name" class="w-full" maxlength="60" placeholder="例如：团队知识库 MCP" />
            </YdFormItem>
            <YdFormItem label="编码" required :extra="formHints.code">
              <YdInput v-model="form.code" class="w-full" :disabled="isEditing" maxlength="40" placeholder="例如：kb-mcp" />
            </YdFormItem>
          </div>

          <YdFormItem label="描述">
            <YdInput v-model="form.description" class="w-full" maxlength="200" placeholder="这个 MCP 服务提供哪些工具" />
          </YdFormItem>

          <div class="gap-x-4 grid grid-cols-1 md:grid-cols-2">
            <YdFormItem label="传输类型" :extra="formHints.transport">
              <YdSelect v-model="form.transport" class="w-full" :options="transportOptions" />
            </YdFormItem>
            <YdFormItem label="超时（毫秒）" :extra="formHints.timeout">
              <YdNumberField v-model="form.timeoutMillis" class="w-full" :min="MIN_TIMEOUT_MILLIS" :max="MAX_TIMEOUT_MILLIS" :step="1000" />
            </YdFormItem>
          </div>

          <YdFormItem label="服务地址" required :extra="formHints.endpointUrl">
            <YdInput v-model="form.endpointUrl" class="w-full" placeholder="http://127.0.0.1:3000/mcp" />
          </YdFormItem>

          <YdFormItem label="请求头（JSON）" :extra="formHints.headers">
            <YdTextarea v-model="headersText" class="w-full" rows="4" input-class="font-mono" :placeholder="HEADER_EXAMPLE" />
          </YdFormItem>

          <div class="gap-x-4 grid grid-cols-1 sm:grid-cols-3">
            <YdFormItem label="启用" :extra="formHints.enabled">
              <YdSwitch v-model="form.enabled" />
            </YdFormItem>
            <YdFormItem label="常驻加载" :extra="formHints.preload">
              <YdSwitch v-model="form.preload" />
            </YdFormItem>
            <YdFormItem label="允许访问私网/回环" :extra="formHints.allowPrivateNetwork">
              <YdSwitch v-model="form.allowPrivateNetwork" />
            </YdFormItem>
          </div>
        </YdForm>
      </YdModal>
    </YdPageMain>
  </div>
</template>

<style scoped>
/* 卡片网格：服务数量少，一服务一卡；窄屏单列。 */
.mcp-grid { display:grid; gap:16px; grid-template-columns:repeat(auto-fill, minmax(340px, 1fr)); }
.mcp-card { display:grid; gap:12px; align-content:start; padding:18px; border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-1); }
/* 停用只弱化，不回退成不可读；连接失败用危险色轻量染色，与卡片内错误说明呼应。 */
.mcp-card.is-disabled { background:var(--color-fill-1); border-style:dashed; }
.mcp-card.is-disabled .mcp-card__head,
.mcp-card.is-disabled .mcp-facts { opacity:.72; }
.mcp-card.is-error { border-color:color-mix(in oklab, var(--color-danger-6) 45%, var(--color-border-2)); }
.mcp-card__head { display:flex; gap:12px; align-items:flex-start; }
.mcp-card__icon { display:grid; flex:none; width:34px; height:34px; place-items:center; border-radius:8px; background:var(--color-fill-2); color:var(--color-text-1); font-size:17px; }
.mcp-card__heading { display:grid; gap:4px; flex:1 1 auto; min-width:0; }
.mcp-card__heading h3 { margin:0; color:var(--color-text-1); font-size:17px; font-weight:600; overflow-wrap:anywhere; }
.mcp-card__chips { display:flex; flex-wrap:wrap; gap:6px; }
.mcp-card__description { margin:0; color:var(--color-text-3); font-size:13px; line-height:1.6; }
.mcp-code { padding:2px 6px; border-radius:6px; background:var(--color-fill-2); color:var(--color-text-2); font-size:11.5px; overflow-wrap:anywhere; }
/* 端点整行可读：地址通常较长，换行不截断，复制走 YdCopyButton。 */
.mcp-endpoint { display:flex; gap:8px; align-items:center; padding:8px 10px; border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-2); }
.mcp-endpoint__url { flex:1 1 auto; min-width:0; color:var(--color-text-2); font-size:12.5px; overflow-wrap:anywhere; }
.mcp-facts { display:grid; gap:8px; margin:0; }
.mcp-fact { display:grid; grid-template-columns:76px minmax(0,1fr); gap:10px; align-items:center; }
.mcp-fact dt { color:var(--color-text-3); font-size:12px; }
.mcp-fact dd { display:flex; flex-wrap:wrap; gap:6px; align-items:center; margin:0; color:var(--color-text-1); font-size:13px; }
.mcp-fact__module { min-width:0; }
/* 工具清单：默认折叠，展开后逐行展示名称、风险与平台工具编码。 */
.mcp-tools { border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-2); }
.mcp-tools__trigger { display:flex; gap:8px; align-items:center; justify-content:space-between; width:100%; padding:9px 12px; color:var(--color-text-2); font-size:13px; }
.mcp-tools__trigger-title { display:inline-flex; gap:6px; align-items:center; }
.mcp-tools__count { color:var(--color-text-3); font-size:12px; }
.mcp-tools__list { display:grid; gap:0; margin:0; padding:0; border-top:1px solid var(--color-border-2); list-style:none; }
.mcp-tool { display:flex; flex-wrap:wrap; gap:8px; align-items:flex-start; justify-content:space-between; padding:10px 12px; border-bottom:1px solid var(--color-border-2); }
.mcp-tool:last-child { border-bottom:none; }
.mcp-tool__main { display:grid; gap:3px; flex:1 1 200px; min-width:0; }
.mcp-tool__name { color:var(--color-text-1); font-size:13px; font-weight:600; overflow-wrap:anywhere; }
.mcp-tool__title { color:var(--color-text-2); font-size:12px; overflow-wrap:anywhere; }
.mcp-tool__description { margin:0; color:var(--color-text-3); font-size:12px; line-height:1.5; }
.mcp-tool__meta { display:flex; flex-wrap:wrap; gap:6px; align-items:center; justify-content:flex-end; }
.mcp-tools__empty { margin:0; padding:12px; border-top:1px solid var(--color-border-2); color:var(--color-text-3); font-size:12.5px; }
.mcp-card__actions { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:8px; padding-top:12px; border-top:1px solid var(--color-border-2); }
/* 空态：说明怎么添加，并给出 MCP 端点与工具模块的下一步。 */
.mcp-empty { display:grid; gap:20px; padding:24px 16px; }
.mcp-empty__steps { display:grid; gap:10px; max-width:760px; margin:0 auto; padding:0; color:var(--color-text-2); font-size:13px; line-height:1.7; list-style:none; counter-reset:mcp-step; }
.mcp-empty__steps li { display:grid; grid-template-columns:24px minmax(0,1fr); gap:10px; align-items:start; }
.mcp-empty__steps li::before { display:grid; width:24px; height:24px; place-items:center; border-radius:50%; background:var(--color-fill-2); color:var(--color-text-2); font-size:12px; counter-increment:mcp-step; content:counter(mcp-step); }
.mcp-empty__steps code { padding:2px 6px; border-radius:6px; background:var(--color-fill-2); color:var(--color-text-2); font-size:12px; overflow-wrap:anywhere; }
@media (max-width: 640px) {
  .mcp-grid { grid-template-columns:minmax(0, 1fr); }
  .mcp-card { padding:14px; }
  .mcp-card__actions { grid-template-columns:minmax(0, 1fr); }
  .mcp-fact { grid-template-columns:minmax(0, 1fr); gap:4px; }
  .mcp-tool__meta { justify-content:flex-start; }
}
</style>
