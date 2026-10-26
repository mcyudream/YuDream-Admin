<script setup lang="ts">
import type { McpServerOverview } from '@/api/modules/platform-mcpserver'
import { computed, onMounted, reactive, ref } from 'vue'
import apiMcpServer from '@/api/modules/platform-mcpserver'
import apiCapability from '@/api/modules/platform-capability'
import { refreshDynamicRoutes } from '@/router/dynamic'
import { useAppFeatureStore } from '@/store/modules/app/features'

const toast = useYdToast()
const appFeatureStore = useAppFeatureStore()
const router = useRouter()

const loading = ref(false)
const loadFailed = ref(false)
const toggling = ref(false)
const overview = ref<McpServerOverview | null>(null)
/** 各模块工具清单展开状态，按模块 code 独立保存。 */
const expanded = reactive<Record<string, boolean>>({})

const siteOrigin = computed(() => window.location.origin)
const enabled = computed(() => overview.value?.enabled ?? false)
const modules = computed(() => overview.value?.modules ?? [])

/** 生成客户端配置示例：完整端点 + 各模块端点各一份，站点域名按当前访问自动填充。 */
const clientSnippet = computed(() => {
  const origin = siteOrigin.value
  const servers: Record<string, { type: string, url: string, headers: Record<string, string> }> = {}
  if (!modules.value.length) {
    servers['yudream-full'] = buildServerEntry(`${origin}${overview.value?.endpointFull ?? '/api/mcp'}`)
  }
  for (const module of modules.value) {
    servers[`yudream-${module.code}`] = buildServerEntry(`${origin}${module.endpoint}`)
  }
  return JSON.stringify({ mcpServers: servers }, null, 2)
})

function buildServerEntry(url: string) {
  return {
    type: 'http',
    url,
    headers: { Authorization: 'Bearer yda_<你的 API Key>' },
  }
}

onMounted(load)

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    const res = await apiMcpServer.overview()
    overview.value = res.data
  }
  catch {
    // 拦截器已提示具体原因，这里只保留可重试的失败状态。
    loadFailed.value = true
  }
  finally {
    loading.value = false
  }
}

/** 连接测试：重新拉取一次总览（内部走能力门禁 + 工具目录解析，等价于 MCP 客户端建联）。 */
async function testConnection() {
  await load()
  if (overview.value) {
    toast.success('连接正常', { description: `${modules.value.length} 个模块 / ${overview.value.toolCount} 个工具可暴露` })
  }
}

async function toggleEnabled() {
  const target = !enabled.value
  toggling.value = true
  try {
    if (target) {
      await apiCapability.enable('mcp-server')
    }
    else {
      await apiCapability.disable('mcp-server')
    }
    await Promise.all([
      refreshDynamicRoutes(router),
      appFeatureStore.load(true),
    ])
    await load()
    toast.success(target ? 'MCP 服务器已启用' : 'MCP 服务器已禁用')
  }
  catch {
    // 拦截器已提示失败原因
  }
  finally {
    toggling.value = false
  }
}
</script>

