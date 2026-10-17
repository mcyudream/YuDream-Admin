<script setup lang="ts">
import type { StatusTone } from './YdEnhancedTable.types'
import { computed, ref, watch } from 'vue'
import FaAvatar from '../basic/avatar/index.vue'
import FaButton from '../basic/button/index.vue'
import FaIcon from '../basic/icon/index.vue'
import FaPopover from '../basic/popover/index.vue'
import './extended.css'

/** 事实清单条目：label 固定，value 允许空（空值弱化显示为 -） */
export interface EntityPeekFact {
  label: string
  value?: string | number | null
  /** 语义色胶囊（状态类事实用） */
  tone?: StatusTone
  /** 等宽字体（会话句柄、ID 等） */
  mono?: boolean
}

export interface EntityPeekChip {
  label: string
  variant?: 'default' | 'secondary' | 'outline' | 'destructive'
}

export interface EntityPeekAction {
  label: string
  icon?: string
  variant?: 'default' | 'outline' | 'secondary' | 'ghost' | 'link' | 'destructive'
  /** 危险操作：自动红色、折叠排在后 */
  danger?: boolean
  disabled?: boolean
  loading?: boolean
  onClick: () => void | Promise<void>
}

const props = withDefaults(defineProps<{
  /** 实体名称（触发器与卡片标题共用） */
  title?: string
  /** 次要标识，如邮箱/编号 */
  subtitle?: string
  avatar?: string
  /** 头像兜底文字（未上传头像时显示） */
  avatarFallback?: string
  status?: { label: string, tone?: StatusTone } | null
  facts?: EntityPeekFact[]
  /** 芯片分组，如角色、部门、标签 */
  chips?: Array<{ label: string, items: EntityPeekChip[] }>
  loading?: boolean
  error?: string
  /** 卡片底部操作（查看态） */
  actions?: EntityPeekAction[]
  /** 允许就地编辑：显示「编辑」入口，编辑态由 v-model:editing 控制 */
  editable?: boolean
  editing?: boolean
  saving?: boolean
  width?: number
  detailLabel?: string
  align?: 'start' | 'center' | 'end'
  side?: 'top' | 'right' | 'bottom' | 'left'
}>(), {
  width: 340,
  align: 'start',
  side: 'bottom',
  detailLabel: '',
  editable: false,
  editing: false,
  saving: false,
})

const emit = defineEmits<{
  'update:editing': [value: boolean]
  'save': []
  'cancel': []
  'detail': []
  'open': []
}>()

const open = ref(false)

// 关闭卡片时退出编辑态，避免下次打开残留半截表单
watch(open, (value) => {
  if (value) {
    emit('open')
    return
  }
  if (props.editing) {
    emit('update:editing', false)
  }
})

const visibleActions = computed(() => (props.actions || []).filter(action => !action.danger))
const dangerActions = computed(() => (props.actions || []).filter(action => action.danger))
const fallbackText = computed(() => props.avatarFallback || props.title?.slice(0, 2) || '')
</script>

<template>
  <FaPopover v-model:open="open" :align :side :side-offset="8" :collision-padding="12" class="yd-entity-peek" :class="[`yd-entity-peek--w${width}`]">
    <slot name="trigger">
      <button type="button" class="yd-entity-peek__trigger" :title="title">
        <slot name="trigger-avatar">
          <FaAvatar v-if="avatar || avatarFallback" :src="avatar || ''" :fallback="fallbackText" :alt="title" class="shrink-0 size-6" />
        </slot>
        <span class="yd-entity-peek__trigger-text">{{ title }}</span>
        <FaIcon name="morph:chevron-down" class="yd-entity-peek__trigger-caret" />
      </button>
    </slot>

    <template #panel>
      <div class="yd-entity-peek__card">
        <header class="yd-entity-peek__head">
          <FaAvatar :src="avatar || ''" :fallback="fallbackText" :alt="title" class="shrink-0 size-10" />
          <div class="yd-entity-peek__identity">
            <span class="yd-entity-peek__name">{{ !title || title === '-' ? '未知实体' : title }}</span>
            <span v-if="subtitle" class="yd-entity-peek__subtitle">{{ subtitle }}</span>
          </div>
          <span v-if="status" class="yd-status-tone shrink-0" :data-tone="status.tone || 'neutral'">{{ status.label }}</span>
        </header>

        <p v-if="loading" class="yd-entity-peek__hint">
          正在加载实体信息…
        </p>
        <p v-else-if="error" class="yd-entity-peek__error">
          {{ error }}
        </p>

        <template v-else>
          <dl v-if="facts?.length" class="yd-entity-peek__facts">
            <div v-for="fact in facts" :key="fact.label" class="yd-entity-peek__fact">
              <dt>{{ fact.label }}</dt>
              <dd>
                <span v-if="fact.value === undefined || fact.value === null || fact.value === ''" class="yd-entity-peek__empty">-</span>
                <span v-else-if="fact.tone" class="yd-status-tone" :data-tone="fact.tone">{{ fact.value }}</span>
                <span v-else :class="fact.mono ? 'yd-entity-peek__mono' : ''">{{ fact.value }}</span>
              </dd>
            </div>
          </dl>

          <div v-for="group in chips || []" :key="group.label" class="yd-entity-peek__chips">
            <span class="yd-entity-peek__chips-label">{{ group.label }}</span>
            <div class="yd-entity-peek__chips-items">
              <span v-for="chip in group.items" :key="chip.label" class="yd-entity-peek__chip">{{ chip.label }}</span>
              <span v-if="!group.items.length" class="yd-entity-peek__empty">-</span>
            </div>
          </div>

          <slot name="extra" />
          <slot v-if="editing" name="editor" />
        </template>

        <footer class="yd-entity-peek__foot">
          <template v-if="editing">
            <FaButton variant="ghost" size="sm" :disabled="saving" @click="emit('cancel')">
              取消
            </FaButton>
            <FaButton size="sm" :loading="saving" @click="emit('save')">
              保存
            </FaButton>
          </template>
          <template v-else>
            <button v-if="detailLabel" type="button" class="yd-entity-peek__detail" @click="emit('detail')">
              {{ detailLabel }}<FaIcon name="morph:arrow-up-right" />
            </button>
            <span class="grow" />
            <FaButton v-if="editable" variant="outline" size="sm" :disabled="loading || !!error" @click="emit('update:editing', true)">
              <FaIcon name="morph:pencil" />编辑
            </FaButton>
            <FaButton
              v-for="action in visibleActions"
              :key="action.label"
              :variant="action.variant || 'outline'"
              size="sm"
              :disabled="action.disabled"
              :loading="action.loading"
              @click="action.onClick()"
            >
              <FaIcon v-if="action.icon" :name="action.icon" />{{ action.label }}
            </FaButton>
            <FaButton
              v-for="action in dangerActions"
              :key="action.label"
              variant="destructive"
              size="sm"
              :disabled="action.disabled"
              :loading="action.loading"
              @click="action.onClick()"
            >
              <FaIcon v-if="action.icon" :name="action.icon" />{{ action.label }}
            </FaButton>
          </template>
        </footer>
      </div>
    </template>
  </FaPopover>
