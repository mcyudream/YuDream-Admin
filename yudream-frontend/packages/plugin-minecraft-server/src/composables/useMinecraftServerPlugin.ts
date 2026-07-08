import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EconomyRecord, InheritanceRule, MinecraftEndpoint, MinecraftServer, SeasonForm, SeasonOperation, ServerForm } from '../types'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createMinecraftApi } from '../api/minecraft-api'

const MANAGE_PERMISSION = 'plugin:minecraft-server:manage'

export function useMinecraftServerPlugin(sdk: YuDreamPluginSdk) {
  const api = createMinecraftApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const saving = ref(false)
  const servers = ref<MinecraftServer[]>([])
  const selectedId = ref('')
  const previewOperation = ref<SeasonOperation | null>(null)
  const operations = ref<SeasonOperation[]>([])
  const records = ref<EconomyRecord[]>([])

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
      servers.value = await api.list(includeDisabled)
      if (!selectedId.value || !servers.value.some(server => server.id === selectedId.value)) {
        selectedId.value = servers.value[0]?.id || ''
      }
      if (selectedId.value) {
        await loadSideData(selectedId.value)
      }
    }
    finally {
      loading.value = false
    }
  }

  async function selectServer(id: string) {
    selectedId.value = id
    previewOperation.value = null
    await loadSideData(id)
  }

  async function loadSideData(id: string) {
    const detail = await api.detail(id)
    replaceServer(detail)
    records.value = await api.myRecords(id)
    if (canManage.value) {
      operations.value = await api.operations(id)
    }
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
      toast.success('状态已刷新')
    }
    finally {
      saving.value = false
    }
  }

  async function previewSeason() {
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
    const target = selectedServer.value
    if (!target) {
      return
    }
    saving.value = true
    try {
      previewOperation.value = await api.openSeason(target.id, seasonPayload())
      toast.success('新周目已开启，余额增扣流水已记录')
      await loadSideData(target.id)
    }
    finally {
      saving.value = false
    }
  }

  async function rollbackOperation(operation: SeasonOperation) {
    saving.value = true
    try {
      previewOperation.value = await api.rollbackOperation(operation.id)
      toast.success('周目操作已撤回，反向流水已记录')
      if (selectedId.value) {
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

  function formatTime(value?: number) {
    if (!value) {
      return '-'
    }
    return new Date(value).toLocaleString('zh-CN', { hour12: false })
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
      return '入账'
    }
    if (direction === 'DEBIT') {
      return '扣账'
    }
    return '不变'
  }

  function endpointAddress(endpoint: MinecraftEndpoint) {
    const port = endpointPortPayload(endpoint)
    return port ? `${endpoint.host}:${port}` : `${endpoint.host}（自动/SRV）`
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

  return reactive({
    loading,
    saving,
    servers,
    selectedId,
    selectedServer,
    previewOperation,
    operations,
    records,
    serverForm,
    seasonForm,
    canManage,
    onlineCount,
    maxCount,
    latestOperation,
    load,
    selectServer,
    newServer,
    editServer,
    addEndpoint,
    removeEndpoint,
    saveServer,
    refreshStatus,
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
  })
}

function endpointPortPayload(endpoint: MinecraftEndpoint) {
  const port = Number(endpoint.port)
  if (!Number.isFinite(port) || port <= 0) {
    return undefined
  }
  return port
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
