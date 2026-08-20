<script setup lang="ts">
import type { PluginDevProject } from '@/api/modules/platform-devtools'

const store = usePluginDevtoolsStore()
const toast = useFaToast()

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
        <FaTag variant="secondary" class="text-xs">
          {{ store.devProjects.length }}
        </FaTag>
        <div class="flex-1" />
        <FaButton variant="outline" size="icon" :loading="store.devProjectsLoading" title="刷新" @click="store.loadDevProjects()">
          <FaIcon name="i-ri:refresh-line" />
        </FaButton>
        <FaButton size="sm" @click="openAdd">
          <FaIcon name="i-ri:add-line" />
          登记目录
        </FaButton>
      </div>
      <div v-if="store.status?.devProjectStoreFile" class="settings-section__hint">
        面板登记的项目持久化在 <span class="font-mono">{{ store.status.devProjectStoreFile }}</span>，此文件可被 coding agent 读取以定位插件源码。
      </div>

      <div v-for="project in store.devProjects" :key="project.code" class="project-row">
        <div class="project-row__main">
          <div class="project-row__title">
            <span class="font-medium font-mono">{{ project.code }}</span>
            <FaTag :variant="project.source === 'CONFIG' ? 'secondary' : 'outline'" class="text-xs">
              {{ project.source === 'CONFIG' ? '配置文件' : '面板登记' }}
            </FaTag>
            <FaTag v-if="!project.autoCompile" variant="secondary" class="text-xs">
              手动编译
            </FaTag>
          </div>
          <FaTooltip :text="project.path" side="top">
            <div class="project-row__path">
              {{ project.path }}
            </div>
          </FaTooltip>
        </div>
        <div class="project-row__dots">
          <FaTooltip v-for="dot in statusDots(project)" :key="dot.label" :text="dot.label" side="top">
            <span class="status-dot" :class="dot.ok ? 'status-dot--ok' : 'status-dot--bad'" />
          </FaTooltip>
        </div>
        <FaTooltip text="立即重载该插件" side="top">
          <FaButton variant="ghost" size="icon" :loading="reloadingCode === project.code" @click="reload(project)">
            <FaIcon name="i-ri:restart-line" />
          </FaButton>
        </FaTooltip>
        <FaTooltip v-if="project.source === 'FILE'" text="从清单文件移除" side="top">
          <FaButton variant="ghost" size="icon" :loading="removingCode === project.code" @click="remove(project)">
            <FaIcon name="i-ri:delete-bin-line" />
          </FaButton>
        </FaTooltip>
      </div>
      <div v-if="!store.devProjects.length && !store.devProjectsLoading" class="settings-empty">
        暂无开发项目；点击「登记目录」选择插件源码目录，或在 yml 的 yudream.platform.plugin.dev-mode.projects 中配置
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
        <FaButton variant="outline" size="sm" :disabled="!resetFab" @click="handleResetFab">
          重置位置
        </FaButton>
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
        <FaButton variant="outline" size="sm" :disabled="!resetPanel" @click="handleResetPanel">
          重置浮窗
        </FaButton>
      </div>
    </div>

    <!-- 登记开发项目 -->
    <FaModal v-model="addOpen" title="登记开发项目" :footer="false" :z-index="2200" content-class="sm:max-w-lg">
      <div class="add-form">
        <div class="add-form__field">
          <span class="add-form__label">插件模块根目录（必填，宿主机绝对路径）</span>
          <FaInput v-model="addForm.path" placeholder="D:/code/yudream-admin-plugins/yudream-plugin-xxx" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">插件编码（留空则从 plugin.yml 自动推断）</span>
          <FaInput v-model="addForm.code" placeholder="与 plugin.yml 的 name 一致" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">前端产物目录（可选，相对模块根目录）</span>
          <FaInput v-model="addForm.frontendDist" placeholder="默认前端 build 输出目录" />
        </div>
        <div class="add-form__field">
          <span class="add-form__label">编译命令（可选，留空用默认 mvn compile）</span>
          <FaInput v-model="addForm.compileCommand" placeholder="mvn -q compile" />
        </div>
        <div class="add-form__switch">
          <FaSwitch v-model="addForm.autoCompile" />
          <span class="add-form__label">源码变化时自动编译</span>
        </div>
        <div class="add-form__actions">
          <FaButton variant="outline" @click="addOpen = false">
            取消
          </FaButton>
          <FaButton :loading="addSaving" @click="submitAdd">
            登记
          </FaButton>
        </div>
      </div>
    </FaModal>
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
