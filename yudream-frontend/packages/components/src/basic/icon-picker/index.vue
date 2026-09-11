<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed, ref, watch } from 'vue'
import { cn } from '#utils'
import FaButton from '../button/index.vue'
import FaIcon from '../icon/index.vue'
import FaInput from '../input/index.vue'
import FaPopover from '../popover/index.vue'
import FaScrollArea from '../scroll-area/index.vue'
import { YD_ICON_CATALOG, YD_ICON_FREQUENT } from './icons'

defineOptions({
  name: 'YdIconPicker',
})

const PAGE_SIZE = 64

const props = withDefaults(defineProps<{
  placeholder?: string
  disabled?: boolean
  clearable?: boolean
  class?: HTMLAttributes['class']
}>(), {
  placeholder: '选择系统图标',
  disabled: false,
  clearable: true,
})

const emit = defineEmits<{
  change: [value: string | undefined]
}>()

const value = defineModel<string | undefined>()
const open = ref(false)
const keyword = ref('')
const page = ref(1)

const frequentIcons = computed(() => {
  const names = new Set(YD_ICON_FREQUENT)
  if (value.value && /^i-ri:/.test(value.value)) {
    names.add(value.value)
  }
  return YD_ICON_CATALOG.filter(item => names.has(item.name))
})

const filteredIcons = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) {
    return YD_ICON_CATALOG
  }
  return YD_ICON_CATALOG.filter(item =>
    item.name.toLowerCase().includes(query) || item.keywords.toLowerCase().includes(query),
  )
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredIcons.value.length / PAGE_SIZE)))

const pagedIcons = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return filteredIcons.value.slice(start, start + PAGE_SIZE)
})

watch(open, (visible) => {
  if (!visible) {
    keyword.value = ''
  }
  page.value = 1
})

watch(keyword, () => {
  page.value = 1
})

watch(totalPages, (pages) => {
  if (page.value > pages) {
    page.value = pages
  }
})

function onTriggerClick(event: Event) {
  if (props.disabled) {
    event.preventDefault()
    event.stopPropagation()
  }
}

function selectIcon(name: string) {
  value.value = name
  emit('change', name)
  open.value = false
}

function clearIcon() {
  if (props.disabled) {
    return
  }
  value.value = undefined
  emit('change', undefined)
}

function prevPage() {
  if (page.value > 1) {
    page.value -= 1
  }
}

function nextPage() {
  if (page.value < totalPages.value) {
    page.value += 1
  }
}
</script>

<template>
  <FaPopover v-model:open="open" class="p-0 w-80 z-[2100]" align="start">
    <div
      class="yd-icon-picker"
      :class="cn({ 'is-disabled': disabled, 'is-open': open }, props.class)"
      role="combobox"
      :aria-expanded="open"
      :aria-disabled="disabled"
      @click.capture="onTriggerClick"
    >
      <span class="yd-icon-picker__preview">
        <FaIcon v-if="value" :name="value" />
        <FaIcon v-else name="i-ri:apps-2-line" class="opacity-40" />
      </span>
      <span class="yd-icon-picker__label">{{ value || placeholder }}</span>
      <span class="yd-icon-picker__suffix">
        <button
          v-if="clearable && value && !disabled"
          type="button"
          class="yd-icon-picker__clear"
          aria-label="清空"
          @click.stop="clearIcon"
        >
          <FaIcon name="i-ri:close-circle-fill" />
        </button>
        <FaIcon name="i-ri:arrow-down-s-line" />
      </span>
    </div>
    <template #panel>
      <div class="yd-icon-picker__panel">
        <FaInput
          v-model="keyword"
          clearable
          placeholder="搜索图标名称"
          class="w-full"
        />
        <div v-if="!keyword && frequentIcons.length" class="yd-icon-picker__group">
          <div class="yd-icon-picker__title">常用</div>
          <div class="yd-icon-picker__grid">
            <FaButton
              v-for="item in frequentIcons"
              :key="`frequent-${item.name}`"
              variant="ghost"
              size="icon"
              :title="item.name"
              :class="['yd-icon-picker__item', { 'is-active': value === item.name }]"
              @click="selectIcon(item.name)"
            >
              <FaIcon :name="item.name" />
            </FaButton>
          </div>
        </div>
        <div class="yd-icon-picker__group">
          <div class="yd-icon-picker__title">
            {{ keyword ? `匹配 ${filteredIcons.length} 个` : `系统图标 ${filteredIcons.length} 个` }}
          </div>
          <FaScrollArea class="yd-icon-picker__scroll">
            <div v-if="pagedIcons.length" class="yd-icon-picker__grid">
              <FaButton
                v-for="item in pagedIcons"
                :key="item.name"
                variant="ghost"
                size="icon"
                :title="item.name"
                :class="['yd-icon-picker__item', { 'is-active': value === item.name }]"
                @click="selectIcon(item.name)"
              >
                <FaIcon :name="item.name" />
              </FaButton>
            </div>
            <div v-else class="yd-icon-picker__empty">
              没有匹配的图标
            </div>
          </FaScrollArea>
          <div v-if="filteredIcons.length" class="yd-icon-picker__pager">
            <span>{{ filteredIcons.length }} 个</span>
            <div class="yd-icon-picker__pager-actions">
              <FaButton variant="ghost" size="icon" :disabled="page <= 1" aria-label="上一页" @click="prevPage">
                <FaIcon name="i-ri:arrow-left-s-line" />
              </FaButton>
              <span>{{ page }} / {{ totalPages }}</span>
              <FaButton variant="ghost" size="icon" :disabled="page >= totalPages" aria-label="下一页" @click="nextPage">
                <FaIcon name="i-ri:arrow-right-s-line" />
              </FaButton>
            </div>
          </div>
        </div>
      </div>
    </template>
  </FaPopover>
</template>

<style scoped>
.yd-icon-picker {
  display: flex;
  min-height: 36px;
  width: 100%;
  cursor: pointer;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: var(--border-radius-medium, 6px);
  background: var(--color-bg-1);
  transition: border-color 0.15s ease;
}

.yd-icon-picker:hover,
.yd-icon-picker.is-open {
  border-color: var(--color-text-3);
}

.yd-icon-picker.is-disabled {
  cursor: not-allowed;
  background: var(--color-fill-2);
  opacity: 0.6;
}

.yd-icon-picker__preview {
  display: inline-flex;
  width: 20px;
  height: 20px;
  flex: 0 0 20px;
  align-items: center;
  justify-content: center;
  color: var(--color-text-2);
}

.yd-icon-picker__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-icon-picker__suffix {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  color: var(--color-text-3);
}

.yd-icon-picker__clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-3);
}

.yd-icon-picker__panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.yd-icon-picker__group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.yd-icon-picker__title {
  color: var(--color-text-3);
  font-size: 12px;
}

.yd-icon-picker__grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 4px;
}

.yd-icon-picker__item {
  width: 32px;
  height: 32px;
  color: var(--color-text-2);
}

.yd-icon-picker__item.is-active {
  background: var(--color-fill-2);
}

.yd-icon-picker__scroll {
  max-height: 240px;
}

.yd-icon-picker__empty {
  display: grid;
  place-items: center;
  min-height: 80px;
  color: var(--color-text-3);
  font-size: 13px;
}

.yd-icon-picker__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-3);
  font-size: 12px;
}

.yd-icon-picker__pager-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
