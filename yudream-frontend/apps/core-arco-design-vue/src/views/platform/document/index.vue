<script setup lang="ts">
import type { ActionMenuItem, StatusColumn, TableColumn, UserColumn, YdCodeEditorInstance, YdSegmentedOption } from '@yudream/components'
import type { GenerationStatus, TemplateStatus, WordGenerateData, WordGenerationRecord, WordTemplate, WordTemplatePayload } from '@/api/modules/platform-document'
import { YdListToolbar } from '@yudream/components'
import apiDocument from '@/api/modules/platform-document'

const modal = useYdModal()
const toast = useYdToast()
const isMobile = useIsMobile()
const { auth } = useAppAuth()
const tour = useTourStore()

const activeTab = ref<'templates' | 'records'>('templates')
const loading = ref(false)
const actionLoading = ref('')
const rows = ref<WordTemplate[]>([])
const recordRows = ref<WordGenerationRecord[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive({ keyword: '' })

const fileInput = ref<InstanceType<typeof YdFilePicker>>()
const replaceInput = ref<InstanceType<typeof YdFilePicker>>()
const pendingFile = ref<File | null>(null)
const replacingTemplate = ref<WordTemplate | null>(null)
const formVisible = ref(false)
const editing = ref<WordTemplate | null>(null)
const placeholdersJson = ref('{}')
const placeholdersEditor = ref<YdCodeEditorInstance | null>(null)
const form = reactive<WordTemplatePayload>({
  name: '',
  code: '',
  placeholders: {},
  description: '',
  status: 'ACTIVE',
})

const guideVisible = ref(false)
const generateVisible = ref(false)
const generatingTemplate = ref<WordTemplate | null>(null)
const generateDataJson = ref('{}')
const generateEditor = ref<YdCodeEditorInstance | null>(null)
const lastRecord = ref<WordGenerationRecord | null>(null)

const tabOptions: YdSegmentedOption<'templates' | 'records'>[] = [
  { label: '模板管理', value: 'templates' },
  { label: '生成记录', value: 'records' },
]
const tabIcons: Record<string, string> = {
  templates: 'morph:file-text',
  records: 'morph:history',
}
const statusOptions: { label: string, value: TemplateStatus }[] = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
]
const placeholdersPlaceholder = '{ "name": "姓名", "date": "日期" }'

/** 教程示例集中定义，交给 YdCode 做高亮与复制，模板里只保留说明文字与短变量。 */
const guideCode = {
  rowLoop: `{{#participants}}
{{index}}  {{studentName}}  {{className}}  {{studentNo}}
{{/participants}}`,
  paragraphLoop: '参与成员：{{#participants}}{{studentName}}、{{/participants}}',
  columnLoop: `{{#participantRows}}
{{l.name}} {{l.class}} {{l.no}}
{{r.name}} {{r.class}} {{r.no}}
{{/participantRows}}`,
  placeholders: `{
  "activityName": "活动名称",
  "date": "活动日期",
  "student.name": "学生姓名",
  "participants": "名单数组"
}`,
  sampleData: `{
  "activityName": "Minecraft 星空社活动",
  "date": "2026年7月8日",
  "student": {
    "name": "张三"
  },
  "participants": [
    {
      "index": 1,
      "studentName": "张三",
      "className": "计科2401",
      "studentNo": "5120240001"
    }
  ]
}`,
  columnData: `{
  "participantRows": [
    {
      "l": { "name": "张三", "class": "计科2401", "no": "5120240001" },
      "r": { "name": "李四", "class": "软工2402", "no": "5120240002" }
    }
  ]
}`,
}

/** 模板身份列：桌面固定首位，移动卡片复用为标题 */
const templateUserColumn: UserColumn<WordTemplate> = {
  nameKey: 'name',
  header: '模板名称',
  width: 200,
}

const templateStatusColumn: StatusColumn<WordTemplate> = {
  statusKey: 'status',
  header: '状态',
  width: 100,
  statusMap: {
    ACTIVE: { label: '启用', variant: 'default' },
    DISABLED: { label: '停用', variant: 'secondary' },
  },
}

