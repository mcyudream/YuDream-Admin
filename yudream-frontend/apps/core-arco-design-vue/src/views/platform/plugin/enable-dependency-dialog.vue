<script setup lang="ts">
import type { PluginMarketplaceBatchInstallItem } from '@/api/modules/platform-plugin-marketplace'
import type { PluginDependencyStatus, PluginDependencyStatusItem } from '@/api/modules/platform-plugin'

const props = defineProps<{
  status: PluginDependencyStatus | null
  loading?: boolean
}>()

const emit = defineEmits<{
  confirm: [payload: { includeSoft: string[], installItems: PluginMarketplaceBatchInstallItem[] }]
}>()

const visible = defineModel<boolean>({ required: true })

/** 勾选「一并启用」的已安装软依赖 */
const enableSoft = reactive<Record<string, boolean>>({})
/** 勾选「下载并安装」的缺失依赖 */
const installMissing = reactive<Record<string, boolean>>({})

watch(() => props.status, (status) => {
  for (const key of Object.keys(enableSoft)) {
    delete enableSoft[key]
  }
  for (const key of Object.keys(installMissing)) {
    delete installMissing[key]
  }
  for (const item of status?.dependencies || []) {
    if (!item.required && item.installed && !item.enabled) {
      enableSoft[item.code] = false
    }
    if (!item.installed && item.storeAvailable && Boolean(item.storeVersion)) {
      installMissing[item.code] = item.required
    }
  }
})

const hardDependencies = computed(() => (props.status?.dependencies || []).filter(item => item.required))
const softDependencies = computed(() => (props.status?.dependencies || []).filter(item => !item.required))

/** 硬依赖未安装且市场不可得时阻止启用 */
const blockedHard = computed(() => hardDependencies.value.filter(item => !item.installed
  && !(item.storeAvailable && Boolean(item.storeVersion))))

const includeSoft = computed(() => Object.keys(enableSoft).filter(code => enableSoft[code]))

const installItems = computed<PluginMarketplaceBatchInstallItem[]>(() => {
  const source = props.status?.dependencies || []
  return source
    .filter(item => !item.installed && Boolean(item.storeVersion) && installMissing[item.code])
    .map(item => ({
      code: item.code,
      releaseVersion: item.storeVersion || '',
      sourceCode: item.storeSourceCode,
    }))
})

function statusText(item: PluginDependencyStatusItem) {
  if (item.installed && item.enabled) {
    return `已启用（${item.installedVersion || '未知版本'}）`
  }
  if (item.installed) {
    return `已安装未启用（${item.installedVersion || '未知版本'}），启用时自动先启用`
  }
  if (item.storeAvailable && item.storeVersion) {
    return `未安装，可从市场下载 ${item.storeVersion}`
  }
  return '未安装，市场未提供'
}
</script>

<template>
  <FaModal
    v-model="visible"
    title="启用前置依赖"
    description="硬依赖会自动先于本插件启用；软依赖可选一并启用或下载。"
    :footer="false"
    content-class="sm:max-w-xl"
  >
    <div v-if="status" class="dep-dialog">
      <div v-if="blockedHard.length" class="dep-dialog__blocked">
        <p v-for="item in blockedHard" :key="item.code">
          硬依赖「{{ item.name || item.code }}」未安装且市场未提供，无法启用本插件。
        </p>
      </div>
      <div v-if="!status.dependencies.length" class="dep-dialog__empty">
        该插件没有前置依赖，可直接启用。
      </div>
      <template v-else>
        <ul class="dep-dialog__list">
          <li v-for="item in hardDependencies" :key="`hard-${item.code}`" class="dep-dialog__item">
            <span class="dep-dialog__check">
              <template v-if="!item.installed && item.storeAvailable && item.storeVersion">
                <FaCheckbox v-model="installMissing[item.code]" disabled>
                  <strong>{{ item.name || item.code }}</strong>
                  <FaTag variant="secondary">硬依赖</FaTag>
                </FaCheckbox>
              </template>
              <template v-else>
                <strong>{{ item.name || item.code }}</strong>
                <FaTag variant="secondary">硬依赖</FaTag>
              </template>
            </span>
            <span class="dep-dialog__meta">{{ statusText(item) }}</span>
          </li>
        </ul>
        <div v-if="softDependencies.length" class="dep-dialog__section">
          软依赖
        </div>
        <ul class="dep-dialog__list">
          <li v-for="item in softDependencies" :key="`soft-${item.code}`" class="dep-dialog__item">
            <span class="dep-dialog__check">
              <FaCheckbox
                v-if="item.installed && !item.enabled"
                v-model="enableSoft[item.code]"
                :disabled="loading"
              >
                <strong>{{ item.name || item.code }}</strong>
                <FaTag variant="secondary">软依赖</FaTag>
              </FaCheckbox>
              <FaCheckbox
                v-else-if="!item.installed && item.storeAvailable && Boolean(item.storeVersion)"
                v-model="installMissing[item.code]"
                :disabled="loading"
              >
                <strong>{{ item.name || item.code }}</strong>
                <FaTag variant="secondary">软依赖</FaTag>
              </FaCheckbox>
              <template v-else>
                <strong>{{ item.name || item.code }}</strong>
                <FaTag variant="secondary">软依赖</FaTag>
              </template>
            </span>
            <span class="dep-dialog__meta">{{ statusText(item) }}</span>
          </li>
        </ul>
      </template>
      <div class="dep-dialog__footer">
        <FaButton variant="outline" :disabled="loading" @click="visible = false">
          取消
        </FaButton>
        <FaButton
          :disabled="Boolean(blockedHard.length) || loading"
          :loading="loading"
          @click="emit('confirm', { includeSoft: includeSoft, installItems })"
        >
          安装 {{ installItems.length }} 项 · 启用所选（{{ 1 + includeSoft.length }} 个插件）
        </FaButton>
      </div>
    </div>
  </FaModal>
</template>

<style scoped>
.dep-dialog {
  display: grid;
  gap: 12px;
}

.dep-dialog__blocked {
  padding: 8px 10px;
  border: 1px solid rgb(var(--danger-6));
  border-radius: 6px;
  background: rgb(var(--danger-1));
  color: rgb(var(--danger-6));
  font-size: 13px;
}

.dep-dialog__blocked p {
  margin: 0;
}

.dep-dialog__empty {
  color: var(--color-text-3);
  font-size: 13px;
}

.dep-dialog__section {
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 700;
}

.dep-dialog__list {
  display: grid;
  max-height: 260px;
  gap: 8px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.dep-dialog__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.dep-dialog__check {
  display: flex;
  gap: 8px;
  align-items: center;
}

.dep-dialog__check :deep(.fa-tag) {
  font-size: 11px;
}

.dep-dialog__meta {
  color: var(--color-text-3);
  font-size: 12px;
}

.dep-dialog__footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
