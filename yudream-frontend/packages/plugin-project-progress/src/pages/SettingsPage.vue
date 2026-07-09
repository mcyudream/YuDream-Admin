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
        <span>Settings</span>
        <h2>插件设置</h2>
      </div>
      <button type="button" @click="model.load">刷新状态</button>
    </section>

    <section class="pp-grid">
      <ProgressPanel title="依赖状态">
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

      <ProgressPanel title="Minecraft 自动打卡">
        <form class="pp-form" @submit.prevent="model.saveProject">
          <label>
            <span>项目</span>
            <select v-model="model.selectedProjectId" @change="model.selectedProject && model.selectProject(model.selectedProject)">
              <option value="">请选择</option>
              <option v-for="project in model.projects" :key="project.id" :value="project.id">
                {{ project.name }}
              </option>
            </select>
          </label>
          <div class="pp-checks">
            <label><input v-model="model.projectForm.minecraftPolicy.enabled" type="checkbox"> 启用 MC 打卡</label>
            <label><input v-model="model.projectForm.minecraftPolicy.autoCheckInEnabled" type="checkbox"> 自动打卡</label>
            <label><input v-model="model.projectForm.minecraftPolicy.includeAfk" type="checkbox"> 计入 AFK</label>
          </div>
          <label>
            <span>服务器 ID</span>
            <input v-model="model.projectForm.minecraftPolicy.serverId" autocomplete="off">
          </label>
          <label>
            <span>要求在线分钟</span>
            <input v-model.number="model.projectForm.minecraftPolicy.requiredOnlineMinutes" type="number" min="1">
          </label>
          <div class="pp-actions">
            <button class="pp-primary" type="submit" :disabled="!model.selectedProjectId || model.saving">保存设置</button>
            <button type="button" :disabled="!model.selectedProjectId" @click="model.autoMinecraftCheckIns">立即检查自动打卡</button>
          </div>
        </form>
      </ProgressPanel>
    </section>
  </section>
</template>
