<script setup lang="ts">
import type { FeishuFailure, FeishuFileItem, FeishuSpaceItem } from '../feishu-contract'
import { failureTitle } from '../feishu-contract'
import FeishuAccessGuide from './FeishuAccessGuide.vue'

/**
 * 站点与目录导航。
 * 云空间目录来自 drive/files，知识库空间来自 wiki/spaces；点选空间后在右侧调用 wikiNodes 浏览节点。
 */
defineProps<{
  trail: { token: string, name: string }[]
  folders: FeishuFileItem[]
  spaces: FeishuSpaceItem[]
  activeSpaceId: string
  spacesLoading: boolean
  spacesFailure: FeishuFailure | null
  spacesHasMore: boolean
}>()

const emit = defineEmits<{
  openRoot: []
  openFolder: [item: FeishuFileItem]
  goToFolder: [index: number]
  selectSpace: [spaceId: string]
  loadMoreSpaces: []
  retrySpaces: []
}>()
</script>

<template>
  <nav class="source-nav" aria-label="站点与目录">
    <section class="source-nav__section">
      <header class="source-nav__head">
        <h2>云空间目录</h2>
        <span class="source-nav__meta">{{ trail.length ? `层级 ${trail.length}` : '根目录' }}</span>
      </header>

      <ol class="source-nav__trail">
        <li>
          <YdAction class="source-nav__crumb" :class="{ 'is-active': !trail.length && !activeSpaceId }" :aria-pressed="!trail.length && !activeSpaceId" @click="emit('openRoot')">
            <YdIcon name="morph:hard-drive" />
            <span>应用根目录</span>
          </YdAction>
        </li>
        <li v-for="(node, index) in trail" :key="node.token">
          <YdAction
            class="source-nav__crumb"
            :class="{ 'is-active': index === trail.length - 1 }"
            :aria-pressed="index === trail.length - 1"
            @click="emit('goToFolder', index)"
          >
            <YdIcon name="morph:corner-down-right" />
            <span>{{ node.name }}</span>
          </YdAction>
        </li>
      </ol>

      <ul v-if="folders.length" class="source-nav__list">
        <li v-for="folder in folders" :key="folder.token">
          <YdAction class="source-nav__item" @click="emit('openFolder', folder)">
            <YdIcon name="morph:folder" />
            <span class="source-nav__label">
              <strong>{{ folder.name || folder.token }}</strong>
              <small>{{ folder.modifiedTime || '文件夹' }}</small>
            </span>
            <YdIcon name="morph:chevron-right" />
          </YdAction>
        </li>
      </ul>
      <p v-else class="source-nav__hint">
        当前目录没有子文件夹；右侧列表直接显示文件。
      </p>
    </section>

    <section class="source-nav__section">
      <header class="source-nav__head">
        <h2>知识库空间</h2>
        <span class="source-nav__meta">{{ spaces.length }} 个</span>
      </header>

      <YdAlert
        icon="morph:info"
        title="选择空间后浏览知识库节点"
        description="选中空间后可查看目录节点。当前只支持打开 docx 文档；电子表格、画板等类型保留元信息并提示未接入预览。"
      />

      <FeishuAccessGuide compact />

      <YdLoadingOverlay :loading="spacesLoading" text="加载知识库">
        <div v-if="spacesFailure" class="source-nav__state">
          <YdAlert variant="destructive" icon="morph:circle-alert" :title="failureTitle(spacesFailure.kind)" :description="spacesFailure.message" />
          <YdButton variant="outline" size="sm" @click="emit('retrySpaces')">
            重试
          </YdButton>
        </div>

        <template v-else-if="spaces.length">
          <ul class="source-nav__list">
            <li v-for="space in spaces" :key="space.spaceId">
              <YdAction
                class="source-nav__item"
                :class="{ 'is-active': space.spaceId === activeSpaceId }"
                :aria-pressed="space.spaceId === activeSpaceId"
                @click="emit('selectSpace', space.spaceId)"
              >
                <YdIcon name="morph:book-text" />
                <span class="source-nav__label">
                  <strong>{{ space.name || space.spaceId }}</strong>
                  <small>{{ space.visibility || space.spaceType || '知识库空间' }}</small>
                </span>
                <YdIcon name="morph:chevron-right" />
              </YdAction>
            </li>
          </ul>
          <YdButton v-if="spacesHasMore" class="source-nav__more" variant="ghost" size="sm" @click="emit('loadMoreSpaces')">
            加载更多知识库
          </YdButton>
        </template>

        <YdEmpty
          v-else
          title="没有可见知识库"
          description="这不是空权限配置入口；请检查飞书应用 API 权限、知识库管理员授权，并按下方教程确认应用已加入目标知识库。"
        >
          <YdButton variant="outline" size="sm" @click="emit('retrySpaces')">
            重新加载知识库
          </YdButton>
        </YdEmpty>
      </YdLoadingOverlay>
    </section>
  </nav>
</template>

<style scoped>
.source-nav {
  display: grid;
  gap: 16px;
  align-content: start;
  min-width: 0;
  height: 100%;
  padding: 14px 12px;
  overflow-y: auto;
  background: var(--color-bg-1);
}

.source-nav__section {
  display: grid;
  gap: 10px;
  align-content: start;
  min-width: 0;
}

.source-nav__head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border-2);
}

.source-nav__head h2 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.source-nav__meta,
.source-nav__hint {
  font-size: 11px;
  color: var(--color-text-3);
}

.source-nav__hint {
  margin: 0;
}

.source-nav__trail,
.source-nav__list {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.source-nav__crumb,
.source-nav__item {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-width: 0;
  padding: 7px 9px;
  border-radius: 6px;
  color: var(--color-text-2);
  font-size: 12px;
  text-align: left;
}

.source-nav__crumb:hover,
.source-nav__item:hover {
  background: var(--color-fill-1);
  color: var(--color-text-1);
}

.source-nav__crumb.is-active,
.source-nav__item.is-active {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}

.source-nav__label {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.source-nav__label strong {
  font-size: 12px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.source-nav__label small {
  font-size: 11px;
  color: var(--color-text-3);
  overflow-wrap: anywhere;
}

.source-nav__state {
  display: grid;
  gap: 8px;
  justify-items: start;
}

.source-nav__more {
  justify-self: start;
}
</style>
