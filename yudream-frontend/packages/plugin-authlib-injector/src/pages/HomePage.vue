<template>
  <section class="authlib-home">
    <section class="authlib-hero">
      <div>
        <span>Authlib Injector</span>
        <h2>Yggdrasil 认证服务</h2>
        <p>基于 Blessing Skin 插件提供的角色与材质资料，对外提供 Minecraft 启动器和服务端可使用的认证协议端点。</p>
      </div>
      <div class="hero-actions">
        <FaButton :loading="model.loading" variant="outline" @click="model.load">
          <FaIcon name="i-ri:refresh-line" />
          刷新状态
        </FaButton>
        <FaButton @click="model.copy(model.launcherUrl)">
          <FaIcon name="i-ri:file-copy-line" />
          复制 API 地址
        </FaButton>
      </div>
    </section>

    <div class="authlib-grid">
      <AuthlibPanel title="启动器配置" eyebrow="Launcher">
        <template #header>
          <FaTag variant="secondary">{{ model.statusText }}</FaTag>
        </template>
        <ApiAddressCard :value="model.launcherUrl" @copy="model.copy" />
        <p class="muted">
          在启动器或 authlib-injector 参数中使用该地址作为认证服务器根路径。协议响应会附带
          <code>X-Authlib-Injector-API-Location</code>。
        </p>
      </AuthlibPanel>

      <AuthlibPanel title="插件状态" eyebrow="Runtime">
        <pre>{{ model.statusPayload }}</pre>
      </AuthlibPanel>
    </div>

    <AuthlibPanel title="协议端点" eyebrow="Protocol">
      <template #header>
        <FaTag>{{ model.endpoints.length }}</FaTag>
      </template>
      <EndpointList :endpoints="model.endpoints" />
    </AuthlibPanel>
  </section>
</template>

<script setup lang="ts">
import { FaButton, FaIcon, FaTag } from '@fantastic-admin/components'
import ApiAddressCard from '../components/ApiAddressCard.vue'
import AuthlibPanel from '../components/AuthlibPanel.vue'
import EndpointList from '../components/EndpointList.vue'
import type { AuthlibPluginModel } from '../composables/useAuthlibPlugin'

defineProps<{
  model: AuthlibPluginModel
}>()
</script>
