import type {
  ActivityProofExportRecord,
  ActivityProofMapping,
  ActivityProofParticipant,
  ActivityProofServer,
  ActivityProofSettings,
  ActivityProofStatus,
  ExportForm,
  TimeValue,
} from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createActivityProofApi } from '../api/activity-proof-api'

export function useActivityProof(sdk: YuDreamPluginSdk) {
  const api = createActivityProofApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const saving = ref(false)
  const uploading = ref(false)
  const exporting = ref(false)
  const status = ref<ActivityProofStatus | null>(null)
  const settings = ref<ActivityProofSettings | null>(null)
  const servers = ref<ActivityProofServer[]>([])
  const participants = ref<ActivityProofParticipant[]>([])
  const mappings = ref<ActivityProofMapping[]>([])
  const exports = ref<ActivityProofExportRecord[]>([])
  const selectedServerId = ref('')
  const selectedPlayerIds = ref<string[]>([])
  const mappingInputs = reactive<Record<string, string>>({})

  const settingsForm = reactive({
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

  const ready = computed(() => !!status.value?.dependencies.minecraftReady && !!status.value?.dependencies.studentInfoReady && !!status.value?.settings.templateReady)
  const selectedServer = computed(() => servers.value.find(item => item.id === selectedServerId.value) || null)
  const unmatchedCount = computed(() => participants.value.filter(item => !item.matched).length)
  const selectedCount = computed(() => selectedPlayerIds.value.length || participants.value.length)

  async function load() {
    loading.value = true
    try {
      const [nextStatus, nextServers, nextExports] = await Promise.all([api.status(), api.servers(), api.exports()])
      status.value = nextStatus
      settings.value = nextStatus.settings
      servers.value = nextServers
      exports.value = nextExports
      syncSettingsForm(nextStatus.settings)
      syncExportDefaults(nextStatus.settings)
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

  async function reloadServerData() {
    if (!selectedServerId.value) {
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
      settings.value = await api.saveSettings({ ...settingsForm })
      if (status.value) {
        status.value.settings = settings.value
      }
      syncExportDefaults(settings.value)
      toast.success('默认信息已保存')
    }
    finally {
      saving.value = false
    }
  }

  async function uploadTemplate(file?: File) {
    if (!file) {
      return
    }
    if (!file.name.toLowerCase().endsWith('.docx')) {
      toast.warning('请选择 .docx 模板文件')
      return
    }
    uploading.value = true
    try {
      const contentBase64 = await fileToBase64(file)
      settings.value = await api.uploadTemplate({
        filename: file.name,
        contentType: file.type || 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        contentBase64,
      })
      if (status.value) {
        status.value.settings = settings.value
      }
      toast.success('模板已上传')
    }
    finally {
      uploading.value = false
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
    if (!selectedServerId.value) {
      toast.warning('请选择服务器')
      return
    }
    if (!settings.value?.templateReady) {
      toast.warning('请先上传 Word 模板')
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
      openDownload(record)
    }
    finally {
      exporting.value = false
    }
  }

  function openDownload(record: ActivityProofExportRecord) {
    if (typeof window !== 'undefined') {
      window.open(api.downloadUrl(record.downloadPath), '_blank')
    }
  }

  function syncSettingsForm(nextSettings: ActivityProofSettings) {
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

  return reactive({
    loading,
    saving,
    uploading,
    exporting,
    status,
    settings,
    servers,
    participants,
    mappings,
    exports,
    selectedServerId,
    selectedPlayerIds,
    mappingInputs,
    settingsForm,
    exportForm,
    ready,
    selectedServer,
    unmatchedCount,
    selectedCount,
    load,
    reloadServerData,
    saveSettings,
    uploadTemplate,
    bindStudent,
    deleteMapping,
    togglePlayer,
    selectAll,
    clearSelection,
    exportWord,
    openDownload,
    formatTime,
    minutes,
  })
}

function fileToBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

function todayText() {
  const date = new Date()
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
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
