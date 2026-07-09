<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import PeoplePicker from '../components/PeoplePicker.vue'
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
        <button type="button" :disabled="model.loading" @click="model.load">刷新</button>
      </div>
    </section>

    <section class="pp-grid">
      <ProgressPanel title="项目列表" subtle="选择项目后维护成员、打卡方式和工作细节">
        <div class="pp-list compact">
          <button
            v-for="project in model.projects"
            :key="project.id"
            type="button"
            :class="{ active: project.id === model.selectedProjectId }"
            @click="model.selectProject(project); model.reloadProjectData()"
          >
            <strong>{{ project.name }}</strong>
            <span>{{ project.enabled ? '启用中' : '已停用' }} · {{ project.memberUserIds.length }} 名成员</span>
          </button>
          <div v-if="!model.projects.length" class="pp-empty">暂无项目</div>
        </div>
      </ProgressPanel>

      <ProgressPanel title="项目配置" subtle="发布人会自动成为项目负责人，可额外添加协同管理员">
        <form class="pp-form" @submit.prevent="model.saveProject">
          <div class="pp-form-row two">
            <label>
              <span>项目名称</span>
              <input v-model="model.projectForm.name" autocomplete="off" placeholder="例如：主城建设一期">
            </label>
            <label>
              <span>状态</span>
              <select v-model="model.projectForm.enabled">
                <option :value="true">启用</option>
                <option :value="false">停用</option>
              </select>
            </label>
          </div>
          <label>
            <span>项目描述</span>
            <textarea v-model="model.projectForm.description" rows="3" placeholder="补充目标、范围或交付要求" />
          </label>

          <PeoplePicker
            v-model="model.projectForm.managerUserIds"
            :model="model"
            title="选择协同管理员"
            placeholder="添加协同管理员"
          />
          <p class="pp-help">当前发布人会由后端自动加入负责人列表，不需要手动选择自己。</p>

          <PeoplePicker
            v-model="model.projectForm.memberUserIds"
            :model="model"
            title="选择项目成员"
            placeholder="添加成员或按部门全选"
          />

          <label>
            <span>进度状态</span>
            <textarea
              v-model="model.projectForm.statusesText"
              rows="4"
              placeholder="每行一个状态：编码,显示名称,是否完成,排序"
            />
          </label>
          <div class="pp-form-row">
            <label>
              <span>默认状态</span>
              <select v-model="model.projectForm.defaultStatusCode">
                <option v-for="item in model.projectStatusOptions" :key="item.code" :value="item.code">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label>
              <span>完成状态</span>
              <select v-model="model.projectForm.doneStatusCode">
                <option v-for="item in model.projectStatusOptions" :key="item.code" :value="item.code">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label>
              <span>返工状态</span>
              <select v-model="model.projectForm.reworkStatusCode">
                <option value="">不指定</option>
                <option v-for="item in model.projectStatusOptions" :key="item.code" :value="item.code">
                  {{ item.label }}
                </option>
              </select>
            </label>
          </div>

          <div class="pp-form-row two">
            <label>
              <span>最小打卡间隔（分钟）</span>
              <input v-model.number="model.projectForm.minCheckInIntervalMinutes" type="number" min="0">
            </label>
            <label>
              <span>Minecraft 服务器</span>
              <input v-model="model.projectForm.minecraftPolicy.serverId" autocomplete="off" placeholder="开启 MC 打卡时填写">
            </label>
          </div>
          <div class="pp-checks">
            <label><input v-model="model.projectForm.allowedCheckInTypes" type="checkbox" value="IMAGE"> 图片</label>
            <label><input v-model="model.projectForm.allowedCheckInTypes" type="checkbox" value="FILE"> 文件</label>
            <label><input v-model="model.projectForm.allowedCheckInTypes" type="checkbox" value="LOCATION"> 定位</label>
            <label><input v-model="model.projectForm.allowedCheckInTypes" type="checkbox" value="MINECRAFT_ONLINE"> MC 在线时长</label>
          </div>
          <div class="pp-checks">
            <label><input v-model="model.projectForm.minecraftPolicy.enabled" type="checkbox"> 本项目启用 MC 打卡</label>
            <label><input v-model="model.projectForm.minecraftPolicy.autoCheckInEnabled" type="checkbox"> 满足在线时长后自动打卡</label>
            <label><input v-model="model.projectForm.minecraftPolicy.includeAfk" type="checkbox"> AFK 计入在线时长</label>
          </div>
          <label>
            <span>要求在线分钟</span>
            <input v-model.number="model.projectForm.minecraftPolicy.requiredOnlineMinutes" type="number" min="1">
          </label>

          <div class="pp-actions">
            <button class="pp-primary" type="submit" :disabled="model.saving">保存项目</button>
            <button
              v-if="model.selectedProject"
              type="button"
              :disabled="model.saving"
              @click="model.deleteProject(model.selectedProject)"
            >
              删除项目
            </button>
            <button
              v-if="model.selectedProject"
              type="button"
              :disabled="model.saving || !model.projectForm.minecraftPolicy.enabled"
              @click="model.autoMinecraftCheckIns"
            >
              检查 MC 自动打卡
            </button>
          </div>
        </form>
      </ProgressPanel>
    </section>

    <section class="pp-grid">
      <ProgressPanel title="工作细节" subtle="任务发布后进入任务中心，用户可直接认领">
        <template #action>
          <button type="button" :disabled="!model.selectedProjectId" @click="model.newDetail">新建细节</button>
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
            <span>{{ model.statusLabel(detail.statusCode) }} · {{ model.assignmentLabel(detail) }}</span>
          </button>
          <div v-if="!model.details.length" class="pp-empty">暂无工作细节</div>
        </div>
      </ProgressPanel>

      <ProgressPanel title="细节配置" subtle="把“谁能做”和“怎么分配”拆成可理解的选项">
        <form class="pp-form" @submit.prevent="model.saveDetail">
          <label>
            <span>标题</span>
            <input v-model="model.detailForm.title" autocomplete="off" :disabled="!model.selectedProjectId">
          </label>
          <label>
            <span>说明</span>
            <textarea v-model="model.detailForm.description" rows="3" :disabled="!model.selectedProjectId" />
          </label>
          <div class="pp-form-row">
            <label>
              <span>当前状态</span>
              <select v-model="model.detailForm.statusCode" :disabled="!model.selectedProjectId">
                <option v-for="item in model.projectStatusOptions" :key="item.code" :value="item.code">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label>
              <span>需要人数</span>
              <input v-model.number="model.detailForm.requiredAssigneeCount" type="number" min="1" :disabled="!model.selectedProjectId">
            </label>
            <label>
              <span>截止时间</span>
              <input v-model="model.detailForm.dueAt" type="datetime-local" :disabled="!model.selectedProjectId">
            </label>
          </div>

          <div class="pp-segment">
            <label>
              <input
                v-model="model.detailForm.assignmentMode"
                type="radio"
                value="CLAIM"
                @change="model.detailForm.candidateScope = 'ALL'"
              >
              用户认领
            </label>
            <label>
              <input
                v-model="model.detailForm.assignmentMode"
                type="radio"
                value="RANDOM"
                @change="model.detailForm.candidateScope = 'PROJECT_MEMBERS'"
              >
              随机分配
            </label>
          </div>

          <div v-if="model.detailForm.assignmentMode === 'CLAIM'" class="pp-choice-group">
            <label>
              <input v-model="model.detailForm.candidateScope" type="radio" value="ALL">
              所有人可认领
            </label>
            <label>
              <input v-model="model.detailForm.candidateScope" type="radio" value="SELECTED">
              仅指定人员可认领
            </label>
          </div>

          <div v-else class="pp-choice-group">
            <label>
              <input v-model="model.detailForm.candidateScope" type="radio" value="PROJECT_MEMBERS">
              从项目成员中随机
            </label>
            <label>
              <input v-model="model.detailForm.candidateScope" type="radio" value="SELECTED">
              从指定人员中随机
            </label>
          </div>

          <PeoplePicker
            v-if="model.detailForm.candidateScope === 'SELECTED'"
            v-model="model.detailForm.candidateUserIds"
            :model="model"
            title="选择候选人员"
            placeholder="选择可参与人员"
          />

          <div class="pp-readonly-users">
            <span>当前负责人</span>
            <div class="pp-chip-list">
              <span v-if="!model.detailForm.assigneeUserIds.length" class="pp-muted">发布后由认领或随机分配产生</span>
              <span v-for="user in model.userOptionsForIds(model.detailForm.assigneeUserIds)" :key="user.id" class="pp-chip">
                {{ model.userLabel(user) }}
              </span>
            </div>
          </div>

          <PeoplePicker
            v-model="model.detailForm.acceptorUserIds"
            :model="model"
            title="选择验收人"
            placeholder="选择验收人（留空则项目负责人可验收）"
          />

          <div class="pp-actions">
            <button class="pp-primary" type="submit" :disabled="!model.selectedProjectId || model.saving">保存细节</button>
            <button v-if="model.selectedDetail" type="button" :disabled="model.saving" @click="model.publish(model.selectedDetail)">发布</button>
            <button
              v-if="model.selectedDetail && model.detailForm.assignmentMode === 'RANDOM'"
              type="button"
              :disabled="model.saving"
              @click="model.randomAssign(model.selectedDetail)"
            >
              立即随机分配
            </button>
          </div>
        </form>
      </ProgressPanel>
    </section>
  </section>
</template>
