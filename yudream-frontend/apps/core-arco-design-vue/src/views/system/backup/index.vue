<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type {
  BackupAnalysis,
  BackupConflictStrategy,
  BackupJob,
  BackupPlan,
  BackupScope,
  RemoteArchive,
  RemoteTarget,
  RemoteTargetType,
} from '@/api/modules/system-backup'
import apiBackup from '@/api/modules/system-backup'
import { saveExcelResponse } from '@/utils/excel'

const modal = useFaModal()
const toast = useFaToast()

const tabList = [
  { label: '导出与导入', value: 'export', icon: 'i-ri:download-cloud-2-line' },
  { label: '任务记录', value: 'jobs', icon: 'i-ri:history-line' },
  { label: '异地目标', value: 'targets', icon: 'i-ri:cloud-line' },
  { label: '备份计划', value: 'plans', icon: 'i-ri:timer-line' },
]
const activeTab = ref('export')

// ---------------------------------------------------------------- 范围与导出

const scopes = ref<BackupScope[]>([])
const exportScopes = ref<(string | number)[]>(['system'])
const exportTargetCode = ref('')
const exporting = ref(false)

const scopeCheckOptions = computed(() => scopes.value.map(scope => ({
  label: scope.displayName,
  value: scope.tag,
  description: scope.description || '',
})))

// ---------------------------------------------------------------- 合并导入

const importInputRef = ref<HTMLInputElement>()
const importing = ref(false)
const importModalVisible = ref(false)
const importStrategy = ref('LOCAL_WINS')
const analysis = ref<BackupAnalysis | null>(null)
const stagedUploadId = ref('')
const importingProgress = ref<number | null>(null)
/** 分片大小 8MB：兼顾请求数与单请求内存/失败重传代价。 */
const CHUNK_SIZE = 8 * 1024 * 1024

const strategyOptions = [
  { label: '以本地数据为准', value: 'LOCAL_WINS', description: '只插入本地缺失的数据，不覆盖任何现有数据' },
  { label: '以备份数据为准', value: 'ARCHIVE_WINS', description: '备份中存在的同标识数据覆盖本地，本地独有数据保留' },
]

const analysisColumns = [
  { accessorKey: 'name', header: '集合' },
  { accessorKey: 'archiveCount', header: '归档数据', align: 'right' as const },
  { accessorKey: 'missingCount', header: '本地缺失', align: 'right' as const },
  { accessorKey: 'conflictCount', header: '重复冲突', align: 'right' as const },
]

// ---------------------------------------------------------------- 任务

const jobs = ref<BackupJob[]>([])
const jobsLoading = ref(false)
let pollTimer: ReturnType<typeof setInterval> | undefined

// ---------------------------------------------------------------- 异地目标

const targets = ref<RemoteTarget[]>([])
const targetsLoading = ref(false)
const targetFormVisible = ref(false)
const targetFormSaving = ref(false)
const targetTesting = ref(false)
const editingTarget = ref<RemoteTarget | null>(null)
const targetForm = reactive({
  code: '',
  name: '',
  type: 'WEBDAV' as RemoteTargetType,
  host: '',
  port: 443,
  username: '',
  password: '',
  basePath: '/',
  passiveMode: true,
  insecureTls: false,
})

const targetTypes: { label: string, value: RemoteTargetType }[] = [
  { label: 'WebDAV（推荐，HTTPS）', value: 'WEBDAV' },
  { label: 'FTP', value: 'FTP' },
  { label: 'FTPS（显式 TLS）', value: 'FTPS' },
]

// 远端归档与恢复
const archivesVisible = ref(false)
const archivesLoading = ref(false)
const archivesTarget = ref<RemoteTarget | null>(null)
const archives = ref<RemoteArchive[]>([])
const restoreStrategy = ref('LOCAL_WINS')

// ---------------------------------------------------------------- 备份计划

const plans = ref<BackupPlan[]>([])
const plansLoading = ref(false)
const planFormVisible = ref(false)
const planFormSaving = ref(false)
const editingPlan = ref<BackupPlan | null>(null)
const planForm = reactive({
  code: '',
  name: '',
  cron: '0 0 3 * * *',
  scopeTags: ['system'] as string[],
  targetCode: '',
  retentionCount: 10,
})

const jobColumns = computed<TableColumn<BackupJob>[]>(() => [
  { accessorKey: 'createTime', header: '创建时间', width: 170 },
  { id: 'type', header: '类型', width: 110 },
  { id: 'scopeTags', header: '范围', width: 220 },
  { id: 'status', header: '状态', width: 170 },
  { id: 'archiveSize', header: '归档大小', width: 110, align: 'right' },
  { id: 'targetName', header: '异地目标', width: 140 },
  { id: 'counts', header: '结果统计', width: 240 },
  { id: 'operation', header: '操作', width: 150, align: 'center', fixed: 'right' },
])

const targetColumns = computed<TableColumn<RemoteTarget>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 180, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 140 },
  { id: 'type', header: '协议', width: 100 },
  { id: 'endpoint', header: '地址', width: 260 },
  { accessorKey: 'username', header: '账号', width: 140 },
  { id: 'enabled', header: '状态', width: 80, align: 'center' },
  { id: 'operation', header: '操作', width: 280, align: 'center', fixed: 'right' },
])

