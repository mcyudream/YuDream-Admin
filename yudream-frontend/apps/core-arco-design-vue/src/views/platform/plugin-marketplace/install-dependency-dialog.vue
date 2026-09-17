<script setup lang="ts">
import type { PluginMarketplaceBatchInstallItem, PluginMarketplaceInstallPlan, PluginMarketplaceInstallPlanEntry } from '@/api/modules/platform-plugin-marketplace'

const props = defineProps<{
  plan: PluginMarketplaceInstallPlan | null
  loading?: boolean
}>()

const emit = defineEmits<{
  confirm: [items: PluginMarketplaceBatchInstallItem[]]
}>()

const visible = defineModel<boolean>({ required: true })

const selections = reactive<Record<string, boolean>>({})

watch(() => props.plan, (plan) => {
  for (const key of Object.keys(selections)) {
    delete selections[key]
  }
  for (const entry of plan?.entries || []) {
    selections[entry.code] = entry.required ? true : !satisfied(entry)
  }
})

function satisfied(entry: PluginMarketplaceInstallPlanEntry) {
  return entry.installed && entry.versionSatisfied
}

/** 可执行下载动作：未满足、市场有候选版本且候选自身可安装。 */
function actionable(entry: PluginMarketplaceInstallPlanEntry) {
  return !satisfied(entry) && entry.storeAvailable && Boolean(entry.storeVersion) && entry.installable
}

/** 硬依赖必须下载，不允许取消勾选。 */
function locked(entry: PluginMarketplaceInstallPlanEntry) {
  return entry.required && actionable(entry)
}

const blockedEntries = computed(() =>
  (props.plan?.entries || []).filter(entry => entry.required && !satisfied(entry) && !actionable(entry)),
)

const selectedItems = computed<PluginMarketplaceBatchInstallItem[]>(() =>
  (props.plan?.entries || [])
    .filter(entry => actionable(entry) && selections[entry.code])
    .map(entry => ({
      code: entry.code,
      releaseVersion: entry.storeVersion || '',
      sourceCode: entry.storeSourceCode,
    })),
)

function entryStatusText(entry: PluginMarketplaceInstallPlanEntry) {
  if (satisfied(entry)) {
    return entry.installedVersion ? `已安装 ${entry.installedVersion}` : '已安装'
  }
  if (entry.installed) {
    return entry.storeVersion
      ? `已装 ${entry.installedVersion || '未知'}，将更新到 ${entry.storeVersion}`
      : `已装 ${entry.installedVersion || '未知'}，市场无可更新版本`
  }
  if (entry.storeAvailable && entry.storeVersion) {
    return entry.installable ? `将安装 ${entry.storeVersion}` : (entry.installDisabledReason || '候选版本不可安装')
  }
  return '市场未提供'
}
</script>

<template>
  <FaModal
    v-model="visible"
    title="安装前置依赖"
    description="勾选需要一并下载的依赖；硬依赖必须安装，软依赖可选。"
    :footer="false"
    content-class="sm:max-w-xl"
  >
    <div v-if="plan" class="dep-dialog">
      <div v-if="blockedEntries.length" class="dep-dialog__blocked">
        <p v-for="entry in blockedEntries" :key="entry.code">
          必需依赖「{{ entry.displayName || entry.code }}」不可用：{{ entry.installDisabledReason || '市场未提供满足要求的版本' }}
        </p>
      </div>
      <div v-if="!plan.entries.length" class="dep-dialog__empty">
        该版本没有前置依赖，可直接安装。
      </div>
      <ul v-else class="dep-dialog__list">
        <li v-for="entry in plan.entries" :key="entry.code" class="dep-dialog__item">
          <span class="dep-dialog__check">
            <FaCheckbox
              v-if="actionable(entry)"
              v-model="selections[entry.code]"
              :disabled="locked(entry) || loading"
            >
              <strong>{{ entry.displayName || entry.code }}</strong>
              <FaTag variant="secondary">{{ entry.required ? '硬依赖' : '软依赖' }}</FaTag>
            </FaCheckbox>
            <template v-else>
              <strong>{{ entry.displayName || entry.code }}</strong>
              <FaTag variant="secondary">{{ entry.required ? '硬依赖' : '软依赖' }}</FaTag>
            </template>
          </span>
          <span class="dep-dialog__meta">{{ entryStatusText(entry) }}<template v-if="entry.storeSourceName"> · {{ entry.storeSourceName }}</template></span>
        </li>
      </ul>
      <div class="dep-dialog__footer">
        <FaButton variant="outline" :disabled="loading" @click="visible = false">
          取消
        </FaButton>
        <FaButton
          :disabled="Boolean(blockedEntries.length) || loading"
          :loading="loading"
          @click="emit('confirm', selectedItems)"
        >
          安装所选（{{ selectedItems.length }} 项依赖）
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

.dep-dialog__list {
  display: grid;
  max-height: 320px;
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
