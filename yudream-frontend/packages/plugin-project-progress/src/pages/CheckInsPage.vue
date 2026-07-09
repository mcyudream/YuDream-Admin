<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectCheckIn } from '../types'
import { computed, watch } from 'vue'

const props = defineProps<{
  model: ProjectProgressModel
}>()

const manualTypeOptions = computed(() => {
  const allowed = props.model.selectedProject?.allowedCheckInTypes || []
  return [
    { label: '图片', value: 'IMAGE' },
    { label: '文件', value: 'FILE' },
    { label: '定位', value: 'LOCATION' },
  ].filter(item => allowed.includes(item.value))
})

function typeLabel(value: string) {
  const labels: Record<string, string> = {
    IMAGE: '图片',
    FILE: '文件',
    LOCATION: '定位',
    MINECRAFT_ONLINE: 'MC 在线时长',
  }
  return labels[value] || value
}

watch(manualTypeOptions, (options) => {
  if (options.length && !options.some(item => item.value === props.model.checkInForm.type)) {
    props.model.checkInForm.type = options[0].value
  }
}, { immediate: true })

async function selectProject(projectId: unknown) {
  await props.model.selectProjectById(String(projectId || ''))
}

function fileNames(files: ProjectCheckIn['files']) {
  return files.map(file => file.filename).join('、')
}
</script>

<template>
  <section class="pp-page">
    <section class="pp-toolbar">
      <div>
        <span>进度打卡</span>
        <h2>项目打卡</h2>
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
      </a-space>
    </section>

    <section class="pp-grid">
      <section class="pp-panel">
        <header class="pp-panel-head">
          <div>
            <h3>提交打卡</h3>
            <span>{{ model.selectedProject?.name || '请选择项目' }}</span>
          </div>
        </header>

        <a-form :model="model.checkInForm" layout="vertical" @submit.prevent>
          <a-form-item label="打卡类型">
            <a-radio-group v-model="model.checkInForm.type" type="button" :disabled="!model.selectedProjectId">
              <a-radio v-for="item in manualTypeOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </a-radio>
            </a-radio-group>
            <a-empty v-if="model.selectedProjectId && !manualTypeOptions.length" description="该项目未开放手动打卡" />
          </a-form-item>
          <a-form-item label="说明">
            <a-textarea v-model="model.checkInForm.summary" :auto-size="{ minRows: 3, maxRows: 5 }" />
          </a-form-item>
          <a-form-item v-if="model.checkInForm.type !== 'LOCATION'" label="上传证明">
            <input
              class="pp-native-file"
              type="file"
              @change="model.evidenceFile = ($event.target as HTMLInputElement).files?.[0] || null"
            >
          </a-form-item>
          <template v-if="model.checkInForm.type === 'LOCATION'">
            <a-form-item label="地址">
              <a-input v-model="model.checkInForm.address" />
            </a-form-item>
            <div class="pp-form-grid two">
              <a-form-item label="纬度">
                <a-input v-model="model.checkInForm.latitude" />
              </a-form-item>
              <a-form-item label="经度">
                <a-input v-model="model.checkInForm.longitude" />
              </a-form-item>
            </div>
          </template>
          <a-space>
            <a-button
              type="primary"
              :loading="model.saving"
              :disabled="!model.selectedProjectId || !manualTypeOptions.length"
              @click="model.submitCheckIn"
            >
              提交打卡
            </a-button>
            <a-button
              :disabled="!model.canProjectMinecraftCheckIn() || model.saving"
              @click="model.minecraftCheckIn()"
            >
              MC 在线时长打卡
            </a-button>
            <a-button
              :disabled="!model.selectedProject?.minecraftPolicy.enabled || model.saving"
              @click="model.autoMinecraftCheckIns"
            >
              检查自动打卡
            </a-button>
          </a-space>
        </a-form>
      </section>

      <section class="pp-panel">
        <header class="pp-panel-head">
          <div>
            <h3>项目打卡记录</h3>
            <span>{{ model.checkIns.length }} 条记录</span>
          </div>
        </header>

        <a-table :data="model.checkIns" :pagination="false" row-key="id" size="small">
          <template #columns>
            <a-table-column title="打卡人" :width="150">
              <template #cell="{ record }">
                {{ model.userLabel(model.usersById[record.userId]) }}
              </template>
            </a-table-column>
            <a-table-column title="类型" :width="120">
              <template #cell="{ record }">
                <a-tag>{{ typeLabel(record.type) }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="说明">
              <template #cell="{ record }">
                <strong>{{ record.summary || '无说明' }}</strong>
                <div v-if="record.location" class="pp-table-sub">{{ record.location.address }}</div>
                <div v-if="record.minecraft" class="pp-table-sub">
                  {{ model.serverLabel(record.minecraft.serverId) }} · 有效在线 {{ model.minutes(record.minecraft.effectiveOnlineMillis) }}
                </div>
                <div v-if="record.files.length" class="pp-table-sub">
                  {{ fileNames(record.files) }}
                </div>
              </template>
            </a-table-column>
            <a-table-column title="时间" :width="170">
              <template #cell="{ record }">
                {{ model.formatTime(record.createdAt) }}
              </template>
            </a-table-column>
          </template>
        </a-table>
        <a-empty v-if="!model.checkIns.length" description="暂无打卡记录" />
      </section>
    </section>
  </section>
</template>
