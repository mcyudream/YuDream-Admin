import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createSkinApi } from '../api/skin-api'
import type { MigrationReport, SkinClosetItem, SkinMe, SkinPlayer, SkinSettings, SkinSummary, SkinTexture, SkinUser } from '../types'

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

  const userForm = reactive({ email: '', nickname: '', password: '' })
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
    driverClass: '',
    jdbcUrl: '',
    username: '',
    password: '',
    textureBaseDir: '',
  })

  const selectedPlayerName = ref('')
  const selectedTextureHash = ref('')
  const selectedClosetId = ref('')
  const assignForm = reactive({ skinHash: '', capeHash: '' })
  const playerRenameForm = reactive({ name: '' })
  const closetRenameForm = reactive({ itemName: '' })
  const canManage = computed(() => {
    return !!me.value?.manage || sdk.account.permissions.includes('*') || sdk.account.permissions.includes('plugin:blessing-skin:manage')
  })
  const canUse = computed(() => {
    return canManage.value || sdk.account.permissions.includes('*') || sdk.account.permissions.includes('plugin:blessing-skin:user')
  })
  const currentUserId = computed(() => me.value?.userId || sdk.account.userId || '')
  const accountName = computed(() => me.value?.hostUser?.nickname || me.value?.hostUser?.username || sdk.account.username || currentUserId.value)

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

  async function load() {
    loading.value = true
    try {
      const [status, current, playerList, textureList, closetList] = await Promise.all([
        api.status(),
        api.me(),
        api.players(),
        api.textures(),
        api.closet(),
      ])
      me.value = current
      const [userList, savedSettings] = canManage.value
        ? await Promise.all([api.users(), api.settings()])
        : [current.skinUser ? [current.skinUser] : [], status.settings || settings.value] as const
      summary.value = status
      users.value = userList
      players.value = playerList
      textures.value = textureList
      closetItems.value = closetList
      settings.value = savedSettings || status.settings || settings.value
      Object.assign(settingsForm, settings.value)
      if (!playerForm.ownerId && currentUserId.value) {
        playerForm.ownerId = currentUserId.value
      }
      if (!closetForm.userId && currentUserId.value) {
        closetForm.userId = currentUserId.value
      }
      if (!selectedPlayerName.value && players.value.length) {
        selectPlayer(players.value[0])
      }
      if (!selectedTextureHash.value && textures.value.length) {
        selectTexture(textures.value[0])
      }
      if (!selectedClosetId.value && closetItems.value.length) {
        selectClosetItem(closetItems.value[0])
      }
    }
    finally {
      loading.value = false
    }
  }

  async function createUser() {
    if (!userForm.email || !userForm.password) {
      toast.error('请填写邮箱和密码')
      return
    }
    saving.value = 'user'
    try {
      await api.createUser({ ...userForm })
      Object.assign(userForm, { email: '', nickname: '', password: '' })
      toast.success('皮肤站用户已创建')
      await load()
    }
    finally {
      saving.value = ''
    }
  }

  async function createPlayer() {
    if (!playerForm.name) {
      toast.error('请填写角色名')
      return
    }
    saving.value = 'player'
    try {
      const player = await api.createPlayer({ ...playerForm })
      Object.assign(playerForm, { name: '', ownerId: '' })
      selectPlayer(player)
      toast.success('角色已创建')
      await load()
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

  async function renamePlayer() {
    if (!selectedPlayerName.value || !playerRenameForm.name) {
      toast.error('请选择角色并填写新名称')
      return
    }
    saving.value = 'player-rename'
    try {
      const player = await api.renamePlayer(selectedPlayerName.value, { ...playerRenameForm })
      toast.success('角色名称已更新')
      await load()
      selectPlayer(player)
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
      await api.deletePlayer(selectedPlayerName.value)
      selectedPlayerName.value = ''
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
      await api.assignTextures(selectedPlayerName.value, { ...assignForm })
      toast.success('材质绑定已保存')
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
      const texture = await api.uploadTexture({ ...textureForm })
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

  async function saveClosetItem() {
    if (!closetForm.textureHash) {
      toast.error('请选择要加入衣柜的材质')
      return
    }
    saving.value = 'closet'
    try {
      const item = await api.saveClosetItem({ ...closetForm })
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
      await api.saveClosetItem({
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
      await api.assignTextures(selectedPlayerName.value, { ...assignForm })
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
      const saved = await api.renameClosetItem(item.id, { ...closetRenameForm })
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
      await api.deleteClosetItem(item.id)
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
    if (!migrationForm.jdbcUrl) {
      toast.error('请填写 JDBC 地址')
      return
    }
    saving.value = 'migration'
    try {
      migrationReport.value = await api.migrate({ ...migrationForm })
      toast.success('迁移完成')
      await load()
    }
    finally {
      saving.value = ''
    }
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
    userForm,
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
    createUser,
    createPlayer,
    selectPlayer,
    selectTexture,
    selectClosetItem,
    renamePlayer,
    deletePlayer,
    assignTextures,
    handleTextureFile,
    uploadTexture,
    saveClosetItem,
    addTextureToCloset,
    useTextureOnSelectedPlayer,
    useClosetItemOnSelectedPlayer,
    renameClosetItem,
    deleteClosetItem,
    saveSettings,
    runMigration,
    textureUrl,
    textureName,
    userName,
    dateText,
  })
}

export type SkinPluginModel = ReturnType<typeof useSkinPlugin>
