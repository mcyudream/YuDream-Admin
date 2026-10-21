<script setup lang="ts">
// 检查器手风琴分区：同一时间只展开一个分区（记住上次展开的分区），标题行显示摘要，避免右侧栏过长、元素互相挤压。
import { computed, ref, useId } from 'vue'

defineOptions({ name: 'StudioInspectorSection' })

const props = defineProps<{ id: string, title: string, summary?: string }>()

const STORAGE_KEY = 'yd.studio.inspector.section'
const openId = useInspectorAccordion()
const open = computed(() => openId.value === props.id)
const bodyId = useId()

function toggle() {
  openId.value = open.value ? '' : props.id
  localStorage.setItem(STORAGE_KEY, openId.value)
}
</script>

<script lang="ts">
// 模块级共享：所有分区实例共用一个展开状态，实现手风琴
const sharedOpen = ref(localStorage.getItem('yd.studio.inspector.section') ?? 'layout')
function useInspectorAccordion() {
  return sharedOpen
}
</script>

<template>
  <section class="studio-inspector-section" :aria-label="title">
    <YdAction class="studio-inspector-section__trigger" :aria-expanded="open" :aria-controls="bodyId" @click="toggle">
      <span class="studio-inspector-section__title">{{ title }}</span>
      <span class="studio-inspector-section__summary">{{ open ? '' : summary }}</span>
      <YdIcon class="shrink-0" :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
    </YdAction>
    <div v-show="open" :id="bodyId" class="studio-inspector-section__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.studio-inspector-section {
  border-top: 1px solid var(--color-border-2);
}
.studio-inspector-section__trigger {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-width: 0;
  padding: 10px 0;
  color: var(--color-text-1);
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
}
.studio-inspector-section__title {
  flex: none;
  font-size: 13px;
  font-weight: 600;
}
.studio-inspector-section__summary {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  color: var(--color-text-3);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.studio-inspector-section__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 12px;
}
</style>
