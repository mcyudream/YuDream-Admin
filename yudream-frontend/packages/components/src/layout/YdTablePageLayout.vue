<script setup lang="ts">
import type { YdTablePageAction } from './table-page-layout-types'
import { computed, provide, ref, useId } from 'vue'
import YdButton from '../basic/button/index.vue'
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger, DropdownMenu as YdDropdownMenu } from '../basic/dropdown/dropdown-menu/index'
import YdIcon from '../basic/icon/index.vue'
import YdPageHeader from '../basic/page-header/index.vue'
import { tableActionsTargetKey } from '../basic/table/table-actions-context'
import YdSegmented from '../extended/YdSegmented.vue'
import { tablePageSearchFoldKey } from './table-page-layout-context'

defineOptions({ name: 'YdTablePageLayout' })

const props = withDefaults(defineProps<YdTablePageLayoutProps>(), {
  showSearch: true,
  searchFolded: undefined,
  searchDefaultFolded: true,
  actionsThreshold: 4,
})
const emit = defineEmits<{
  'update:activeTab': [value: string]
  'update:searchFolded': [value: boolean]
}>()
/** 操作栏尾部容器：表格的「列显隐」控件经 Teleport 并入此处，与操作按钮同排且最后 */
const actionsAppendId = `yd-table-actions-${useId()}`
provide(tableActionsTargetKey, actionsAppendId)

export interface YdTablePageLayoutProps {
  /** 页面标题 */
  title?: string
  /** 是否显示搜索区 */
  showSearch?: boolean
  /** 搜索区折叠状态(可由 v-model:searchFolded 接管) */
  searchFolded?: boolean
  /** 搜索区默认折叠状态(未用 v-model 时的初始值) */
  searchDefaultFolded?: boolean
  /** Tab选项 */
  tabs?: Array<{ label: string, value: string }>
  /** 当前激活的tab */
  activeTab?: string
  /** 操作按钮配置 */
  actions?: YdTablePageAction[]
  /** 操作按钮显示阈值,超过此数量自动折叠不常用的 */
  actionsThreshold?: number
}

// defineModel 的默认值不能引用局部变量(会被提升出 setup),这里手动实现双向绑定:
// 外部传 v-model:searchFolded 时以外部值为准,否则内部自管理,初值取 searchDefaultFolded
const foldedInner = ref<boolean>()
const searchFolded = computed({
  get: () => props.searchFolded ?? foldedInner.value ?? props.searchDefaultFolded,
  set: (value: boolean) => {
    foldedInner.value = value
    emit('update:searchFolded', value)
  },
})

// 供内嵌的 YdDynamicSearchForm 使用：提交/重置后收起搜索区，把空间还给表格
provide(tablePageSearchFoldKey, (fold: boolean) => {
  searchFolded.value = fold
})

const activeTabLocal = computed({
  get: () => props.activeTab || '',
  set: (value: string) => emit('update:activeTab', value),
})

/** 直接显示的操作按钮 */
const visibleActions = computed(() => {
  if (!props.actions || props.actions.length <= props.actionsThreshold) {
    return props.actions || []
  }

  // 优先显示: 不可折叠的 + 前N个
  const pinnedActions = props.actions.filter(a => !a.autoFold)
  const foldableActions = props.actions.filter(a => a.autoFold)

  const remainingSlots = props.actionsThreshold - pinnedActions.length
  if (remainingSlots > 0) {
    return [...pinnedActions, ...foldableActions.slice(0, remainingSlots)]
  }

  return pinnedActions
})

/** 折叠到"更多"菜单的操作 */
const foldedActions = computed(() => {
  if (!props.actions || props.actions.length <= props.actionsThreshold) {
    return []
  }

  const visibleSet = new Set(visibleActions.value)
  return props.actions.filter(a => !visibleSet.has(a))
})
</script>

