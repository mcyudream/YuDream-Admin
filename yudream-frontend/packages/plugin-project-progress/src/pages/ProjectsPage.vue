<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectProgressProject } from '../types'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PeoplePicker from '../components/PeoplePicker.vue'

const props = defineProps<{
  model: ProjectProgressModel
}>()

const modalVisible = ref(false)
const router = useRouter()

const modalTitle = computed(() => props.model.selectedProjectId ? '编辑项目' : '新建项目')

const checkInTypeOptions = [
  { label: '图片', value: 'IMAGE' },
  { label: '文件', value: 'FILE' },
  { label: '定位', value: 'LOCATION' },
  { label: 'MC 在线时长', value: 'MINECRAFT_ONLINE' },
]

function typeLabel(value: string) {
  return checkInTypeOptions.find(item => item.value === value)?.label || value
}

async function createProject() {
  props.model.newProject()
  await props.model.loadMinecraftServers()
  modalVisible.value = true
}

async function editProject(project: ProjectProgressProject) {
  props.model.selectProject(project)
  await props.model.loadMinecraftServers()
  modalVisible.value = true
}

async function openProject(project: ProjectProgressProject) {
  await props.model.selectProjectById(project.id)
  await router.push({ name: 'platform-plugin-project-progress-details' })
}

async function saveProject() {
  await props.model.saveProject()
  modalVisible.value = false
}
</script>