/** 行级操作统一收进组件内置操作菜单：权限用 visible 过滤，停用态用 disabled 表达，危险动作保留原确认弹窗。 */
const templateActions: ActionMenuItem<WordTemplate>[] = [
  {
    label: '生成',
    icon: 'morph:file-output',
    visible: () => auth('platform:document:generate'),
    disabled: row => row.status !== 'ACTIVE',
    onClick: openGenerate,
  },
  {
    label: '原文件',
    icon: 'morph:file-text',
    disabled: row => !row.templateFileUrl,
    onClick: row => openFile(row.templateFileUrl),
  },
  {
    label: '编辑',
    icon: 'morph:pencil',
    visible: () => auth('platform:document:edit'),
    onClick: openEdit,
  },
  {
    label: '替换',
    icon: 'morph:refresh-cw',
    visible: () => auth('platform:document:edit'),
    onClick: pickReplaceFile,
  },
  {
    label: '停用',
    icon: 'morph:ban',
    danger: true,
    visible: row => row.status === 'ACTIVE' && auth('platform:document:edit'),
    onClick: confirmDisable,
  },
  {
    label: '启用',
    icon: 'morph:circle-check',
    visible: row => row.status !== 'ACTIVE' && auth('platform:document:edit'),
    onClick: confirmEnable,
  },
]

const templateColumns = computed<TableColumn<WordTemplate>[]>(() => [
  { accessorKey: 'code', header: '模板编码', width: 160 },
  { accessorKey: 'originalFilename', header: '模板文件', width: 220 },
  { id: 'placeholders', header: '占位符', width: 260 },
  { id: 'updateTime', header: '更新时间', width: 180 },
])

/** 记录身份列：模板编码，桌面固定首位，移动卡片复用为标题 */
const recordUserColumn: UserColumn<WordGenerationRecord> = {
  nameKey: 'templateCode',
  header: '模板编码',
  width: 200,
}

const recordStatusColumn: StatusColumn<WordGenerationRecord> = {
  statusKey: 'status',
  header: '状态',
  width: 100,
  statusMap: {
    SUCCESS: { label: '成功', variant: 'default' },
    FAILED: { label: '失败', variant: 'destructive' },
  },
}

/** 记录没有文件时保留原有的“未生成文件”提示：菜单项只读禁用，不触发下载。 */
const recordActions: ActionMenuItem<WordGenerationRecord>[] = [
  {
    label: row => row.outputFileUrl ? '下载' : '未生成文件',
    icon: 'morph:download',
    disabled: row => !row.outputFileUrl,
    onClick: row => openFile(row.outputFileUrl),
  },
]

const recordColumns = computed<TableColumn<WordGenerationRecord>[]>(() => [
  { accessorKey: 'outputFilename', header: '生成文件', width: 260 },
  { accessorKey: 'operatorId', header: '操作人', width: 140 },
  { id: 'generatedAt', header: '生成时间', width: 180 },
  { id: 'errorMessage', header: '异常', width: 300 },
])

const placeholdersIssue = computed(() => jsonIssue(placeholdersJson.value))
const generateIssue = computed(() => jsonIssue(generateDataJson.value))
const lastRecordJson = computed(() => (lastRecord.value ? JSON.stringify(lastRecord.value, null, 2) : ''))
const trimmedKeyword = computed(() => search.keyword.trim())

onMounted(load)

watch(activeTab, () => {
  pagination.page = 1
  pagination.total = 0
  load()
})

async function load() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
    }
    if (activeTab.value === 'templates') {
      const res = await apiDocument.pageTemplates(params)
      rows.value = res.data.records
      pagination.total = res.data.total
    }
    else {
      const res = await apiDocument.pageRecords(params)
      recordRows.value = res.data.records
      pagination.total = res.data.total
    }
  }
  finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  pagination.page = 1
  load()
}

function onPageChange(page: number) {
  pagination.page = page
  load()
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  load()
}

function openGuide() {
  guideVisible.value = true
}

function replayGuide() {
  tour.start('platform.document.word-template')
}

function pickCreateFile() {
  fileInput.value?.open()
}

function onCreateFileChange(files: File[]) {
  const file = files[0]
  fileInput.value?.reset()
  if (!file) {
    return
  }
  pendingFile.value = file
  openCreate()
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    name: stripExt(pendingFile.value?.name || ''),
    code: '',
    placeholders: {},
    description: '',
    status: 'ACTIVE' as TemplateStatus,
  })
  placeholdersJson.value = '{}'
  formVisible.value = true
}

