<script setup lang="ts">
import type { YuDreamPluginSdk } from '@/plugins/sdk'

const props = defineProps<{
  sdk: YuDreamPluginSdk
}>()

const toast = useFaToast()
const loading = ref(false)
const status = ref<unknown>(null)
const baseUrl = computed(() => absoluteUrl(props.sdk.http.url('/')).replace(/\/$/, ''))
const endpoints = computed(() => [
  { method: 'GET', path: '/', note: 'ALI 元数据与公钥' },
  { method: 'POST', path: '/authserver/authenticate', note: '账号登录并获取 accessToken' },
  { method: 'POST', path: '/authserver/refresh', note: '刷新访问令牌' },
  { method: 'POST', path: '/authserver/validate', note: '校验访问令牌' },
  { method: 'POST', path: '/authserver/invalidate', note: '吊销访问令牌' },
  { method: 'POST', path: '/authserver/signout', note: '账号登出全部会话' },
  { method: 'POST', path: '/sessionserver/session/minecraft/join', note: '客户端加入服务器' },
  { method: 'GET', path: '/sessionserver/session/minecraft/hasJoined', note: '服务端验证玩家' },
  { method: 'GET', path: '/sessionserver/session/minecraft/profile/{uuid}', note: '查询角色材质属性' },
  { method: 'POST', path: '/api/profiles/minecraft', note: '批量查询角色' },
])

const launcherText = computed(() => `${baseUrl.value}/`)

onMounted(load)

async function load() {
  loading.value = true
  try {
    status.value = await props.sdk.http.get('/status')
  }
  finally {
    loading.value = false
  }
}

async function copy(value: string) {
  await navigator.clipboard.writeText(value)
  toast.success('已复制')
}

function absoluteUrl(url: string) {
  if (/^https?:\/\//i.test(url)) {
    return url
  }
  return `${window.location.origin}${url.startsWith('/') ? url : `/${url}`}`
}
</script>

<template>
  <div class="authlib-plugin">
    <section class="hero-panel">
      <div>
        <h2>Authlib Injector 服务端</h2>
        <p>该插件基于 Blessing Skin 插件提供的角色与材质资料，对外提供 authlib-injector / Yggdrasil 协议端点。</p>
      </div>
      <div class="hero-actions">
        <FaButton :loading="loading" variant="outline" @click="load">
          <FaIcon name="i-ri:refresh-line" />
          刷新状态
        </FaButton>
        <FaButton @click="copy(launcherText)">
          <FaIcon name="i-ri:file-copy-line" />
          复制 API 地址
        </FaButton>
      </div>
    </section>

    <section class="config-panel">
      <div class="section-head">
        <strong>启动器配置</strong>
        <FaTag variant="secondary">authlib-injector</FaTag>
      </div>
      <div class="copy-line">
        <code>{{ launcherText }}</code>
        <FaButton size="sm" variant="outline" @click="copy(launcherText)">复制</FaButton>
      </div>
      <p>在启动器或 authlib-injector 参数中使用上方地址作为认证服务器根路径。协议响应会附带 <code>X-Authlib-Injector-API-Location</code>。</p>
    </section>

    <section class="endpoint-panel">
      <div class="section-head">
        <strong>协议端点</strong>
        <FaTag>{{ endpoints.length }}</FaTag>
      </div>
      <div class="endpoint-list">
        <article v-for="item in endpoints" :key="`${item.method}-${item.path}`" class="endpoint-row">
          <FaTag :variant="item.method === 'GET' ? 'secondary' : 'default'">{{ item.method }}</FaTag>
          <code>{{ item.path }}</code>
          <span>{{ item.note }}</span>
        </article>
      </div>
    </section>

    <section class="status-panel">
      <div class="section-head">
        <strong>插件状态</strong>
      </div>
      <pre>{{ status ? JSON.stringify(status, null, 2) : '暂无状态数据' }}</pre>
    </section>
  </div>
</template>

<style scoped>
.authlib-plugin {
  display: grid;
  gap: 14px;
}

.hero-panel,
.config-panel,
.endpoint-panel,
.status-panel {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.hero-panel {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.hero-panel h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
}

.hero-panel p,
.config-panel p,
.endpoint-row span {
  margin: 0;
  color: var(--color-text-3);
  font-size: 13px;
}

.hero-actions,
.section-head,
.copy-line,
.endpoint-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.hero-actions,
.section-head {
  justify-content: space-between;
}

.copy-line {
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.copy-line code {
  overflow: hidden;
  flex: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.endpoint-list {
  display: grid;
  gap: 8px;
}

.endpoint-row {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.endpoint-row code {
  min-width: 320px;
  color: var(--color-text-1);
}

pre {
  overflow: auto;
  max-height: 360px;
  margin: 0;
  padding: 12px;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-1);
  font-size: 12px;
}

@media (max-width: 900px) {
  .hero-panel,
  .endpoint-row {
    grid-template-columns: 1fr;
  }

  .hero-panel,
  .hero-actions,
  .section-head,
  .copy-line,
  .endpoint-row {
    align-items: stretch;
    flex-direction: column;
  }

  .endpoint-row code {
    min-width: 0;
  }
}
</style>
