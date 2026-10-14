<script setup lang="ts">
import type { PluginDevProject, PluginDevProjectScanResult } from '@/api/modules/platform-devtools'
import apiPlugin from '@/api/modules/platform-plugin'
import DevProjectDirectoryBrowser from './DevProjectDirectoryBrowser.vue'

const store = usePluginDevtoolsStore()
const toast = useYdToast()

// 悬浮按钮/浮窗位置重置由外层 index.vue 注入（位置状态在 useDevtoolsFab / useDevtoolsPanel 内）
const resetFab = inject<(() => Promise<void>) | undefined>('pluginDevtoolsFabReset', undefined)
const resetPanel = inject<(() => void) | undefined>('pluginDevtoolsPanelReset', undefined)

onMounted(() => {
  store.loadDevProjects()
})

function statusDots(project: PluginDevProject) {
  return [
    { label: project.pathExists ? '源码目录存在' : '源码目录不存在', ok: project.pathExists },
    { label: project.classesBuilt ? '类产物已编译' : '类产物未编译（先执行一次 mvn compile）', ok: project.classesBuilt },
    { label: project.descriptorReady ? 'plugin.yml 可读' : 'plugin.yml 缺失', ok: project.descriptorReady },
  ]
}

const reloadingCode = ref('')
const frontendReloadingCode = ref('')
const enablingCode = ref('')

async function enable(project: PluginDevProject) {
  enablingCode.value = project.code
  try {
    await apiPlugin.enable(project.code)
    await store.loadStatus(true)
    toast.success(`插件 ${project.code} 已启用`)
  }
  catch {
    // 拦截器已提示
  }
  finally {
    enablingCode.value = ''
  }
}

async function reload(project: PluginDevProject) {
  reloadingCode.value = project.code
  try {
    await store.reloadDevPlugin(project.code)
    toast.success('重载指令已提交')
  }
  catch {
    // 拦截器已提示
  }
  finally {
    reloadingCode.value = ''
  }
}

async function reloadFrontend(project: PluginDevProject) {
  frontendReloadingCode.value = project.code
  try {
    await store.reloadFrontend(project.code)
    toast.success('前端重载已触发')
  }
  catch {
    // 拦截器已提示
  }
  finally {
    frontendReloadingCode.value = ''
  }
}

const removingCode = ref('')
async function remove(project: PluginDevProject) {
  removingCode.value = project.code
  try {
    await store.removeDevProject(project.code)
    toast.success(`已移除开发项目 ${project.code}`)
  }
  catch {
    // 拦截器已提示
  }
  finally {
    removingCode.value = ''
  }
}

// ---------- 添加开发项目 ----------
const addOpen = ref(false)
const addSaving = ref(false)
const browseOpen = ref(false)
const addForm = ref({
  path: '',
  code: '',
  frontendDist: '',
  compileCommand: '',
  autoCompile: true,
})

function openAdd() {
  addForm.value = { path: '', code: '', frontendDist: '', compileCommand: '', autoCompile: true }
  addOpen.value = true
}

async function submitAdd() {
  if (!addForm.value.path.trim()) {
    toast.warning('插件目录不能为空')
    return
  }
  addSaving.value = true
  try {
    await store.addDevProject({
      path: addForm.value.path.trim(),
      code: addForm.value.code.trim() || undefined,
      frontendDist: addForm.value.frontendDist.trim() || undefined,
      compileCommand: addForm.value.compileCommand.trim() || undefined,
      autoCompile: addForm.value.autoCompile,
    })
    toast.success('开发项目已登记')
    addOpen.value = false
  }
  catch {
    // 拦截器已提示
  }
  finally {
    addSaving.value = false
  }
}

function handleBrowseSelect(path: string, inferredCode?: string) {
  addForm.value.path = path
  if (inferredCode && !addForm.value.code.trim()) {
    addForm.value.code = inferredCode
  }
}

// ---------- 批量登记子目录 ----------
const batchOpen = ref(false)
const batchSaving = ref(false)
const batchBrowseOpen = ref(false)
const batchPath = ref('')
const batchResult = ref<PluginDevProjectScanResult | null>(null)

