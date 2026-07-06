import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createSkinApi } from '../api/skin-api'
import type { MigrationLogEntry, MigrationReport, MigrationStatus, SkinClosetItem, SkinMe, SkinPlayer, SkinSettings, SkinSummary, SkinTexture, SkinUser } from '../types'

export function useSkinPlugin(sdk: YuDreamPluginSdk) {
  const api = createSkinApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const saving = ref('')

  const me = ref<SkinMe | null>(null)
  const summary = ref<SkinSummary>({ users: 0, players: 0, textures: 0, closetItems: 0, options: 0 })
  const settings = ref<SkinSettings>({ maxPlayersPerUser: 3, allowPublicUpload: true, siteNotice: '' })
  const users = ref<SkinUser[]>([])
  const players = ref<SkinPlayer[]>([])
  const textures = ref<SkinTexture[]>([])
  const closetItems = ref<SkinClosetItem[]>([])
  const migrationReport = ref<MigrationReport | null>(null)
  const migrationStatus = ref<MigrationStatus>({ state: 'IDLE', running: false, logs: [] })
  const migrationLogs = ref<MigrationLogEntry[]>([])
  const migrationLogVisible = ref(false)
  const migrationStream = ref<AbortController | null>(null)
  const scope = ref<'user' | 'admin'>('user')
  let loadVersion = 0

  const playerForm = reactive({ name: '', ownerId: '' })
  const textureForm = reactive({
    name: '',
    type: 'steve',
    model: 'default',
    contentType: 'image/png',
    base64: '',
    publicAccess: true,
  })
  const closetForm = reactive({ userId: '', textureHash: '', itemName: '' })
  const settingsForm = reactive<SkinSettings>({ maxPlayersPerUser: 3, allowPublicUpload: true, siteNotice: '' })
  const migrationForm = reactive({
    host: 'localhost',
    port: 3306,
    database: 'blessing_skin',
    username: '',
    password: '',
    textureBaseDir: '',
    textureArchiveBase64: '',
    textureArchiveName: '',
  })

  const selectedPlayerName = ref('')
  const selectedTextureHash = ref('')
  const selectedClosetId = ref('')
  const assignForm = reactive({ skinHash: '', capeHash: '' })
  const playerRenameForm = reactive({ name: '' })
  const closetRenameForm = reactive({ itemName: '' })
  const canManage = computed(() => {
    return !!me.value?.manage || sdk.account.permissions.includes('*') || sdk.account.permissions.includes('plugin:yudream-skin:manage')
  })
  const canUse = computed(() => {
    return canManage.value || sdk.account.permissions.includes('*') || sdk.account.permissions.includes('plugin:yudream-skin:user')
  })
  const currentUserId = computed(() => me.value?.userId || sdk.account.userId || '')
  const accountName = computed(() => me.value?.hostUser?.nickname || me.value?.hostUser?.username || sdk.account.username || currentUserId.value)
  const defaultPlayerName = computed(() => me.value?.defaultPlayerName || '')

  const textureOptions = computed(() => textures.value.map(item => ({
    label: `${item.name} (${item.hash.slice(0, 10)})`,
    value: item.hash,
  })))

  const closetTextureOptions = computed(() => closetItems.value
    .map((item) => {
      const texture = textures.value.find(texture => texture.hash === item.textureHash)
      return {
        item,
        texture,
        label: `${item.itemName || texture?.name || item.textureHash} (${item.textureHash.slice(0, 10)})`,
        value: item.textureHash,
      }
    }))

  const skinTextureOptions = computed(() => closetTextureOptions.value
    .filter(item => item.texture?.type !== 'cape')
    .map(({ label, value }) => ({ label, value })))

  const capeTextureOptions = computed(() => closetTextureOptions.value
    .filter(item => item.texture?.type === 'cape')
    .map(({ label, value }) => ({ label, value })))

  const userOptions = computed(() => {
    const options = users.value.map(item => ({
      label: `${item.nickname || item.email} (${item.id})`,
      value: item.id,
    }))
    if (options.length || !currentUserId.value) {
      return options
    }
    return [{
      label: `${me.value?.hostUser?.nickname || sdk.account.username || '当前用户'} (${currentUserId.value})`,
      value: currentUserId.value,
    }]
  })

  const selectedPlayer = computed(() => players.value.find(item => item.name === selectedPlayerName.value))
  const selectedTexture = computed(() => textures.value.find(item => item.hash === selectedTextureHash.value))
  const selectedClosetItem = computed(() => closetItems.value.find(item => item.id === selectedClosetId.value))
  const selectedClosetTexture = computed(() => textures.value.find(item => item.hash === selectedClosetItem.value?.textureHash))
  const selectedPlayerSkin = computed(() => textureUrl(selectedPlayer.value?.skinHash))
  const selectedPlayerCape = computed(() => textureUrl(selectedPlayer.value?.capeHash))
  const selectedPlayerSlim = computed(() => textures.value.find(item => item.hash === selectedPlayer.value?.skinHash)?.model === 'slim')
  const selectedTextureSkin = computed(() => selectedTexture.value?.type === 'cape' ? '' : textureUrl(selectedTexture.value?.hash))
  const selectedTextureCape = computed(() => selectedTexture.value?.type === 'cape' ? textureUrl(selectedTexture.value?.hash) : '')
  const selectedTextureSlim = computed(() => selectedTexture.value?.model === 'slim')
  const selectedClosetSkin = computed(() => selectedClosetTexture.value?.type === 'cape' ? '' : textureUrl(selectedClosetTexture.value?.hash))
  const selectedClosetCape = computed(() => selectedClosetTexture.value?.type === 'cape' ? textureUrl(selectedClosetTexture.value?.hash) : '')
  const selectedClosetSlim = computed(() => selectedClosetTexture.value?.model === 'slim')

  async function load(options: {
    includeMigration?: boolean
    includePlayers?: boolean
    includeTextures?: boolean
    includeCloset?: boolean
    scope?: 'user' | 'admin'
  } = {}) {
    const nextScope = options.scope || scope.value
    const includePlayers = options.includePlayers ?? true
    const includeTextures = options.includeTextures ?? true
    const includeCloset = options.includeCloset ?? true
    const currentLoadVersion = ++loadVersion
    const scopeChanged = scope.value !== nextScope
    if (scopeChanged || !includePlayers) {
      players.value = []
      clearSelectedPlayer()
    }
    if (scopeChanged || !includeTextures) {
      textures.value = []
      selectedTextureHash.value = ''
    }
    if (scopeChanged || !includeCloset) {
      closetItems.value = []
      clearSelectedCloset()
    }
    scope.value = nextScope
    loading.value = true
    try {
      const [status, current, textureList, currentMigration] = await Promise.all([
        api.status(),
        api.me(),
        includeTextures
          ? (nextScope === 'admin' ? api.adminTextures() : api.textures())
          : Promise.resolve(textures.value),
        options.includeMigration ? api.migrationStatus().catch(() => migrationStatus.value) : Promise.resolve(migrationStatus.value),
      ])
      me.value = current
      const [playerList, closetList] = await Promise.all([
        includePlayers
          ? (nextScope === 'admin' ? api.adminPlayers() : api.players())
          : Promise.resolve(players.value),
        includeCloset
          ? (nextScope === 'admin' ? api.adminCloset() : api.closet())
          : Promise.resolve(closetItems.value),
      ])
      const [userList, savedSettings] = nextScope === 'admin' && canManage.value
        ? [hostUserOption(current), await api.settings()] as const
          : [hostUserOption(current), status.settings || settings.value] as const
      if (currentLoadVersion !== loadVersion || scope.value !== nextScope) {
        return
      }
      summary.value = status
      users.value = userList
      players.value = playerList
      textures.value = textureList
      closetItems.value = closetList
      settings.value = savedSettings || status.settings || settings.value
      Object.assign(settingsForm, settings.value)
      applyMigrationStatus(currentMigration)
      if (!playerForm.ownerId && currentUserId.value) {
        playerForm.ownerId = currentUserId.value
      }
      if (!closetForm.userId && currentUserId.value) {
        closetForm.userId = currentUserId.value
      }
      if (includePlayers) {
        const defaultPlayer = current.defaultPlayerName
          ? playerList.find(player => player.name === current.defaultPlayerName)
          : undefined
        const currentSelectedPlayer = selectedPlayerName.value
          ? playerList.find(player => player.name === selectedPlayerName.value)
          : undefined
        if (defaultPlayer && (!selectedPlayerName.value || !currentSelectedPlayer)) {
          selectPlayer(defaultPlayer)
        }
        else if (!currentSelectedPlayer && playerList.length) {
          selectPlayer(defaultPlayer || playerList.find(player => !!player.skinHash || !!player.capeHash) || playerList[0])
        }
        else if (!currentSelectedPlayer) {
          clearSelectedPlayer()
        }
      }
      if (includeTextures) {
        const currentSelectedTexture = selectedTextureHash.value
          ? textureList.find(texture => texture.hash === selectedTextureHash.value)
          : undefined
        if (!currentSelectedTexture && textureList.length) {
          selectTexture(textureList[0])
        }
        else if (!currentSelectedTexture) {
          selectedTextureHash.value = ''
        }
      }
      if (includeCloset) {
        const currentSelectedCloset = selectedClosetId.value
          ? closetList.find(item => item.id === selectedClosetId.value)
          : undefined
        if (!currentSelectedCloset && closetList.length) {
          selectClosetItem(closetList[0])
        }
        else if (!currentSelectedCloset) {
          clearSelectedCloset()
        }
      }
    }
    finally {
      if (currentLoadVersion === loadVersion) {
        loading.value = false
      }
    }
  }

  async function createPlayer() {
    if (!playerForm.name) {
      toast.error('请填写角色名')
      return
    }
    saving.value = 'player'
    try {
      const player = scope.value === 'admin' ? await api.createAdminPlayer({ ...playerForm }) : await api.createPlayer({ ...playerForm })
      Object.assign(playerForm, { name: '', ownerId: '' })
      selectPlayer(player)
      toast.success('角色已创建')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function createAdminPlayer(data: Record<string, unknown>) {
    saving.value = 'admin-player'
    try {
      const player = await api.createAdminPlayer(data)
      toast.success('角色已创建')
      await load()
      return player
    }
    finally {
      saving.value = ''
    }
  }

  function selectPlayer(player: SkinPlayer) {
    selectedPlayerName.value = player.name
    assignForm.skinHash = player.skinHash || ''
    assignForm.capeHash = player.capeHash || ''
    playerRenameForm.name = player.name
  }

  function clearSelectedPlayer() {
    selectedPlayerName.value = ''
    assignForm.skinHash = ''
    assignForm.capeHash = ''
    playerRenameForm.name = ''
  }

  function selectTexture(texture: SkinTexture) {
    selectedTextureHash.value = texture.hash
    if (!closetForm.textureHash) {
      closetForm.textureHash = texture.hash
    }
  }

  function selectClosetItem(item: SkinClosetItem) {
    selectedClosetId.value = item.id
    selectedTextureHash.value = item.textureHash
    closetRenameForm.itemName = item.itemName || textureName(item.textureHash)
  }

  function clearSelectedCloset() {
    selectedClosetId.value = ''
    closetRenameForm.itemName = ''
  }

  async function renamePlayer() {
    if (!selectedPlayerName.value || !playerRenameForm.name) {
      toast.error('请选择角色并填写新名称')
      return
    }
    saving.value = 'player-rename'
    try {
      const player = scope.value === 'admin'
        ? await api.renameAdminPlayer(selectedPlayerName.value, { ...playerRenameForm })
        : await api.renamePlayer(selectedPlayerName.value, { ...playerRenameForm })
      toast.success('角色名称已更新')
      await load()
      selectPlayer(player)
    }
    finally {
      saving.value = ''
    }
  }

  async function renameAdminPlayer(name: string, data: Record<string, unknown>) {
    saving.value = `admin-player:${name}`
    try {
      const player = await api.renameAdminPlayer(name, data)
      toast.success('角色名称已更新')
      await load()
      return player
    }
    finally {
      saving.value = ''
    }
  }

  async function deletePlayer() {
    if (!selectedPlayerName.value) {
      toast.error('请先选择角色')
      return
    }
    saving.value = 'player-delete'
    try {
      if (scope.value === 'admin') {
        await api.deleteAdminPlayer(selectedPlayerName.value)
      }
      else {
        await api.deletePlayer(selectedPlayerName.value)
      }
      selectedPlayerName.value = ''
      toast.success('角色已删除')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function deleteAdminPlayer(name: string) {
    saving.value = `admin-player:${name}`
    try {
      await api.deleteAdminPlayer(name)
      if (selectedPlayerName.value === name) {
        selectedPlayerName.value = ''
      }
      toast.success('角色已删除')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function assignTextures() {
    if (!selectedPlayerName.value) {
      toast.error('请先选择角色')
      return
    }
    saving.value = 'assign'
    try {
      if (scope.value === 'admin') {
        await api.assignAdminTextures(selectedPlayerName.value, { ...assignForm })
      }
      else {
        await api.assignTextures(selectedPlayerName.value, { ...assignForm })
      }
      toast.success('材质绑定已保存')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function assignAdminTextures(name: string, data: Record<string, unknown>) {
    saving.value = `admin-assign:${name}`
    try {
      const player = await api.assignAdminTextures(name, data)
      toast.success('材质绑定已保存')
      await load()
      return player
    }
    finally {
      saving.value = ''
    }
  }

  async function setDefaultPlayer(player = selectedPlayer.value) {
    if (!player) {
      toast.error('请先选择角色')
      return
    }
    saving.value = `default-player:${player.name}`
    try {
      const saved = await api.setDefaultPlayer({ name: player.name })
      if (me.value) {
        me.value.defaultPlayerName = saved.name
      }
      selectPlayer(saved)
      toast.success('默认角色已切换')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function handleTextureFile(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) {
      return
    }
    textureForm.contentType = file.type || 'image/png'
    textureForm.base64 = await fileToBase64(file)
  }

  async function uploadTexture() {
    if (!textureForm.base64) {
      toast.error('请选择 PNG 文件')
      return
    }
    saving.value = 'texture'
    try {
      const texture = scope.value === 'admin' ? await api.uploadAdminTexture({ ...textureForm }) : await api.uploadTexture({ ...textureForm })
      Object.assign(textureForm, {
        name: '',
        type: 'steve',
        model: 'default',
        contentType: 'image/png',
        base64: '',
        publicAccess: true,
      })
      toast.success('材质已上传')
      await load()
      selectTexture(texture)
    }
    finally {
      saving.value = ''
    }
  }

  async function uploadAdminTexture(data: Record<string, unknown>) {
    saving.value = 'admin-texture'
    try {
      const texture = await api.uploadAdminTexture(data)
      toast.success('材质已上传')
      await load()
      return texture
    }
    finally {
      saving.value = ''
    }
  }

  async function deleteAdminTexture(hash: string) {
    saving.value = `admin-texture:${hash}`
    try {
      await api.deleteAdminTexture(hash)
      if (selectedTextureHash.value === hash) {
        selectedTextureHash.value = ''
      }
      toast.success('材质已删除')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function saveClosetItem() {
    if (!closetForm.textureHash) {
      toast.error('请选择要加入衣柜的材质')
      return
    }
    saving.value = 'closet'
    try {
      const item = scope.value === 'admin' ? await api.saveAdminClosetItem({ ...closetForm }) : await api.saveClosetItem({ ...closetForm })
      Object.assign(closetForm, { userId: '', textureHash: '', itemName: '' })
      toast.success('衣柜项已保存')
      await load()
      selectClosetItem(item)
    }
    finally {
      saving.value = ''
    }
  }

  async function addTextureToCloset(texture: SkinTexture) {
    closetForm.textureHash = texture.hash
    closetForm.itemName = texture.name
    await saveClosetItem()
  }

  async function useTextureOnSelectedPlayer(texture = selectedTexture.value) {
    if (!texture) {
      toast.error('请先选择材质')
      return
    }
    if (!selectedPlayerName.value) {
      toast.error('请先选择角色')
      return
    }
    saving.value = `use-texture:${texture.hash}`
    try {
      const saveItem = scope.value === 'admin' ? api.saveAdminClosetItem : api.saveClosetItem
      await saveItem({
        userId: currentUserId.value,
        textureHash: texture.hash,
        itemName: texture.name,
      })
      if (texture.type === 'cape') {
        assignForm.capeHash = texture.hash
      }
      else {
        assignForm.skinHash = texture.hash
      }
      if (scope.value === 'admin') {
        await api.assignAdminTextures(selectedPlayerName.value, { ...assignForm })
      }
      else {
        await api.assignTextures(selectedPlayerName.value, { ...assignForm })
      }
      toast.success('已保存到衣柜并应用到当前角色')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function useClosetItemOnSelectedPlayer(item = selectedClosetItem.value) {
    if (!item) {
      toast.error('请先选择衣柜项')
      return
    }
    const texture = textures.value.find(texture => texture.hash === item.textureHash)
    if (!texture) {
      toast.error('材质信息不存在')
      return
    }
    await useTextureOnSelectedPlayer(texture)
  }

  async function renameClosetItem(item = selectedClosetItem.value) {
    if (!item || !closetRenameForm.itemName) {
      toast.error('请选择衣柜项并填写名称')
      return
    }
    saving.value = `closet-rename:${item.id}`
    try {
      const saved = scope.value === 'admin'
        ? await api.renameAdminClosetItem(item.id, { ...closetRenameForm })
        : await api.renameClosetItem(item.id, { ...closetRenameForm })
      toast.success('衣柜显示名称已更新')
      await load()
      selectClosetItem(saved)
    }
    finally {
      saving.value = ''
    }
  }

  async function deleteClosetItem(item: SkinClosetItem) {
    saving.value = `closet:${item.id}`
    try {
      if (scope.value === 'admin') {
        await api.deleteAdminClosetItem(item.id)
      }
      else {
        await api.deleteClosetItem(item.id)
      }
      if (selectedClosetId.value === item.id) {
        selectedClosetId.value = ''
      }
      toast.success('衣柜项已删除')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function saveSettings() {
    saving.value = 'settings'
    try {
      settings.value = await api.saveSettings({ ...settingsForm })
      Object.assign(settingsForm, settings.value)
      toast.success('皮肤站配置已保存')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function runMigration() {
    if (!migrationForm.host || !migrationForm.username) {
      toast.error('请填写数据库主机和账号')
      return
    }
    saving.value = 'migration'
    try {
      applyMigrationStatus(await api.migrate({ ...migrationForm }))
      toast.success('迁移已开始')
      openMigrationLog()
      connectMigrationEvents()
    }
    finally {
      saving.value = ''
    }
  }

  async function handleMigrationArchive(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) {
      return
    }
    if (!file.name.toLowerCase().endsWith('.zip')) {
      toast.error('请选择 zip 压缩包')
      input.value = ''
      return
    }
    migrationForm.textureArchiveBase64 = await fileToBase64(file)
    migrationForm.textureArchiveName = file.name
    toast.success('材质压缩包已选择')
  }

  function openMigrationLog() {
    migrationLogVisible.value = true
    if (migrationStatus.value.running) {
      connectMigrationEvents()
    }
  }

  function connectMigrationEvents() {
    migrationStream.value?.abort()
    const controller = new AbortController()
    migrationStream.value = controller
    void readMigrationEvents(controller)
  }

  async function readMigrationEvents(controller: AbortController) {
    try {
      const headers: Record<string, string> = {
        Accept: 'text/event-stream',
        'Accept-Language': 'zh-CN',
      }
      const token = localStorage.getItem('token')
      if (token) {
        headers.Authorization = token
      }
      const response = await fetch(api.migrationEventsUrl(), {
        headers,
        signal: controller.signal,
      })
      if (!response.ok || !response.body) {
        throw new Error(`迁移日志连接失败：${response.status}`)
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }
        buffer += decoder.decode(value, { stream: true })
        const chunks = buffer.split(/\r?\n\r?\n/)
        buffer = chunks.pop() || ''
        chunks.forEach(handleMigrationEventChunk)
      }
      if (buffer.trim()) {
        handleMigrationEventChunk(buffer)
      }
    }
    catch (error) {
      if (!controller.signal.aborted) {
        toast.error(error instanceof Error ? error.message : '迁移日志连接失败')
      }
    }
    finally {
      if (migrationStream.value === controller) {
        migrationStream.value = null
      }
    }
  }

  function handleMigrationEventChunk(chunk: string) {
    let eventName = 'message'
    const dataLines: string[] = []
    chunk.split(/\r?\n/).forEach((line) => {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      }
      else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    })
    if (!dataLines.length) {
      return
    }
    const payload = JSON.parse(dataLines.join('\n'))
    if (eventName === 'migration.status') {
      applyMigrationStatus(payload)
    }
    else if (eventName === 'migration.log') {
      appendMigrationLog(payload)
    }
  }

  function applyMigrationStatus(status?: MigrationStatus) {
    if (!status) {
      return
    }
    migrationStatus.value = status
    migrationReport.value = status.report || null
    migrationLogs.value = status.logs || []
  }

  function appendMigrationLog(log: MigrationLogEntry) {
    const exists = migrationLogs.value.some(item => item.time === log.time && item.message === log.message)
    if (!exists) {
      migrationLogs.value.push(log)
    }
  }

  function hostUserOption(current: SkinMe): SkinUser[] {
    if (!current.hostUser) {
      return []
    }
    return [{
      id: String(current.hostUser.id),
      email: current.hostUser.email || '',
      nickname: current.hostUser.nickname || current.hostUser.username || String(current.hostUser.id),
    }]
  }

  function textureUrl(hash?: string) {
    return api.textureUrl(hash)
  }

  function textureName(hash?: string) {
    if (!hash) {
      return '-'
    }
    return textures.value.find(item => item.hash === hash)?.name || hash
  }

  function userName(userId?: string) {
    if (!userId) {
      return '-'
    }
    if (userId === currentUserId.value) {
      return accountName.value
    }
    const user = users.value.find(item => item.id === userId)
    return user ? `${user.nickname || user.email}` : userId
  }

  function dateText(value?: number | string) {
    if (value === undefined || value === null || value === '') {
      return '-'
    }
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString()
  }

  async function fileToBase64(file: File) {
    const buffer = await file.arrayBuffer()
    let binary = ''
    const bytes = new Uint8Array(buffer)
    bytes.forEach((byte) => {
      binary += String.fromCharCode(byte)
    })
    return btoa(binary)
  }

  return reactive({
    loading,
    saving,
    me,
    summary,
    settings,
    users,
    players,
    textures,
    closetItems,
    migrationReport,
    migrationStatus,
    migrationLogs,
    migrationLogVisible,
    scope,
    playerForm,
    textureForm,
    closetForm,
    settingsForm,
    migrationForm,
    selectedPlayerName,
    selectedTextureHash,
    selectedClosetId,
    assignForm,
    playerRenameForm,
    closetRenameForm,
    canManage,
    canUse,
    currentUserId,
    accountName,
    defaultPlayerName,
    selectedPlayer,
    selectedTexture,
    selectedClosetItem,
    selectedClosetTexture,
    selectedPlayerSkin,
    selectedPlayerCape,
    selectedPlayerSlim,
    selectedTextureSkin,
    selectedTextureCape,
    selectedTextureSlim,
    selectedClosetSkin,
    selectedClosetCape,
    selectedClosetSlim,
    textureOptions,
    skinTextureOptions,
    capeTextureOptions,
    closetTextureOptions,
    userOptions,
    load,
    createPlayer,
    createAdminPlayer,
    selectPlayer,
    selectTexture,
    selectClosetItem,
    renamePlayer,
    renameAdminPlayer,
    deletePlayer,
    deleteAdminPlayer,
    assignTextures,
    assignAdminTextures,
    setDefaultPlayer,
    handleTextureFile,
    uploadTexture,
    uploadAdminTexture,
    deleteAdminTexture,
    saveClosetItem,
    addTextureToCloset,
    useTextureOnSelectedPlayer,
    useClosetItemOnSelectedPlayer,
    renameClosetItem,
    deleteClosetItem,
    saveSettings,
    runMigration,
    handleMigrationArchive,
    openMigrationLog,
    textureUrl,
    textureName,
    userName,
    dateText,
  })
}

export type SkinPluginModel = ReturnType<typeof useSkinPlugin>