<template>
  <section class="pp-page">
    <section class="pp-toolbar">
      <div>
        <span>项目配置</span>
        <h2>项目管理</h2>
      </div>
      <a-space>
        <a-button :loading="model.loading" @click="model.load">刷新</a-button>
        <a-button type="primary" @click="createProject">新建项目</a-button>
      </a-space>
    </section>

    <a-table
      class="pp-arco-table"
      :data="model.projects"
      :loading="model.loading"
      :pagination="false"
      row-key="id"
    >
      <template #columns>
        <a-table-column title="项目">
          <template #cell="{ record }">
            <strong>{{ record.name }}</strong>
            <div class="pp-table-sub">{{ record.description || '暂无描述' }}</div>
          </template>
        </a-table-column>
        <a-table-column title="状态" :width="110">
          <template #cell="{ record }">
            <a-tag :color="record.enabled ? 'green' : 'gray'">
              {{ record.enabled ? '启用中' : '已停用' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="负责人" :width="220">
          <template #cell="{ record }">
            <a-space wrap>
              <a-tag v-for="user in model.userOptionsForIds(record.managerUserIds)" :key="user.id">
                {{ model.userLabel(user) }}
              </a-tag>
            </a-space>
          </template>
        </a-table-column>
        <a-table-column title="成员" :width="90">
          <template #cell="{ record }">
            {{ record.memberUserIds.length }} 人
          </template>
        </a-table-column>
        <a-table-column title="打卡方式" :width="220">
          <template #cell="{ record }">
            <a-space wrap>
              <a-tag v-for="type in record.allowedCheckInTypes" :key="type">
                {{ typeLabel(type) }}
              </a-tag>
            </a-space>
          </template>
        </a-table-column>
        <a-table-column title="MC 服务器" :width="160">
          <template #cell="{ record }">
            {{ record.minecraftPolicy.enabled ? model.serverLabel(record.minecraftPolicy.serverId) : '未启用' }}
          </template>
        </a-table-column>
        <a-table-column title="更新时间" :width="170">
          <template #cell="{ record }">
            {{ model.formatTime(record.updatedAt) }}
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="210" fixed="right">
          <template #cell="{ record }">
            <a-space>
              <a-button type="text" @click="openProject(record)">查看</a-button>
              <a-button type="text" @click="editProject(record)">编辑</a-button>
              <a-button type="text" status="danger" @click="model.deleteProject(record)">删除</a-button>
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>

    <a-modal
      v-model:visible="modalVisible"
      :title="modalTitle"
      :width="860"
      :mask-closable="false"
      unmount-on-close
    >
      <a-form :model="model.projectForm" layout="vertical">
        <div class="pp-form-grid two">
          <a-form-item label="项目名称">
            <a-input v-model="model.projectForm.name" placeholder="例如：主城建设一期" />
          </a-form-item>
          <a-form-item label="启用状态">
            <a-switch v-model="model.projectForm.enabled" checked-text="启用" unchecked-text="停用" />
          </a-form-item>
        </div>
        <a-form-item label="项目描述">
          <a-textarea v-model="model.projectForm.description" :auto-size="{ minRows: 3, maxRows: 5 }" />
        </a-form-item>

        <a-form-item label="协同管理员">
          <PeoplePicker
            v-model="model.projectForm.managerUserIds"
            :model="model"
            title="选择协同管理员"
            placeholder="添加协同管理员"
          />
          <p class="pp-help">发布人会自动成为项目负责人，这里只需要选择额外协同管理员。</p>
        </a-form-item>

        <a-form-item label="项目成员">
          <PeoplePicker
            v-model="model.projectForm.memberUserIds"
            :model="model"
            title="选择项目成员"
            placeholder="添加成员或按部门全选"
          />
        </a-form-item>

        <a-form-item label="进度状态">
          <a-textarea
            v-model="model.projectForm.statusesText"
            :auto-size="{ minRows: 4, maxRows: 6 }"
            placeholder="每行一个状态：编码,显示名称,是否完成,排序"
          />
        </a-form-item>

        <div class="pp-form-grid three">
          <a-form-item label="默认状态">
            <a-select v-model="model.projectForm.defaultStatusCode">
              <a-option v-for="item in model.projectStatusOptions" :key="item.code" :value="item.code">
                {{ item.label }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="完成状态">
            <a-select v-model="model.projectForm.doneStatusCode">
              <a-option v-for="item in model.projectStatusOptions" :key="item.code" :value="item.code">
                {{ item.label }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="返工状态">
            <a-select v-model="model.projectForm.reworkStatusCode" allow-clear>
              <a-option v-for="item in model.projectStatusOptions" :key="item.code" :value="item.code">
                {{ item.label }}
              </a-option>
            </a-select>
          </a-form-item>
        </div>

        <div class="pp-form-grid two">
          <a-form-item label="打卡周期（分钟）">
            <a-input-number v-model="model.projectForm.minCheckInIntervalMinutes" :min="0" />
            <p class="pp-help">1440 表示每天 00:00-24:00 至少打卡一次；480 表示每天每 8 小时一个打卡周期。0 表示不限制周期。</p>
          </a-form-item>
          <a-form-item label="允许打卡方式">
            <a-checkbox-group v-model="model.projectForm.allowedCheckInTypes">
              <a-checkbox v-for="item in checkInTypeOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </a-checkbox>
            </a-checkbox-group>
          </a-form-item>
        </div>

        <div class="pp-form-grid three">
          <a-form-item label="本项目 MC 打卡">
            <a-switch v-model="model.projectForm.minecraftPolicy.enabled" checked-text="启用" unchecked-text="关闭" />
          </a-form-item>
          <a-form-item label="满足时长自动打卡">
            <a-switch v-model="model.projectForm.minecraftPolicy.autoCheckInEnabled" checked-text="启用" unchecked-text="关闭" />
          </a-form-item>
          <a-form-item label="AFK 计入在线时长">
            <a-switch v-model="model.projectForm.minecraftPolicy.includeAfk" checked-text="计入" unchecked-text="排除" />
          </a-form-item>
        </div>

        <div class="pp-form-grid two">
          <a-form-item label="Minecraft 服务器">
            <a-select
              v-model="model.projectForm.minecraftPolicy.serverId"
              allow-clear
              :disabled="!model.projectForm.minecraftPolicy.enabled"
              placeholder="选择服务器"
            >
              <a-option v-for="server in model.minecraftServers" :key="server.id" :value="server.id">
                {{ server.name }}{{ server.currentSeasonName ? ` · ${server.currentSeasonName}` : '' }}
              </a-option>
            </a-select>
          </a-form-item>
          <a-form-item label="要求在线分钟">
            <a-input-number
              v-model="model.projectForm.minecraftPolicy.requiredOnlineMinutes"
              :min="1"
              :disabled="!model.projectForm.minecraftPolicy.enabled"
            />
          </a-form-item>
        </div>
      </a-form>

      <template #footer>
        <a-space>
          <a-button @click="modalVisible = false">取消</a-button>
          <a-button type="primary" :loading="model.saving" @click="saveProject">保存项目</a-button>
        </a-space>
      </template>
    </a-modal>
  </section>
</template>