<template>
  <!-- data-yd-fullheight：宿主布局据此把外层容器切为固定视口高，滚动交给页面内部 -->
  <div class="yd-table-page-layout" data-yd-fullheight>
    <!-- 页面标题区:只放标题 -->
    <YdPageHeader v-if="title" :title="title" class="mb-0">
      <template v-if="$slots['page-title']" #default>
        <slot name="page-title" />
      </template>
    </YdPageHeader>

    <div class="yd-table-page-layout__body">
      <!-- 搜索卡片：独立成卡，固定于顶部；整行点击切换展开，箭头带旋转过渡 -->
      <div v-if="showSearch" class="yd-table-page-layout__search">
        <button
          type="button"
          class="yd-table-page-layout__search-toggle"
          :aria-expanded="!searchFolded"
          @click="searchFolded = !searchFolded"
        >
          <YdIcon
            name="morph:chevron-down"
            class="yd-table-page-layout__search-chevron"
            :class="{ 'is-folded': searchFolded }"
          />
          <span>搜索</span>
        </button>

        <!-- 展开动画：grid-template-rows 0fr↔1fr 平滑过渡，内层裁剪高度 -->
        <div
          class="yd-table-page-layout__search-body"
          :class="{ 'is-open': !searchFolded }"
        >
          <div class="yd-table-page-layout__search-inner">
            <div class="yd-table-page-layout__search-content">
              <slot name="search" :fold="searchFolded" />
            </div>
          </div>
        </div>
      </div>

      <!-- 表格卡片：操作栏 + 表格滚动区 + 底部固定分页；卡片铺满剩余高度 -->
      <div class="yd-table-page-layout__card">
        <!-- Tab + 操作栏：列显隐控件经 Teleport 并入操作栏末尾（三空时整行由 CSS 收起） -->
        <div class="yd-table-page-layout__toolbar">
          <!-- 左侧Tab -->
          <div class="yd-table-page-layout__tabs">
            <YdSegmented
              v-if="tabs && tabs.length"
              v-model="activeTabLocal"
              :options="tabs"
              aria-label="数据视图切换"
            />
            <slot name="tabs" />
          </div>

          <!-- 右侧操作按钮 -->
          <div class="yd-table-page-layout__actions">
            <slot name="actions">
              <!-- 直接显示的操作按钮 -->
              <YdButton
                v-for="(action, index) in visibleActions"
                :key="index"
                :ref="action.ref"
                :variant="action.variant || 'default'"
                :disabled="action.disabled"
                @click="action.onClick"
              >
                <YdIcon v-if="action.icon" :name="action.icon" />
                {{ action.label }}
              </YdButton>

              <!-- 折叠的操作 -->
              <YdDropdownMenu v-if="foldedActions.length">
                <DropdownMenuTrigger as-child>
                  <YdButton variant="outline">
                    <YdIcon name="morph:more-horizontal" />
                    更多
                  </YdButton>
                </DropdownMenuTrigger>

                <DropdownMenuContent>
                  <DropdownMenuItem
                    v-for="(action, index) in foldedActions"
                    :key="index"
                    :disabled="action.disabled"
                    @click="action.onClick"
                  >
                    <YdIcon v-if="action.icon" :name="action.icon" />
                    <span>{{ action.label }}</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </YdDropdownMenu>
            </slot>
            <!-- 列显隐传送目标：始终位于操作按钮之后 -->
            <div :id="actionsAppendId" class="yd-table-page-layout__actions-append" />
          </div>
        </div>

        <!-- 表格滚动区：页面不再整体滚动，纵向滚动发生在这里 -->
        <div class="yd-table-page-layout__table">
          <slot name="table" />
        </div>

        <!-- 分页固定于卡片底部（表格滚动时保持可见）；空态由 CSS 收起 -->
        <div class="yd-table-page-layout__footer">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.yd-table-page-layout {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

/* 内容区：搜索卡与表格卡纵向排列，余高全部给表格卡 */
.yd-table-page-layout__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
  padding: 16px;
  /* 视口过矮（表格卡触底最小高度）时整体可滚，避免表格被压缩到看不见 */
  overflow: auto;
}

.yd-table-page-layout__search {
  flex-shrink: 0;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.yd-table-page-layout__search-toggle {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-width: 0;
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 8px;
  transition: background-color 0.15s ease;
}

.yd-table-page-layout__search-toggle:hover {
  background: var(--color-fill-1);
}

/* 箭头折叠时顺时针转回 -90°，与展开动作呼应 */
.yd-table-page-layout__search-chevron {
  transition: transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.yd-table-page-layout__search-chevron.is-folded {
  transform: rotate(-90deg);
}

/* 展开/收起：grid 轨道 0fr↔1fr 平滑过渡（无需测量内容高度） */
.yd-table-page-layout__search-body {
  display: grid;
  grid-template-rows: 0fr;
  opacity: 0;
  transition: grid-template-rows 0.24s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s ease;
}

.yd-table-page-layout__search-body.is-open {
  grid-template-rows: 1fr;
  opacity: 1;
}

.yd-table-page-layout__search-inner {
  min-height: 0;
  overflow: hidden;
}

/* 搜索内容限高并内部滚动：字段多或屏幕矮时不至于把表格挤没 */
.yd-table-page-layout__search-content {
  max-height: min(40vh, 360px);
  padding: 0 16px 16px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

/* 表格卡片：铺满剩余高度，内部三段式（操作栏 / 滚动区 / 分页） */
.yd-table-page-layout__card {
  display: flex;
  flex: 1;
  flex-direction: column;
  /* 表格卡最小可读高度：低于此值由内容区整体滚动兜底（小屏高度场景） */
  min-height: 280px;
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.yd-table-page-layout__toolbar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
}

/* Tab、操作按钮与列显隐都没有内容时收起整行，避免空占位 */
.yd-table-page-layout__toolbar:not(:has(
  .yd-table-page-layout__tabs > *,
  .yd-table-page-layout__actions > :not(.yd-table-page-layout__actions-append),
  .yd-table-page-layout__actions-append > *
)) {
  display: none;
}

.yd-table-page-layout__tabs {
  flex: 1;
  min-width: 0;
}

.yd-table-page-layout__actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.yd-table-page-layout__actions-append {
  display: flex;
  align-items: center;
}

/* 表格滚动区：整页不滚动，纵向滚动发生在这里 */
.yd-table-page-layout__table {
  flex: 1;
  min-height: 0;
  padding: 0 16px;
  overflow: auto;
  overscroll-behavior: contain;
}

/* 分页固定于卡片底部 */
.yd-table-page-layout__footer {
  flex-shrink: 0;
  padding: 10px 16px;
  border-top: 1px solid var(--color-border-2);
}

/* 未提供分页等底部内容时不占位 */
.yd-table-page-layout__footer:not(:has(*)) {
  display: none;
}

/* 响应式适配 */
@media (width <= 768px) {
  .yd-table-page-layout__body {
    gap: 12px;
    padding: 12px;
  }

  .yd-table-page-layout__toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .yd-table-page-layout__tabs {
    width: 100%;
  }

  .yd-table-page-layout__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