const planColumns = computed<TableColumn<BackupPlan>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 170, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 140 },
  { accessorKey: 'cron', header: 'cron', width: 140 },
  { id: 'scopeTags', header: '范围', width: 220 },
  { accessorKey: 'targetName', header: '异地目标', width: 140 },
  { accessorKey: 'retentionCount', header: '保留份数', width: 90, align: 'right' },
  { id: 'enabled', header: '状态', width: 80, align: 'center' },
  { accessorKey: 'lastRunAt', header: '上次执行', width: 170 },
  { id: 'operation', header: '操作', width: 240, align: 'center', fixed: 'right' },
])

const planScopeModel = computed({
  get: () => planForm.scopeTags as (string | number)[],
  set: (value) => {
    planForm.scopeTags = value.map(String)
  },
})

const exportTargetOptions = computed(() => [
  { label: '仅本机导出（归档留在服务器）', value: '' },
  ...targets.value.filter(target => target.enabled).map(target => ({
    label: `立即推送到 ${target.name}（${target.code}）`,
    value: target.code,
  })),
])

const targetOptions = computed(() => targets.value.filter(target => target.enabled).map(target => ({
  label: `${target.name}（${target.code}）`,
  value: target.code,
})))

onMounted(() => {
  loadScopes()
  loadJobs()
  loadTargets()
  loadPlans()
  pollTimer = setInterval(pollJobs, 3000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})

async function loadScopes() {
  try {
    const res = await apiBackup.scopes()
    scopes.value = res.data
  }
  catch {
    // 拦截器已提示
  }
}

async function loadJobs(silent = false) {
  if (!silent) {
    jobsLoading.value = true
  }
  try {
    const res = await apiBackup.jobs(50)
    jobs.value = res.data
  }
  catch {
    // 拦截器已提示
  }
  finally {
    if (!silent) {
      jobsLoading.value = false
    }
  }
}

async function pollJobs() {
  if (!jobs.value.some(job => job.status === 'QUEUED' || job.status === 'RUNNING')) {
    return
  }
  const hadActive = jobs.value.some(job => job.status === 'QUEUED' || job.status === 'RUNNING')
  await loadJobs(true)
  const stillActive = jobs.value.some(job => job.status === 'QUEUED' || job.status === 'RUNNING')
  if (hadActive && !stillActive) {
    toast.success('备份任务已结束，请查看任务记录')
  }
}

function hasActiveJob() {
  return jobs.value.some(job => job.status === 'QUEUED' || job.status === 'RUNNING')
}

// ---------------------------------------------------------------- 导出

function confirmExport() {
  if (exportScopes.value.length === 0) {
    toast.error('请至少选择一个备份范围')
    return
  }
  if (hasActiveJob()) {
    toast.warning('已有备份任务在执行，请等待完成')
    return
  }
  const names = scopes.value
    .filter(scope => exportScopes.value.includes(scope.tag))
    .map(scope => scope.displayName)
    .join('、')
  const pushTarget = targets.value.find(target => target.code === exportTargetCode.value)
  const targetText = pushTarget ? `，完成后立即推送到异地「${pushTarget.name}」` : '，归档留在服务器可供下载'
  modal.confirm({
    title: '确认立即执行备份',
    content: `将对范围「${names}」执行全量备份${targetText}。视数据量可能耗时较长，任务在后台执行。`,
    onConfirm: async () => {
      exporting.value = true
      try {
        await apiBackup.export({
          scopeTags: exportScopes.value.map(String),
          targetCode: exportTargetCode.value || undefined,
        })
        toast.success('备份任务已创建')
        activeTab.value = 'jobs'
        await loadJobs()
      }
      finally {
        exporting.value = false
      }
    },
  })
}

// ---------------------------------------------------------------- 合并导入

function pickImportFile() {
  importInputRef.value?.click()
}

async function onImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  importing.value = true
  importingProgress.value = 0
  let uploadId = ''
  try {
    // 分片顺序上传：服务端按偏移追加落盘并增量算摘要，规避超大档单请求超时/内存问题
    uploadId = (await apiBackup.beginChunkUpload({ name: file.name, size: file.size })).data
    stagedUploadId.value = uploadId
    let offset = 0
    while (offset < file.size) {
      const chunk = file.slice(offset, Math.min(offset + CHUNK_SIZE, file.size))
      offset = Number((await apiBackup.uploadChunk(uploadId, offset, chunk)).data ?? offset)
      importingProgress.value = Math.round((offset / file.size) * 100)
    }
    await apiBackup.finishChunkUpload({ uploadId, size: file.size })
    const res = await apiBackup.analyzeStaged(uploadId)
    analysis.value = res.data
    importStrategy.value = 'LOCAL_WINS'
    importModalVisible.value = true
  }
  catch {
    if (uploadId) {
      void apiBackup.abortChunkUpload(uploadId).catch(() => {})
    }
    stagedUploadId.value = ''
  }
  finally {
    importing.value = false
    importingProgress.value = null
  }
}

function submitImport() {
  const uploadId = stagedUploadId.value
  if (!uploadId || !analysis.value) {
    return
  }
  const localWins = importStrategy.value === 'LOCAL_WINS'
  // 后端全局 Jackson 把 long 序列化为字符串（防精度丢失），参与算术前必须 Number()
  const conflictTotal
    = analysis.value.collections.reduce((sum, item) => sum + Number(item.conflictCount), 0)
      + Number(analysis.value.objects.conflictCount)
  modal.confirm({
    title: '确认合并导入',
    content: `已选择「${localWins ? '以本地数据为准' : '以备份数据为准'}」。`
      + `重复数据 ${conflictTotal} 条将${localWins ? '保留本地、只补缺失部分' : '以备份覆盖本地'}；`
      + `任一端独有的数据始终保留，不会删除。确认开始合并导入吗？`,
    onConfirm: async () => {
      importModalVisible.value = false
      const res = await apiBackup.importStaged(uploadId, importStrategy.value as BackupConflictStrategy)
      toast.success(`合并导入任务已创建（#${res.data.id}）`)
      analysis.value = null
      stagedUploadId.value = ''
      activeTab.value = 'jobs'
      await loadJobs()
    },
  })
}

