<script setup lang="ts">
import { computed, ref } from 'vue'
import FaButton from '../basic/button/index.vue'
import FaIcon from '../basic/icon/index.vue'

export interface YdSessionItem {
  id: string
  title: string
  messageCount?: number
  pinned?: boolean
  scopeType?: string
  updatedAt?: string
}

const props = defineProps<{
  sessions: YdSessionItem[]
  activeId?: string
  loading?: boolean
}>()

const emits = defineEmits<{
  select: [id: string]
  create: []
  rename: [session: YdSessionItem]
  pin: [session: YdSessionItem]
  remove: [session: YdSessionItem]
}>()

const keyword = ref('')

const filtered = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) {
    return props.sessions
  }
  return props.sessions.filter(session => session.title.toLowerCase().includes(text))
})

const pinnedSessions = computed(() => filtered.value.filter(session => session.pinned))
const normalSessions = computed(() => filtered.value.filter(session => !session.pinned))

function scopeIcon(scopeType?: string): string {
  if (scopeType === 'WIKI') {
    return 'morph:book'
  }
  if (scopeType === 'AGENT') {
    return 'morph:bot'
  }
  return 'morph:message-circle'
}
</script>

<template>
  <aside class="yd-session-list">
    <div class="yd-session-list__head">
      <FaButton variant="outline" class="yd-session-list__new" @click="emits('create')">
        <FaIcon name="morph:plus" />
        新建会话
      </FaButton>
      <div class="yd-session-list__search">
        <FaIcon name="morph:search" class="yd-session-list__search-icon" />
        <input v-model="keyword" type="search" placeholder="搜索会话" class="yd-session-list__search-input">
      </div>
    </div>

    <div class="yd-session-list__body">
      <div v-if="loading" class="yd-session-list__empty">
        加载中…
      </div>
      <div v-else-if="!filtered.length" class="yd-session-list__empty">
        {{ sessions.length ? '没有匹配的会话' : '暂无会话，点击上方开始新对话' }}
      </div>

      <template v-if="pinnedSessions.length">
        <div class="yd-session-list__group">
          已置顶
        </div>
        <button
          v-for="session in pinnedSessions"
          :key="session.id"
          type="button"
          class="yd-session"
          :class="{ 'is-active': session.id === activeId }"
          @click="emits('select', session.id)"
        >
          <FaIcon :name="scopeIcon(session.scopeType)" class="yd-session__icon" />
          <span class="yd-session__title">{{ session.title }}</span>
          <FaIcon name="morph:pin" class="yd-session__pin" />
          <span class="yd-session__actions">
            <button type="button" title="重命名" @click.stop="emits('rename', session)"><FaIcon name="morph:pencil" /></button>
            <button type="button" title="取消置顶" @click.stop="emits('pin', session)"><FaIcon name="morph:pin" /></button>
            <button type="button" title="删除" @click.stop="emits('remove', session)"><FaIcon name="morph:trash-2" /></button>
          </span>
        </button>
      </template>

      <div v-if="normalSessions.length" class="yd-session-list__group">
        最近对话
      </div>
      <button
        v-for="session in normalSessions"
        :key="session.id"
        type="button"
        class="yd-session"
        :class="{ 'is-active': session.id === activeId }"
        @click="emits('select', session.id)"
      >
        <FaIcon :name="scopeIcon(session.scopeType)" class="yd-session__icon" />
        <span class="yd-session__title">{{ session.title }}</span>
        <span class="yd-session__actions">
          <button type="button" title="重命名" @click.stop="emits('rename', session)"><FaIcon name="morph:pencil" /></button>
          <button type="button" title="置顶" @click.stop="emits('pin', session)"><FaIcon name="morph:pin" /></button>
          <button type="button" title="删除" @click.stop="emits('remove', session)"><FaIcon name="morph:trash-2" /></button>
        </span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.yd-session-list {
  display: flex;
  flex-direction: column;
  width: 248px;
  min-width: 220px;
  background: var(--color-bg-2);
}

.yd-session-list__head {
  display: grid;
  gap: 10px;
  padding: 14px 14px 10px;
}

.yd-session-list__new {
  justify-content: center;
  width: 100%;
  border-radius: 10px;
}

.yd-session-list__search {
  display: flex;
  gap: 7px;
  align-items: center;
  padding: 0 11px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 9px;
}

.yd-session-list__search-icon {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--color-text-3);
}

.yd-session-list__search-input {
  flex: 1;
  min-width: 0;
  padding: 7px 0;
  font: inherit;
  font-size: 13px;
  color: var(--color-text-1);
  outline: 0;
  background: transparent;
  border: 0;
}

.yd-session-list__body {
  flex: 1;
  min-height: 0;
  padding: 6px 10px 16px;
  overflow-y: auto;
}

.yd-session-list__group {
  padding: 12px 8px 5px;
  font-size: 11px;
  color: var(--color-text-3);
}

.yd-session-list__empty {
  padding: 28px 12px;
  font-size: 13px;
  color: var(--color-text-3);
  text-align: center;
}

.yd-session {
  position: relative;
  display: flex;
  gap: 9px;
  align-items: center;
  width: 100%;
  padding: 9px 10px;
  font: inherit;
  font-size: 13px;
  color: var(--color-text-2);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 9px;
}

.yd-session:hover {
  color: var(--color-text-1);
  background: var(--color-fill-2);
}

.yd-session.is-active {
  color: var(--color-text-1);
  background: color-mix(in oklab, var(--yd-emphasis, oklch(var(--primary))) 10%, transparent);
}

.yd-session__icon {
  flex-shrink: 0;
  font-size: 15px;
  color: var(--color-text-3);
}

.yd-session.is-active .yd-session__icon {
  color: var(--yd-emphasis, oklch(var(--primary)));
}

.yd-session__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-session__pin {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--yd-emphasis, oklch(var(--primary)));
}

.yd-session__actions {
  position: absolute;
  right: 6px;
  display: none;
  gap: 1px;
  padding-left: 16px;
  background: linear-gradient(90deg, transparent, var(--color-fill-2) 30%);
  border-radius: 6px;
}

.yd-session.is-active .yd-session__actions {
  background: linear-gradient(90deg, transparent, color-mix(in oklab, var(--yd-emphasis, oklch(var(--primary))) 10%, transparent) 30%);
}

.yd-session:hover .yd-session__actions {
  display: inline-flex;
}

.yd-session:hover .yd-session__pin {
  display: none;
}

.yd-session__actions button {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  color: var(--color-text-3);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 5px;
}

.yd-session__actions button:hover {
  color: var(--yd-emphasis, oklch(var(--primary)));
  background: var(--color-bg-1);
}
</style>