</template>

<style scoped>
.yd-entity-peek__trigger {
  display: inline-flex;
  gap: 0.4rem;
  align-items: center;
  max-width: 100%;
  padding: 0.1rem 0.25rem;
  font: inherit;
  color: inherit;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: var(--yd-control-radius);
  transition: background var(--yd-motion-duration-fast);
}

.yd-entity-peek__trigger:hover {
  background: color-mix(in oklab, currentcolor 8%, transparent);
}

.yd-entity-peek__trigger-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-entity-peek__trigger-caret {
  font-size: 0.75em;
  opacity: 0.45;
}

.yd-entity-peek__card {
  display: grid;
  gap: 0.75rem;
}

.yd-entity-peek__head {
  display: flex;
  gap: 0.65rem;
  align-items: center;
}

.yd-entity-peek__identity {
  display: grid;
  flex: 1;
  min-width: 0;
}

.yd-entity-peek__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-entity-peek__subtitle {
  overflow: hidden;
  font-size: 0.75rem;
  color: var(--yd-muted, oklch(var(--muted-foreground)));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-entity-peek__facts {
  display: grid;
  gap: 0.35rem 0.75rem;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.yd-entity-peek__fact {
  display: grid;
  min-width: 0;
}

.yd-entity-peek__fact dt {
  font-size: 0.7rem;
  color: var(--yd-muted, oklch(var(--muted-foreground)));
}

.yd-entity-peek__fact dd {
  margin: 0;
  overflow: hidden;
  font-size: 0.8rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-entity-peek__mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
}

.yd-entity-peek__empty {
  color: color-mix(in oklab, oklch(var(--muted-foreground)) 45%, transparent);
}

.yd-entity-peek__chips {
  display: grid;
  gap: 0.3rem;
}

.yd-entity-peek__chips-label {
  font-size: 0.7rem;
  color: var(--yd-muted, oklch(var(--muted-foreground)));
}

.yd-entity-peek__chips-items {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.yd-entity-peek__chip {
  padding: 0.05rem 0.4rem;
  font-size: 0.72rem;
  background: color-mix(in oklab, oklch(var(--muted)) 70%, transparent);
  border-radius: var(--yd-control-radius);
}

.yd-entity-peek__hint,
.yd-entity-peek__error {
  margin: 0;
  font-size: 0.8rem;
}

.yd-entity-peek__hint {
  color: var(--yd-muted, oklch(var(--muted-foreground)));
}

.yd-entity-peek__error {
  color: oklch(var(--destructive));
}

.yd-entity-peek__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  align-items: center;
  padding-top: 0.6rem;
  border-top: 1px solid color-mix(in oklab, oklch(var(--border)) 70%, transparent);
}

.yd-entity-peek__foot .grow {
  flex: 1;
}

.yd-entity-peek__detail {
  display: inline-flex;
  gap: 0.25rem;
  align-items: center;
  padding: 0;
  font-size: 0.78rem;
  color: var(--yd-emphasis, oklch(var(--primary)));
  cursor: pointer;
  background: none;
  border: 0;
}

.yd-entity-peek__detail:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
