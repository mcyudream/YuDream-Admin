<script setup lang="ts">
import type { YdTablePageAction } from './table-page-layout-types'
import { computed, ref } from 'vue'
import YdButton from '../basic/button/index.vue'
import { DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger, DropdownMenu as YdDropdownMenu } from '../basic/dropdown/dropdown-menu/index'
import YdIcon from '../basic/icon/index.vue'
import YdPageHeader from '../basic/page-header/index.vue'
import YdPageMain from '../basic/page-main/index.vue'
import YdSegmented from '../extended/YdSegmented.vue'

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

/** 是否有Tab或操作栏 */
const hasToolbar = computed(() => {
  return (props.tabs && props.tabs.length > 0) || (props.actions && props.actions.length > 0)
})
</script>

<template>
  <div class="yd-table-page-layout">
    <!-- 页面标题区:只放标题 -->
    <YdPageHeader v-if="title" :title="title">
      <template v-if="$slots['page-title']" #default>
        <slot name="page-title" />
      </template>
    </YdPageHeader>

    <YdPageMain>
      <!-- 搜索折叠区:折叠态只显示「搜索」标题行;展开后标题行右侧出现收起按钮 -->
      <div v-if="showSearch" class="yd-table-page-layout__search">
        <div class="yd-table-page-layout__search-head">
          <button
            type="button"
            class="yd-table-page-layout__search-toggle"
            :aria-expanded="!searchFolded"
            @click="searchFolded = !searchFolded"
          >
            <YdIcon :name="searchFolded ? 'morph:chevron-right' : 'morph:chevron-down'" />
            <span>搜索</span>
          </button>
          <button
            v-if="!searchFolded"
            type="button"
            class="yd-table-page-layout__search-close"
            aria-label="收起搜索"
            title="收起搜索"
            @click="searchFolded = true"
          >
            <YdIcon name="morph:x" />
          </button>
        </div>

        <div v-show="!searchFolded" class="yd-table-page-layout__search-body">
          <slot name="search" :fold="searchFolded" />
        </div>
      </div>

      <!-- Tab + 操作栏 -->
      <div v-if="hasToolbar" class="yd-table-page-layout__toolbar">
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
        </div>
      </div>

      <!-- 表格区 -->
      <div class="yd-table-page-layout__table">
        <slot name="table" />
      </div>

      <!-- 底部:分页等 -->
      <div v-if="$slots.footer" class="yd-table-page-layout__footer">
        <slot name="footer" />
      </div>
    </YdPageMain>
  </div>
</template>

<style scoped>
.yd-table-page-layout {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 100%;
}

.yd-table-page-layout__search {
  margin-bottom: 16px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.yd-table-page-layout__search-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.yd-table-page-layout__search-toggle {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
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

.yd-table-page-layout__search-close {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-right: 12px;
  color: var(--color-text-3);
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 4px;
  transition: background-color 0.15s ease;
}

.yd-table-page-layout__search-close:hover {
  color: var(--color-text-1);
  background: var(--color-fill-2);
}

.yd-table-page-layout__search-body {
  padding: 0 16px 16px;
}

.yd-table-page-layout__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
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

.yd-table-page-layout__table {
  flex: 1;
  min-height: 0;
}

.yd-table-page-layout__footer {
  margin-top: 16px;
}

/* 响应式适配 */
@media (width <= 768px) {
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
