<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@/plugins/sdk'

type SkinPage = 'dashboard' | 'players' | 'textures' | 'closet' | 'system'

interface SkinSettings {
  maxPlayersPerUser: number
  allowPublicUpload: boolean
  siteNotice: string
}

interface SkinSummary {
  users: number
  players: number
  textures: number
  closetItems: number
  options: number
  settings?: SkinSettings
}

interface SkinUser {
  id: string
  email: string
  nickname: string
  migratedUid?: number
  createdAt?: number
}

interface SkinPlayer {
  uuid: string
  ownerId: string
  name: string
  skinHash?: string
  capeHash?: string
  lastModified?: number
}

interface SkinTexture {
  hash: string
  name: string
  type: string
  model: string
  size?: number
  publicAccess?: boolean
}

interface SkinClosetItem {
  id: string
  userId: string
  textureHash: string
  itemName?: string
  createdAt?: number
}

interface MigrationReport {
  users: number
  players: number
  textures: number
  closetItems: number
  options: number
  warnings: string[]
}

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

const toast = useFaToast()
const loading = ref(false)
const saving = ref('')

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
  uploaderId: '',
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
const assignForm = reactive({ skinHash: '', capeHash: '' })

const currentPage = computed<SkinPage>(() => {
  const component = String((props.route?.meta?.plugin as any)?.component || '').toLowerCase()
  if (component.includes('players')) {
    return 'players'
  }
  if (component.includes('textures')) {
    return 'textures'
  }
  if (component.includes('closet')) {
    return 'closet'
  }
  if (component.includes('system')) {
    return 'system'
  }
  return 'dashboard'
})

const statCards = computed(() => [
  { label: '用户', value: summary.value.users, icon: 'i-ri:user-3-line' },
  { label: '角色', value: summary.value.players, icon: 'i-ri:gamepad-line' },
  { label: '皮肤材质', value: summary.value.textures, icon: 'i-ri:t-shirt-2-line' },
  { label: '衣柜项', value: summary.value.closetItems, icon: 'i-ri:archive-drawer-line' },
])

const textureOptions = computed(() => textures.value.map(item => ({
  label: `${item.name} (${item.hash.slice(0, 10)})`,
  value: item.hash,
})))

const userOptions = computed(() => users.value.map(item => ({
  label: `${item.nickname || item.email} (${item.id})`,
  value: item.id,
})))

onMounted(load)
watch(currentPage, () => load())

async function load() {
  loading.value = true
  try {
    const [status, userList, playerList, textureList, closetList, savedSettings] = await Promise.all([
      props.sdk.http.get<SkinSummary>('/status'),
      props.sdk.http.get<SkinUser[]>('/users?page=1&size=100'),
      props.sdk.http.get<SkinPlayer[]>('/players?page=1&size=100'),
      props.sdk.http.get<SkinTexture[]>('/textures?page=1&size=100'),
      props.sdk.http.get<SkinClosetItem[]>('/closet?page=1&size=200'),
      props.sdk.http.get<SkinSettings>('/settings'),
    ])
    summary.value = status
    users.value = userList
    players.value = playerList
    textures.value = textureList
    closetItems.value = closetList
    settings.value = savedSettings || status.settings || settings.value
    Object.assign(settingsForm, settings.value)
    if (!selectedPlayerName.value && players.value.length) {
      selectPlayer(players.value[0])
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
    await props.sdk.http.post('/users', { ...userForm })
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
    await props.sdk.http.post('/players', { ...playerForm })
    Object.assign(playerForm, { name: '', ownerId: '' })
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
}

async function assignTextures() {
  if (!selectedPlayerName.value) {
    toast.error('请先选择角色')
    return
  }
  saving.value = 'assign'
  try {
    await props.sdk.http.request(`/players/${encodeURIComponent(selectedPlayerName.value)}/textures`, {
      method: 'PUT',
      data: { ...assignForm },
    })
    toast.success('角色材质已更新')
    await load()
  }
  finally {
    saving.value = ''
  }
}

async function onTextureFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) {
    return
  }
  textureForm.contentType = file.type || 'image/png'
  textureForm.name ||= file.name.replace(/\.[^.]+$/, '')
  textureForm.base64 = await fileToBase64(file)
}

async function uploadTexture() {
  if (!textureForm.name || !textureForm.base64) {
    toast.error('请填写材质名称并选择 PNG 文件')
    return
  }
  saving.value = 'texture'
  try {
    await props.sdk.http.post('/textures', { ...textureForm })
    Object.assign(textureForm, {
      name: '',
      type: 'steve',
      model: 'default',
      contentType: 'image/png',
      base64: '',
      publicAccess: true,
      uploaderId: '',
    })
    toast.success('材质已上传')
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
    await props.sdk.http.post('/closet', { ...closetForm })
    Object.assign(closetForm, { userId: '', textureHash: '', itemName: '' })
    toast.success('衣柜项已保存')
    await load()
  }
  finally {
    saving.value = ''
  }
}