<template>
  <div class="mcpserver-page">
    <YdPageHeader
      title="MCP 服务器"
      description="把平台插件工具按调用者权限暴露为 MCP 端点，供外部 AI 客户端（ZCode、Claude Code 等）直连管理"
    >
      <YdButton variant="outline" :loading="loading" @click="testConnection">
        连接测试
      </YdButton>
    </YdPageHeader>

    <YdAlert
      v-if="loadFailed"
      variant="destructive"
      title="加载失败"
      class="mcpserver-block"
    >
      <template #description>
        加载 MCP 服务器总览失败，请确认能力已启用后重试。
        <YdButton variant="link" size="sm" @click="load">重试</YdButton>
      </template>
    </YdAlert>

    <div v-else class="mcpserver-body">
      <section class="mcpserver-card">
        <header class="mcpserver-card__title">
          <YdIcon name="morph:toggle-left" />
          能力状态
        </header>
        <label class="mcpserver-switch">
          <YdSwitch
            :model-value="enabled"
            :disabled="toggling"
            v-auth="enabled ? 'platform:capability:disable' : 'platform:capability:enable'"
            @update:model-value="toggleEnabled"
          />
          <span>
            {{ enabled ? '已启用' : '已禁用' }} · {{ overview?.toolCount ?? 0 }} 个工具 / {{ modules.length }} 个模块
          </span>
        </label>
        <p class="mcpserver-hint">
          关闭后 /api/mcp 端点立即失效，已配置的客户端将无法连接，本菜单随之隐藏（可在「平台能力」页重新开启）。
        </p>
      </section>

      <section class="mcpserver-card">
        <header class="mcpserver-card__title">
          <YdIcon name="morph:list-ordered" />
          三步接入
        </header>

        <div class="mcpserver-step">
          <div class="mcpserver-step__head">
            <span class="mcpserver-step__no">1</span>
            创建 API Key（客户端的访问凭证）
          </div>
          <p class="mcpserver-hint">
            登录平台后，点击右上角 <strong>头像 → 个人设置 → API Key</strong>，新建密钥（<code class="mcpserver-mono">yda_</code>
            前缀，明文仅显示一次）。勾选你要通过 MCP 操作的权限，例如
            <code class="mcpserver-mono">plugin:mcpanel:view</code>（查看实例/文件/备份）与
            <code class="mcpserver-mono">plugin:mcpanel:manage</code>(启停/写文件/改配置/备份恢复)。
          </p>
        </div>

        <div class="mcpserver-step">
          <div class="mcpserver-step__head">
            <span class="mcpserver-step__no">2</span>
            选择端点（调用者只能看到自己有权限的工具）
          </div>
          <div class="mcpserver-endpoint">
            <div class="mcpserver-endpoint__row">
              <span class="mcpserver-endpoint__label">完整端点（聚合全部模块）</span>
              <div class="mcpserver-endpoint__value">
                <code class="mcpserver-mono">{{ siteOrigin }}{{ overview?.endpointFull }}</code>
                <YdCopyButton :text="`${siteOrigin}${overview?.endpointFull ?? ''}`" />
              </div>
            </div>
            <div v-for="module in modules" :key="module.code" class="mcpserver-endpoint__row">
              <span class="mcpserver-endpoint__label">{{ module.displayName }}（{{ module.code }} · {{ module.toolCount }} 个工具）</span>
              <div class="mcpserver-endpoint__value">
                <code class="mcpserver-mono">{{ siteOrigin }}{{ module.endpoint }}</code>
                <YdCopyButton :text="`${siteOrigin}${module.endpoint}`" />
              </div>
            </div>
          </div>
        </div>

        <div class="mcpserver-step">
          <div class="mcpserver-step__head">
            <span class="mcpserver-step__no">3</span>
            客户端配置（复制到 ZCode / Claude Code 的 MCP 配置）
          </div>
          <div class="mcpserver-snippet">
            <pre class="mcpserver-mono">{{ clientSnippet }}</pre>
            <YdCopyButton :text="clientSnippet" label="复制配置" />
          </div>
          <p class="mcpserver-hint">
            只需要某一个模块时，把 url 换成对应模块端点即可。API Key 的权限决定能看到的工具：
            无权限的模块在完整端点里不会出现。
          </p>
        </div>
      </section>

      <section class="mcpserver-card">
        <header class="mcpserver-card__title">
          <YdIcon name="morph:wrench" />
          工具清单（{{ overview?.toolCount ?? 0 }}）
        </header>
        <p class="mcpserver-hint">
          暴露规则：工具需声明 MCP 通道；DESTRUCTIVE 与需二次确认的工具不暴露；写操作凭 API Key
          授权的权限码放行。调用与审计全走平台既有链路。
        </p>
        <div v-for="module in modules" :key="module.code" class="mcpserver-module">
          <YdCollapsible v-model="expanded[module.code]" class="mcpserver-module__list">
            <template #trigger="{ open }">
              <span class="mcpserver-module__trigger">
                <span>
                  <YdIcon name="morph:package" />
                  {{ module.displayName }}（{{ module.code }}）
                  <span class="mcpserver-module__count">{{ module.toolCount }} 个工具</span>
                </span>
                <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
              </span>
            </template>
            <div class="mcpserver-module__tools">
              <div v-for="tool in module.tools" :key="tool.name" class="mcpserver-tool">
                <div class="mcpserver-tool__head">
                  <code class="mcpserver-mono mcpserver-tool__name">{{ tool.name }}</code>
                  <YdTag :variant="tool.write ? 'default' : 'secondary'">
                    {{ tool.write ? '写' : '读' }}
                  </YdTag>
                </div>
                <p class="mcpserver-tool__desc">
                  {{ tool.title }}<template v-if="tool.description"> — {{ tool.description }}</template>
                  <code class="mcpserver-mono mcpserver-tool__perm">{{ tool.permissionCode || '（登录即可）' }}</code>
                </p>
              </div>
            </div>
          </YdCollapsible>
        </div>
        <div v-if="!modules.length && enabled" class="mcpserver-module">
          <YdStateIllustration name="empty" size="sm" />
          <p class="mcpserver-hint">
            暂无可暴露的模块：等待插件注册声明了 MCP 通道的工具。
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.mcpserver-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mcpserver-block {
  margin-bottom: 12px;
}

.mcpserver-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mcpserver-card {
  padding: 16px;
  border: 1px solid var(--dividing-line-color, hsl(var(--border)));
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mcpserver-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
}

.mcpserver-switch {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}

.mcpserver-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--secondary-color, hsl(var(--muted-foreground)));
}

.mcpserver-step {
  padding: 12px;
  border: 1px dashed var(--dividing-line-color, hsl(var(--border)));
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mcpserver-step__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 13px;
}

.mcpserver-step__no {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.mcpserver-endpoint {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mcpserver-endpoint__row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mcpserver-endpoint__label {
  font-size: 12px;
  color: var(--secondary-color, hsl(var(--muted-foreground)));
}

.mcpserver-endpoint__value {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mcpserver-mono {
  font-family: var(--font-family-mono, ui-monospace, monospace);
  font-size: 12px;
  word-break: break-all;
}

.mcpserver-snippet {
  position: relative;
  padding: 12px;
  border-radius: 8px;
  background: hsl(var(--muted));
  overflow: auto;
}

.mcpserver-snippet pre {
  margin: 0;
  white-space: pre-wrap;
}

.mcpserver-snippet > :last-child {
  position: absolute;
  top: 8px;
  right: 8px;
}

.mcpserver-module__trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: 13px;
}

.mcpserver-module__count {
  color: var(--secondary-color, hsl(var(--muted-foreground)));
  font-size: 12px;
  margin-left: 6px;
}

.mcpserver-module__tools {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 4px;
}

.mcpserver-tool__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mcpserver-tool__name {
  font-weight: 600;
}

.mcpserver-tool__desc {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--secondary-color, hsl(var(--muted-foreground)));
}

.mcpserver-tool__perm {
  margin-left: 6px;
  opacity: 0.85;
}
</style>