// ---------------------------------------------------------------- 任务操作

function confirmRemoveJob(row: BackupJob) {
  modal.confirm({
    title: '确认删除任务',
    content: `确认删除任务 #${row.id} 吗？${row.type === 'EXPORT' ? '其本机归档会一并删除。' : ''}`,
    onConfirm: async () => {
      await apiBackup.removeJob(row.id)
      toast.success('已删除')
      await loadJobs()
    },
  })
}

async function downloadArchive(row: BackupJob) {
  // blob 响应经拦截器包装为 { data: Blob, headers }，复用既有的保存工具（解析 UTF-8 文件名头）
  const res = await apiBackup.downloadArchive(row.id)
  saveExcelResponse(res, row.archiveName || `backup-${row.id}.zip`)
}

// ---------------------------------------------------------------- 异地目标

async function loadTargets() {
  targetsLoading.value = true
  try {
    const res = await apiBackup.targets()
    targets.value = res.data
  }
  finally {
    targetsLoading.value = false
  }
}

function defaultPortFor(type: RemoteTargetType) {
  return type === 'WEBDAV' ? 443 : 21
}

function openTargetForm(row?: RemoteTarget) {
  editingTarget.value = row || null
  Object.assign(targetForm, row
    ? {
        code: row.code,
        name: row.name,
        type: row.type,
        host: row.host,
        port: row.port ?? defaultPortFor(row.type),
        username: row.username,
        password: '',
        basePath: row.basePath || '/',
        passiveMode: row.passiveMode !== false,
        insecureTls: row.insecureTls === true,
      }
    : {
        code: '',
        name: '',
        type: 'WEBDAV',
        host: '',
        port: 443,
        username: '',
        password: '',
        basePath: '/',
        passiveMode: true,
        insecureTls: false,
      })
  targetFormVisible.value = true
}

watch(() => targetForm.type, (type) => {
  targetForm.port = defaultPortFor(type)
})

async function saveTargetForm() {
  if (!editingTarget.value && !targetForm.code?.trim()) {
    toast.error('请填写目标编码')
    return
  }
  if (!targetForm.name?.trim() || !targetForm.host?.trim()) {
    toast.error('请填写名称与主机地址')
    return
  }
  targetFormSaving.value = true
  try {
    if (editingTarget.value) {
      await apiBackup.updateTarget(editingTarget.value.id, { ...targetForm, code: undefined })
      toast.success('目标已保存')
    }
    else {
      await apiBackup.createTarget({ ...targetForm })
      toast.success('目标已创建')
    }
    targetFormVisible.value = false
    await loadTargets()
  }
  finally {
    targetFormSaving.value = false
  }
}

async function testTarget(row: RemoteTarget) {
  targetTesting.value = true
  try {
    await apiBackup.testTarget(row.id)
    toast.success(`「${row.name}」连接成功`)
  }
  finally {
    targetTesting.value = false
  }
}

function confirmToggleTarget(row: RemoteTarget) {
  const enabling = !row.enabled
  modal.confirm({
    title: enabling ? '确认启用' : '确认停用',
    content: `确认${enabling ? '启用' : '停用'}异地目标「${row.name}」吗？停用后引用它的备份计划将执行失败。`,
    onConfirm: async () => {
      await (enabling ? apiBackup.enableTarget(row.id) : apiBackup.disableTarget(row.id))
      toast.success(enabling ? '已启用' : '已停用')
      await loadTargets()
    },
  })
}

function confirmRemoveTarget(row: RemoteTarget) {
  modal.confirm({
    title: '确认删除目标',
    content: `确认删除异地目标「${row.name}」吗？远端已备份的文件不会删除。`,
    onConfirm: async () => {
      await apiBackup.removeTarget(row.id)
      toast.success('已删除')
      await loadTargets()
    },
  })
}

async function openArchives(row: RemoteTarget) {
  archivesTarget.value = row
  archives.value = []
  archivesVisible.value = true
  archivesLoading.value = true
  try {
    const res = await apiBackup.targetArchives(row.id)
    archives.value = res.data
  }
  finally {
    archivesLoading.value = false
  }
}

function confirmRestore(row: RemoteArchive) {
  const target = archivesTarget.value
  if (!target) {
    return
  }
  const localWins = restoreStrategy.value === 'LOCAL_WINS'
  modal.confirm({
    title: '确认从异地恢复',
    content: `将从「${target.name}」下载归档 ${row.name} 并合并导入，策略为「${localWins ? '以本地数据为准' : '以备份数据为准'}」。确认开始吗？`,
    onConfirm: async () => {
      await apiBackup.restoreFromTarget(target.id, { archiveName: row.name, strategy: restoreStrategy.value as BackupConflictStrategy })
      toast.success('恢复任务已创建')
      archivesVisible.value = false
      activeTab.value = 'jobs'
      await loadJobs()
    },
  })
}

// ---------------------------------------------------------------- 备份计划

async function loadPlans() {
  plansLoading.value = true
  try {
    const res = await apiBackup.plans()
    plans.value = res.data
  }
  finally {
    plansLoading.value = false
  }
}

