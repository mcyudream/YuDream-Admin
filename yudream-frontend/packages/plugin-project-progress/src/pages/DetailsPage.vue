<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectWorkDetail } from '../types'
import { computed, ref } from 'vue'
import PeoplePicker from '../components/PeoplePicker.vue'
import EvidenceFileList from '../components/EvidenceFileList.vue'

const props = defineProps<{
  model: ProjectProgressModel
}>()

const modalVisible = ref(false)

const modalTitle = computed(() => props.model.selectedDetailId ? '编辑工作细节' : '新建工作细节')
const editableStatusOptions = computed(() => props.model.projectStatusOptions.filter((item) => {
  const doneCode = props.model.selectedProject?.doneStatusCode
  return item.code !== doneCode || props.model.selectedDetail?.statusCode === item.code
}))

async function selectProject(projectId: unknown) {
  await props.model.selectProjectById(String(projectId || ''))
}

function createDetail() {
  props.model.newDetail()
  modalVisible.value = true
}

function editDetail(detail: ProjectWorkDetail) {
  props.model.selectDetail(detail)
  modalVisible.value = true
}

async function saveDetail() {
  await props.model.saveDetail()
  modalVisible.value = false
}
</script>

<template>
  <section class="pp-page">
    <section class="pp-toolbar">
      <div>
        <span>任务拆分</span>
        <h2>工作细节</h2>
      </div>
      <a-space>
        <a-select
          :model-value="model.selectedProjectId"
          placeholder="选择项目"
          class="pp-project-select"
          @change="selectProject"
        >
          <a-option v-for="project in model.projects" :key="project.id" :value="project.id">
            {{ project.name }}
          </a-option>
        </a-select>
        <a-button :loading="model.loading" @click="model.load">刷新</a-button>
        <a-button type="primary" :disabled="!model.selectedProjectId" @click="createDetail">
          新建细节
        </a-button>
      </a-space>
    </section>

    <a-table
      class="pp-arco-table"
      :data="model.details"
      :loading="model.loading"
      :pagination="false"
      row-key="id"
    >
      <template #columns>
        <a-table-column title="工作细节">
          <template #cell="{ record }">
            <strong>{{ record.title }}</strong>
            <div class="pp-table-sub">{{ record.description || '暂无说明' }}</div>
            <div v-if="record.acceptanceSummary || record.acceptanceFiles.length" class="pp-material-box mt-3">
              <p>{{ record.acceptanceSummary || '暂无验收说明' }}</p>
              <EvidenceFileList :model="model" :files="record.acceptanceFiles" compact />
            </div>
          </template>
        </a-table-column>
        <a-table-column title="状态" :width="150">
          <template #cell="{ record }">
            <a-space wrap>
              <a-tag :color="record.pendingAcceptance ? 'orange' : 'arcoblue'">
                {{ model.detailStatusLabel(record) }}
              </a-tag>
              <a-tag v-if="record.pendingAcceptance" color="orange">待验收</a-tag>
            </a-space>
          </template>
        </a-table-column>
        <a-table-column title="分配方式" :width="150">
          <template #cell="{ record }">
            {{ model.assignmentLabel(record) }}
          </template>
        </a-table-column>
        <a-table-column title="负责人" :width="240">
          <template #cell="{ record }">
            <a-space wrap>
              <span v-if="!record.assigneeUserIds.length" class="pp-muted">未分配</span>
              <a-tag v-for="user in model.userOptionsForIds(record.assigneeUserIds)" :key="user.id">
                {{ model.userLabel(user) }}
              </a-tag>
            </a-space>
          </template>
        </a-table-column>
        <a-table-column title="验收人" :width="220">
          <template #cell="{ record }">
            <a-space wrap>
              <a-tag v-if="!record.acceptorUserIds.length">项目负责人</a-tag>
              <a-tag v-for="user in model.userOptionsForIds(record.acceptorUserIds)" :key="user.id">
                {{ model.userLabel(user) }}
              </a-tag>
            </a-space>
          </template>
        </a-table-column>
        <a-table-column title="发布时间" :width="120">
          <template #cell="{ record }">
            <a-tag :color="record.published ? 'green' : 'gray'">
              {{ record.published ? '已发布' : '草稿' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="截止时间" :width="170">
          <template #cell="{ record }">
            {{ model.formatTime(record.dueAt) }}
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="260" fixed="right">
          <template #cell="{ record }">
            <a-space>
              <a-button type="text" @click="editDetail(record)">编辑</a-button>
              <a-button type="text" :disabled="record.published" @click="model.publish(record)">
                发布
              </a-button>
              <a-button
                v-if="record.assignmentMode === 'RANDOM'"
                type="text"
                @click="model.randomAssign(record)"
              >
                随机分配
              </a-button>
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>

    <a-modal
      v-model:visible="modalVisible"
      :title="modalTitle"
      :width="820"
      :mask-closable="false"
      unmount-on-close
    >
      <a-form :model="model.detailForm" layout="vertical">
        <a-form-item label="标题">
          <a-input v-model="model.detailForm.title" :disabled="!model.selectedProjectId" />
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea
            v-model="model.detailForm.description"
            :auto-size="{ minRows: 3, maxRows: 5 }"
            :disabled="!model.selectedProjectId"
          />
        </a-form-item>

        <div class="pp-form-grid three">
          <a-form-item label="当前状态">
            <a-select v-model="model.detailForm.statusCode" :disabled="!model.selectedProjectId">
              <a-option v-for="item in editableStatusOptions" :key="item.code" :value="item.code">
                {{ item.label }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="需要人数">
            <a-input-number v-model="model.detailForm.requiredAssigneeCount" :min="1" :disabled="!model.selectedProjectId" />
          </a-form-item>
          <a-form-item label="截止时间">
            <a-date-picker
              v-model="model.detailForm.dueAt"
              show-time
              format="YYYY-MM-DD HH:mm:ss"
              :disabled="!model.selectedProjectId"
            />
          </a-form-item>
        </div>

        <a-form-item label="分配方式">
          <a-radio-group v-model="model.detailForm.assignmentMode" type="button">
            <a-radio value="CLAIM" @change="model.detailForm.candidateScope = 'ALL'">用户认领</a-radio>
            <a-radio value="RANDOM" @change="model.detailForm.candidateScope = 'PROJECT_MEMBERS'">随机分配</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="参与范围">
          <a-radio-group
            v-if="model.detailForm.assignmentMode === 'CLAIM'"
            v-model="model.detailForm.candidateScope"
            type="button"
          >
            <a-radio value="ALL">所有人可认领</a-radio>
            <a-radio value="SELECTED">仅指定人员可认领</a-radio>
          </a-radio-group>
          <a-radio-group v-else v-model="model.detailForm.candidateScope" type="button">
            <a-radio value="PROJECT_MEMBERS">从项目成员中随机</a-radio>
            <a-radio value="SELECTED">从指定人员中随机</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item v-if="model.detailForm.candidateScope === 'SELECTED'" label="候选人员">
          <PeoplePicker
            v-model="model.detailForm.candidateUserIds"
            :model="model"
            title="选择候选人员"
            placeholder="选择可参与人员"
          />
        </a-form-item>

        <a-form-item label="当前负责人">
          <a-space wrap>
            <span v-if="!model.detailForm.assigneeUserIds.length" class="pp-muted">发布后由认领或随机分配产生</span>
            <a-tag v-for="user in model.userOptionsForIds(model.detailForm.assigneeUserIds)" :key="user.id">
              {{ model.userLabel(user) }}
            </a-tag>
          </a-space>
        </a-form-item>

        <a-form-item label="验收人">
          <PeoplePicker
            v-model="model.detailForm.acceptorUserIds"
            :model="model"
            title="选择验收人"
            placeholder="留空则项目负责人可验收"
          />
        </a-form-item>
      </a-form>

      <template #footer>
        <a-space>
          <a-button @click="modalVisible = false">取消</a-button>
          <a-button type="primary" :loading="model.saving" :disabled="!model.selectedProjectId" @click="saveDetail">
            保存细节
          </a-button>
        </a-space>
      </template>
    </a-modal>
  </section>
</template>
