import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EconomyRecord, InheritanceRule, MinecraftEndpoint, MinecraftServer, MinecraftStatusSnapshot, PlayerActivity, SeasonForm, SeasonOperation, ServerForm, TimeValue } from '../types'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createMinecraftApi } from '../api/minecraft-api'

const MANAGE_PERMISSION = 'plugin:minecraft-server:manage'
const STATUS_HISTORY_WINDOW = 24 * 60 * 60 * 1000
const STATUS_HISTORY_LIMIT = 144

export function useMinecraftServerPlugin(sdk: YuDreamPluginSdk) {
  const api = createMinecraftApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const saving = ref(false)
  const walletEnabled = ref(true)
  const servers = ref<MinecraftServer[]>([])
  const selectedId = ref('')
  const previewOperation = ref<SeasonOperation | null>(null)
  const operations = ref<SeasonOperation[]>([])
  const records = ref<EconomyRecord[]>([])
  const statusHistory = ref<MinecraftStatusSnapshot[]>([])
  const playerActivities = ref<PlayerActivity[]>([])
  const recordsPager = reactive({ page: 1, size: 20, hasNext: false })
  const operationsPager = reactive({ page: 1, size: 10, hasNext: false })
  const playerActivitiesPager = reactive({ page: 1, size: 20, hasNext: false })

  const serverForm = reactive<ServerForm>({
    id: '',
    name: '',
    descriptionMarkdown: '# 服务器介绍\n\n在这里写服务器规则、玩法和入服说明。',
    enabled: true,
    sort: 0,
    endpoints: [blankEndpoint()],
    seasons: [],
  })

  const seasonForm = reactive<SeasonForm>({
    name: '',
    description: '',
    startedAtText: datetimeLocal(Date.now()),
    remark: '',
    rules: defaultRules(),
  })

  const canManage = computed(() => sdk.account.permissions.includes('*') || sdk.account.permissions.includes(MANAGE_PERMISSION))
  const selectedServer = computed(() => servers.value.find(server => server.id === selectedId.value) || servers.value[0])
  const onlineCount = computed(() => selectedServer.value?.status?.onlinePlayers ?? 0)
  const maxCount = computed(() => selectedServer.value?.status?.maxPlayers ?? 0)
  const latestOperation = computed(() => operations.value.find(item => item.status === 'APPLIED'))

  async function load(includeDisabled = canManage.value) {
    loading.value = true
    try {
      await loadEconomyStatus()
      servers.value = await api.list(includeDisabled)
      if (!selectedId.value || !servers.value.some(server => server.id === selectedId.value)) {
        selectedId.value = servers.value[0]?.id || ''
        statusHistory.value = []
        recordsPager.page = 1
        operationsPager.page = 1
        playerActivitiesPager.page = 1
      }
      if (selectedId.value) {
        await loadSideData(selectedId.value)
      }
    }
    finally {
      loading.value = false
    }
  }

  async function loadEconomyStatus() {
    try {
      const status = await api.economyStatus()
      walletEnabled.value = status.walletEnabled !== false
    }
    catch {
      walletEnabled.value = false
    }
    if (!walletEnabled.value) {
      clearWalletData()
    }
  }

  function clearWalletData() {
    records.value = []
    operations.value = []
    previewOperation.value = null
    recordsPager.page = 1
    operationsPager.page = 1
    recordsPager.hasNext = false
    operationsPager.hasNext = false
  }

  async function selectServer(id: string) {
    selectedId.value = id
    previewOperation.value = null
    recordsPager.page = 1
    operationsPager.page = 1
    playerActivitiesPager.page = 1
    await loadSideData(id)
  }

  async function loadSideData(id: string) {
    const detail = await api.detail(id)
    replaceServer(detail)
    await loadStatusHistory(id)
    if (walletEnabled.value) {
      await loadRecords(id)
    }
    else {
      clearWalletData()
    }
    if (canManage.value) {
      await Promise.all([
        walletEnabled.value ? loadOperations(id) : Promise.resolve(),
        loadPlayerActivities(id),
      ])
    }
    else {
      playerActivities.value = []
      playerActivitiesPager.hasNext = false
    }
  }

  async function loadRecords(serverId = selectedId.value) {
    if (!serverId || !walletEnabled.value) {
      records.value = []
      recordsPager.hasNext = false
      return
    }
    const items = await api.myRecords(serverId, recordsPager.page, recordsPager.size)
    recordsPager.hasNext = items.length >= recordsPager.size
    records.value = items
  }

  async function loadOperations(serverId = selectedId.value) {
    if (!serverId || !walletEnabled.value) {
      operations.value = []
      operationsPager.hasNext = false
      return
    }
    const items = await api.operations(serverId, operationsPager.page, operationsPager.size)
    operationsPager.hasNext = items.length >= operationsPager.size
    operations.value = items
  }

  async function loadStatusHistory(serverId = selectedId.value) {
    if (!serverId) {
      statusHistory.value = []
      return
    }
    statusHistory.value = await api.statusHistory(serverId, Date.now() - STATUS_HISTORY_WINDOW, STATUS_HISTORY_LIMIT)
  }

  async function loadPlayerActivities(serverId = selectedId.value) {
    if (!serverId || !canManage.value) {
      playerActivities.value = []
      playerActivitiesPager.hasNext = false
      return
    }
    const items = await api.playerActivities(serverId, playerActivitiesPager.page, playerActivitiesPager.size)
    playerActivitiesPager.hasNext = items.length >= playerActivitiesPager.size
    playerActivities.value = items
  }

  function newServer() {
    Object.assign(serverForm, {
      id: '',
      name: '',
      descriptionMarkdown: '# 服务器介绍\n\n在这里写服务器规则、玩法和入服说明。',
      enabled: true,
      sort: servers.value.length * 10,
      endpoints: [blankEndpoint()],
      seasons: [],
    })
  }

  function editServer(server: MinecraftServer) {
    serverForm.id = server.id
    serverForm.name = server.name
    serverForm.descriptionMarkdown = server.descriptionMarkdown || ''
    serverForm.enabled = server.enabled
    serverForm.sort = server.sort
    serverForm.endpoints = server.endpoints.map(endpoint => ({
      ...endpoint,
      port: endpoint.port && Number(endpoint.port) > 0 ? endpoint.port : undefined,
    }))
    serverForm.seasons = server.seasons.map(season => ({ ...season }))
  }

  function addEndpoint() {
    serverForm.endpoints.push(blankEndpoint(serverForm.endpoints.length * 10))
  }

  function removeEndpoint(index: number) {
    if (serverForm.endpoints.length <= 1) {
      toast.warning('至少保留一条线路')
      return
    }
    serverForm.endpoints.splice(index, 1)
  }

  async function saveServer() {
    if (!serverForm.name.trim()) {
      toast.warning('请填写服务器名称')
      return
    }
    if (serverForm.endpoints.some(endpoint => !endpoint.host.trim())) {
      toast.warning('请填写每条线路的 IP 或域名')
      return
    }
    saving.value = true
    try {
      const saved = await api.save({
        ...serverForm,
        endpoints: serverForm.endpoints.map((endpoint, index) => ({
          ...endpoint,
          sort: index * 10,
          port: endpointPortPayload(endpoint),
        })),
      })
      replaceServer(saved)
      selectedId.value = saved.id
      editServer(saved)
      toast.success('服务器已保存')
    }
    finally {
      saving.value = false
    }
  }

  async function refreshStatus(server?: MinecraftServer) {
    const target = server || selectedServer.value
    if (!target) {
      return
    }
    saving.value = true
    try {
      const refreshed = await api.detail(target.id, true)
      replaceServer(refreshed)
      if (target.id === selectedId.value) {
        await loadStatusHistory(target.id)
      }
      toast.success('状态已刷新')
    }
    finally {
      saving.value = false
    }
  }

  async function previewSeason() {
    if (!walletEnabled.value) {
      toast.warning('钱包插件未启用，周目货币继承已关闭')
      return
    }
    const target = selectedServer.value
    if (!target) {
      toast.warning('请先选择服务器')
      return
    }
    saving.value = true
    try {
      previewOperation.value = await api.previewSeason(target.id, seasonPayload())
      toast.success('继承预览已生成')
    }
    finally {
      saving.value = false
    }
  }

  async function openSeason() {
    if (!walletEnabled.value) {
      toast.warning('钱包插件未启用，周目货币继承已关闭')
      return
    }
    const target = selectedServer.value
    if (!target) {
      return
    }
    saving.value = true
    try {
      previewOperation.value = await api.openSeason(target.id, seasonPayload())
      toast.success('新周目已开启，余额重置流水已记录')
      recordsPager.page = 1
      operationsPager.page = 1
      playerActivitiesPager.page = 1
      await loadSideData(target.id)
    }
    finally {
      saving.value = false
    }
  }

  async function rollbackOperation(operation: SeasonOperation) {
    if (!walletEnabled.value) {
      toast.warning('钱包插件未启用，周目货币继承已关闭')
      return
    }
    saving.value = true
    try {
      previewOperation.value = await api.rollbackOperation(operation.id)
      toast.success('周目操作已撤回，反向流水已记录')
      if (selectedId.value) {
        recordsPager.page = 1
        operationsPager.page = 1
        playerActivitiesPager.page = 1
        await loadSideData(selectedId.value)
      }
    }
    finally {
      saving.value = false
    }
  }

  function addRule() {
    seasonForm.rules.push({ assetPattern: '*', minAmount: '', maxAmount: '', inheritRate: '0.5' })
  }

  function removeRule(index: number) {
    seasonForm.rules.splice(index, 1)
  }

  function resetRules() {
    seasonForm.rules.splice(0, seasonForm.rules.length, ...defaultRules())
  }

  function formatAmount(value?: number | string) {
    const number = Number(value ?? 0)
    if (!Number.isFinite(number)) {
      return String(value ?? '0')
    }
    return number.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
  }

  function formatTime(value?: TimeValue) {
    const timestamp = normalizeTime(value)
    if (!timestamp) {
      return '-'
    }
    return new Date(timestamp).toLocaleString('zh-CN', { hour12: false })
  }

  function statusText(status?: string) {
    if (status === 'ONLINE') {
      return '在线'
    }
    if (status === 'OFFLINE') {
      return '离线'
    }
    if (status === 'APPLIED') {
      return '已应用'
    }
    if (status === 'ROLLED_BACK') {
      return '已撤回'
    }
    if (status === 'PREVIEW') {
      return '预览'
    }
    return status || '-'
  }

  function directionText(direction?: string) {
    if (direction === 'CREDIT') {
      return '补入'
    }
    if (direction === 'DEBIT') {
      return '扣除'
    }
    return '不变'
  }

  function endpointAddress(endpoint: MinecraftEndpoint) {
    const port = endpointPortPayload(endpoint)
    return port ? `${endpoint.host}:${port}` : `${endpoint.host}（自动/SRV）`
  }

  async function copyServerId(id?: string) {
    const value = id || selectedServer.value?.id || serverForm.id
    if (!value) {
      toast.warning('当前服务器还没有 ID')
      return
    }
    try {
      await navigator.clipboard.writeText(value)
      toast.success('服务器 ID 已复制')
    }
    catch {
      toast.warning(`服务器 ID：${value}`)
    }
  }

  async function deleteServer(server?: MinecraftServer) {
    const target = server || selectedServer.value
    if (!target) {
      return
    }
    saving.value = true
    try {
      await api.remove(target.id)
      servers.value = servers.value.filter(item => item.id !== target.id)
      if (selectedId.value === target.id) {
        const next = servers.value[0]
        selectedId.value = next?.id || ''
        clearSideData()
        if (next) {
          editServer(next)
          await loadSideData(next.id)
        }
        else {
          newServer()
        }
      }
      else if (serverForm.id === target.id) {
        newServer()
      }
      toast.success('服务器已删除')
    }
    finally {
      saving.value = false
    }
  }

  async function nextRecordsPage() {
    if (!walletEnabled.value || !recordsPager.hasNext) {
      return
    }
    recordsPager.page += 1
    await loadRecords()
  }

  async function prevRecordsPage() {
    if (!walletEnabled.value || recordsPager.page <= 1) {
      return
    }
    recordsPager.page -= 1
    await loadRecords()
  }

  async function nextOperationsPage() {
    if (!walletEnabled.value || !operationsPager.hasNext) {
      return
    }
    operationsPager.page += 1
    await loadOperations()
  }

  async function prevOperationsPage() {
    if (!walletEnabled.value || operationsPager.page <= 1) {
      return
    }
    operationsPager.page -= 1
    await loadOperations()
  }

  async function nextPlayerActivitiesPage() {
    if (!playerActivitiesPager.hasNext) {
      return
    }
    playerActivitiesPager.page += 1
    await loadPlayerActivities()
  }

  async function prevPlayerActivitiesPage() {
    if (playerActivitiesPager.page <= 1) {
      return
    }
    playerActivitiesPager.page -= 1
    await loadPlayerActivities()
  }

  function formatDuration(value?: number) {
    const totalSeconds = Math.max(Math.floor(Number(value || 0) / 1000), 0)
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    if (hours > 0) {
      return `${hours}h ${minutes}m`
    }
    if (minutes > 0) {
      return `${minutes}m ${seconds}s`
    }
    return `${seconds}s`
  }

  function seasonPayload() {
    return {
      name: seasonForm.name.trim(),
      description: seasonForm.description.trim() || undefined,
      startedAt: new Date(seasonForm.startedAtText).getTime(),
      remark: seasonForm.remark.trim() || undefined,
      rules: seasonForm.rules.map(rule => ({
        assetPattern: String(rule.assetPattern || '*').trim().toUpperCase(),
        minAmount: rule.minAmount === '' ? '0' : rule.minAmount,
        maxAmount: rule.maxAmount === '' ? undefined : rule.maxAmount,
        inheritRate: rule.inheritRate,
      })),
    }
  }

  function replaceServer(server: MinecraftServer) {
    const index = servers.value.findIndex(item => item.id === server.id)
    if (index >= 0) {
      servers.value[index] = server
    }
    else {
      servers.value.push(server)
    }
  }

  function clearSideData() {
    statusHistory.value = []
    playerActivities.value = []
    playerActivitiesPager.page = 1
    playerActivitiesPager.hasNext = false
    clearWalletData()
  }

  async function uploadMarkdownImage(file: File) {
    if (!file.type.startsWith('image/')) {
      throw new Error('请选择图片文件')
    }
    if (!sdk.files?.uploadImage) {
      throw new Error('当前宿主未提供文件上传能力')
    }
    const uploaded = await sdk.files.uploadImage(file, {
      module: 'minecraft-server',
      publicAccess: true,
    })
    const url = uploaded.assetUrl || sdk.files.assetUrl(uploaded.url)
    if (!url) {
      throw new Error('图片上传后未返回访问地址')
    }
    return {
      url,
      alt: uploaded.originalName || file.name,
      title: uploaded.originalName || file.name,
    }
  }

  return reactive({
    loading,
    saving,
    walletEnabled,
    servers,
    selectedId,
    selectedServer,
    previewOperation,
    operations,
    records,
    statusHistory,
    playerActivities,
    recordsPager,
    operationsPager,
    playerActivitiesPager,
    serverForm,
    seasonForm,
    canManage,
    onlineCount,
    maxCount,
    latestOperation,
    load,
    loadEconomyStatus,
    loadRecords,
    loadOperations,
    loadStatusHistory,
    loadPlayerActivities,
    selectServer,
    newServer,
    editServer,
    addEndpoint,
    removeEndpoint,
    saveServer,
    deleteServer,
    refreshStatus,
    copyServerId,
    previewSeason,
    openSeason,
    rollbackOperation,
    addRule,
    removeRule,
    resetRules,
    formatAmount,
    formatTime,
    statusText,
    directionText,
    endpointAddress,
    uploadMarkdownImage,
    formatDuration,
    nextRecordsPage,
    prevRecordsPage,
    nextOperationsPage,
    prevOperationsPage,
    nextPlayerActivitiesPage,
    prevPlayerActivitiesPage,
  })
}