function openEdit(row: WordTemplate) {
  editing.value = row
  pendingFile.value = null
  Object.assign(form, {
    name: row.name,
    code: row.code,
    placeholders: row.placeholders || {},
    description: row.description || '',
    status: row.status || 'ACTIVE',
  })
  placeholdersJson.value = JSON.stringify(row.placeholders || {}, null, 2)
  formVisible.value = true
}

async function saveForm() {
  const placeholders = parsePlaceholdersJson(placeholdersJson.value)
  if (!placeholders) {
    return
  }
  const payload = { ...form, placeholders }
  if (editing.value) {
    await apiDocument.updateTemplate(editing.value.id, payload)
  }
  else {
    if (!pendingFile.value) {
      toast.error('请先选择 DOCX 模板文件')
      return
    }
    await apiDocument.uploadTemplate(pendingFile.value, payload)
  }
  toast.success('Word 模板已保存')
  formVisible.value = false
  pendingFile.value = null
  await load()
}

function pickReplaceFile(row: WordTemplate) {
  replacingTemplate.value = row
  replaceInput.value?.open()
}

async function onReplaceFileChange(files: File[]) {
  const file = files[0]
  const target = replacingTemplate.value
  replaceInput.value?.reset()
  if (!file || !target) {
    return
  }
  await apiDocument.replaceTemplateFile(target.id, file)
  toast.success('模板文件已替换')
  replacingTemplate.value = null
  await load()
}

function confirmDisable(row: WordTemplate) {
  modal.confirm({
    title: '确认停用',
    content: `确认停用 Word 模板「${row.name}」吗？`,
    onConfirm: async () => {
      await apiDocument.disableTemplate(row.id)
      toast.success('Word 模板已停用')
      await load()
    },
  })
}

function confirmEnable(row: WordTemplate) {
  modal.confirm({
    title: '确认启用',
    content: `确认启用 Word 模板「${row.name}」吗？`,
    onConfirm: async () => {
      await apiDocument.enableTemplate(row.id)
      toast.success('Word 模板已启用')
      await load()
    },
  })
}

function openGenerate(row: WordTemplate) {
  generatingTemplate.value = row
  generateDataJson.value = JSON.stringify(defaultGenerateData(row), null, 2)
  lastRecord.value = null
  generateVisible.value = true
}

async function generate() {
  if (!generatingTemplate.value) {
    return
  }
  const data = parseJson(generateDataJson.value, '生成数据')
  if (!data) {
    return
  }
  actionLoading.value = 'generate'
  try {
    const res = await apiDocument.generate(generatingTemplate.value.id, data)
    lastRecord.value = res.data
    toast.success('Word 文档已生成')
    await load()
  }
  finally {
    actionLoading.value = ''
  }
}

/** 生成数据回到模板当前占位符推导出的默认结构，避免编辑器被改乱后无从还原。 */
function resetGenerateData() {
  if (generatingTemplate.value) {
    generateDataJson.value = JSON.stringify(defaultGenerateData(generatingTemplate.value), null, 2)
  }
}

function onPlaceholdersFormatError() {
  toast.error('占位符 JSON 无法格式化', { description: '请先修正 JSON 语法' })
}

function onGenerateFormatError() {
  toast.error('生成数据 JSON 无法格式化', { description: '请先修正 JSON 语法' })
}

function parsePlaceholdersJson(value: string) {
  const parsed = parseJson(value, '占位符')
  if (!parsed) {
    return null
  }
  const result: Record<string, string> = {}
  for (const [key, item] of Object.entries(parsed)) {
    if (item != null && typeof item !== 'string') {
      toast.error('占位符说明必须是字符串', { description: `请检查 ${key}` })
      return null
    }
    result[key] = item || ''
  }
  return result
}

function parseJson(value: string, label: string) {
  try {
    const parsed = value ? JSON.parse(value) : {}
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      toast.error(`${label}必须是 JSON 对象`)
      return null
    }
    return parsed as WordGenerateData
  }
  catch {
    toast.error(`${label}格式错误`, { description: '请输入合法 JSON 对象' })
    return null
  }
}

/** 编辑器内的即时提示：只反馈 JSON 对象形态，提交时仍由 parseJson 完整校验。 */
function jsonIssue(value: string) {
  const text = value.trim()
  if (!text) {
    return ''
  }
  try {
    const parsed = JSON.parse(text)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      return '需要 JSON 对象'
    }
    return ''
  }
  catch {
    return 'JSON 语法错误'
  }
}

