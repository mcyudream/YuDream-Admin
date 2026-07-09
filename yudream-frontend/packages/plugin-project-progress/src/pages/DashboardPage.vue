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
        <span>Project Monitor</span>
        <h2>项目进度看板</h2>
      </div>
      <button type="button" @click="model.load">刷新</button>
    </section>

    <section class="pp-metrics">
      <article>
        <span>项目数</span>
        <strong>{{ model.projects.length }}</strong>
      </article>
      <article>
        <span>当前项目进度</span>
        <strong>{{ model.completion }}%</strong>
      </article>
      <article>
        <span>工作细节</span>
        <strong>{{ model.details.length }}</strong>
      </article>
      <article>
        <span>Minecraft</span>
        <strong>{{ model.status?.minecraftReady ? '已联动' : '未启用' }}</strong>
      </article>
    </section>

    <section class="pp-grid">
      <ProgressPanel title="项目列表" subtle="选择一个项目查看细节">
        <div class="pp-list">
          <button
            v-for="project in model.projects"
            :key="project.id"
            type="button"
            :class="{ active: project.id === model.selectedProjectId }"
            @click="model.selectProject(project); model.reloadProjectData()"
          >
            <strong>{{ project.name }}</strong>
            <span>{{ project.enabled ? '启用' : '停用' }} · {{ project.memberUserIds.length }} 成员</span>
          </button>
          <div v-if="!model.projects.length" class="pp-empty">暂无项目</div>
        </div>
      </ProgressPanel>

      <ProgressPanel title="细节进度" :subtle="model.selectedProject?.name || '未选择项目'">
        <div class="pp-progress-line">
          <span :style="{ width: `${model.completion}%` }" />
        </div>
        <table class="pp-table">
          <thead>
            <tr>
              <th>任务</th>
              <th>状态</th>
              <th>负责人</th>
              <th>验收人</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="detail in model.details" :key="detail.id">
              <td>{{ detail.title }}</td>
              <td>{{ detail.statusCode }}</td>
              <td>{{ detail.assigneeUserIds.join(', ') || '-' }}</td>
              <td>{{ detail.acceptorUserIds.join(', ') || '-' }}</td>
            </tr>
            <tr v-if="!model.details.length">
              <td colspan="4">
                <div class="pp-empty">暂无工作细节</div>
              </td>
            </tr>
          </tbody>
        </table>
      </ProgressPanel>
    </section>

    <ProgressPanel title="实时动态" subtle="最近项目事件">
      <div class="pp-feed">
        <article v-for="event in model.events" :key="event.id">
          <strong>{{ event.message }}</strong>
          <span>{{ event.type }} · {{ model.formatTime(event.createdAt) }}</span>
        </article>
        <div v-if="!model.events.length" class="pp-empty">暂无动态</div>
      </div>
    </ProgressPanel>
  </section>
</template>
