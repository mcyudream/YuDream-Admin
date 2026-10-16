<script setup lang="ts">
import type { SearchConfig, SearchOptionsSource, TableColumn, YdTablePageAction } from '@yudream/components'
import { useYdToast, YdResponsiveEnhancedTable, YdTablePageLayout } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useYdToast()

/** 内容条目：演示用虚构数据 */
interface ContentItem {
  id: string
  index: number
  title: string
  code: string
  words: number
  updatedAt: string
}

const rows = ref<ContentItem[]>([
  { id: '1', index: 1, title: '产品需求文档（2026 Q4）', code: 'DOC_0001', words: 2380, updatedAt: '2026-10-12' },
  { id: '2', index: 2, title: '用户调研访谈纪要（八月）', code: 'DOC_0002', words: 1560, updatedAt: '2026-10-08' },
  { id: '3', index: 3, title: '接口设计规范 v2.1', code: 'DOC_0003', words: 4120, updatedAt: '2026-09-30' },
  { id: '4', index: 4, title: '版本发布检查清单', code: 'DOC_0004', words: 760, updatedAt: '2026-09-26' },
])

const columns: TableColumn<ContentItem>[] = [
  { accessorKey: 'index', header: '序号', width: 80, align: 'center' },
  { accessorKey: 'title', header: '标题', minWidth: 320 },
  { accessorKey: 'code', header: '文档编号', width: 180 },
  { accessorKey: 'words', header: '字数', width: 100, align: 'center' },
  { accessorKey: 'updatedAt', header: '最后更新时间', width: 180 },
]

/** 静态搜索配置：演示 @Searchable 注解产物；真实项目由 /api/search-config 按 queryClass 返回 */
const searchConfig: SearchConfig = {
  queryClassName: 'demo.ContentPageQuery',
  querySimpleName: 'ContentPageQuery',
  fields: [
    { fieldName: 'keyword', label: '关键词', placeholder: '标题 / 文档编号', controlType: 'TEXT' as never, order: 1, showWhenFolded: true, width: '240px', fieldType: 'String' },
    { fieldName: 'category', label: '内容分类', controlType: 'SELECT' as never, order: 2, showWhenFolded: false, width: '200px', optionsSource: 'categoryOptions', fieldType: 'String' },
    { fieldName: 'status', label: '发布状态', controlType: 'SELECT' as never, order: 3, showWhenFolded: false, width: '200px', optionsSource: 'statusOptions', fieldType: 'String' },
    { fieldName: 'updatedTime', label: '更新时间', controlType: 'DATE_RANGE' as never, order: 4, showWhenFolded: false, width: '200px', fieldType: 'LocalDate' },
  ],
}

const optionsSources: SearchOptionsSource[] = [
  { id: 'categoryOptions', loader: () => [{ label: '产品', value: 'product' }, { label: '研发', value: 'dev' }, { label: '运营', value: 'ops' }, { label: '设计', value: 'design' }] },
  { id: 'statusOptions', loader: () => [{ label: '草稿', value: 'draft' }, { label: '已发布', value: 'published' }, { label: '已归档', value: 'archived' }] },
]

const searchQuery = ref<Record<string, any>>({})

const tabs = [
  { label: '文章', value: 'article' },
  { label: '页面', value: 'page' },
  { label: '附件', value: 'attachment' },
  { label: '素材', value: 'asset' },
]
const activeTab = ref('article')

/** 操作按钮：autoFold 的项在超过阈值时收进「更多」，danger 项红色标识 */
const actions: YdTablePageAction[] = [
  { label: '新建内容', icon: 'morph:plus', variant: 'default', onClick: () => toast.success('打开新建面板') },
  { label: '导入', icon: 'morph:upload', variant: 'outline', autoFold: true, onClick: () => toast('打开导入面板') },
  { label: '导出', icon: 'morph:download', variant: 'outline', autoFold: true, onClick: () => toast.success('导出任务已创建') },
  { label: '下载模板', icon: 'morph:file-down', variant: 'outline', autoFold: true, onClick: () => toast('下载导入模板') },
  { label: '批量删除', icon: 'morph:trash-2', danger: true, autoFold: true, onClick: () => toast.error('危险操作需二次确认') },
]

