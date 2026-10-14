<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import { useYdFilePicker, YdButton, YdCard, YdCheckbox, YdLoadingOverlay, YdResponsiveTable } from '@yudream/components'
import { onBeforeUnmount, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

interface Item { id: string, name: string, disabled?: boolean }
const rows: Item[] = [
  { id: '90071992547409931', name: '跨断点保留字符串 ID' },
  { id: 'entry-2', name: '第二个文件' },
  { id: 'locked', name: '禁用的条目', disabled: true },
]
const columns: TableColumn<Item>[] = [
  { type: 'selection', disabled: row => Boolean(row.disabled) },
  { accessorKey: 'name', header: '内容' },
  { accessorKey: 'id', header: '标识' },
]
const selection = ref<Record<string, boolean>>({ '90071992547409931': true })
const forceCards = ref(false)
const loading = ref(false)
const edits = ref(0)
const picker = useYdFilePicker({ accept: '.txt,.md' })
const picked = ref('尚未选择')
let timer: ReturnType<typeof setTimeout> | undefined
function beginLoading() {
  loading.value = true
  clearTimeout(timer)
  timer = setTimeout(() => {
    loading.value = false
  }, 2200)
}
async function pick() {
  const files = await picker.open()
  picked.value = files.map(file => file.name).join('、') || '已取消选择'
}
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <DemoCard title="响应式列表选择" description="桌面与卡片共享表格选择状态；初始选中长字符串 ID，禁用项不能选择。可切换视图验证批量操作。">
    <YdButton variant="outline" class="mb-3" @click="forceCards = !forceCards">
      {{ forceCards ? '切回桌面表格' : '切到卡片视图' }}
    </YdButton>
    <YdResponsiveTable v-model:row-selection="selection" :data="rows" :columns="columns" :get-row-id="row => row.id" selectable multiple :mobile-breakpoint="forceCards ? '(min-width: 0px)' : '(max-width: 768px)'">
      <template #card="{ row, selected, selectDisabled, toggleSelected }">
        <YdCard>
          <div class="selection-card">
            <YdCheckbox :model-value="selected" :disabled="selectDisabled" :aria-label="`选择${row.name}`" @update:model-value="toggleSelected($event === true)" />
            <div><strong>{{ row.name }}</strong><p>{{ row.id }}</p></div>
          </div>
        </YdCard>
      </template>
    </YdResponsiveTable>
    <p class="demo-status" role="status">
      已选：{{ Object.keys(selection).filter(key => selection[key]).join('、') || '无' }}
    </p>
  </DemoCard>
  <DemoCard title="容器加载遮罩" description="加载保持内容尺寸和状态，遮罩期间阻止内容交互；完成后恢复原来的焦点。">
    <YdLoadingOverlay :loading="loading">
      <div class="overlay-content">
        <YdButton @click="beginLoading">
          加载这块内容
        </YdButton>
        <YdButton variant="outline" @click="edits++">
          编辑次数 {{ edits }}
        </YdButton>
        <p>这里是原有内容，加载时不销毁或挤压布局。</p>
      </div>
    </YdLoadingOverlay>
  </DemoCard>
  <DemoCard title="函数式本地文件选择" description="旧导入动作通过公开组合式能力打开文件选择，取消/重复打开和销毁会释放临时输入。">
    <YdButton variant="outline" @click="pick">
      选择本地文档
    </YdButton>
    <p class="demo-status" role="status">
      {{ picked }}
    </p>
  </DemoCard>
</template>

<style scoped>
.selection-card { display: flex; align-items: center; gap: 12px; }
.selection-card p, .demo-status { font-size: 12px; color: var(--color-text-3); }
.overlay-content { display: flex; min-height: 140px; flex-wrap: wrap; gap: 12px; align-content: center; align-items: center; padding: 20px; border: 1px solid var(--color-border-2); border-radius: var(--yd-control-radius); }
.overlay-content p { flex-basis: 100%; color: var(--color-text-2); }
</style>