function defaultGenerateData(row: WordTemplate) {
  const result: WordGenerateData = {}
  Object.entries(row.placeholders || {}).forEach(([key, label]) => {
    assignSampleValue(result, key, label || '')
  })
  return result
}

function assignSampleValue(target: WordGenerateData, key: string, value: string) {
  const parts = key.split('.').map(item => item.trim()).filter(Boolean)
  if (!parts.length) {
    return
  }
  let cursor = target
  parts.slice(0, -1).forEach((part) => {
    const current = cursor[part]
    if (!current || Array.isArray(current) || typeof current !== 'object') {
      cursor[part] = {}
    }
    cursor = cursor[part] as WordGenerateData
  })
  cursor[parts[parts.length - 1]] = value
}

function openFile(url?: string) {
  if (url) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

function stripExt(name: string) {
  return name.replace(/\.[^.]+$/, '')
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function resultStatus(status: GenerationStatus) {
  return status === 'SUCCESS' ? 'success' : 'error'
}

function resultTitle(status: GenerationStatus) {
  return status === 'SUCCESS' ? '文档已生成' : '生成失败'
}

function resultDescription(record: WordGenerationRecord) {
  if (record.status === 'SUCCESS') {
    return record.outputFilename
      ? `已生成 ${record.outputFilename}，可下载或到生成记录中回溯。`
      : '文档已生成，可到生成记录中回溯。'
  }
  return record.errorMessage || '请检查生成数据后重试。'
}

function placeholderKeys(row: WordTemplate) {
  return Object.keys(row.placeholders || {})
}

/** 表格与移动卡片共用单元格：只列前几个占位符，完整清单在编辑弹窗里。 */
function visiblePlaceholderKeys(row: WordTemplate) {
  return placeholderKeys(row).slice(0, 6)
}

function hiddenPlaceholderCount(row: WordTemplate) {
  return Math.max(0, placeholderKeys(row).length - 6)
}

function tabIcon(value: string | number) {
  return tabIcons[String(value)] || 'morph:file-text'
}
</script>

<template>
  <div>
    <!-- data-tour 锚点固定挂在页头操作上：切标签页不影响引导定位，教程入口也不再依赖教程内容是否展开 -->
    <YdPageHeader title="Word 模板" class="mb-0">
      <template #description>
        维护 DOCX 模板文件与占位符说明，生成记录可回溯每一次导出的结果与异常。
      </template>
      <YdButton data-tour="document-guide" variant="outline" @click="openGuide">
        <YdIcon name="morph:book-open" />
        写法教程
      </YdButton>
      <YdButton v-if="tour.isCompleted('platform.document.word-template')" variant="ghost" @click="replayGuide">
        <YdIcon name="morph:circle-help" />
        操作引导
      </YdButton>
      <YdButton v-auth="'platform:document:edit'" data-tour="document-upload" @click="pickCreateFile">
        <YdIcon name="morph:cloud-upload" />
        上传模板
      </YdButton>
      <YdFilePicker ref="fileInput" accept=".docx" label="选择 DOCX 模板" class="hidden" @select="onCreateFileChange">
        <template #default />
      </YdFilePicker>
      <YdFilePicker ref="replaceInput" accept=".docx" label="替换 DOCX 模板" class="hidden" @select="onReplaceFileChange">
        <template #default />
      </YdFilePicker>
    </YdPageHeader>

    <YdPageMain>
      <div class="doc-toolbar">
        <YdSegmented v-model="activeTab" data-tour="document-tabs" :options="tabOptions" :block="isMobile" size="sm" aria-label="模板与生成记录视图">
          <template #option="{ option }">
            <span class="doc-tab">
              <YdIcon :name="tabIcon(option.value)" />
              {{ option.label }}
            </span>
          </template>
        </YdSegmented>
        <YdListToolbar
          v-model:keyword="search.keyword"
          class="doc-toolbar__search"
          placeholder="模板名称 / 编码 / 文件名"
          :search-width="360"
          @search="load"
          @clear="load"
        >
          <template #actions>
            <YdButton variant="outline" @click="resetSearch">
              重置
            </YdButton>
            <YdButton :loading="loading" @click="load">
              <YdIcon name="morph:search" />
              筛选
            </YdButton>
          </template>
        </YdListToolbar>
      </div>

      <section :key="activeTab" class="doc-list doc-pane">
        <header class="doc-list__head">
          <div class="doc-list__heading">
            <h2>{{ activeTab === 'templates' ? '模板列表' : '生成记录' }}</h2>
            <span class="doc-list__count">共 {{ pagination.total }} 条</span>
          </div>
          <YdButton variant="ghost" size="sm" :loading="loading" @click="load">
            <YdIcon name="morph:refresh-cw" />
            刷新
          </YdButton>
        </header>

        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            v-if="activeTab === 'templates' && rows.length"
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1200px]"
            stripe
            column-visibility
            border
            :user-column="templateUserColumn"
            :status-column="templateStatusColumn"
            :actions="templateActions"
            :columns="templateColumns"
            :data="rows"
          >
            <template #cell-placeholders="{ row }">
              <div class="placeholder-tags">
                <YdTag v-for="key in visiblePlaceholderKeys(row.original)" :key="key" variant="secondary">
                  {{ key }}
                </YdTag>
                <YdTag v-if="hiddenPlaceholderCount(row.original)" variant="secondary">
                  +{{ hiddenPlaceholderCount(row.original) }}
                </YdTag>
                <span v-if="!placeholderKeys(row.original).length" class="doc-muted">-</span>
              </div>
            </template>
            <template #cell-updateTime="{ row }">
              {{ dateText(row.original.updateTime) }}
            </template>
          </YdResponsiveEnhancedTable>

          <YdResponsiveEnhancedTable
            v-else-if="activeTab === 'records' && recordRows.length"
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1260px]"
            stripe
            column-visibility
            border
            :user-column="recordUserColumn"
            :status-column="recordStatusColumn"
            :actions="recordActions"
            :columns="recordColumns"
            :data="recordRows"
          >
            <template #cell-generatedAt="{ row }">
              {{ dateText(row.original.generatedAt) }}
            </template>
            <template #cell-errorMessage="{ row }">
              <span class="doc-cell-clamp line-clamp-1" :title="row.original.errorMessage || undefined">{{ row.original.errorMessage || '-' }}</span>
            </template>
          </YdResponsiveEnhancedTable>

          <YdEmpty
            v-else-if="!loading && activeTab === 'templates'"
            :illustration="trimmedKeyword ? 'no-result' : 'empty'"
            :title="trimmedKeyword ? '没有匹配的模板' : '还没有 Word 模板'"
            :description="trimmedKeyword ? '换个关键词，或清空筛选后重新查看。' : '上传 DOCX 模板并登记占位符，就能在这里生成与替换文档。'"
          >
            <YdButton v-if="trimmedKeyword" variant="outline" @click="resetSearch">
              清空筛选
            </YdButton>
            <YdButton v-else v-auth="'platform:document:edit'" @click="pickCreateFile">
              <YdIcon name="morph:cloud-upload" />
              上传模板
            </YdButton>
            <YdButton variant="outline" @click="openGuide">
              <YdIcon name="morph:book-open" />
              写法教程
            </YdButton>
          </YdEmpty>

          <YdEmpty
            v-else-if="!loading"
            :illustration="trimmedKeyword ? 'no-result' : 'empty'"
            :title="trimmedKeyword ? '没有匹配的生成记录' : '还没有生成记录'"
            :description="trimmedKeyword ? '换个关键词，或清空筛选后重新查看。' : '在模板管理里选择已启用的模板并生成文档后，记录会出现在这里。'"
          >
            <YdButton v-if="trimmedKeyword" variant="outline" @click="resetSearch">
              清空筛选
            </YdButton>
            <YdButton v-else v-auth="'platform:document:generate'" variant="outline" @click="activeTab = 'templates'">
              去模板管理生成
            </YdButton>
          </YdEmpty>

          <div v-else class="doc-list__placeholder" aria-hidden="true" />
        </YdLoadingOverlay>

        <YdPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          class="mt-1"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </section>
    </YdPageMain>

    <!-- 写法教程按需打开：长内容进弹窗，主体滚动由弹窗内容区承担，底部不再挂默认按钮 -->
    <YdModal
      v-model="guideVisible"
      title="Word 模板写法教程"
      description="在 Word 文档里写变量和循环，上传到这里维护；业务插件只需要选择已启用的模板。"
      :footer="false"
      class="sm:max-w-5xl"
    >
      <div class="guide-pane">
        <div class="guide-flow">
          <section class="guide-chapter">
            <header class="guide-chapter__head">
              <span class="guide-index">01</span>
              <div>
                <h3>在模板中写变量</h3>
                <p>正文、表格、页眉和页脚都可以放变量；生成时会用 JSON 中同名的数据替换。</p>
              </div>
            </header>
            <div class="guide-example">
              <span class="guide-label">变量写法</span>
              <div class="guide-code-row">
                <code v-pre>{{activityName}}</code>
                <code>${activityName}</code>
                <code v-pre>{{student.name}}</code>
              </div>
              <p>
                点号表示读取嵌套对象，例如 <code v-pre>{{student.name}}</code> 对应数据 <code>{ "student": { "name": "张三" } }</code>。
              </p>
            </div>
          </section>

          <section class="guide-chapter">
            <header class="guide-chapter__head">
              <span class="guide-index">02</span>
              <div>
                <h3>需要重复内容时使用循环</h3>
                <p>循环的数据必须是数组。按排版场景选择表格行、段落或双栏名单写法。</p>
              </div>
            </header>
            <div class="guide-subsection">
              <div class="guide-subsection__head">
                <h4>表格名单</h4>
                <span>每个数组元素复制一行</span>
              </div>
              <YdCode :code="guideCode.rowLoop" title="表格行循环" class="guide-code" />
              <p>循环内的字段默认读取当前成员，也可以写成 <code v-pre>{{item.studentName}}</code>。</p>
            </div>
            <div class="guide-subsection">
              <div class="guide-subsection__head">
                <h4>段落名单</h4>
                <span>在一段文字中拼接内容</span>
              </div>
              <YdCode :code="guideCode.paragraphLoop" title="段落内循环" class="guide-code" />
            </div>
            <div class="guide-subsection">
              <div class="guide-subsection__head">
                <h4>双栏名单</h4>
                <span>每个数组元素包含左右两位成员</span>
              </div>
              <YdCode :code="guideCode.columnLoop" title="双栏名单循环" class="guide-code" />
              <p><code>l/r</code> 是 <code>left/right</code> 的短别名。请把它们放在 <code>participantRows</code> 数组项中，不要作为根级变量。</p>
              <YdCode :code="guideCode.columnData" language="json" title="双栏名单数据" line-numbers class="guide-code" />
            </div>
          </section>

          <section class="guide-chapter">
            <header class="guide-chapter__head">
              <span class="guide-index">03</span>
              <div>
                <h3>准备生成数据</h3>
                <p>占位符 JSON 记录变量说明，也会作为手动生成的默认值；实际替换使用生成数据 JSON。</p>
              </div>
            </header>
            <div class="guide-data-grid">
              <div class="guide-subsection">
                <div class="guide-subsection__head">
                  <h4>占位符说明</h4>
                  <span>变量名与说明</span>
                </div>
                <YdCode :code="guideCode.placeholders" language="json" title="占位符 JSON" line-numbers class="guide-code" />
              </div>
              <div class="guide-subsection">
                <div class="guide-subsection__head">
                  <h4>生成数据</h4>
                  <span>值的结构要与模板变量匹配</span>
                </div>
                <YdCode :code="guideCode.sampleData" language="json" title="生成数据 JSON" line-numbers class="guide-code" />
              </div>
            </div>
          </section>

          <section class="guide-chapter guide-chapter--notes">
            <header class="guide-chapter__head">
              <span class="guide-index">04</span>
              <div>
                <h3>开始使用</h3>
                <p>变量名建议使用字母、数字、下划线、点和短横线；模板上传后保持启用，再从操作菜单选择“生成”。</p>
              </div>
            </header>
            <p class="guide-note">
              双栏循环的开始与结束标记单独放行，中间放需要重复的变量行。模板语法不确定时，可先用少量数据试生成并检查结果。
            </p>
          </section>
        </div>
      </div>
    </YdModal>

    <YdModal v-model="formVisible" :title="editing ? `编辑 Word 模板 · ${editing.name}` : '上传 Word 模板'" show-cancel-button class="sm:max-w-4xl" @confirm="saveForm">
      <YdForm :model="form" layout="vertical">
        <div class="gap-x-4 grid grid-cols-1 md:grid-cols-2">
          <YdFormItem label="模板名称" required>
            <YdInput v-model="form.name" />
          </YdFormItem>
          <YdFormItem label="模板编码" required>
            <YdInput v-model="form.code" :disabled="!!editing" />
          </YdFormItem>
          <YdFormItem label="状态">
            <YdSelect v-model="form.status" :options="statusOptions" />
          </YdFormItem>
          <YdFormItem label="模板文件">
            <YdInput :model-value="editing?.originalFilename || pendingFile?.name || '-'" disabled />
          </YdFormItem>
          <YdFormItem label="占位符 JSON" class="md:col-span-2" extra="键是模板变量名（支持 student.name 这类点路径），值是变量说明；同时作为手动生成时的默认 JSON。">
            <YdCodeEditor
              ref="placeholdersEditor"
              v-model="placeholdersJson"
              language="json"
              height="240px"
              aria-label="占位符 JSON"
              :error="placeholdersIssue"
              :placeholder="placeholdersPlaceholder"
              @format-error="onPlaceholdersFormatError"
            />
            <div class="doc-editor-foot">
              <div class="doc-editor-foot__actions">
                <YdButton size="sm" variant="outline" @click="placeholdersEditor?.format()">
                  <YdIcon name="morph:wand-sparkles" />
                  格式化
                </YdButton>
              </div>
            </div>
          </YdFormItem>
          <YdFormItem label="描述" class="md:col-span-2">
            <YdTextarea v-model="form.description" rows="4" />
          </YdFormItem>
        </div>
      </YdForm>
    </YdModal>

    <YdModal v-model="generateVisible" :title="generatingTemplate ? `生成 Word 文档 · ${generatingTemplate.name}` : '生成 Word 文档'" show-cancel-button class="sm:max-w-4xl" :confirm-loading="actionLoading === 'generate'" @confirm="generate">
      <div class="generate-grid">
        <YdForm :model="{}" layout="vertical">
          <YdFormItem label="生成数据 JSON" extra="点路径会读取嵌套对象；双栏名单请使用 participantRows 数组，每一项包含 l 与 r。">
            <YdCodeEditor
              ref="generateEditor"
              v-model="generateDataJson"
              language="json"
              height="360px"
              aria-label="生成数据 JSON"
              :error="generateIssue"
              @format-error="onGenerateFormatError"
            />
            <div class="doc-editor-foot">
              <div class="doc-editor-foot__actions">
                <YdButton size="sm" variant="ghost" :disabled="!generatingTemplate" @click="resetGenerateData">
                  <YdIcon name="morph:rotate-ccw" />
                  模板默认值
                </YdButton>
                <YdButton size="sm" variant="outline" @click="generateEditor?.format()">
                  <YdIcon name="morph:wand-sparkles" />
                  格式化
                </YdButton>
              </div>
            </div>
          </YdFormItem>
        </YdForm>
        <section class="result-panel">
          <div class="result-panel__head">
            <span class="result-panel__title">最近结果</span>
          </div>
          <template v-if="lastRecord">
            <YdResult
              :status="resultStatus(lastRecord.status)"
              :title="resultTitle(lastRecord.status)"
              :description="resultDescription(lastRecord)"
              illustration-size="sm"
            >
              <template #action>
                <YdButton v-if="lastRecord.outputFileUrl" variant="outline" @click="openFile(lastRecord.outputFileUrl)">
                  <YdIcon name="morph:download" />
                  下载生成文件
                </YdButton>
              </template>
            </YdResult>
            <dl class="result-panel__meta">
              <div v-if="lastRecord.outputFilename">
                <dt>生成文件</dt>
                <dd>{{ lastRecord.outputFilename }}</dd>
              </div>
              <div>
                <dt>生成时间</dt>
                <dd>{{ dateText(lastRecord.generatedAt) }}</dd>
              </div>
              <div v-if="lastRecord.operatorId">
                <dt>操作人</dt>
                <dd>{{ lastRecord.operatorId }}</dd>
              </div>
            </dl>
            <YdCode :code="lastRecordJson" language="json" title="生成记录 JSON" line-numbers class="result-code result-code--tall" />
          </template>
          <YdEmpty
            v-else
            illustration="empty"
            title="尚未生成"
            description="填写左侧生成数据后点击「生成」，这里会显示最近一次的结果与下载入口。"
          />
        </section>
      </div>
    </YdModal>
  </div>
