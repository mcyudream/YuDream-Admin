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
        <span>Project Admin</span>
        <h2>项目与工作细节</h2>
      </div>
      <div class="pp-actions">
        <button type="button" @click="model.newProject">新建项目</button>
        <button type="button" @click="model.load">刷新</button>
      </div>
    </section>

    <section class="pp-grid">
      <ProgressPanel title="项目维护" subtle="成员 ID 使用逗号或空格分隔">
        <div class="pp-list compact">
          <button
            v-for="project in model.projects"
            :key="project.id"
            type="button"
            :class="{ active: project.id === model.selectedProjectId }"
            @click="model.selectProject(project); model.reloadProjectData()"
          >
            <strong>{{ project.name }}</strong>
            <span>{{ project.defaultStatusCode }} → {{ project.doneStatusCode }}</span>
          </button>
        </div>
        <form class="pp-form" @submit.prevent="model.saveProject">
          <label>
            <span>项目名称</span>
            <input v-model="model.projectForm.name" autocomplete="off">
          </label>
          <label>
            <span>项目描述</span>
            <textarea v-model="model.projectForm.description" rows="3" />
          </label>
          <label>
            <span>管理员用户 ID</span>
            <input v-model="model.projectForm.managerUserIds" autocomplete="off">
          </label>
          <label>
            <span>成员用户 ID</span>
            <input v-model="model.projectForm.memberUserIds" autocomplete="off">
          </label>
          <label>
            <span>状态定义</span>
            <textarea v-model="model.projectForm.statusesText" rows="5" />
          </label>
          <div class="pp-form-row">
            <label>
              <span>默认状态</span>
              <input v-model="model.projectForm.defaultStatusCode" autocomplete="off">
            </label>
            <label>
              <span>完成状态</span>
              <input v-model="model.projectForm.doneStatusCode" autocomplete="off">
            </label>
            <label>
              <span>返工状态</span>
              <input v-model="model.projectForm.reworkStatusCode" autocomplete="off">
            </label>
          </div>
          <label>
            <span>最小打卡间隔（分钟）</span>
            <input v-model.number="model.projectForm.minCheckInIntervalMinutes" type="number" min="0">
          </label>
          <div class="pp-checks">
            <label><input v-model="model.projectForm.allowedCheckInTypes" type="checkbox" value="IMAGE"> 图片</label>
            <label><input v-model="model.projectForm.allowedCheckInTypes" type="checkbox" value="FILE"> 文件</label>
            <label><input v-model="model.projectForm.allowedCheckInTypes" type="checkbox" value="LOCATION"> 定位</label>
            <label><input v-model="model.projectForm.allowedCheckInTypes" type="checkbox" value="MINECRAFT_ONLINE"> MC 在线时长</label>
          </div>
          <button class="pp-primary" type="submit" :disabled="model.saving">保存项目</button>
        </form>
      </ProgressPanel>

      <ProgressPanel title="工作细节" subtle="发布后可认领或随机分配">
        <template #action>
          <button type="button" @click="model.newDetail">新建细节</button>
        </template>
        <div class="pp-list compact">
          <button
            v-for="detail in model.details"
            :key="detail.id"
            type="button"
            :class="{ active: detail.id === model.selectedDetailId }"
            @click="model.selectDetail(detail)"
          >
            <strong>{{ detail.title }}</strong>
            <span>{{ detail.statusCode }} · {{ detail.assignmentMode }}</span>
          </button>
        </div>
        <form class="pp-form" @submit.prevent="model.saveDetail">
          <label>
            <span>标题</span>
            <input v-model="model.detailForm.title" autocomplete="off">
          </label>
          <label>
            <span>说明</span>
            <textarea v-model="model.detailForm.description" rows="3" />
          </label>
          <div class="pp-form-row">
            <label>
              <span>状态</span>
              <input v-model="model.detailForm.statusCode" autocomplete="off">
            </label>
            <label>
              <span>分配模式</span>
              <select v-model="model.detailForm.assignmentMode">
                <option value="CLAIM">用户认领</option>
                <option value="RANDOM">随机分配</option>
              </select>
            </label>
            <label>
              <span>人数</span>
              <input v-model.number="model.detailForm.requiredAssigneeCount" type="number" min="1">
            </label>
          </div>
          <label>
            <span>候选用户 ID</span>
            <input v-model="model.detailForm.candidateUserIds" autocomplete="off">
          </label>
          <label>
            <span>负责人 ID</span>
            <input v-model="model.detailForm.assigneeUserIds" autocomplete="off">
          </label>
          <label>
            <span>验收人 ID</span>
            <input v-model="model.detailForm.acceptorUserIds" autocomplete="off">
          </label>
          <label>
            <span>截止时间</span>
            <input v-model="model.detailForm.dueAt" type="datetime-local">
          </label>
          <div class="pp-actions">
            <button class="pp-primary" type="submit" :disabled="model.saving">保存细节</button>
            <button v-if="model.selectedDetail" type="button" @click="model.publish(model.selectedDetail)">发布</button>
            <button v-if="model.selectedDetail" type="button" @click="model.randomAssign(model.selectedDetail)">随机分配</button>
          </div>
        </form>
      </ProgressPanel>
    </section>
  </section>
</template>
