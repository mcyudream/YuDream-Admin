<script setup lang="ts">
import type { Component } from 'vue'
import { createPluginSdk } from '@/plugins/sdk'

interface PluginRouteMeta {
  pluginCode: string
  component?: string
  entry?: string
  moduleName?: string
  sdkVersion?: string
}

const route = useRoute()
const toast = useFaToast()
const loading = ref(false)
const result = ref<unknown>()
const remoteComponent = shallowRef<Component | null>(null)
const remoteError = ref('')

const plugin = computed(() => (route.meta.plugin || {}) as PluginRouteMeta)
const sdk = computed(() => createPluginSdk(plugin.value.pluginCode || ''))

watch(plugin, loadRemoteComponent, { immediate: true })

async function loadRemoteComponent() {
  remoteComponent.value = null
  remoteError.value = ''
  if (!plugin.value.entry) {
    return
  }
  try {
    const module = await import(/* @vite-ignore */ plugin.value.entry)
    remoteComponent.value = resolveRemoteComponent(module)
  }
  catch (error: any) {
    remoteError.value = error?.message || '远程组件加载失败'
  }
}

function resolveRemoteComponent(module: any) {
  if (plugin.value.component && module[plugin.value.component]) {
    return module[plugin.value.component]
  }
  if (plugin.value.moduleName && module[plugin.value.moduleName]) {
    return module[plugin.value.moduleName]
  }
  return module.default || null
}

async function callHello() {
  if (!plugin.value.pluginCode) {
    return
  }
  loading.value = true
  try {
    result.value = await sdk.value.http.get('/hello')
    toast.success('插件接口调用成功')
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <FaPageHeader :title="String(route.meta.title || '插件页面')" class="mb-0">
      <template #description>
        当前页面由插件 manifest 动态注册，运行时通过 YuDream Plugin SDK 调用插件接口。
      </template>
    </FaPageHeader>

    <FaPageMain>
      <component
        :is="remoteComponent"
        v-if="remoteComponent"
        :sdk="sdk"
        :route="route"
      />
      <div v-else class="plugin-runtime-layout">
        <section class="runtime-panel">
          <div class="runtime-title">
            <div class="runtime-icon">
              <FaIcon :name="String(route.meta.icon || 'i-ri:puzzle-2-line')" />
            </div>
            <div>
              <h2>{{ route.meta.title }}</h2>
              <p>{{ plugin.component || '-' }}</p>
            </div>
          </div>

          <div class="runtime-grid">
            <div>
              <span>插件编码</span>
              <strong>{{ plugin.pluginCode || '-' }}</strong>
            </div>
            <div>
              <span>模块名称</span>
              <strong>{{ plugin.moduleName || '-' }}</strong>
            </div>
            <div>
              <span>SDK 版本</span>
              <strong>{{ plugin.sdkVersion || sdk.version }}</strong>
            </div>
            <div>
              <span>远程入口</span>
              <strong>{{ plugin.entry || '未配置' }}</strong>
            </div>
          </div>

          <div class="runtime-actions">
            <FaButton :loading="loading" @click="callHello">
              <FaIcon name="i-ri:send-plane-line" />
              调用 /hello
            </FaButton>
          </div>
          <div v-if="remoteError" class="error-panel">
            {{ remoteError }}
          </div>
        </section>

        <section class="runtime-panel">
          <div class="section-title">
            <span>接口返回</span>
          </div>
          <pre>{{ result ? JSON.stringify(result, null, 2) : '暂无调用结果' }}</pre>
        </section>
      </div>
    </FaPageMain>
  </div>
</template>

<style scoped>
.plugin-runtime-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
}

.runtime-panel {
  display: grid;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.runtime-title {
  display: flex;
  gap: 12px;
  align-items: center;
}

.runtime-title h2 {
  margin: 0 0 4px;
  color: var(--color-text-1);
  font-size: 18px;
  font-weight: 700;
}

.runtime-title p {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
}

.runtime-icon {
  display: grid;
  width: 42px;
  height: 42px;
  flex: none;
  place-items: center;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: rgb(var(--primary-6));
  font-size: 22px;
}

.runtime-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.runtime-grid div {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.runtime-grid span,
.section-title {
  color: var(--color-text-3);
  font-size: 12px;
}

.runtime-grid strong {
  overflow: hidden;
  color: var(--color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.runtime-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-start;
}

pre {
  overflow: auto;
  min-height: 180px;
  max-height: 420px;
  margin: 0;
  padding: 12px;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-1);
  font-size: 12px;
}
</style>