</template>

<style scoped>
/* 工具条：视图切换 + 查询 + 筛选收在一条窄带里，切标签页不再重复整个查询区 */
.doc-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.doc-toolbar__search {
  flex: 0 1 auto;
  min-width: 220px;
}

.doc-tab {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

/* 教程采用单列顺序阅读；层级由章节编号、分隔线和小节标题建立，不叠加卡片 */
.guide-pane {
  display: block;
}

.guide-flow {
  display: grid;
  gap: 22px;
}

.guide-chapter {
  display: grid;
  gap: 14px;
  min-width: 0;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border-2);
}

.guide-chapter:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.guide-chapter__head {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.guide-index {
  padding-top: 2px;
  font-size: 12px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-3);
}

.guide-chapter__head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--color-text-1);
}

.guide-chapter__head p,
.guide-subsection > p {
  margin: 5px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-2);
}

.guide-example,
.guide-subsection {
  display: grid;
  gap: 9px;
  min-width: 0;
  margin-left: 40px;
}

.guide-label,
.guide-subsection__head span {
  font-size: 12px;
  color: var(--color-text-3);
}

.guide-subsection__head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: baseline;
}

.guide-subsection__head h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.guide-example > p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-2);
}

.guide-flow code {
  display: inline-flex;
  max-width: 100%;
  padding: 2px 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 5px;
}

