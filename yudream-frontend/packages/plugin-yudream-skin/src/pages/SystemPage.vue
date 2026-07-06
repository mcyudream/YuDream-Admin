<script setup lang="ts">
import type { SkinPluginModel } from '../composables/useSkinPlugin'
import { FaButton, FaIcon, FaInput, FaModal, FaSwitch } from '@fantastic-admin/components'

defineProps<{
  model: SkinPluginModel
}>()

function stateText(state: string) {
  const labels: Record<string, string> = {
    IDLE: '未迁移',
    PENDING: '等待迁移',
    RUNNING: '正在迁移',
    SUCCESS: '迁移完成',
    FAILED: '迁移失败',
  }
  return labels[state] || state
}
</script>

<template>
  <div class="skin-admin-layout">
    <section class="skin-admin-section">
      <div class="skin-admin-section__head">
        <div>
          <h3>插件设置</h3>
          <p>仅维护皮肤插件自己的运行配置，站点公告与系统用户仍由系统模块管理。</p>
        </div>
      </div>

      <a-form :model="model.settingsForm" layout="vertical">
        <div class="skin-admin-form">
          <a-form-item label="每个用户最多角色数">
            <FaInput v-model.number="model.settingsForm.maxPlayersPerUser" type="number" placeholder="0 表示不限制" class="w-full" />
          </a-form-item>
          <a-form-item label="公开上传材质">
            <div class="skin-admin-switch">
              <span>{{ model.settingsForm.allowPublicUpload ? '允许普通用户公开上传' : '仅管理员可公开材质' }}</span>
              <FaSwitch v-model="model.settingsForm.allowPublicUpload" />
            </div>
          </a-form-item>
        </div>
        <div class="mt-2 flex justify-end">
          <FaButton :loading="model.saving === 'settings'" @click="model.saveSettings">
            <FaIcon name="i-ri:save-3-line" />
            保存设置
          </FaButton>
        </div>
      </a-form>
    </section>

    <section class="skin-admin-section">
      <div class="skin-admin-section__head">
        <div>
          <h3>Blessing Skin 数据迁移</h3>
          <p>迁移 MySQL 数据与材质压缩包，日志会通过 SSE 实时推送。</p>
        </div>
        <FaButton variant="outline" @click="model.openMigrationLog">
          <FaIcon name="i-ri:file-list-3-line" />
          查看日志
        </FaButton>
      </div>

      <a-form :model="model.migrationForm" layout="vertical">
        <div class="skin-admin-form three">
          <a-form-item label="数据库主机">
            <FaInput v-model="model.migrationForm.host" placeholder="localhost" class="w-full" />
          </a-form-item>
          <a-form-item label="端口">
            <FaInput v-model.number="model.migrationForm.port" type="number" placeholder="3306" class="w-full" />
          </a-form-item>
          <a-form-item label="数据库名">
            <FaInput v-model="model.migrationForm.database" placeholder="blessing_skin" class="w-full" />
          </a-form-item>
          <a-form-item label="账号">
            <FaInput v-model="model.migrationForm.username" class="w-full" />
          </a-form-item>
          <a-form-item label="密码">
            <FaInput v-model="model.migrationForm.password" type="password" class="w-full" />
          </a-form-item>
          <a-form-item label="材质压缩包">
            <input class="file-input" type="file" accept=".zip,application/zip,application/x-zip-compressed" @change="model.handleMigrationArchive">
          </a-form-item>
        </div>
        <p v-if="model.migrationForm.textureArchiveName" class="hint-line">
          已选择 {{ model.migrationForm.textureArchiveName }}
        </p>
        <div class="mt-3 flex flex-wrap items-center justify-between gap-3">
          <div class="migration-state compact-state">
            <span>{{ stateText(model.migrationStatus.state) }}</span>
            <small v-if="model.migrationStatus.startedAt">开始于 {{ model.dateText(model.migrationStatus.startedAt) }}</small>
          </div>
          <FaButton :loading="model.saving === 'migration' || model.migrationStatus.running" @click="model.runMigration">
            <FaIcon name="i-ri:database-2-line" />
            {{ model.migrationStatus.running ? '正在迁移' : '开始迁移' }}
          </FaButton>
        </div>
      </a-form>

      <div v-if="model.migrationReport" class="report-grid mt-3">
        <span>用户 {{ model.migrationReport.users }}</span>
        <span>角色 {{ model.migrationReport.players }}</span>
        <span>材质 {{ model.migrationReport.textures }}</span>
        <span>衣柜 {{ model.migrationReport.closetItems }}</span>
        <span>配置 {{ model.migrationReport.options }}</span>
      </div>
    </section>

    <FaModal v-model="model.migrationLogVisible" title="迁移日志" :footer="false" class="sm:max-w-4xl">
      <div class="migration-log">
        <div class="migration-log__head">
          <strong>{{ stateText(model.migrationStatus.state) }}</strong>
          <FaButton v-if="model.migrationStatus.running" variant="outline" @click="model.openMigrationLog">
            <FaIcon name="i-ri:refresh-line" />
            重新连接
          </FaButton>
        </div>
        <div class="migration-log__body">
          <div v-if="!model.migrationLogs.length" class="empty-state compact">暂无迁移日志</div>
          <p v-for="item in model.migrationLogs" :key="`${item.time}-${item.message}`" :class="{ error: item.level === 'ERROR' }">
            <time>{{ model.dateText(item.time) }}</time>
            <span>{{ item.message }}</span>
          </p>
        </div>
      </div>
    </FaModal>
  </div>
</template>
