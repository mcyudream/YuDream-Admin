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

/** 内容条目：演示用虚构数据；行数足够多以展示表格卡内滚动与固定表头 */
const titlePool = ['产品需求文档（2026 Q4）', '用户调研访谈纪要（八月）', '接口设计规范 v2.1', '版本发布检查清单', '运营活动复盘（九月）', '设计规范对照表']

const rows = ref<ContentItem[]>(Array.from({ length: 24 }, (_, index) => {
  const position = index + 1
  return {
    id: String(position),
    index: position,
    title: index < titlePool.length ? titlePool[index] : `${titlePool[index % titlePool.length]} #${position}`,
    code: `DOC_${String(position).padStart(4, '0')}`,
    words: 760 + position * 137,
    updatedAt: `2026-10-${String(12 - (index % 10)).padStart(2, '0')}`,
  }
}))

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
    { fieldName: 'words', label: '字数', controlType: 'NUMBER_RANGE' as never, order: 5, showWhenFolded: false, width: '200px', fieldType: 'Long' },
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

/** 统计区示例：KPI + 图表版式（真实页面图表用 @yudream/dataviz，这里用轻量 SVG/CSS 演示版式与交互） */
const statsKpis = [
  { title: '总用户', value: '365', desc: '含启用与停用' },
  { title: '邮箱已验证', value: '363', desc: '占比 99.5%' },
  { title: '已启用', value: '364', desc: '占比 99.7%' },
  { title: '本周新增', value: '12', desc: '今日 +3' },
]

const statsTrend = [3, 5, 4, 8, 6, 9, 7, 11, 8, 12, 10, 14, 9, 13, 15, 12, 16, 14, 18, 15, 19, 17, 21, 18, 22, 20, 24, 21, 26, 23]
const statsRoles = [
  { label: '普通用户', value: 42, color: 'var(--yd-emphasis)' },
  { label: '超级管理员', value: 18, color: 'var(--yd-status-success)' },
  { label: '管理员', value: 12, color: 'var(--yd-status-warning)' },
  { label: '访客', value: 8, color: 'var(--yd-status-info)' },
]
const statsCompleteness = [
  { label: '已填部门', ratio: 0.72 },
  { label: '已分配角色', ratio: 0.68 },
  { label: '已填邮箱', ratio: 0.95 },
  { label: '已绑定 QQ', ratio: 0.41 },
]

const statsTrendPath = computed(() => {
  const max = Math.max(...statsTrend)
  return statsTrend
    .map((value, index) => `${index === 0 ? 'M' : 'L'}${(index / (statsTrend.length - 1) * 100).toFixed(2)},${(28 - value / max * 26).toFixed(2)}`)
    .join(' ')
})

const statsDonut = computed(() => {
  const total = statsRoles.reduce((sum, role) => sum + role.value, 0)
  let acc = 0
  return statsRoles.map((role) => {
    const start = acc / total * 360
    acc += role.value
    const end = acc / total * 360
    return `${role.color} ${start.toFixed(1)}deg ${end.toFixed(1)}deg`
  }).join(', ')
})

const page = ref(1)
const size = ref(10)
const total = ref(rows.value.length)

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