function openBatch() {
  batchPath.value = ''
  batchResult.value = null
  batchOpen.value = true
}

function handleBatchBrowseSelect(path: string) {
  batchPath.value = path
}

async function submitBatch() {
  const path = batchPath.value.trim()
  if (!path) {
    toast.warning('父目录不能为空')
    return
  }
  batchSaving.value = true
  try {
    batchResult.value = await store.batchAddDevProjects(path)
    const registered = batchResult.value.registered.length
    const skipped = batchResult.value.skipped.length
    if (registered === 0 && skipped === 0) {
      toast.warning('未发现可登记的插件模块')
    }
    else {
      toast.success(`已登记 ${registered} 个插件，跳过 ${skipped} 个`)
    }
  }
  catch {
    // 拦截器已提示
  }
  finally {
    batchSaving.value = false
  }
}

// ---------- 新建插件骨架 ----------
const scaffoldOpen = ref(false)
const scaffoldSaving = ref(false)
const scaffoldBrowseOpen = ref(false)
const scaffoldForm = ref({
  parentDir: '',
  code: '',
  displayName: '',
  version: '',
  description: '',
  depend: '',
  softdepend: '',
  register: true,
})

function openScaffold() {
  scaffoldForm.value = { parentDir: '', code: '', displayName: '', version: '', description: '', depend: '', softdepend: '', register: true }
  scaffoldOpen.value = true
}

function splitCodes(raw: string): string[] | undefined {
  const codes = raw.split(/[,，\s]+/).map(item => item.trim()).filter(Boolean)
  return codes.length ? codes : undefined
}

async function submitScaffold() {
  const parentDir = scaffoldForm.value.parentDir.trim()
  const code = scaffoldForm.value.code.trim()
  if (!parentDir) {
    toast.warning('父目录不能为空')
    return
  }
  if (!code) {
    toast.warning('插件编码不能为空')
    return
  }
  scaffoldSaving.value = true
  try {
    const result = await store.scaffoldPlugin({
      parentDir,
      code,
      displayName: scaffoldForm.value.displayName.trim() || undefined,
      version: scaffoldForm.value.version.trim() || undefined,
      description: scaffoldForm.value.description.trim() || undefined,
      depend: splitCodes(scaffoldForm.value.depend),
      softdepend: splitCodes(scaffoldForm.value.softdepend),
      register: scaffoldForm.value.register,
    })
    toast.success(`骨架已生成：${result.projectPath}；执行一次 mvn compile 后开发模式自动加载`)
    scaffoldOpen.value = false
  }
  catch {
    // 拦截器已提示
  }
  finally {
    scaffoldSaving.value = false
  }
}

function handleScaffoldBrowseSelect(path: string) {
  scaffoldForm.value.parentDir = path
}

async function handleResetFab() {
  if (!resetFab) {
    return
  }
  await resetFab()
  toast.success('悬浮按钮位置已重置')
}

function handleResetPanel() {
  if (!resetPanel) {
    return
  }
  resetPanel()
  toast.success('浮窗位置与尺寸已重置')
}
</script>