function openPlanForm(row?: BackupPlan) {
  editingPlan.value = row || null
  const defaultScope = scopes.value.find(scope => scope.defaultSchedule)?.defaultSchedule
  Object.assign(planForm, row
    ? {
        code: row.code,
        name: row.name,
        cron: row.cron,
        scopeTags: [...row.scopeTags],
        targetCode: row.targetCode,
        retentionCount: Number(row.retentionCount ?? 10),
      }
    : {
        code: '',
        name: '',
        cron: defaultScope || '0 0 3 * * *',
        scopeTags: ['system'],
        targetCode: targets.value[0]?.code || '',
        retentionCount: 10,
      })
  planFormVisible.value = true
}

async function savePlanForm() {
  if (!editingPlan.value && !planForm.code?.trim()) {
    toast.error('请填写计划编码')
    return
  }
  if (!planForm.name?.trim() || !planForm.cron?.trim()) {
    toast.error('请填写计划名称与 cron 表达式')
    return
  }
  if (!planForm.targetCode) {
    toast.error('请选择异地目标')
    return
  }
  planFormSaving.value = true
  try {
    if (editingPlan.value) {
      await apiBackup.updatePlan(editingPlan.value.id, { ...planForm, code: undefined })
      toast.success('计划已保存')
    }
    else {
      await apiBackup.createPlan({ ...planForm })
      toast.success('计划已创建')
    }
    planFormVisible.value = false
    await loadPlans()
  }
  finally {
    planFormSaving.value = false
  }
}

function confirmRunPlan(row: BackupPlan) {
  modal.confirm({
    title: '确认立即执行',
    content: `确认按计划「${row.name}」立即执行一次异地备份吗？`,
    onConfirm: async () => {
      await apiBackup.runPlan(row.id)
      toast.success('备份任务已入队')
      activeTab.value = 'jobs'
      await loadJobs()
    },
  })
}

function confirmTogglePlan(row: BackupPlan) {
  const enabling = !row.enabled
  modal.confirm({
    title: enabling ? '确认启用' : '确认停用',
    content: `确认${enabling ? '启用' : '停用'}备份计划「${row.name}」吗？`,
    onConfirm: async () => {
      await (enabling ? apiBackup.enablePlan(row.id) : apiBackup.disablePlan(row.id))
      toast.success(enabling ? '已启用' : '已停用')
      await loadPlans()
    },
  })
}

function confirmRemovePlan(row: BackupPlan) {
  modal.confirm({
    title: '确认删除计划',
    content: `确认删除备份计划「${row.name}」吗？已推送到异地的备份不受影响。`,
    onConfirm: async () => {
      await apiBackup.removePlan(row.id)
      toast.success('已删除')
      await loadPlans()
    },
  })
}

// ---------------------------------------------------------------- 展示辅助

function jobTypeText(row: BackupJob) {
  return {
    EXPORT: '全量导出',
    REMOTE_BACKUP: '异地备份',
    IMPORT: '合并导入',
    REMOTE_RESTORE: '异地恢复',
  }[row.type] || row.type
}

function jobStatusVariant(row: BackupJob) {
  switch (row.status) {
    case 'SUCCEEDED':
      return 'default'
    case 'FAILED':
      return 'destructive'
    case 'RUNNING':
      return 'secondary'
    default:
      return 'outline'
  }
}

function jobStatusText(row: BackupJob) {
  if (row.status === 'RUNNING' || row.status === 'QUEUED') {
    return `${row.status === 'RUNNING' ? '执行中' : '排队中'} ${row.percent ?? 0}%`
  }
  return row.status === 'SUCCEEDED' ? '成功' : '失败'
}

function jobCountsText(row: BackupJob) {
  if (row.type === 'IMPORT' || row.type === 'REMOTE_RESTORE') {
    return `新增 ${row.insertedCount ?? 0} · 重复 ${row.conflictCount ?? 0} · 跳过 ${row.skippedCount ?? 0}`
  }
  const parts: string[] = []
  if (row.collectionCount) {
    parts.push(`集合 ${row.collectionCount}`)
  }
  if (row.documentCount) {
    parts.push(`文档 ${row.documentCount}`)
  }
  if (row.objectCount) {
    parts.push(`对象 ${row.objectCount}`)
  }
  if (row.pluginFileCount) {
    parts.push(`插件文件 ${row.pluginFileCount}`)
  }
  return parts.join(' · ') || '—'
}

function targetEndpoint(row: RemoteTarget) {
  return `${row.host}:${row.port}${row.basePath || '/'}`
}

function scopeText(tag?: string[]) {
  if (!tag || tag.length === 0) {
    return '系统数据'
  }
  return tag.map((item) => {
    if (item === 'system') {
      return '系统数据'
    }
    const scope = scopes.value.find(candidate => candidate.tag === item)
    return scope?.displayName || item
  }).join('、')
}