const page = ref(1)
const size = ref(10)
const total = ref(4)

const layoutCode = `<YdTablePageLayout
  title="内容管理"
  :tabs="[{ label: '文章', value: 'article' }]"
  v-model:active-tab="activeTab"
  :actions="actions"
  :actions-threshold="3"
>
  <template #search="{ fold }">
    <YdDynamicSearchForm
      v-model="searchQuery"
      :config="searchConfig"
      :fold="fold"
      :options-sources="optionsSources"
      @search="load"
      @reset="reset"
    />
  </template>

  <template #table>
    <YdResponsiveEnhancedTable :data="rows" :columns="columns" row-key="id" />
  </template>

  <template #footer>
    <YdPagination v-model:page="page" v-model:size="size" :total="total" />
  </template>
</YdTablePageLayout>`

const dynamicFormCode = `// 后端查询类（注解驱动，前端零硬编码）
public class ContentPageQuery extends PageQuery {
    @Searchable(label = "关键词", showWhenFolded = true, order = 1)
    private String keyword;

    @Searchable(label = "内容分类", optionsSource = "categoryOptions", order = 2)
    private String category;

    @Searchable(label = "更新时间", controlType = ControlType.DATE_RANGE, order = 3)
    private LocalDate updatedTime;
}

// 前端按 queryClass 拉取配置并自动渲染
const { searchConfig, searchQuery } = useTablePage({
  queryClass: 'demo.ContentPageQuery',
  loadApi: apiContent.page,
  optionsSources,
})`
</script>

<template>
  <DemoCard
    title="YdTablePageLayout"
    description="表格页标准版式：页头只放标题；搜索默认折叠、点击展开；Tab 在表格上方左侧、操作按钮在右侧，超过阈值自动收进「更多」，危险操作红色标识"
    :code="layoutCode"
  >
    <YdTablePageLayout
      v-model:active-tab="activeTab"
      title="内容管理"
      :tabs="tabs"
      :actions="actions"
      :actions-threshold="3"
    >
      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          :options-sources="optionsSources"
          @search="() => toast.success('触发查询（示例未接后端）')"
          @reset="() => { searchQuery = {} }"
        />
      </template>

      <template #table>
        <YdResponsiveEnhancedTable
          :data="rows"
          :columns="columns"
          row-key="id"
          table-root-class="rounded-lg overflow-hidden"
        />
      </template>

      <template #footer>
        <div class="flex justify-end">
          <YdPagination
            v-model:page="page"
            v-model:size="size"
            :total="total"
          />
        </div>
      </template>
    </YdTablePageLayout>

    <ul class="text-xs text-muted-foreground mt-3 space-y-1">
      <li>折叠态只显示「搜索」行，点击展开筛选表单；展开后右上角可收起。</li>
      <li>操作按钮超过 <code>actionsThreshold</code> 时，标记 <code>autoFold</code> 的项收进「更多」下拉；<code>danger</code> 项红色标识且默认可二次确认。</li>
      <li>表格默认铺满可用宽度；移动端（≤768px）自动切换卡片视图。</li>
    </ul>
  </DemoCard>

  <DemoCard
    title="YdDynamicSearchForm + useTablePage"
    description="注解驱动的动态筛选表单：后端 @Searchable 声明字段元数据，前端按 queryClass 拉取配置自动渲染，无需重复定义筛选控件"
    :code="dynamicFormCode"
  >
    <YdDynamicSearchForm
      v-model="searchQuery"
      :config="searchConfig"
      :options-sources="optionsSources"
      @search="() => toast.success('触发查询')"
      @reset="() => { searchQuery = {} }"
    />
    <ul class="text-xs text-muted-foreground mt-3 space-y-1">
      <li>控件按 <code>controlType</code> 渲染：TEXT 输入框、SELECT 下拉、DATE_RANGE 双日期、NUMBER_RANGE 最小-最大区间。</li>
      <li>下拉选项由 <code>optionsSource</code> 对应前端 <code>SearchOptionsSource</code> 的 loader 提供，支持同步与异步。</li>
      <li><code>useTablePage</code> 封装「拉配置 + 查询 + 分页 + 重置」，见上方代码示例。</li>
    </ul>
  </DemoCard>
</template>