.guide-code-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.guide-code {
  min-width: 0;
}

.guide-data-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-left: 40px;
}

.guide-note {
  padding-left: 12px;
  margin: 0 0 0 40px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-2);
  border-left: 2px solid var(--color-border-2);
}

/* 长示例在块内滚动，保持教程弹窗的整体高度可控 */
.guide-data-grid :deep(.yd-code-body),
.result-code--tall :deep(.yd-code-body) {
  max-height: 260px;
}

/* 数据区：标题 + 计数 + 刷新，然后是表格或空态 */
.doc-list {
  display: grid;
  gap: 12px;
}

.doc-list__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.doc-list__heading {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.doc-list__heading h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: var(--color-text-1);
}

.doc-list__count {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-3);
}

/* 首次加载时占位，保证 YdLoadingOverlay 有稳定尺寸 */
.doc-list__placeholder {
  min-height: 280px;
}

.placeholder-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.doc-muted {
  font-size: 12px;
  color: var(--color-text-3);
}

/* 异常列压成一行，完整原因通过 title 与操作菜单里的结果面板查看 */
.doc-cell-clamp {
  color: var(--color-text-2);
}

/* 生成弹窗：左侧编辑器、右侧最近结果 */
.generate-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
  gap: 16px;
  align-items: start;
}

