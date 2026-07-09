import type {
  ActivityProofExportRecord,
  ActivityProofMapping,
  ActivityProofParticipant,
  ActivityProofServer,
  ActivityProofSettings,
  ActivityProofStatus,
  ActivityProofTemplate,
  ExportForm,
  TimeValue,
} from '../types'
import type { YuDreamPluginBlobResponse, YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createActivityProofApi } from '../api/activity-proof-api'

export function useActivityProof(sdk: YuDreamPluginSdk) {
  const api = createActivityProofApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const saving = ref(false)
  const exporting = ref(false)
  const status = ref<ActivityProofStatus | null>(null)
  const settings = ref<ActivityProofSettings | null>(null)
  const templates = ref<ActivityProofTemplate[]>([])
  const servers = ref<ActivityProofServer[]>([])
  const participants = ref<ActivityProofParticipant[]>([])
  const mappings = ref<ActivityProofMapping[]>([])
  const exports = ref<ActivityProofExportRecord[]>([])
  const myExports = ref<ActivityProofExportRecord[]>([])
  const selectedServerId = ref('')
  const selectedPlayerIds = ref<string[]>([])
  const mappingInputs = reactive<Record<string, string>>({})

  const settingsForm = reactive({
    templateId: '',
    defaultActivityName: '',
    defaultCollege: '',
    defaultIssuer: '',
  })

  const exportForm = reactive<ExportForm>({
    activityName: '',
    activityDate: '',
    proofNo: '',
    college: '',
    issuer: '',
    issueDate: todayText(),
    minOnlineMinutes: 0,
    includeAfk: false,
  })

  const ready = computed(() => !!status.value?.dependencies.minecraftReady && !!status.value?.dependencies.studentInfoReady && !!status.value?.dependencies.wordTemplateReady && !!status.value?.settings.templateReady)
  const selectedServer = computed(() => servers.value.find(item => item.id === selectedServerId.value) || null)
  const selectedTemplate = computed(() => templates.value.find(item => item.id === settingsForm.templateId) || null)
  const unmatchedCount = computed(() => participants.value.filter(item => !item.matched).length)
  const selectedCount = computed(() => selectedPlayerIds.value.length || participants.value.length)
  const canManage = computed(() => sdk.account.permissions.includes('*') || sdk.account.permissions.includes('plugin:yudream-student-info:manage'))

  async function loadPage(page: 'export' | 'records' | 'mine') {
    if (page === 'records') {
      await loadRecords()
      return
    }
    if (page === 'mine') {
      await loadMine()
      return
    }
    await load()
  }

  async function load() {
    loading.value = true
    try {
      const nextStatus = await api.status()
      status.value = nextStatus
      settings.value = nextStatus.settings
      syncSettingsForm(nextStatus.settings)
      syncExportDefaults(nextStatus.settings)
      if (nextStatus.dependencies.wordTemplateReady) {
        templates.value = await api.templates()
      }
      else {
        templates.value = []
      }
      if (!nextStatus.dependencies.minecraftReady) {
        servers.value = []
        selectedServerId.value = ''
        participants.value = []
        mappings.value = []
        selectedPlayerIds.value = []
        return
      }
      const nextServers = await api.servers()
      servers.value = nextServers
      if (!selectedServerId.value && nextServers.length) {
        selectedServerId.value = nextServers[0].id
      }
      if (selectedServerId.value) {
        await reloadServerData()
      }
    }
    finally {
      loading.value = false
    }
  }

  async function loadRecords() {
    loading.value = true
    try {
      exports.value = await api.exports()
    }
    finally {
      loading.value = false
    }
  }

  async function loadMine() {
    loading.value = true
    try {
      myExports.value = await api.myExports()
    }
    finally {
      loading.value = false
    }
  }

  async function reloadServerData() {
    if (!status.value?.dependencies.minecraftReady || !selectedServerId.value) {
      participants.value = []
      mappings.value = []
      return
    }
    const [nextParticipants, nextMappings] = await Promise.all([
      api.participants(selectedServerId.value, exportForm.minOnlineMinutes, exportForm.includeAfk),
      api.mappings(selectedServerId.value),
    ])
    participants.value = nextParticipants
    mappings.value = nextMappings
    nextParticipants.forEach((item) => {
      mappingInputs[item.playerId] = mappingInputs[item.playerId] || item.studentNo || ''
    })
    selectedPlayerIds.value = selectedPlayerIds.value.filter(id => nextParticipants.some(item => item.playerId === id))
  }

  async function saveSettings() {
    saving.value = true
    try {
      const templateId = toTemplateId(settingsForm.templateId)
      settings.value = await api.saveSettings({
        defaultActivityName: settingsForm.defaultActivityName,
        defaultCollege: settingsForm.defaultCollege,
        defaultIssuer: settingsForm.defaultIssuer,
        templateId,
      })
      if (status.value) {
        status.value.settings = settings.value
      }
      syncSettingsForm(settings.value)
      syncExportDefaults(settings.value)
      toast.success('默认信息已保存')
    }
    finally {
      saving.value = false
    }
  }

  async function reloadTemplates() {
    if (!status.value?.dependencies.wordTemplateReady) {
      templates.value = []
      return
    }
    templates.value = await api.templates()
    toast.success('模板列表已刷新')
  }

  async function selectTemplate() {
    const templateId = toTemplateId(settingsForm.templateId)
    if (!templateId) {
      return
    }
    saving.value = true
    try {
      settings.value = await api.selectTemplate(templateId)
      if (status.value) {
        status.value.settings = settings.value
      }
      syncSettingsForm(settings.value)
      toast.success('模板已选择')
    }
    finally {
      saving.value = false
    }
  }

  async function bindStudent(row: ActivityProofParticipant) {
    const studentNo = mappingInputs[row.playerId] || ''
    if (!studentNo.trim()) {
      toast.warning('请填写学号')
      return
    }
    saving.value = true
    try {
      await api.saveMapping({
        serverId: row.serverId,
        playerId: row.playerId,
        playerName: row.playerName,
        studentNo: studentNo.trim(),
      })
      toast.success('映射已保存')
      await reloadServerData()
    }
    finally {
      saving.value = false
    }
  }

  async function deleteMapping(row: ActivityProofMapping) {
    saving.value = true
    try {
      await api.deleteMapping(row.id)
      toast.success('映射已删除')
      await reloadServerData()
    }
    finally {
      saving.value = false
    }
  }

  function togglePlayer(row: ActivityProofParticipant) {
    const index = selectedPlayerIds.value.indexOf(row.playerId)
    if (index >= 0) {
      selectedPlayerIds.value.splice(index, 1)
      return
    }
    selectedPlayerIds.value.push(row.playerId)
  }

  function selectAll() {
    selectedPlayerIds.value = participants.value.map(item => item.playerId)
  }

  function clearSelection() {
    selectedPlayerIds.value = []
  }

  async function exportWord() {
    if (!status.value?.dependencies.minecraftReady) {
      toast.warning('请先启用 Minecraft 服务器插件')
      return
    }
    if (!status.value?.dependencies.studentInfoReady) {
      toast.warning('请先启用学生信息插件')
      return
    }
    if (!status.value?.dependencies.wordTemplateReady) {
      toast.warning('请先在能力管理中启用 Word 模板能力')
      return
    }
    if (!selectedServerId.value) {
      toast.warning('请选择服务器')
      return
    }
    if (!settings.value?.templateReady) {
      toast.warning('请选择 Word 模板')
      return
    }
    exporting.value = true
    try {
      const record = await api.exportWord({
        ...exportForm,
        serverId: selectedServerId.value,
        selectedPlayerIds: selectedPlayerIds.value,
      })
      exports.value = [record, ...exports.value.filter(item => item.id !== record.id)]
      toast.success('活动证明已生成')
      await openDownload(record)
    }
    finally {
      exporting.value = false
    }
  }

  async function openDownload(record: ActivityProofExportRecord) {
    await downloadFile(record.downloadPath, record.outputFilename || 'activity-proof.docx')
  }

  async function openStampedPdf(record: ActivityProofExportRecord) {
    await downloadFile(record.stampedPdfDownloadPath, record.stampedPdfFilename || 'activity-proof.pdf')
  }

  async function downloadFile(path: string, fallbackName: string) {
    if (!path) {
      toast.warning('暂无可下载文件')
      return
    }
    saving.value = true
    try {
      saveBlobResponse(await api.download(path), fallbackName)
    }
    catch (error) {
      toast.warning(errorMessage(error))
    }
    finally {
      saving.value = false
    }
  }

  async function uploadStampedPdf(record: ActivityProofExportRecord, event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file) {
      return
    }
    if (file.type && file.type !== 'application/pdf') {
      toast.warning('请上传 PDF 文件')
      return
    }
    saving.value = true
    try {
      const nextRecord = await api.uploadStampedPdf(record.id, {
        filename: file.name,
        contentType: file.type || 'application/pdf',
        base64: await fileToBase64(file),
      })
      exports.value = exports.value.map(item => item.id === nextRecord.id ? nextRecord : item)
      toast.success('盖章 PDF 已上传')
    }
    finally {
      saving.value = false
    }
  }

  async function deleteExportRecord(record: ActivityProofExportRecord) {
    if (typeof window !== 'undefined' && !window.confirm(`确定删除「${record.outputFilename}」吗？`)) {
      return
    }
    saving.value = true
    try {
      await api.deleteExport(record.id)
      exports.value = exports.value.filter(item => item.id !== record.id)
      toast.success('导出记录已删除')
    }
    finally {
      saving.value = false
    }
  }

  function syncSettingsForm(nextSettings: ActivityProofSettings) {
    settingsForm.templateId = nextSettings.templateId || ''
    settingsForm.defaultActivityName = nextSettings.defaultActivityName || ''
    settingsForm.defaultCollege = nextSettings.defaultCollege || ''
    settingsForm.defaultIssuer = nextSettings.defaultIssuer || ''
  }

  function syncExportDefaults(nextSettings: ActivityProofSettings) {
    exportForm.activityName = exportForm.activityName || nextSettings.defaultActivityName || ''
    exportForm.college = exportForm.college || nextSettings.defaultCollege || ''
    exportForm.issuer = exportForm.issuer || nextSettings.defaultIssuer || ''
  }

  function formatTime(value: TimeValue) {
    const timestamp = normalizeTime(value)
    return timestamp ? new Date(timestamp).toLocaleString('zh-CN', { hour12: false }) : '-'
  }

  function minutes(value: number) {
    return `${Math.floor(value / 60000)} 分钟`
  }

  function formatFileSize(value: number) {
    if (!value) {
      return '-'
    }
    if (value < 1024 * 1024) {
      return `${Math.max(1, Math.round(value / 1024))} KB`
    }
    return `${(value / 1024 / 1024).toFixed(1)} MB`
  }

  return reactive({
    loading,
    saving,
    exporting,
    status,
    settings,
    templates,
    servers,
    participants,
    mappings,
    exports,
    myExports,
    selectedServerId,
    selectedPlayerIds,
    mappingInputs,
    settingsForm,
    exportForm,
    ready,
    canManage,
    selectedServer,
    selectedTemplate,
    unmatchedCount,
    selectedCount,
    loadPage,
    load,
    loadRecords,
    loadMine,
    reloadServerData,
    reloadTemplates,
    selectTemplate,
    saveSettings,
    bindStudent,
    deleteMapping,
    togglePlayer,
    selectAll,
    clearSelection,
    exportWord,
    openDownload,
    openStampedPdf,
    uploadStampedPdf,
    deleteExportRecord,
    formatTime,
    formatFileSize,
    minutes,
  })
}

function fileToBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const value = String(reader.result || '')
      const commaIndex = value.indexOf(',')
      resolve(commaIndex >= 0 ? value.slice(commaIndex + 1) : value)
    }
    reader.onerror = () => reject(reader.error || new Error('文件读取失败'))
    reader.readAsDataURL(file)
  })
}

function saveBlobResponse(response: YuDreamPluginBlobResponse, fallbackName: string) {
  if (typeof document === 'undefined') {
    return
  }
  const filename = resolveFilename(header(response.headers, 'content-disposition'), fallbackName)
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function header(headers: Record<string, string>, name: string) {
  return Object.entries(headers || {}).find(([key]) => key.toLowerCase() === name)?.[1]
}

function resolveFilename(disposition: string | undefined, fallbackName: string) {
  if (!disposition) {
    return fallbackName
  }
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }
  const match = disposition.match(/filename="?([^";]+)"?/i)
  return match?.[1] ? decodeURIComponent(match[1]) : fallbackName
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '文件下载失败'
}

function todayText() {
  const date = new Date()
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

function toTemplateId(value: string) {
  if (!value.trim()) {
    return null
  }
  return value.trim()
}

function normalizeTime(value: TimeValue) {
  if (value == null || value === '') {
    return 0
  }
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0, nano = 0] = value
    return validTimestamp(new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000)).getTime())
  }
  if (typeof value === 'number') {
    return validTimestamp(value)
  }
  const numeric = Number(value)
  if (Number.isFinite(numeric)) {
    return validTimestamp(numeric)
  }
  return validTimestamp(Date.parse(value))
}

function validTimestamp(value: number) {
  if (!Number.isFinite(value) || value <= 0) {
    return 0
  }
  return value < 10000000000 ? value * 1000 : value
}

export type ActivityProofModel = ReturnType<typeof useActivityProof>
