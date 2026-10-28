<script setup lang="ts">
import type { McpServerOverview } from '@/api/modules/platform-mcpserver'
import { useYdToast, YdAlert, YdButton, YdCard, YdCollapsible, YdCopyButton, YdEmpty, YdFilterSelect, YdForm, YdFormItem, YdIcon, YdListToolbar, YdLoadingOverlay, YdPageHeader, YdPageMain, YdSegmented, YdSelect, YdSkeleton, YdStat, YdSwitch, YdTag } from '@yudream/components'
import { computed, onMounted, reactive, ref, toRefs, watch } from 'vue'
import apiCapability from '@/api/modules/platform-capability'
import apiMcpServer from '@/api/modules/platform-mcpserver'
import { refreshDynamicRoutes } from '@/router/dynamic'
import { useAppFeatureStore } from '@/store/modules/app/features'

const toast = useYdToast()
const appFeatureStore = useAppFeatureStore()
const router = useRouter()
const loading = ref(false)
const loadFailed = ref(false)
const toggling = ref(false)
const overview = ref<McpServerOverview | null>(null)
const panel = ref('setup')
const connectionForm = reactive({ endpointCode: '' })
const { endpointCode } = toRefs(connectionForm)
const keyword = ref('')
const toolAccess = ref('all')
const expanded = reactive<Record<string, boolean>>({})
const enabled = computed(() => overview.value?.enabled ?? false)
const modules = computed(() => overview.value?.modules ?? [])
const fullEndpoint = computed(() => `${window.location.origin}${overview.value?.endpointFull || '/api/mcp'}`)
const selectedModule = computed(() => modules.value.find(module => module.code === endpointCode.value))
const selectedEndpoint = computed(() => selectedModule.value ? `${window.location.origin}${selectedModule.value.endpoint}` : fullEndpoint.value)
const endpointOptions = computed(() => [
  { label: '完整端点 · 全部模块', value: '' },
  ...modules.value.map(module => ({ label: `${module.displayName} · ${module.toolCount} 个工具`, value: module.code })),
])
// 一次只配置一个端点，避免把完整端点与各模块重复加入客户端。
const clientSnippet = computed(() => JSON.stringify({
  mcpServers: {
    [selectedModule.value ? `yudream-${selectedModule.value.code}` : 'yudream-full']: {
      type: 'http',
      url: selectedEndpoint.value,
      headers: { Authorization: 'Bearer yda_<你的 API Key>' },
    },
  },
}, null, 2))
const filteredModules = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  return modules.value.flatMap((module) => {
    const matchesModule = `${module.displayName} ${module.code}`.toLocaleLowerCase().includes(query)
    const tools = module.tools.filter(tool => (matchesModule || `${tool.name} ${tool.title} ${tool.description} ${tool.permissionCode}`.toLocaleLowerCase().includes(query))
      && (toolAccess.value === 'all' || (toolAccess.value === 'write' ? tool.write : !tool.write)))
    return tools.length ? [{ ...module, tools }] : []
  })
})
const filteredToolCount = computed(() => filteredModules.value.reduce((count, module) => count + module.tools.length, 0))
const hasToolFilter = computed(() => Boolean(keyword.value.trim()) || toolAccess.value !== 'all')
const writeToolCount = computed(() => modules.value.reduce((count, module) => count + module.tools.filter(tool => tool.write).length, 0))

watch(modules, (items) => {
  if (endpointCode.value && !items.some(module => module.code === endpointCode.value)) {
    endpointCode.value = ''
  }
})
onMounted(load)

async function load() {
  if (loading.value) {
    return false
  }
  loading.value = true
  loadFailed.value = false
  try {
    overview.value = (await apiMcpServer.overview()).data
    return true
  }
  catch {
    loadFailed.value = true
    return false
  }
  finally {
    loading.value = false
  }
}

async function refreshOverview() {
  if (await load()) {
    toast.success('服务器状态已更新')
  }
}