function formatSize(size?: number | string) {
  // long 出网为字符串，先转数值再比较/格式化
  const value = Number(size)
  if (!Number.isFinite(value)) {
    return '—'
  }
  if (value < 1024) {
    return `${value} B`
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`
  }
  if (value < 1024 * 1024 * 1024) {
    return `${(value / 1024 / 1024).toFixed(1)} MB`
  }
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function formatArchiveTime(millis?: number) {
  if (!millis) {
    return '—'
  }
  return new Date(millis).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div>
    <FaPageHeader title="数据备份" class="mb-0">
      <template #description>
        全量导出系统与插件数据为可迁移归档，按合并策略无损导入；支持 FTP/FTPS/WebDAV 异地定时备份与恢复。
      </template>
    </FaPageHeader>
    <FaPageMain>
      <FaTabs v-model="activeTab" :list="tabList">
        <template #export>
          <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <div class="rounded-lg border p-4">
              <div class="text-base font-medium">
                全量导出
              </div>
              <div class="mt-1 text-xs text-secondary-foreground/60">
                将所选范围打包为 ZIP 归档（Mongo 集合 EJSON + 对象存储 + 插件数据），可在本页下载或用于迁移导入。
              </div>
              <FaCheckboxGroup v-model="exportScopes" :options="scopeCheckOptions" class="mt-3" />
              <div class="mt-4 flex flex-col gap-3">
                <div>
                  <div class="mb-1 text-xs font-medium text-secondary-foreground/70">执行方式</div>
                  <FaSelect v-model="exportTargetCode" :options="exportTargetOptions" class="max-w-sm" />
                  <div class="mt-1 text-xs text-secondary-foreground/60">
                    选异地目标即「立即执行备份」：归档生成后当场推送，无需计划。
                  </div>
                </div>
                <div>
                  <FaButton v-auth="'system:backup:export'" :loading="exporting" :disabled="hasActiveJob()" @click="confirmExport">
                    <FaIcon name="i-ri:save-3-line" />
                    立即执行备份
                  </FaButton>
                </div>
              </div>
            </div>
            <div class="rounded-lg border p-4">
              <div class="text-base font-medium">
                合并导入
              </div>
              <div class="mt-1 text-xs text-secondary-foreground/60">
                上传本系统导出的备份归档，先分析重复与缺失，再按管理员选择的「以哪边为准」策略无损合并。任一端独有数据始终保留。
              </div>
              <div class="mt-4">
                <input ref="importInputRef" type="file" accept=".zip" class="hidden" @change="onImportFile">
                <FaButton v-auth="'system:backup:import'" variant="outline" :loading="importing" @click="pickImportFile">
                  <FaIcon name="i-ri:upload-cloud-2-line" />
                  选择备份归档并分析
                </FaButton>
                <template v-if="importing">
                  <FaProgress :model-value="importingProgress ?? 0" class="mt-3" />
                  <div class="mt-1 text-xs text-secondary-foreground/60">
                    分片上传中 {{ importingProgress ?? 0 }}%（8MB/片，服务端合并后分析）
                  </div>
                </template>
              </div>
              <FaAlert
                class="mt-4"
                icon="i-ri:information-line"
                title="跨主机导入提示"
                description="若源站主密钥（YUDREAM_CREDENTIAL_KEY）不同，归档中加密存储的凭据需导入后重新配置。"
              />
            </div>
          </div>
        </template>

        <template #jobs>
          <FaProgress v-if="jobsLoading" :model-value="100" class="mb-2" />
          <FaResponsiveTable
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1180px]"
            border
            stripe
            :columns="jobColumns"
            :data="jobs"
          >
            <template #toolbar>
              <div class="flex flex-wrap items-center justify-between gap-2">
                <span class="text-xs text-secondary-foreground/60">每 3 秒自动刷新执行中任务</span>
                <FaButton variant="outline" size="sm" :loading="jobsLoading" @click="loadJobs()">
                  <FaIcon name="i-ri:refresh-line" />
                  刷新
                </FaButton>
              </div>
            </template>
            <template #cell-type="{ row }">
              <FaTag variant="secondary">{{ jobTypeText(row.original) }}</FaTag>
            </template>
            <template #cell-scopeTags="{ row }">
              <span class="text-sm">{{ scopeText(row.original.scopeTags) }}</span>
            </template>
            <template #cell-status="{ row }">
              <div class="flex flex-col items-start gap-1">
                <FaTag :variant="jobStatusVariant(row.original)">{{ jobStatusText(row.original) }}</FaTag>
                <FaProgress
                  v-if="row.original.status === 'RUNNING' || row.original.status === 'QUEUED'"
                  :model-value="row.original.percent || 0"
                  class="h-1 self-stretch"
                />
                <!-- td 自带 whitespace-nowrap，提示必须显式截断，否则会横穿右侧列 -->
                <span v-if="row.original.message" class="max-w-[154px] truncate text-xs text-secondary-foreground/60" :title="row.original.message">
                  {{ row.original.message }}
                </span>
              </div>
            </template>
            <template #cell-archiveSize="{ row }">
              {{ formatSize(row.original.archiveSize) }}
            </template>
            <template #cell-targetName="{ row }">
              {{ row.original.targetName || '—' }}
            </template>
            <template #cell-counts="{ row }">
              <span class="text-xs">{{ jobCountsText(row.original) }}</span>
            </template>
            <template #cell-operation="{ row }">
              <div class="flex justify-center gap-2">
                <FaButton
                  v-if="row.original.type === 'EXPORT' && row.original.status === 'SUCCEEDED'"
                  v-auth="'system:backup:download'"
                  variant="outline"
                  size="sm"
                  @click="downloadArchive(row.original)"
                >
                  下载归档
                </FaButton>
                <FaButton
                  v-if="row.original.status === 'SUCCEEDED' || row.original.status === 'FAILED'"
                  v-auth="'system:backup:delete'"
                  variant="destructive"
                  size="sm"
                  @click="confirmRemoveJob(row.original)"
                >
                  删除
                </FaButton>
              </div>
            </template>
            <template #card="{ row }">
              <FaCard class="w-full">
                <div class="flex flex-col gap-2 text-sm">
                  <div class="flex items-center justify-between">
                    <span class="font-medium">#{{ row.id }} {{ jobTypeText(row) }}</span>
                    <FaTag :variant="jobStatusVariant(row)">{{ jobStatusText(row) }}</FaTag>
                  </div>
                  <div class="text-xs text-secondary-foreground/60">
                    {{ formatTime(row.createTime) }} · {{ scopeText(row.scopeTags) }} · {{ formatSize(row.archiveSize) }}
                  </div>
                  <div v-if="row.message" class="break-all text-xs text-secondary-foreground/60">
                    {{ row.message }}
                  </div>
                  <div class="flex gap-2 border-t pt-2">
                    <FaButton
                      v-if="row.type === 'EXPORT' && row.status === 'SUCCEEDED'"
                      v-auth="'system:backup:download'"
                      variant="outline"
                      size="sm"
                      @click="downloadArchive(row)"
                    >
                      下载归档
                    </FaButton>
                    <FaButton
                      v-if="row.status === 'SUCCEEDED' || row.status === 'FAILED'"
                      v-auth="'system:backup:delete'"
                      variant="destructive"
                      size="sm"
                      @click="confirmRemoveJob(row)"
                    >
                      删除
                    </FaButton>
                  </div>
                </div>
              </FaCard>
            </template>
          </FaResponsiveTable>
        </template>

        <template #targets>
          <FaProgress v-if="targetsLoading" :model-value="100" class="mb-2" />
          <FaResponsiveTable
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1080px]"
            border
            stripe
            :columns="targetColumns"
            :data="targets"
          >
            <template #toolbar>
              <div class="flex justify-end">
                <FaButton v-auth="'system:backup:config'" @click="openTargetForm()">
                  <FaIcon name="i-ri:add-line" />
                  添加目标
                </FaButton>
              </div>
            </template>
            <template #cell-type="{ row }">
              <FaTag variant="secondary">{{ row.original.type }}</FaTag>
            </template>
            <template #cell-endpoint="{ row }">
              <span class="break-all text-sm">{{ targetEndpoint(row.original) }}</span>
            </template>
            <template #cell-enabled="{ row }">
              <FaTag :variant="row.original.enabled ? 'default' : 'secondary'">
                {{ row.original.enabled ? '启用' : '停用' }}
              </FaTag>
            </template>
            <template #cell-operation="{ row }">
              <div class="flex flex-wrap justify-center gap-2">
                <FaButton v-auth="'system:backup:test'" variant="outline" size="sm" :loading="targetTesting" @click="testTarget(row.original)">
                  测试
                </FaButton>
                <FaButton v-auth="'system:backup:run'" variant="outline" size="sm" @click="openArchives(row.original)">
                  远端归档
                </FaButton>
                <FaButton v-auth="'system:backup:config'" variant="outline" size="sm" @click="confirmToggleTarget(row.original)">
                  {{ row.original.enabled ? '停用' : '启用' }}
                </FaButton>
                <FaButton v-auth="'system:backup:config'" variant="link" size="sm" @click="openTargetForm(row.original)">
                  编辑
                </FaButton>
                <FaButton v-auth="'system:backup:delete'" variant="destructive" size="sm" @click="confirmRemoveTarget(row.original)">
                  删除
                </FaButton>
              </div>
            </template>
            <template #card="{ row }">
              <FaCard class="w-full">
                <div class="flex flex-col gap-2 text-sm">
                  <div class="flex items-center justify-between">
                    <span class="font-medium">{{ row.name }}</span>
                    <FaTag :variant="row.enabled ? 'default' : 'secondary'">
                      {{ row.enabled ? '启用' : '停用' }}
                    </FaTag>
                  </div>
                  <div class="text-xs break-all text-secondary-foreground/60">
                    {{ row.type }} · {{ targetEndpoint(row) }} · {{ row.username || '匿名' }}
                  </div>
                  <div class="flex flex-wrap gap-2 border-t pt-2">
                    <FaButton v-auth="'system:backup:test'" variant="outline" size="sm" @click="testTarget(row)">
                      测试
                    </FaButton>
                    <FaButton v-auth="'system:backup:run'" variant="outline" size="sm" @click="openArchives(row)">
                      远端归档
                    </FaButton>
                    <FaButton v-auth="'system:backup:config'" variant="link" size="sm" @click="openTargetForm(row)">
                      编辑
                    </FaButton>
                    <FaButton v-auth="'system:backup:delete'" variant="destructive" size="sm" @click="confirmRemoveTarget(row)">
                      删除
                    </FaButton>
                  </div>
                </div>
              </FaCard>
            </template>
          </FaResponsiveTable>
        </template>

        <template #plans>
          <FaProgress v-if="plansLoading" :model-value="100" class="mb-2" />
          <FaResponsiveTable
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1180px]"
            border
            stripe
            :columns="planColumns"
            :data="plans"
          >
            <template #toolbar>
              <div class="flex flex-wrap items-center justify-between gap-2">
                <span class="text-xs text-secondary-foreground/60">cron 为 6 位 Spring 表达式（秒 分 时 日 月 周），如每天 3 点：0 0 3 * * *</span>
                <FaButton v-auth="'system:backup:config'" :disabled="targets.length === 0" @click="openPlanForm()">
                  <FaIcon name="i-ri:add-line" />
                  添加计划
                </FaButton>
              </div>
            </template>
            <template #cell-scopeTags="{ row }">
              <span class="text-sm">{{ scopeText(row.original.scopeTags) }}</span>
            </template>
            <template #cell-enabled="{ row }">
              <FaTag :variant="row.original.enabled ? 'default' : 'secondary'">
                {{ row.original.enabled ? '启用' : '停用' }}
              </FaTag>
            </template>
            <template #cell-lastRunAt="{ row }">
              <div class="flex flex-col items-start gap-1">
                <span class="text-sm">{{ formatTime(row.original.lastRunAt) }}</span>
                <FaTag v-if="row.original.lastStatus" :variant="row.original.lastStatus === 'SUCCEEDED' ? 'default' : 'destructive'">
                  {{ row.original.lastStatus === 'SUCCEEDED' ? '成功' : row.original.lastStatus === 'FAILED' ? '失败' : '执行中' }}
                </FaTag>
              </div>
            </template>
            <template #cell-operation="{ row }">
              <div class="flex flex-wrap justify-center gap-2">
                <FaButton v-auth="'system:backup:run'" variant="outline" size="sm" @click="confirmRunPlan(row.original)">
                  立即执行
                </FaButton>
                <FaButton v-auth="'system:backup:config'" variant="outline" size="sm" @click="confirmTogglePlan(row.original)">
                  {{ row.original.enabled ? '停用' : '启用' }}
                </FaButton>
                <FaButton v-auth="'system:backup:config'" variant="link" size="sm" @click="openPlanForm(row.original)">
                  编辑
                </FaButton>
                <FaButton v-auth="'system:backup:delete'" variant="destructive" size="sm" @click="confirmRemovePlan(row.original)">
                  删除
                </FaButton>
              </div>
            </template>
            <template #card="{ row }">
              <FaCard class="w-full">
                <div class="flex flex-col gap-2 text-sm">
                  <div class="flex items-center justify-between">
                    <span class="font-medium">{{ row.name }}</span>
                    <FaTag :variant="row.enabled ? 'default' : 'secondary'">
                      {{ row.enabled ? '启用' : '停用' }}
                    </FaTag>
                  </div>
                  <div class="text-xs text-secondary-foreground/60">
                    {{ row.cron }} · {{ scopeText(row.scopeTags) }} · {{ row.targetName }}
                  </div>
                  <div class="flex flex-wrap gap-2 border-t pt-2">
                    <FaButton v-auth="'system:backup:run'" variant="outline" size="sm" @click="confirmRunPlan(row)">
                      立即执行
                    </FaButton>
                    <FaButton v-auth="'system:backup:config'" variant="link" size="sm" @click="openPlanForm(row)">
                      编辑
                    </FaButton>
                    <FaButton v-auth="'system:backup:delete'" variant="destructive" size="sm" @click="confirmRemovePlan(row)">
                      删除
                    </FaButton>
                  </div>
                </div>
              </FaCard>
            </template>
          </FaResponsiveTable>
        </template>
      </FaTabs>

      <FaModal
        v-model="importModalVisible"
        title="合并导入分析"
        show-cancel-button
        class="sm:max-w-3xl"
        confirm-button-text="开始导入"
        @confirm="submitImport"
      >
        <template v-if="analysis">
          <div class="mb-3 flex flex-wrap items-center gap-2 text-xs text-secondary-foreground/70">
            <FaTag :variant="analysis.masterKeyMatch ? 'default' : 'destructive'">
              {{ analysis.masterKeyMatch ? '主密钥一致' : '主密钥不一致，加密凭据可能需重新配置' }}
            </FaTag>
            <span v-if="analysis.createdAt">备份时间：{{ formatTime(analysis.createdAt) }}</span>
            <span v-if="analysis.hostVersion">宿主版本：{{ analysis.hostVersion }}</span>
          </div>
          <FaAlert
            v-if="analysis.warnings.length"
            icon="i-ri:alert-line"
            title="导入前请注意"
            :description="analysis.warnings.join('；')"
            class="mb-3"
          />
          <FaResponsiveTable
            row-key="name"
            table-root-class="rounded-lg overflow-hidden"
            border
            :columns="analysisColumns"
            :data="analysis.collections"
          />
          <div class="mt-2 flex flex-wrap items-center gap-2 text-xs text-secondary-foreground/70">
            <FaTag variant="secondary">对象存储文件</FaTag>
            <span>归档 {{ analysis.objects.archiveCount }} · 本地缺失 {{ analysis.objects.missingCount }} · 重复冲突 {{ analysis.objects.conflictCount }} · 共 {{ formatSize(analysis.objects.totalBytes) }}</span>
          </div>
          <div v-if="analysis.pluginScopes.length" class="mt-3 flex flex-wrap gap-2">
            <FaTag
              v-for="scope in analysis.pluginScopes"
              :key="`${scope.pluginCode}/${scope.scopeCode}`"
              :variant="scope.available ? 'secondary' : 'destructive'"
            >
              {{ scope.pluginCode }}/{{ scope.scopeCode }} · {{ scope.fileCount }} 文件 · {{ scope.available ? '可恢复' : '插件未安装，将跳过' }}
            </FaTag>
          </div>
          <FaDivider class="my-3" />
          <FaRadioGroup v-model="importStrategy" :options="strategyOptions" />
        </template>
      </FaModal>

      <FaModal
        v-model="targetFormVisible"
        :title="editingTarget ? '编辑异地目标' : '添加异地目标'"
        show-cancel-button
        class="sm:max-w-2xl"
        :confirm-loading="targetFormSaving"
        @confirm="saveTargetForm"
      >
        <a-form :model="targetForm" layout="vertical">
          <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <a-form-item label="目标编码">
              <FaInput v-model="targetForm.code" :disabled="!!editingTarget" placeholder="小写字母、数字或连字符，如 nas-webdav" />
            </a-form-item>
            <a-form-item label="名称">
              <FaInput v-model="targetForm.name" placeholder="目标显示名称" />
            </a-form-item>
            <a-form-item label="协议类型">
              <FaSelect v-model="targetForm.type" :options="targetTypes" />
            </a-form-item>
            <a-form-item label="主机地址">
              <FaInput v-model="targetForm.host" placeholder="如 192.168.1.10 或 nas.example.com" />
            </a-form-item>
            <a-form-item label="端口">
              <FaNumberField v-model="targetForm.port" class="w-full" :min="1" :max="65535" />
            </a-form-item>
            <a-form-item label="账号">
              <FaInput v-model="targetForm.username" placeholder="可留空" />
            </a-form-item>
            <a-form-item label="密码">
              <FaInput v-model="targetForm.password" type="password" :placeholder="editingTarget?.passwordSet ? '留空保持原密码' : '访问密码'" />
            </a-form-item>
            <a-form-item label="基础路径">
              <FaInput v-model="targetForm.basePath" placeholder="/dav/backup，须以 / 开头" />
            </a-form-item>
          </div>
          <a-form-item v-if="targetForm.type !== 'FTP'" label="跳过 TLS 证书校验">
            <FaSwitch v-model="targetForm.insecureTls" />
            <div class="mt-1 text-xs text-secondary-foreground/60">
              自签名证书或用 IP 直连 HTTPS/FTPS 时开启；存在中间人风险，仅建议可信内网使用。
            </div>
          </a-form-item>
          <a-form-item v-if="targetForm.type !== 'WEBDAV'" label="FTP 被动模式">
            <FaSwitch v-model="targetForm.passiveMode" />
            <div class="mt-1 text-xs text-secondary-foreground/60">
              多数防火墙/NAT 环境需保持开启。
            </div>
          </a-form-item>
          <FaAlert
            icon="i-ri:key-2-line"
            title="凭据与安全"
            description="密码经主密钥加密存储；WebDAV 建议使用 HTTPS（非 80 端口默认按 HTTPS 访问）。归档将写入基础路径下，文件名前缀 yudream-backup-。"
          />
        </a-form>
      </FaModal>

      <FaModal
        v-model="archivesVisible"
        :title="`远端归档 · ${archivesTarget?.name || ''}`"
        class="sm:max-w-3xl"
        :show-cancel-button="false"
        confirm-button-text="关闭"
      >
        <div class="mb-3 flex flex-wrap items-center gap-3 text-sm">
          <span class="shrink-0 text-secondary-foreground/60">恢复策略</span>
          <FaRadioGroup v-model="restoreStrategy" :options="strategyOptions" />
        </div>
        <FaProgress v-if="archivesLoading" :model-value="100" class="mb-2" />
        <div v-if="archives.length === 0 && !archivesLoading" class="py-6 text-center text-sm text-secondary-foreground/60">
          远端暂无备份归档
        </div>
        <div v-else class="flex flex-col gap-2">
          <div
            v-for="archive in archives"
            :key="archive.name"
            class="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3"
          >
            <div class="min-w-0">
              <div class="break-all text-sm font-medium">
                {{ archive.name }}
              </div>
              <div class="text-xs text-secondary-foreground/60">
                {{ formatSize(archive.size) }} · {{ formatArchiveTime(archive.modifiedAtMillis) }}
              </div>
            </div>
            <FaButton v-auth="'system:backup:run'" variant="outline" size="sm" @click="confirmRestore(archive)">
              恢复
            </FaButton>
          </div>
        </div>
      </FaModal>

      <FaModal
        v-model="planFormVisible"
        :title="editingPlan ? '编辑备份计划' : '添加备份计划'"
        show-cancel-button
        class="sm:max-w-2xl"
        :confirm-loading="planFormSaving"
        @confirm="savePlanForm"
      >
        <a-form :model="planForm" layout="vertical">
          <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <a-form-item label="计划编码">
              <FaInput v-model="planForm.code" :disabled="!!editingPlan" placeholder="小写字母、数字或连字符，如 daily-3am" />
            </a-form-item>
            <a-form-item label="名称">
              <FaInput v-model="planForm.name" placeholder="计划显示名称" />
            </a-form-item>
            <a-form-item label="cron 表达式">
              <FaInput v-model="planForm.cron" placeholder="0 0 3 * * *（每天 3 点）" />
            </a-form-item>
            <a-form-item label="异地目标">
              <FaSelect v-model="planForm.targetCode" :options="targetOptions" placeholder="选择目标" />
            </a-form-item>
            <a-form-item label="保留份数">
              <FaNumberField v-model="planForm.retentionCount" class="w-full" :min="1" :max="100" />
            </a-form-item>
          </div>
          <a-form-item label="备份范围">
            <FaCheckboxGroup v-model="planScopeModel" :options="scopeCheckOptions" />
          </a-form-item>
          <FaAlert
            icon="i-ri:information-line"
            title="调度说明"
            description="到点后按所选范围生成全量归档并推送到异地目标，自动清理超出保留份数的旧备份（按前缀匹配）。"
          />
        </a-form>
      </FaModal>
    </FaPageMain>
  </div>
</template>