<template>
  <div class="settings-panel">
    <div class="settings-section">
      <div class="settings-section__header">
        <span class="settings-section__title">开发项目</span>
        <YdTag variant="secondary" class="text-xs">
          {{ store.devProjects.length }}
        </YdTag>
        <div class="flex-1" />
        <YdButton variant="outline" size="icon" :loading="store.devProjectsLoading" title="刷新" @click="store.loadDevProjects()">
          <YdIcon name="morph:refresh-cw" />
        </YdButton>
        <YdButton variant="outline" size="sm" @click="openScaffold">
          <YdIcon name="morph:wand-sparkles" />
          新建插件
        </YdButton>
        <YdButton variant="outline" size="sm" @click="openBatch">
          <YdIcon name="morph:folder-plus" />
          批量登记
        </YdButton>
        <YdButton size="sm" @click="openAdd">
          <YdIcon name="morph:plus" />
          登记目录
        </YdButton>
      </div>
      <div v-if="store.status?.devProjectStoreFile" class="settings-section__hint">
        面板登记的项目持久化在 <span class="font-mono">{{ store.status.devProjectStoreFile }}</span>，此文件可被 coding agent 读取以定位插件源码。
      </div>

      <div v-for="project in store.devProjects" :key="project.code" class="project-row">
        <div class="project-row__main">
          <div class="project-row__title">
            <span class="font-medium font-mono">{{ project.code }}</span>
            <YdTag :variant="project.source === 'CONFIG' ? 'secondary' : 'outline'" class="text-xs">
              {{ project.source === 'CONFIG' ? '配置文件' : '面板登记' }}
            </YdTag>
            <YdTag v-if="!project.autoCompile" variant="secondary" class="text-xs">
              手动编译
            </YdTag>
          </div>
          <YdTooltip :text="project.path" side="top">
            <div class="project-row__path">
              {{ project.path }}
            </div>
          </YdTooltip>
        </div>
        <div class="project-row__dots">
          <YdTooltip v-for="dot in statusDots(project)" :key="dot.label" :text="dot.label" side="top">
            <span class="status-dot" :class="dot.ok ? 'status-dot--ok' : 'status-dot--bad'" />
          </YdTooltip>
        </div>
        <YdTooltip text="加载并启用该插件" side="top">
          <YdButton variant="ghost" size="icon" :loading="enablingCode === project.code" @click="enable(project)">
            <YdIcon name="morph:play" />
          </YdButton>
        </YdTooltip>
        <YdTooltip text="重挂载该插件前端远程模块" side="top">
          <YdButton variant="ghost" size="icon" :loading="frontendReloadingCode === project.code" @click="reloadFrontend(project)">
            <YdIcon name="morph:refresh-cw" />
          </YdButton>
        </YdTooltip>
        <YdTooltip text="立即 Java 重载该插件" side="top">
          <YdButton variant="ghost" size="icon" :loading="reloadingCode === project.code" @click="reload(project)">
            <YdIcon name="morph:rotate-cw" />
          </YdButton>
        </YdTooltip>
        <YdTooltip v-if="project.source === 'FILE'" text="从清单文件移除" side="top">
          <YdButton variant="ghost" size="icon" :loading="removingCode === project.code" @click="remove(project)">
            <YdIcon name="morph:trash-2" />
          </YdButton>
        </YdTooltip>
      </div>
      <div v-if="!store.devProjects.length && !store.devProjectsLoading" class="settings-empty">
        暂无开发项目；点击「批量登记」选择插件仓根目录一键扫描子模块，或「登记目录」登记单个插件，也可在 yml 的 yudream.platform.plugin.dev-mode.projects 中配置
      </div>
    </div>

    <div class="settings-section">
      <div class="settings-section__header">
        <span class="settings-section__title">面板偏好</span>
      </div>
      <div class="pref-row">
        <div class="pref-row__main">
          <div class="pref-row__title">
            悬浮按钮位置
          </div>
          <div class="pref-row__desc">
            按钮可拖拽换位，拖到屏幕边缘（24px 内）收成半隐边缘条；重置可恢复默认位置
          </div>
        </div>
        <YdButton variant="outline" size="sm" :disabled="!resetFab" @click="handleResetFab">
          重置位置
        </YdButton>
      </div>
      <div class="pref-row">
        <div class="pref-row__main">
          <div class="pref-row__title">
            浮窗位置与尺寸
          </div>
          <div class="pref-row__desc">
            拖动标题栏移动浮窗，拖右下角缩放；重置可恢复默认居中位置与尺寸
          </div>
        </div>
        <YdButton variant="outline" size="sm" :disabled="!resetPanel" @click="handleResetPanel">
          重置浮窗
        </YdButton>
      </div>
    </div>

    <!-- 登记开发项目 -->
    <YdModal v-model="addOpen" title="登记开发项目" :footer="false" :z-index="2200" content-class="sm:max-w-lg">
      <div class="add-form">
        <div class="add-form__field">
          <span class="add-form__label">插件模块根目录（必填，宿主机绝对路径）</span>
          <div class="add-form__path">
            <YdInput v-model="addForm.path" class="flex-1" placeholder="D:/code/yudream-admin-plugins/yudream-plugin-xxx" />
            <YdButton variant="outline" @click="browseOpen = true">
              <YdIcon name="morph:folder-open" />
              浏览
            </YdButton>
          </div>
        </div>
        <div class="add-form__field">
          <span class="add-form__label">插件编码（留空则从 plugin.yml 自动推断）</span>
          <YdInput v-model="addForm.code" placeholder="与 plugin.yml 的 name 一致" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">前端产物目录（可选，相对模块根目录）</span>
          <YdInput v-model="addForm.frontendDist" placeholder="默认前端 build 输出目录" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">编译命令（可选，留空默认 mvn -q compile -DskipTests -P dev-export）</span>
          <YdInput v-model="addForm.compileCommand" placeholder="mvn -q compile -DskipTests -P dev-export" />
        </div>
        <div class="add-form__switch">
          <YdSwitch v-model="addForm.autoCompile" />
          <span class="add-form__label">源码变化时自动编译</span>
        </div>
        <div class="add-form__actions">
          <YdButton variant="outline" @click="addOpen = false">
            取消
          </YdButton>
          <YdButton :loading="addSaving" @click="submitAdd">
            登记
          </YdButton>
        </div>
      </div>
    </YdModal>

    <!-- 宿主机目录浏览（嵌套于登记表单之上） -->
    <DevProjectDirectoryBrowser v-model="browseOpen" @select="handleBrowseSelect" />

    <!-- 批量登记子目录 -->
    <YdModal v-model="batchOpen" title="批量登记子目录" :footer="false" :z-index="2200" content-class="sm:max-w-lg">
      <div class="add-form">
        <div class="add-form__field">
          <span class="add-form__label">父目录（必填，扫描其中的插件模块并去重登记）</span>
          <div class="add-form__path">
            <YdInput v-model="batchPath" class="flex-1" placeholder="D:/code/yudream-admin-plugins" />
            <YdButton variant="outline" @click="batchBrowseOpen = true">
              <YdIcon name="morph:folder-open" />
              浏览
            </YdButton>
          </div>
        </div>
        <div class="settings-section__hint">
          最多向下扫描 3 层，跳过 node_modules / target / dist / .git 等目录；已在配置文件或面板清单中的插件会被跳过。
        </div>
        <div v-if="batchResult" class="batch-result">
          <div class="batch-result__title">
            新登记 {{ batchResult.registered.length }} 个
          </div>
          <div v-for="item in batchResult.registered" :key="item.code" class="batch-result__row">
            <span class="font-mono">{{ item.code }}</span>
            <span class="batch-result__path">{{ item.path }}</span>
          </div>
          <div v-if="batchResult.skipped.length" class="batch-result__title">
            已跳过 {{ batchResult.skipped.length }} 个
          </div>
          <div v-for="item in batchResult.skipped" :key="`${item.code || ''}:${item.path}`" class="batch-result__row">
            <span class="font-mono">{{ item.code || '—' }}</span>
            <span>{{ item.reason }}</span>
            <span class="batch-result__path">{{ item.path }}</span>
          </div>
        </div>
        <div class="add-form__actions">
          <YdButton variant="outline" @click="batchOpen = false">
            {{ batchResult ? '关闭' : '取消' }}
          </YdButton>
          <YdButton :loading="batchSaving" @click="submitBatch">
            {{ batchResult ? '再次扫描' : '扫描并登记' }}
          </YdButton>
        </div>
      </div>
    </YdModal>
    <DevProjectDirectoryBrowser v-model="batchBrowseOpen" @select="handleBatchBrowseSelect" />

    <!-- 新建插件骨架 -->
    <YdModal v-model="scaffoldOpen" title="新建插件骨架" :footer="false" :z-index="2200" content-class="sm:max-w-lg">
      <div class="add-form">
        <div class="add-form__field">
          <span class="add-form__label">父目录（必填，模块生成到 父目录/yudream-plugin-{编码}）</span>
          <div class="add-form__path">
            <YdInput v-model="scaffoldForm.parentDir" class="flex-1" placeholder="D:/code/yudream-admin-plugins" />
            <YdButton variant="outline" @click="scaffoldBrowseOpen = true">
              <YdIcon name="morph:folder-open" />
              浏览
            </YdButton>
          </div>
        </div>
        <div class="add-form__field">
          <span class="add-form__label">插件编码（必填，kebab-case，如 demo-tool）</span>
          <YdInput v-model="scaffoldForm.code" placeholder="小写字母开头，仅小写字母/数字/连字符" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">显示名称（可选，默认用编码）</span>
          <YdInput v-model="scaffoldForm.displayName" placeholder="演示工具" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">版本（可选，默认 1.0.0）</span>
          <YdInput v-model="scaffoldForm.version" placeholder="1.0.0" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">描述（可选）</span>
          <YdInput v-model="scaffoldForm.description" placeholder="插件用途一句话说明" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">硬依赖（可选，逗号分隔的插件编码）</span>
          <YdInput v-model="scaffoldForm.depend" placeholder="如 minecraft-utils" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">软依赖（可选，逗号分隔；缺失时不阻塞启用）</span>
          <YdInput v-model="scaffoldForm.softdepend" placeholder="如 qq-binding" />
        </div>
        <div class="add-form__switch">
          <YdSwitch v-model="scaffoldForm.register" />
          <span class="add-form__label">生成后登记为开发模式项目</span>
        </div>
        <div class="add-form__actions">
          <YdButton variant="outline" @click="scaffoldOpen = false">
            取消
          </YdButton>
          <YdButton :loading="scaffoldSaving" @click="submitScaffold">
            生成
          </YdButton>
        </div>
      </div>
    </YdModal>

    <!-- 宿主机目录浏览（脚手架选父目录，嵌套于脚手架表单之上） -->
    <DevProjectDirectoryBrowser v-model="scaffoldBrowseOpen" @select="handleScaffoldBrowseSelect" />
  </div>