.doc-editor-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding-top: 8px;
}

.doc-editor-foot__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.result-panel {
  display: grid;
  gap: 12px;
  align-content: start;
  min-width: 0;
  padding: 14px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.result-panel__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.result-panel__title {
  font-size: 13px;
  font-weight: 650;
  color: var(--color-text-1);
}

.result-panel__meta {
  display: grid;
  gap: 0;
  margin: 0;
}

.result-panel__meta > div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px solid var(--color-border-2);
}

.result-panel__meta > div:last-child {
  border-bottom: none;
}

.result-panel__meta dt {
  font-size: 12px;
  color: var(--color-text-3);
}

.result-panel__meta dd {
  min-width: 0;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
}

.result-code--tall :deep(.yd-code-body) {
  max-height: 260px;
}

/* 内容揭示只保留一次轻微淡入；雅致/紧凑与减少动态效果偏好下直接出现 */
.doc-pane {
  animation: doc-pane-in 0.24s cubic-bezier(0.32, 0.72, 0, 1) both;
}

@keyframes doc-pane-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

:global([data-admin-style="calm"]) .doc-pane,
:global([data-admin-style="compact"]) .doc-pane {
  animation: none;
}

@media (width <= 900px) {
  .guide-data-grid,
  .generate-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (width <= 560px) {
  .guide-example,
  .guide-subsection,
  .guide-data-grid,
  .guide-note {
    margin-left: 0;
  }

  .result-panel__meta > div {
    grid-template-columns: minmax(0, 1fr);
    gap: 2px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .doc-pane {
    animation: none;
  }
}
</style>
