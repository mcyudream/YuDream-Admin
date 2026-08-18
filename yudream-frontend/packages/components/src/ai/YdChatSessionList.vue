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
    return 'i-ri:book-2-line'
  }
  if (scopeType === 'AGENT') {
    return 'i-ri:robot-2-line'
  }
  return 'i-ri:chat-3-line'
}
</script>

<template>
  <aside class="yd-session-list">
    <div class="yd-session-list__head">
      <FaButton variant="outline" class="yd-session-list__new" @click="emits('create')">
        <FaIcon name="i-ri:add-line" />
        新建会话
      </FaButton>
      <div class="yd-session-list__search">
        <FaIcon name="i-ri:search-line" class="yd-session-list__search-icon" />
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
          <FaIcon name="i-ri:pushpin-2-fill" class="yd-session__pin" />
          <span class="yd-session__actions">
            <button type="button" title="重命名" @click.stop="emits('rename', session)"><FaIcon name="i-ri:edit-line" /></button>
            <button type="button" title="取消置顶" @click.stop="emits('pin', session)"><FaIcon name="i-ri:pushpin-line" /></button>
            <button type="button" title="删除" @click.stop="emits('remove', session)"><FaIcon name="i-ri:delete-bin-line" /></button>
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
          <button type="button" title="重命名" @click.stop="emits('rename', session)"><FaIcon name="i-ri:edit-line" /></button>
          <button type="button" title="置顶" @click.stop="emits('pin', session)"><FaIcon name="i-ri:pushpin-line" /></button>
          <button type="button" title="删除" @click.stop="emits('remove', session)"><FaIcon name="i-ri:delete-bin-line" /></button>
        </span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.yd-session-list {
  display: flex;
  width: 248px;
  min-width: 220px;
  flex-direction: column;
  background: var(--color-bg-2);
}

.yd-session-list__head {
  display: grid;
  gap: 10px;
  padding: 14px 14px 10px;
}

.yd-session-list__new {
  width: 100%;
  justify-content: center;
  border-radius: 10px;
}

.yd-session-list__search {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 11px;
  border: 1px solid var(--color-border-2);
  border-radius: 9px;
  background: var(--color-bg-1);
}

.yd-session-list__search-icon {
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 14px;
}

.yd-session-list__search-input {
  min-width: 0;
  flex: 1;
  padding: 7px 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text-1);
  font: inherit;
  font-size: 13px;
}

.yd-session-list__body {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 6px 10px 16px;
}

.yd-session-list__group {
  padding: 12px 8px 5px;
  color: var(--color-text-3);
  font-size: 11px;
}

.yd-session-list__empty {
  padding: 28px 12px;
  color: var(--color-text-3);
  font-size: 13px;
  text-align: center;
}

.yd-session {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 9px 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  text-align: left;
}

.yd-session:hover {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}

.yd-session.is-active {
  background: rgba(var(--primary-6), 0.1);
  color: var(--color-text-1);
}

.yd-session__icon {
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 15px;
}

.yd-session.is-active .yd-session__icon {
  color: rgb(var(--primary-6));
}

.yd-session__title {
  overflow: hidden;
  min-width: 0;
  flex: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-session__pin {
  flex-shrink: 0;
  color: rgb(var(--primary-6));
  font-size: 12px;
}

.yd-session__actions {
  position: absolute;
  right: 6px;
  display: none;
  gap: 1px;
  padding-left: 16px;
  border-radius: 6px;
  background: linear-gradient(90deg, transparent, var(--color-fill-2) 30%);
}

.yd-session.is-active .yd-session__actions {
  background: linear-gradient(90deg, transparent, rgba(var(--primary-6), 0.1) 30%);
}

.yd-session:hover .yd-session__actions {
  display: inline-flex;
}

.yd-session:hover .yd-session__pin {
  display: none;
}

.yd-session__actions button {
  display: grid;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  place-items: center;
}

.yd-session__actions button:hover {
  background: var(--color-bg-1);
  color: rgb(var(--primary-6));
}
</style>