</template>

<style scoped>
.settings-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.settings-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings-section__header {
  display: flex;
  gap: 8px;
  align-items: center;
}

.settings-section__title {
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 600;
}

.settings-section__hint {
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.batch-result {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 240px;
  overflow: auto;
  padding: 8px 0;
}

.batch-result__title {
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 600;
}

.batch-result__row {
  display: flex;
  gap: 8px;
  align-items: baseline;
  color: var(--color-text-2);
  font-size: 12px;
}

.batch-result__path {
  min-width: 0;
  color: var(--color-text-3);
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-row {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.project-row__main {
  flex: 1;
  min-width: 0;
}

.project-row__title {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
  color: var(--color-text-1);
  font-size: 13px;
}

.project-row__path {
  margin-top: 2px;
  color: var(--color-text-3);
  font-size: 12px;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-row__dots {
  display: flex;
  gap: 4px;
  align-items: center;
  flex-shrink: 0;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot--ok {
  background: var(--color-success, #00b42a);
}

.status-dot--bad {
  background: var(--color-danger, #f53f3f);
}

.settings-empty {
  padding: 24px 12px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.8;
  text-align: center;
}

.pref-row {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.pref-row__main {
  flex: 1;
  min-width: 0;
}

.pref-row__title {
  color: var(--color-text-2);
  font-size: 13px;
}

.pref-row__desc {
  margin-top: 2px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.add-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.add-form__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.add-form__path {
  display: flex;
  gap: 8px;
  align-items: center;
}

.add-form__label {
  color: var(--color-text-3);
  font-size: 12px;
}

.add-form__switch {
  display: flex;
  gap: 8px;
  align-items: center;
}

.add-form__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