function endpointPortPayload(endpoint: MinecraftEndpoint) {
  const port = Number(endpoint.port)
  if (!Number.isFinite(port) || port <= 0) {
    return undefined
  }
  return port
}

function normalizeTime(value: TimeValue) {
  if (value == null || value === '') {
    return 0
  }
  if (value instanceof Date) {
    return validTimestamp(value.getTime())
  }
  if (Array.isArray(value)) {
    return normalizeDateArray(value)
  }
  if (typeof value === 'number') {
    return validTimestamp(value)
  }
  const text = value.trim()
  if (!text) {
    return 0
  }
  const numeric = Number(text)
  if (Number.isFinite(numeric)) {
    return validTimestamp(numeric)
  }
  return validTimestamp(Date.parse(text))
}

function normalizeDateArray(value: number[]) {
  if (value.length < 3) {
    return 0
  }
  const [year, month, day, hour = 0, minute = 0, second = 0, nano = 0] = value
  return validTimestamp(new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000)).getTime())
}

function validTimestamp(value: number) {
  if (!Number.isFinite(value) || value <= 0) {
    return 0
  }
  return value < 10000000000 ? value * 1000 : value
}

function blankEndpoint(sort = 0): MinecraftEndpoint {
  return {
    name: sort === 0 ? '主线' : '备用线路',
    host: '',
    port: undefined,
    edition: 'JAVA',
    primaryLine: sort === 0,
    enabled: true,
    sort,
  }
}

function defaultRules(): InheritanceRule[] {
  return [
    { assetPattern: '*', minAmount: '0', maxAmount: '100', inheritRate: '0.5' },
    { assetPattern: '*', minAmount: '100', maxAmount: '200', inheritRate: '0.6' },
    { assetPattern: '*', minAmount: '200', maxAmount: '350', inheritRate: '0.75' },
    { assetPattern: '*', minAmount: '350', maxAmount: '500', inheritRate: '0.85' },
    { assetPattern: '*', minAmount: '500', maxAmount: '', inheritRate: '0.9' },
  ]
}

function datetimeLocal(value: number) {
  const date = new Date(value)
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0, 16)
}

export type MinecraftServerPluginModel = ReturnType<typeof useMinecraftServerPlugin>