async function deleteClosetItem(item: SkinClosetItem) {
  saving.value = `closet:${item.id}`
  try {
    await props.sdk.http.request(`/closet/${encodeURIComponent(item.id)}`, { method: 'DELETE' })
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
    settings.value = await props.sdk.http.request<SkinSettings>('/settings', {
      method: 'PUT',
      data: { ...settingsForm },
    })
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
    migrationReport.value = await props.sdk.http.post<MigrationReport>('/migration/blessing-skin', { ...migrationForm })
    toast.success('迁移任务已完成')
    await load()
  }
  finally {
    saving.value = ''
  }
}

function textureUrl(hash?: string) {
  return hash ? props.sdk.http.url(`/textures/${hash}`) : ''
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

function dateText(value?: number) {
  return value ? new Date(value).toLocaleString() : '-'
}

function fileToBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || '').split(',')[1] || '')
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}
</script>

<template>
  <div class="skin-plugin">
    <section v-if="currentPage === 'dashboard'" class="dashboard-page">
      <div class="summary-grid">
        <div v-for="item in statCards" :key="item.label" class="summary-card">
          <span class="summary-icon"><FaIcon :name="item.icon" /></span>
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
        <FaButton :loading="loading" class="refresh-button" @click="load">
          <FaIcon name="i-ri:refresh-line" />
          刷新
        </FaButton>
      </div>

      <div class="workbench two-col">
        <div class="panel">
          <div class="panel-head">
            <strong>皮肤站概览</strong>
            <FaTag variant="secondary">Blessing Skin</FaTag>
          </div>
          <div class="metric-list">
            <div>
              <span>最多角色数</span>
              <strong>{{ settings.maxPlayersPerUser <= 0 ? '不限' : `${settings.maxPlayersPerUser} 个 / 用户` }}</strong>
            </div>
            <div>
              <span>公开上传</span>
              <strong>{{ settings.allowPublicUpload ? '允许' : '关闭' }}</strong>
            </div>
            <div>
              <span>公告</span>
              <strong>{{ settings.siteNotice || '未设置' }}</strong>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <strong>最近角色</strong>
            <FaTag variant="secondary">{{ players.length }}</FaTag>
          </div>
          <div class="item-list compact">
            <div v-for="player in players.slice(0, 6)" :key="player.uuid" class="list-row static">
              <span>
                <strong>{{ player.name }}</strong>
                <small>{{ player.skinHash ? '已绑定皮肤' : '未绑定皮肤' }}</small>
              </span>
              <small>{{ dateText(player.lastModified) }}</small>
            </div>
            <div v-if="!players.length" class="empty-state">暂无角色</div>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'players'" class="workbench two-col">
      <div class="panel">
        <div class="panel-head">
          <strong>角色列表</strong>
          <FaTag variant="secondary">{{ players.length }}</FaTag>
        </div>
        <div class="item-list">
          <button
            v-for="player in players"
            :key="player.uuid"
            type="button"
            class="list-row"
            :class="{ active: selectedPlayerName === player.name }"
            @click="selectPlayer(player)"
          >
            <span>
              <strong>{{ player.name }}</strong>
              <small>{{ player.uuid }}</small>
            </span>
            <FaTag variant="secondary">{{ player.skinHash ? '已绑定皮肤' : '未绑定' }}</FaTag>
          </button>
          <div v-if="!players.length" class="empty-state">暂无角色</div>
        </div>
      </div>

      <div class="panel stack">
        <div>
          <div class="panel-head"><strong>创建角色</strong><span>限制：{{ settings.maxPlayersPerUser <= 0 ? '不限' : `${settings.maxPlayersPerUser} 个 / 用户` }}</span></div>
          <div class="form-grid">
            <label>
              <span>角色名</span>
              <FaInput v-model="playerForm.name" placeholder="Steve" />
            </label>
            <label>
              <span>所属用户 ID</span>
              <FaSelect v-model="playerForm.ownerId" clearable :options="userOptions" />
            </label>
            <FaButton :loading="saving === 'player'" @click="createPlayer">
              <FaIcon name="i-ri:add-line" />
              创建角色
            </FaButton>
          </div>
        </div>

        <div class="divider" />

        <div>
          <div class="panel-head"><strong>分配材质</strong><span>{{ selectedPlayerName || '未选择角色' }}</span></div>
          <div class="form-grid">
            <label>
              <span>皮肤</span>
              <FaSelect v-model="assignForm.skinHash" clearable :options="textureOptions" />
            </label>
            <label>
              <span>披风</span>
              <FaSelect v-model="assignForm.capeHash" clearable :options="textureOptions" />
            </label>
            <FaButton :loading="saving === 'assign'" @click="assignTextures">
              <FaIcon name="i-ri:save-3-line" />
              保存绑定
            </FaButton>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'textures'" class="workbench two-col">
      <div class="panel">
        <div class="panel-head">
          <strong>皮肤库</strong>
          <FaTag variant="secondary">{{ textures.length }}</FaTag>
        </div>
        <div class="texture-grid">
          <article v-for="texture in textures" :key="texture.hash" class="texture-card">
            <img v-if="textureUrl(texture.hash)" :src="textureUrl(texture.hash)" alt="">
            <div>
              <strong>{{ texture.name }}</strong>
              <span>{{ texture.model || texture.type }} / {{ texture.size || 0 }} bytes</span>
              <code>{{ texture.hash }}</code>
            </div>
          </article>
          <div v-if="!textures.length" class="empty-state">暂无材质</div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head"><strong>上传材质</strong><span>{{ settings.allowPublicUpload ? '可公开' : '强制私有' }}</span></div>
        <div class="form-grid">
          <label>
            <span>名称</span>
            <FaInput v-model="textureForm.name" />
          </label>
          <label>
            <span>模型</span>
            <FaSelect v-model="textureForm.type" :options="[
              { label: 'Steve 皮肤', value: 'steve' },
              { label: 'Alex 皮肤', value: 'alex' },
              { label: '披风', value: 'cape' },
            ]" />
          </label>
          <label>
            <span>上传用户 ID</span>
            <FaSelect v-model="textureForm.uploaderId" clearable :options="userOptions" />
          </label>
          <label>
            <span>PNG 文件</span>
            <input class="file-input" type="file" accept="image/png" @change="onTextureFile">
          </label>
          <label class="switch-row">
            <span>公开材质</span>
            <FaSwitch v-model="textureForm.publicAccess" :disabled="!settings.allowPublicUpload" />
          </label>
          <FaButton :loading="saving === 'texture'" @click="uploadTexture">
            <FaIcon name="i-ri:upload-cloud-2-line" />
            上传
          </FaButton>
        </div>
      </div>
    </section>

    <section v-else-if="currentPage === 'closet'" class="workbench two-col">
      <div class="panel">
        <div class="panel-head">
          <strong>衣柜列表</strong>
          <FaTag variant="secondary">{{ closetItems.length }}</FaTag>
        </div>
        <div class="item-list">
          <div v-for="item in closetItems" :key="item.id" class="list-row static closet-row">
            <span>
              <strong>{{ item.itemName || textureName(item.textureHash) }}</strong>
              <small>{{ userName(item.userId) }} / {{ textureName(item.textureHash) }}</small>
            </span>
            <FaButton size="sm" variant="outline" :loading="saving === `closet:${item.id}`" @click="deleteClosetItem(item)">
              <FaIcon name="i-ri:delete-bin-6-line" />
              删除
            </FaButton>
          </div>
          <div v-if="!closetItems.length" class="empty-state">暂无衣柜项</div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head"><strong>加入衣柜</strong></div>
        <div class="form-grid">
          <label>
            <span>用户</span>
            <FaSelect v-model="closetForm.userId" clearable :options="userOptions" />
          </label>
          <label>
            <span>材质</span>
            <FaSelect v-model="closetForm.textureHash" :options="textureOptions" />
          </label>
          <label>
            <span>显示名称</span>
            <FaInput v-model="closetForm.itemName" placeholder="留空使用材质名称" />
          </label>
          <FaButton :loading="saving === 'closet'" @click="saveClosetItem">
            <FaIcon name="i-ri:add-line" />
            保存衣柜项
          </FaButton>
        </div>
      </div>
    </section>

    <section v-else class="workbench two-col">
      <div class="panel stack">
        <div>
          <div class="panel-head"><strong>皮肤站配置</strong></div>
          <div class="form-grid">
            <label>
              <span>每个用户最多角色数</span>
              <FaInput v-model.number="settingsForm.maxPlayersPerUser" type="number" placeholder="0 表示不限制" />
            </label>
            <label class="switch-row">
              <span>允许公开上传材质</span>
              <FaSwitch v-model="settingsForm.allowPublicUpload" />
            </label>
            <label>
              <span>站点公告</span>
              <FaInput v-model="settingsForm.siteNotice" placeholder="显示在皮肤站仪表盘的简短提示" />
            </label>
            <FaButton :loading="saving === 'settings'" @click="saveSettings">
              <FaIcon name="i-ri:save-3-line" />
              保存配置
            </FaButton>
          </div>
        </div>

        <div class="divider" />

        <div>
          <div class="panel-head"><strong>皮肤站用户</strong><FaTag variant="secondary">{{ users.length }}</FaTag></div>
          <div class="form-grid">
            <label>
              <span>邮箱</span>
              <FaInput v-model="userForm.email" placeholder="skin@example.com" />
            </label>
            <label>
              <span>昵称</span>
              <FaInput v-model="userForm.nickname" />
            </label>
            <label>
              <span>密码</span>
              <FaInput v-model="userForm.password" type="password" />
            </label>
            <FaButton :loading="saving === 'user'" @click="createUser">
              <FaIcon name="i-ri:user-add-line" />
              创建用户
            </FaButton>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head"><strong>Blessing Skin 数据迁移</strong></div>
        <div class="form-grid">
          <label>
            <span>JDBC Driver</span>
            <FaInput v-model="migrationForm.driverClass" placeholder="com.mysql.cj.jdbc.Driver / org.sqlite.JDBC" />
          </label>
          <label>
            <span>JDBC URL</span>
            <FaInput v-model="migrationForm.jdbcUrl" placeholder="jdbc:mysql://localhost:3306/blessing_skin" />
          </label>
          <label>
            <span>用户名</span>
            <FaInput v-model="migrationForm.username" />
          </label>
          <label>
            <span>密码</span>
            <FaInput v-model="migrationForm.password" type="password" />
          </label>
          <label>
            <span>旧材质目录</span>
            <FaInput v-model="migrationForm.textureBaseDir" placeholder="storage/textures" />
          </label>
          <FaButton :loading="saving === 'migration'" @click="runMigration">
            <FaIcon name="i-ri:database-2-line" />
            开始迁移
          </FaButton>
        </div>

        <div v-if="migrationReport" class="report-grid">
          <span>用户 {{ migrationReport.users }}</span>
          <span>角色 {{ migrationReport.players }}</span>
          <span>材质 {{ migrationReport.textures }}</span>
          <span>衣柜 {{ migrationReport.closetItems }}</span>
          <span>配置 {{ migrationReport.options }}</span>
        </div>
        <div v-if="migrationReport?.warnings?.length" class="warning-list">
          <strong>警告</strong>
          <span v-for="item in migrationReport.warnings" :key="item">{{ item }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.skin-plugin {
  display: grid;
  gap: 14px;
}

.dashboard-page,
.workbench,
.stack {
  display: grid;
  gap: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

.summary-card,
.panel {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.summary-card {
  display: grid;
  gap: 6px;
  padding: 14px;
}

.summary-icon {
  color: rgb(var(--primary-6));
  font-size: 20px;
}

.summary-card span:not(.summary-icon) {
  color: var(--color-text-3);
  font-size: 12px;
}

.summary-card strong {
  font-size: 24px;
}

.refresh-button {
  min-height: 78px;
}

.two-col {
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  align-items: start;
}

.panel {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.panel-head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.panel-head span,
.list-row small,
.texture-card span,
.metric-list span {
  color: var(--color-text-3);
  font-size: 12px;
}

.item-list,
.form-grid,
.texture-grid,
.warning-list,
.metric-list {
  display: grid;
  gap: 10px;
}

.compact {
  gap: 8px;
}

.list-row {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
  color: var(--color-text-1);
  text-align: left;
}

.list-row.active,
.list-row:not(.static):hover {
  border-color: rgb(var(--primary-6));
  background: var(--color-fill-2);
}

.list-row span {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.list-row small,
.texture-card code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.closet-row {
  grid-template-columns: minmax(0, 1fr) auto;
}

.form-grid label,
.metric-list div {
  display: grid;
  gap: 6px;
}

.form-grid label span {
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 600;
}

.metric-list div {
  padding: 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.switch-row {
  grid-template-columns: 1fr auto;
  align-items: center;
}

.file-input {
  min-height: 36px;
  padding: 6px 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
  color: var(--color-text-2);
}

.divider {
  height: 1px;
  background: var(--color-border-2);
}

.texture-grid {
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}

.texture-card {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.texture-card img {
  width: 72px;
  height: 72px;
  object-fit: contain;
  image-rendering: pixelated;
  border-radius: 4px;
  background: var(--color-fill-2);
}

.texture-card div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
  gap: 8px;
}

.report-grid span,
.warning-list span {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--color-fill-2);
}

.warning-list span {
  color: rgb(var(--warning-6));
}

.empty-state {
  padding: 24px;
  border: 1px dashed var(--color-border-2);
  border-radius: 6px;
  color: var(--color-text-3);
  text-align: center;
}

@media (max-width: 980px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
