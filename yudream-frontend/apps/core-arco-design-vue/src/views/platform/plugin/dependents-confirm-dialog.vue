<script setup lang="ts">
import type { PluginDependents } from '@/api/modules/platform-plugin'

const props = defineProps<{
  dependents: PluginDependents | null
  /** unload=卸载 delete=删除记录 */
  action: 'unload' | 'delete'
  loading?: boolean
}>()

const emit = defineEmits<{
  confirm: [cascade: boolean]
}>()

const visible = defineModel<boolean>({ required: true })

const hardDependents = computed(() => (props.dependents?.dependents || []).filter(item => item.required))
const softDependents = computed(() => (props.dependents?.dependents || []).filter(item => !item.required))
const runningDependents = computed(() => (props.dependents?.dependents || []).filter(item => item.loaded || item.enabled))

const actionText = computed(() => (props.action === 'delete' ? '删除' : '卸载'))
</script>

<template>
  <FaModal
    v-model="visible"
    title="存在依赖该插件的插件"
    description="级联处理会按依赖顺序停机；软依赖方可自动降级恢复运行，硬依赖方将保持禁用。"
    :footer="false"
    content-class="sm:max-w-xl"
  >
    <div v-if="dependents" class="dep-dialog">
      <div v-if="hardDependents.length" class="dep-dialog__section">
        硬依赖方（{{ actionText }}后保持禁用，需先恢复本插件再手动启用）
      </div>
      <ul v-if="hardDependents.length" class="dep-dialog__list">
        <li v-for="item in hardDependents" :key="`hard-${item.code}`" class="dep-dialog__item">
          <strong>{{ item.name || item.code }}</strong>
          <span class="dep-dialog__meta">{{ item.code }} · {{ item.enabled ? '运行中' : item.loaded ? '已加载' : '未运行' }}</span>
        </li>
      </ul>
      <div v-if="softDependents.length" class="dep-dialog__section">
        软依赖方（停机后自动降级恢复，相关功能缺失但不影响运行）
      </div>
      <ul v-if="softDependents.length" class="dep-dialog__list">
        <li v-for="item in softDependents" :key="`soft-${item.code}`" class="dep-dialog__item">
          <strong>{{ item.name || item.code }}</strong>
          <span class="dep-dialog__meta">{{ item.code }} · {{ item.enabled ? '运行中' : item.loaded ? '已加载' : '未运行' }}</span>
        </li>
      </ul>
      <div class="dep-dialog__footer">
        <FaButton variant="outline" :disabled="loading" @click="visible = false">
          取消
        </FaButton>
        <FaButton
          variant="destructive"
          :loading="loading"
          @click="emit('confirm', Boolean(runningDependents.length))"
        >
          {{ runningDependents.length ? `级联${actionText}（含 ${runningDependents.length} 个运行中依赖方）` : `确认${actionText}` }}
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