const statsCode = `<YdTablePageLayout
  title="用户管理"
  stats-storage-key="system-user"
  :stats-default-expanded="true"
>
  <template #stats="{ narrow }">
    <YdStats>
      <YdStat title="总用户" :value="365" description="含启用与停用" />
      <YdStat title="邮箱已验证" :value="363" description="占比 99.5%" />
    </YdStats>

    <!-- 真实页面图表用 @yudream/dataviz；窄屏不挂载 -->
    <div v-if="!narrow" class="grid gap-3 charts">
      <LineChart :dataset="trendDataset" :height="190" />
      <PieChart :dataset="roleDataset" :height="190" />
    </div>
  </template>

  <template #table>…</template>
  <template #footer>…</template>
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
    <div class="h-[560px]">
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
    </div>

    <ul class="text-xs text-muted-foreground mt-3 space-y-1">
      <li>折叠态只显示「搜索」行，点击整行平滑展开筛选表单；再次点击收起。</li>
      <li>操作按钮超过 <code>actionsThreshold</code> 时，标记 <code>autoFold</code> 的项收进「更多」下拉；<code>danger</code> 项红色标识且默认可二次确认。</li>
      <li>表格默认铺满可用宽度；移动端（≤768px）自动切换卡片视图。</li>
      <li>给布局一个确定高度（示例为 <code>h-[560px]</code>）后，纵向滚动发生在表格卡内部：表头固定、固定列钉住，分页始终留在卡片底部。</li>
    </ul>
  </DemoCard>

  <DemoCard
    title="YdTablePageLayout · 统计情报区（#stats）"
    description="表格页的「情报板」：KPI 条 + 图表自适应一行；默认折叠并记住状态，窄屏只留 KPI、图表不挂载（真实页面用 @yudream/dataviz 的 ECharts 封装，这里用轻量 SVG/CSS 演示版式）"
    :code="statsCode"
  >
    <div class="h-[460px]">
      <YdTablePageLayout
        title="用户管理"
        :actions="actions"
        :actions-threshold="3"
        :stats-default-expanded="true"
        stats-storage-key="showcase-stats"
      >
        <template #stats="{ narrow }">
          <div class="pt-1 flex flex-col gap-4">
            <YdStats>
              <YdStat v-for="kpi in statsKpis" :key="kpi.title" :title="kpi.title" :value="kpi.value" :description="kpi.desc" />
            </YdStats>

            <div v-if="!narrow" class="stats-demo__charts gap-3 grid">
              <section class="stats-demo__card">
                <p class="stats-demo__title">
                  近 30 天新增注册
                </p>
                <svg class="stats-demo__spark" viewBox="0 0 100 30" preserveAspectRatio="none" aria-hidden="true">
                  <path :d="statsTrendPath" fill="none" stroke="var(--yd-emphasis)" stroke-width="1.2" vector-effect="non-scaling-stroke" />
                </svg>
              </section>

              <section class="stats-demo__card">
                <p class="stats-demo__title">
                  角色分布
                </p>
                <div class="stats-demo__donut-row">
                  <span class="stats-demo__donut" :style="{ backgroundImage: `conic-gradient(${statsDonut})` }" aria-hidden="true" />
                  <ul class="stats-demo__legend">
                    <li v-for="role in statsRoles" :key="role.label">
                      <span class="stats-demo__dot" :style="{ background: role.color }" />{{ role.label }}
                    </li>
                  </ul>
                </div>
              </section>

              <section class="stats-demo__card">
                <p class="stats-demo__title">
                  资料完善
                </p>
                <ul class="stats-demo__bars">
                  <li v-for="item in statsCompleteness" :key="item.label">
                    <span>{{ item.label }}</span>
                    <span class="stats-demo__bar"><i :style="{ width: `${item.ratio * 100}%` }" /></span>
                    <b>{{ Math.round(item.ratio * 100) }}%</b>
                  </li>
                </ul>
              </section>
            </div>
          </div>
        </template>

        <template #table>
          <YdResponsiveEnhancedTable
            :data="rows.slice(0, 8)"
            :columns="columns"
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
          />
        </template>
      </YdTablePageLayout>
    </div>

    <ul class="text-xs text-muted-foreground mt-3 space-y-1">
      <li>统计区默认折叠，点击「统计」整行展开；折叠状态按 <code>statsStorageKey</code>（默认当前路径）记入 localStorage。</li>
      <li>插槽传出 <code>narrow</code>：窄屏只保留 KPI 条，图表由页面 <code>v-if</code> 不挂载，省掉图表初始化开销。</li>
      <li>KPI 与图表容器用 <code>auto-fit + minmax</code> 网格自适应：宽屏一行四张，窄屏自动折行，无需媒体查询。</li>
      <li>它只是「情报板」：表格仍是页面主战场，统计不占首屏。</li>
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

<style scoped>
/* 统计区示例：轻量图形（真实页面用 @yudream/dataviz） */
.stats-demo__charts {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.stats-demo__card {
  min-width: 0;
  padding: 12px 14px;
  background: oklch(var(--card));
  border: 1px solid oklch(var(--border));
  border-radius: 8px;
}

.stats-demo__title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: oklch(var(--muted-foreground));
}

.stats-demo__spark {
  width: 100%;
  height: 120px;
}

.stats-demo__donut-row {
  display: flex;
  gap: 14px;
  align-items: center;
}

.stats-demo__donut {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  mask: radial-gradient(circle, transparent 54%, #000 55%);
}

.stats-demo__legend {
  display: grid;
  gap: 6px;
  font-size: 12px;
}

.stats-demo__legend li {
  display: flex;
  gap: 6px;
  align-items: center;
  color: oklch(var(--muted-foreground));
}

.stats-demo__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.stats-demo__bars {
  display: grid;
  gap: 8px;
  font-size: 12px;
}

.stats-demo__bars li {
  display: grid;
  grid-template-columns: 76px 1fr 34px;
  gap: 8px;
  align-items: center;
  color: oklch(var(--muted-foreground));
}

.stats-demo__bar {
  height: 6px;
  overflow: hidden;
  background: oklch(var(--muted));
  border-radius: 999px;
}

.stats-demo__bar i {
  display: block;
  height: 100%;
  background: var(--yd-emphasis);
}
</style>
