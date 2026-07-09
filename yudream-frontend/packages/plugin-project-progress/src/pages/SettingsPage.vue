<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import ProgressPanel from '../components/ProgressPanel.vue'

defineProps<{
  model: ProjectProgressModel
}>()
</script>

<template>
  <section class="pp-page">
    <section class="pp-toolbar">
      <div>
        <span>联动状态</span>
        <h2>插件状态</h2>
      </div>
      <button type="button" @click="model.load">刷新状态</button>
    </section>

    <ProgressPanel title="依赖能力" subtle="Minecraft 自动打卡已改为按项目配置，这里只显示联动能力是否可用">
      <div class="pp-status-grid">
        <article :class="{ ok: model.status?.mailReady }">
          <span>邮件通知</span>
          <strong>{{ model.status?.mailReady ? '可用' : '不可用' }}</strong>
        </article>
        <article :class="{ ok: model.status?.minecraftReady }">
          <span>Minecraft Server</span>
          <strong>{{ model.status?.minecraftReady ? '已启用' : '未启用' }}</strong>
        </article>
      </div>
    </ProgressPanel>
  </section>
</template>