async function toggleEnabled() {
  if (toggling.value || loading.value || !overview.value) {
    return
  }
  const target = !enabled.value
  toggling.value = true
  try {
    if (target) {
      await apiCapability.enable('mcp-server')
    }
    else { await apiCapability.disable('mcp-server') }
    overview.value = { ...overview.value, enabled: target }
    await Promise.all([refreshDynamicRoutes(router), appFeatureStore.load(true)])
    await load()
    toast.success(target ? 'MCP 服务器已启用' : 'MCP 服务器已禁用')
  }
  catch {
    // 拦截器呈现失败原因。
  }
  finally {
    toggling.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  toolAccess.value = 'all'
}
</script>

<template>
  <div class="mcpserver-page">
    <YdPageHeader title="MCP 服务器" description="让外部 AI 客户端连接平台，使用已授权的插件工具。">
      <YdButton variant="outline" :loading="loading" :disabled="toggling" @click="refreshOverview">
        <YdIcon name="morph:refresh-cw" />更新状态
      </YdButton>
    </YdPageHeader>
    <YdPageMain>
      <YdAlert v-if="loadFailed && overview" variant="destructive" title="状态更新失败">
        <template #description>
          <p>当前保留上次读取的内容，服务器状态和工具清单可能已经变化。</p>
          <YdButton variant="outline" size="sm" @click="load">
            重新加载
          </YdButton>
        </template>
      </YdAlert>
      <YdLoadingOverlay :loading="loading">
        <div v-if="!overview && !loadFailed" class="mcpserver-skeleton" role="status" aria-label="正在加载 MCP 服务器">
          <YdSkeleton variant="rect" :height="112" />
          <YdSkeleton variant="rect" :height="320" />
        </div>
        <YdEmpty v-else-if="!overview" title="MCP 服务器加载失败" description="请检查网络连接与访问权限，然后重新加载。" illustration="error">
          <template #action>
            <YdButton variant="outline" @click="load">
              重新加载
            </YdButton>
          </template>
        </YdEmpty>
        <div v-else class="mcpserver-content">
          <div class="mcpserver-stats">
            <YdStat title="服务状态" :value="enabled ? '已启用' : '已禁用'" description="外部客户端访问开关">
              <template #actions>
                <YdSwitch
                  v-auth="enabled ? 'platform:capability:disable' : 'platform:capability:enable'"
                  :model-value="enabled"
                  :disabled="toggling || loading"
                  aria-label="启用 MCP 服务器"
                  @update:model-value="toggleEnabled"
                />
                <span class="mcpserver-hint">{{ toggling ? '正在更新…' : '停用后可在「平台能力」重新开启' }}</span>
              </template>
            </YdStat>
            <YdStat title="工具模块" :value="modules.length" description="由已加载插件提供" />
            <YdStat title="可暴露工具" :value="overview.toolCount" :description="`其中 ${writeToolCount} 个写入工具，调用时校验权限`" />
          </div>
          <YdAlert v-if="!enabled" title="MCP 服务器已禁用" description="以下为配置参考。启用服务器后，外部客户端才能调用工具。" />
          <YdSegmented v-model="panel" aria-label="MCP 服务器视图" :options="[{ label: '接入配置', value: 'setup' }, { label: `工具目录 · ${overview.toolCount}`, value: 'tools' }]" />

          <div v-if="panel === 'setup'" class="mcpserver-setup">
            <YdCard title="连接你的 AI 客户端" description="选择一个端点，复制配置，再填入自己的 API Key。">
              <div class="mcpserver-stack">
                <YdForm :model="connectionForm" layout="vertical">
                  <YdFormItem field="endpointCode" label="连接范围" extra="完整端点聚合全部模块；选择单个模块可缩小工具范围。">
                    <YdSelect v-model="endpointCode" :options="endpointOptions" aria-label="连接范围" class="w-full" />
                  </YdFormItem>
                </YdForm>
                <div class="mcpserver-endpoint">
                  <div class="mcpserver-stack mcpserver-endpoint__text">
                    <span class="mcpserver-hint">{{ selectedModule?.displayName || '完整端点' }} · Streamable HTTP</span>
                    <code>{{ selectedEndpoint }}</code>
                  </div>
                  <YdCopyButton :text="selectedEndpoint" label="复制地址" />
                </div>
                <div class="mcpserver-config">
                  <div class="mcpserver-config__header">
                    <span>客户端配置 · JSON</span>
                    <YdCopyButton :text="clientSnippet" label="复制配置" />
                  </div>
                  <pre aria-label="MCP 客户端配置"><code>{{ clientSnippet }}</code></pre>
                </div>
                <p class="mcpserver-hint">
                  将配置加入支持 Streamable HTTP 的客户端，并把占位内容替换为自己的 API Key。一次连接一个端点即可。
                </p>
              </div>
            </YdCard>
            <div class="mcpserver-stack">
              <YdCard title="三步完成接入">
                <ol class="mcpserver-steps">
                  <li><strong>创建访问密钥</strong><p>打开右上角头像 → 个人设置 → API Key，创建密钥并勾选要使用的权限。明文只显示一次，请妥善保存。</p></li>
                  <li><strong>复制客户端配置</strong><p>选择完整端点或一个模块，将配置加入客户端，替换 <code>yda_&lt;你的 API Key&gt;</code>。</p></li>
                  <li><strong>在客户端确认工具</strong><p>重新连接后查看工具列表。实际可见工具由密钥权限决定，可以在「工具目录」查找所需权限。</p></li>
                </ol>
              </YdCard>
              <YdCard title="连接遇到问题？">
                <ul class="mcpserver-help">
                  <li><strong>无法连接：</strong>确认服务器已启用，客户端能访问此站点，且传输方式为 Streamable HTTP。</li>
                  <li><strong>没有工具：</strong>检查 API Key 的权限，以及插件是否已启用并注册 MCP 工具。</li>
                  <li><strong>某个操作不可用：</strong>需要二次确认或具有破坏风险的工具不会在此端点暴露。</li>
                </ul>
                <p class="mcpserver-hint">
                  「更新状态」仅重新读取平台总览；请在客户端验证实际连接与认证。
                </p>
              </YdCard>
            </div>
          </div>

          <div v-else class="mcpserver-stack">
            <YdListToolbar v-model:keyword="keyword" :title="`工具目录 · ${filteredToolCount} / ${overview.toolCount}`" placeholder="搜索模块、工具或权限">
              <template #filters>
                <YdFilterSelect v-model="toolAccess" width="100%" aria-label="工具操作类型" :options="[{ label: '全部操作', value: 'all' }, { label: '只读工具', value: 'read' }, { label: '写入工具', value: 'write' }]" />
              </template>
              <template #actions>
                <YdButton variant="outline" :disabled="!hasToolFilter" @click="resetFilters">
                  重置
                </YdButton>
              </template>
            </YdListToolbar>
            <p class="mcpserver-hint">
              此处展示平台可暴露的工具。客户端只能看到其 API Key 已授权的部分，写入操作也会逐次校验权限。
            </p>
            <YdEmpty v-if="!modules.length" title="暂无可暴露的工具" description="启用提供 MCP 工具的插件后，工具会出现在这里。" illustration="empty" />
            <YdEmpty v-else-if="!filteredModules.length" title="没有匹配的工具" description="试试其他名称或权限码，也可以清除筛选条件。" illustration="no-result">
              <template #action>
                <YdButton variant="outline" @click="resetFilters">
                  清除筛选
                </YdButton>
              </template>
            </YdEmpty>
            <YdCollapsible v-for="module in filteredModules" :key="module.code" :model-value="hasToolFilter || expanded[module.code]" class="mcpserver-module" @update:model-value="expanded[module.code] = $event">
              <template #trigger="{ open }">
                <span class="mcpserver-module__trigger">
                  <span><strong>{{ module.displayName }}</strong><span class="mcpserver-hint">{{ module.code }} · {{ module.tools.length }} 个工具</span></span>
                  <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
                </span>
              </template>
              <div class="mcpserver-module__tools">
                <article v-for="tool in module.tools" :key="tool.name" class="mcpserver-tool">
                  <header class="mcpserver-tool__header">
                    <strong>{{ tool.title || tool.name }}</strong>
                    <YdTag :variant="tool.write ? 'outline' : 'secondary'">
                      {{ tool.write ? '写入' : '只读' }}
                    </YdTag>
                  </header>
                  <code>{{ tool.name }}</code>
                  <p v-if="tool.description" class="mcpserver-hint">
                    {{ tool.description }}
                  </p>
                  <div class="mcpserver-tool__permission">
                    <span class="mcpserver-hint">所需权限</span><code>{{ tool.permissionCode || '登录即可' }}</code><YdCopyButton v-if="tool.permissionCode" :text="tool.permissionCode" />
                  </div>
                </article>
              </div>
            </YdCollapsible>
          </div>
        </div>
      </YdLoadingOverlay>
    </YdPageMain>
  </div>
</template>

<style scoped>
.mcpserver-page { min-width: 0; }
.mcpserver-content, .mcpserver-stack, .mcpserver-skeleton { display: grid; min-width: 0; gap: var(--yd-content-gap, 16px); }
.mcpserver-stats { display: grid; grid-template-columns: minmax(0, 1.4fr) repeat(2, minmax(0, 1fr)); gap: var(--yd-content-gap, 16px); }
.mcpserver-setup { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr); align-items: start; gap: var(--yd-content-gap, 16px); }
.mcpserver-hint { margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.8; overflow-wrap: anywhere; }
.mcpserver-endpoint { display: flex; min-width: 0; align-items: center; gap: 12px; padding: 12px; background: var(--color-fill-1); border: 1px solid var(--color-border-2); border-radius: var(--yd-surface-radius); }
.mcpserver-endpoint__text { flex: 1; gap: 4px; }
code { font-family: ui-monospace, monospace; font-size: 12px; overflow-wrap: anywhere; }
.mcpserver-config { min-width: 0; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: var(--yd-surface-radius); }
.mcpserver-config__header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 12px; background: var(--color-fill-1); color: var(--color-text-2); font-size: 12px; }
.mcpserver-config pre { overflow: auto; margin: 0; padding: 16px; line-height: 1.8; color: var(--color-text-1); }
.mcpserver-steps { display: grid; gap: 20px; margin: 0; padding-left: 24px; list-style: decimal; }
.mcpserver-steps strong { font-size: 14px; }
.mcpserver-steps p, .mcpserver-help { margin: 6px 0 0; color: var(--color-text-2); font-size: 13px; line-height: 1.8; }
.mcpserver-help { display: grid; gap: 12px; padding: 0 0 12px; list-style: none; }
.mcpserver-module { min-width: 0; border: 1px solid var(--color-border-2); border-radius: var(--yd-surface-radius); }
.mcpserver-module__trigger { display: flex; width: 100%; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; padding: 14px; text-align: left; }
.mcpserver-module__trigger > span { display: grid; min-width: 0; gap: 4px; overflow-wrap: anywhere; }
.mcpserver-module__tools { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; padding: 0 14px 14px; }
.mcpserver-tool { display: grid; min-width: 0; align-content: start; gap: 8px; padding: 14px; background: var(--color-fill-1); border-radius: var(--yd-surface-radius); }
.mcpserver-tool__header { display: flex; align-items: start; justify-content: space-between; gap: 8px; font-size: 14px; overflow-wrap: anywhere; }
.mcpserver-tool__permission { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
@media (width <= 1024px) { .mcpserver-setup { grid-template-columns: minmax(0, 1fr); } }
@media (width <= 640px) {
  .mcpserver-stats, .mcpserver-module__tools { grid-template-columns: minmax(0, 1fr); }
  .mcpserver-endpoint { align-items: start; flex-direction: column; }
}
</style>
