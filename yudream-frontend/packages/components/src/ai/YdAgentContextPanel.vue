<script setup lang="ts">
import type { YdAgentContextFile, YdAgentHarnessState, YdAgentMcpServer, YdAgentSkill, YdAgentWorkspace } from './agent-workbench'
import { computed, ref } from 'vue'
import { YdButton, YdCheckbox, YdIcon, YdProgress, YdSwitch, YdTabs } from '../adapters/index'
import '../theme/appearance.css'

defineOptions({ name: 'YdAgentContextPanel' })

const props = withDefaults(defineProps<{
  workspace?: YdAgentWorkspace
  files?: YdAgentContextFile[]
  skills?: YdAgentSkill[]
  mcpServers?: YdAgentMcpServer[]
  harness: YdAgentHarnessState
  busy?: boolean
}>(), {
  files: () => [],
  skills: () => [],
  mcpServers: () => [],
  busy: false,
})

const emit = defineEmits<{
  fileSelect: [file: YdAgentContextFile]
  fileToggle: [path: string, included: boolean]
  skillToggle: [code: string, enabled: boolean]
}>()

const activeTab = ref<string | number>('files')
const includedCount = computed(() => props.files.filter(file => file.included).length)
const enabledSkillCount = computed(() => props.skills.filter(skill => skill.enabled).length)
const connectedServerCount = computed(() => props.mcpServers.filter(server => server.status === 'connected').length)
const tabs = computed(() => [
  { value: 'files', label: `文件 ${props.files.length}`, icon: 'morph:files' },
  { value: 'skills', label: `Skills ${props.skills.length}`, icon: 'morph:sparkles' },
  { value: 'mcp', label: `MCP ${props.mcpServers.length}`, icon: 'morph:plug' },
])

const workspaceKind = computed(() => props.workspace
  ? {
      local: '本地工作区',
      worktree: '独立 Worktree',
      remote: '远程工作区',
    }[props.workspace.kind]
  : '工作区')
const workspaceAccess = computed(() => props.workspace
  ? {
      read: '只读',
      write: '可读写',
      unavailable: '不可用',
    }[props.workspace.access]
  : '未连接')
const harnessStatus = computed(() => ({
  idle: { label: '就绪', icon: 'morph:circle-dot' },
  running: { label: '运行中', icon: 'morph:loader-circle' },
  waiting: { label: '等待确认', icon: 'morph:circle-pause' },
  completed: { label: '已完成', icon: 'morph:circle-check' },
  error: { label: '运行失败', icon: 'morph:circle-alert' },
  cancelled: { label: '已停止', icon: 'morph:circle-stop' },
}[props.harness.status]))
const contextUsage = computed(() => {
  const tokens = props.harness.contextTokens
  const limit = props.harness.contextLimit
  if (tokens === undefined || limit === undefined || !Number.isFinite(tokens) || !Number.isFinite(limit) || limit <= 0) {
    return undefined
  }
  return {
    tokens: Math.max(0, tokens),
    limit,
    percent: Math.min(100, Math.max(0, tokens / limit * 100)),
  }
})
const elapsed = computed(() => {
  const milliseconds = props.harness.elapsedMs
  if (milliseconds === undefined || !Number.isFinite(milliseconds) || milliseconds < 0) {
    return undefined
  }
  const seconds = Math.floor(milliseconds / 1000)
  return seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
})
const numberFormatter = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 0 })

function fileName(path: string) {
  return path.split(/[\\/]/).filter(Boolean).at(-1) || path
}

function toggleFile(path: string, included: boolean) {
  if (!props.busy) {
    emit('fileToggle', path, included)
  }
}

function toggleSkill(code: string, enabled: boolean) {
  if (!props.busy) {
    emit('skillToggle', code, enabled)
  }
}

function serverStatus(server: YdAgentMcpServer) {
  return {
    connected: { label: '已连接', icon: 'morph:plug-zap' },
    disconnected: { label: '未连接', icon: 'morph:unplug' },
    error: { label: '连接错误', icon: 'morph:circle-alert' },
  }[server.status]
}
</script>

<template>
  <aside class="yd-agent-context" aria-label="Agent 工作区与上下文">
    <header class="yd-agent-context__workspace">
      <div class="yd-agent-context__eyebrow">
        <YdIcon name="morph:folder-code" />
        {{ workspaceKind }}
        <span class="yd-agent-context__access">
          <YdIcon :name="workspace?.access === 'write' ? 'morph:lock-open' : 'morph:lock-keyhole'" />
          {{ workspaceAccess }}
        </span>
      </div>
      <h3>{{ workspace?.name || '尚未选择工作区' }}</h3>
      <code v-if="workspace?.path" class="yd-agent-context__workspace-path" :title="workspace.path">{{ workspace.path }}</code>
      <p v-else class="yd-agent-context__description">
        选择工作区后，可查看参与本次任务的文件与能力。
      </p>
      <div v-if="workspace?.branch" class="yd-agent-context__branch">
        <YdIcon name="morph:git-branch" />
        <span>{{ workspace.branch }}</span>
      </div>
      <p v-if="workspace?.description" class="yd-agent-context__description">
        {{ workspace.description }}
      </p>
    </header>

    <YdTabs
      v-model="activeTab"
      :list="tabs"
      class="yd-agent-context__tabs"
      list-class="yd-agent-context__tab-list"
      content-class="yd-agent-context__tab-content"
    >
      <template #files>
        <div class="yd-agent-context__section-summary">
          <span>已纳入 {{ includedCount }} 个文件</span>
          <span v-if="busy" class="yd-agent-context__locked"><YdIcon name="morph:lock-keyhole" /> 上下文已锁定</span>
        </div>
        <ul v-if="files.length" class="yd-agent-context__file-list">
          <li v-for="file in files" :key="file.path" class="yd-agent-context__file">
            <YdCheckbox
              :model-value="!!file.included"
              :disabled="busy"
              class="yd-agent-context__file-checkbox"
              @update:model-value="toggleFile(file.path, $event === true)"
            >
              <span class="sr-only">纳入上下文：{{ file.path }}</span>
            </YdCheckbox>
            <YdButton variant="ghost" class="yd-agent-context__file-button" :title="file.path" @click="emit('fileSelect', file)">
              <YdIcon name="morph:file-code" />
              <span class="yd-agent-context__file-text">
                <strong>{{ fileName(file.path) }}</strong>
                <code>{{ file.path }}</code>
                <span v-if="file.description" class="yd-agent-context__description">{{ file.description }}</span>
              </span>
              <YdIcon name="morph:arrow-up-right" class="yd-agent-context__file-open" />
            </YdButton>
          </li>
        </ul>
        <div v-else class="yd-agent-context__empty">
          <YdIcon name="morph:files" />
          <strong>没有上下文文件</strong>
          <p>将相关文件加入上下文，帮助 Agent 理解当前代码。</p>
        </div>
      </template>

      <template #skills>
        <div class="yd-agent-context__section-summary">
          <span>已启用 {{ enabledSkillCount }} 项 Skill</span>
          <span v-if="busy" class="yd-agent-context__locked"><YdIcon name="morph:lock-keyhole" /> 上下文已锁定</span>
        </div>
        <ul v-if="skills.length" class="yd-agent-context__capability-list">
          <li v-for="skill in skills" :key="skill.code" class="yd-agent-context__skill">
            <div class="yd-agent-context__capability-heading">
              <YdIcon name="morph:sparkles" />
              <strong>{{ skill.name }}</strong>
              <YdSwitch
                :model-value="skill.enabled"
                :disabled="busy"
                :aria-label="`启用 Skill：${skill.name}`"
                @update:model-value="toggleSkill(skill.code, $event === true)"
              />
            </div>
            <p class="yd-agent-context__description">
              {{ skill.description }}
            </p>
            <code v-if="skill.source" class="yd-agent-context__source" :title="skill.source">{{ skill.source }}</code>
          </li>
        </ul>
        <div v-else class="yd-agent-context__empty">
          <YdIcon name="morph:sparkles" />
          <strong>暂无可用 Skills</strong>
          <p>宿主提供技能后，可在这里选择任务需要的工作流程。</p>
        </div>
      </template>

      <template #mcp>
        <div class="yd-agent-context__section-summary">
          <span>{{ connectedServerCount }} / {{ mcpServers.length }} 个服务已连接</span>
          <span>工具由宿主提供</span>
        </div>
        <ul v-if="mcpServers.length" class="yd-agent-context__capability-list">
          <li v-for="server in mcpServers" :key="server.code" class="yd-agent-context__server">
            <div class="yd-agent-context__capability-heading">
              <YdIcon name="morph:plug" />
              <strong>{{ server.name }}</strong>
              <span class="yd-agent-context__server-state" :data-status="server.status">
                <YdIcon :name="serverStatus(server).icon" />
                {{ serverStatus(server).label }}
              </span>
            </div>
            <p v-if="server.description" class="yd-agent-context__description">
              {{ server.description }}
            </p>
            <code v-if="server.transport" class="yd-agent-context__source">{{ server.transport }}</code>
            <details v-if="server.tools?.length" class="yd-agent-context__tools">
              <summary>{{ server.tools.length }} 个工具<YdIcon name="morph:chevron-down" /></summary>
              <ul>
                <li v-for="tool in server.tools" :key="tool">
                  <YdIcon name="morph:wrench" /><code>{{ tool }}</code>
                </li>
              </ul>
            </details>
            <p v-else class="yd-agent-context__tool-empty">
              未提供工具清单
            </p>
          </li>
        </ul>
        <div v-else class="yd-agent-context__empty">
          <YdIcon name="morph:plug" />
          <strong>未配置 MCP 服务</strong>
          <p>连接状态与工具清单由宿主同步至此面板。</p>
        </div>
      </template>
    </YdTabs>

    <footer class="yd-agent-context__harness">
      <div class="yd-agent-context__harness-title">
        <YdIcon name="morph:workflow" />
        <strong>{{ harness.name || 'Harness' }}</strong>
        <span class="yd-agent-context__harness-status" role="status">
          <YdIcon :name="harnessStatus.icon" :class="{ 'yd-agent-context__spin': harness.status === 'running' }" />
          {{ harnessStatus.label }}
        </span>
      </div>
      <div v-if="harness.phase || elapsed" class="yd-agent-context__harness-phase">
        <span>{{ harness.phase || '本次运行' }}</span>
        <span v-if="elapsed">{{ elapsed }}</span>
      </div>
      <div v-if="contextUsage" class="yd-agent-context__usage">
        <div class="yd-agent-context__usage-label">
          <span>上下文用量</span>
          <span><strong>{{ numberFormatter.format(contextUsage.tokens) }}</strong> / {{ numberFormatter.format(contextUsage.limit) }} tokens</span>
        </div>
        <YdProgress :model-value="contextUsage.percent" class="yd-agent-context__progress" aria-label="上下文用量" />
      </div>
      <dl v-if="harness.checkpoint || harness.memory" class="yd-agent-context__harness-meta">
        <div v-if="harness.checkpoint">
          <dt><YdIcon name="morph:bookmark-check" />检查点</dt>
          <dd>{{ harness.checkpoint }}</dd>
        </div>
        <div v-if="harness.memory">
          <dt><YdIcon name="morph:brain" />记忆</dt>
          <dd>{{ harness.memory }}</dd>
        </div>
      </dl>
      <code v-if="harness.runId" class="yd-agent-context__run-id" :title="harness.runId">Run · {{ harness.runId }}</code>
    </footer>
  </aside>
</template>

<style scoped>
.yd-agent-context {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  font-size: 12px;
  color: var(--color-text-1);
  background: var(--color-bg-1);
}

.yd-agent-context__workspace,
.yd-agent-context__harness {
  flex-shrink: 0;
  padding: 18px 16px;
}

.yd-agent-context__workspace {
  border-bottom: 1px solid var(--color-border-2);
}

.yd-agent-context__eyebrow,
.yd-agent-context__access,
.yd-agent-context__branch,
.yd-agent-context__locked,
.yd-agent-context__server-state,
.yd-agent-context__harness-status {
  display: flex;
  gap: 5px;
  align-items: center;
}

.yd-agent-context__eyebrow {
  font-size: 11px;
  color: var(--color-text-2);
}

.yd-agent-context__access {
  margin-left: auto;
  color: var(--color-text-2);
}

.yd-agent-context__workspace h3 {
  margin: 10px 0 4px;
  font-size: 16px;
  font-weight: 650;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.yd-agent-context__workspace-path,
.yd-agent-context__source,
.yd-agent-context__run-id {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  color: var(--color-text-2);
  white-space: nowrap;
}

.yd-agent-context__workspace-path {
  font-size: 11px;
}

.yd-agent-context__branch {
  width: fit-content;
  max-width: 100%;
  padding: 4px 7px;
  margin-top: 10px;
  color: var(--color-text-1);
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.yd-agent-context__branch span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-agent-context__description {
  margin: 5px 0 0;
  font-size: 11px;
  font-weight: 400;
  line-height: 1.7;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
}

.yd-agent-context__tabs {
  flex: 1;
  min-height: 180px;
  padding: 12px 10px 0;
  overflow: hidden;
}

.yd-agent-context__tabs :deep(.yd-agent-context__tab-list) {
  flex-shrink: 0;
  gap: 2px;
  padding: 3px;
  background: var(--color-fill-2);
  border-radius: var(--yd-control-radius);
}

.yd-agent-context__tabs :deep(.yd-agent-context__tab-list [role="tab"]) {
  padding: 7px 4px;
  font-size: 11px;
}

.yd-agent-context__tabs :deep(.yd-agent-context__tab-content) {
  flex: 1;
  min-height: 0;
  padding: 0 0 12px;
  margin-top: 8px;
  overflow: auto;
  scrollbar-width: thin;
}

.yd-agent-context__section-summary {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 6px 6px 10px;
  font-size: 10px;
  color: var(--color-text-2);
}

.yd-agent-context__locked {
  flex-shrink: 0;
}

.yd-agent-context__file-list,
.yd-agent-context__capability-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.yd-agent-context__file {
  display: flex;
  gap: 4px;
  align-items: flex-start;
  min-width: 0;
  padding: 4px 2px;
  border-radius: var(--yd-control-radius);
  transition: background var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.yd-agent-context__file:hover {
  background: var(--color-fill-1);
}

.yd-agent-context__file-checkbox {
  flex-shrink: 0;
  padding: 10px 4px;
}

.yd-agent-context__file-button {
  flex: 1;
  gap: 8px;
  align-items: flex-start;
  justify-content: flex-start;
  min-width: 0;
  height: auto;
  min-height: 42px;
  padding: 8px 5px;
  text-align: left;
  white-space: normal;
}

.yd-agent-context__file-button > :first-child {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--color-text-2);
}

.yd-agent-context__file-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.yd-agent-context__file-text strong {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  font-weight: 550;
  white-space: nowrap;
}

.yd-agent-context__file-text code {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 10px;
  font-weight: 400;
  color: var(--color-text-2);
  white-space: nowrap;
}

.yd-agent-context__file-open {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--color-text-2);
}

.yd-agent-context__skill,
.yd-agent-context__server {
  padding: 12px 7px;
  border-bottom: 1px solid var(--color-border-2);
}

.yd-agent-context__skill:last-child,
.yd-agent-context__server:last-child {
  border-bottom: 0;
}

.yd-agent-context__capability-heading {
  display: flex;
  gap: 7px;
  align-items: center;
  min-width: 0;
}

.yd-agent-context__capability-heading > :first-child {
  flex-shrink: 0;
  color: var(--color-text-2);
}

.yd-agent-context__capability-heading > strong {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  font-weight: 550;
  overflow-wrap: anywhere;
}

.yd-agent-context__source {
  width: fit-content;
  max-width: 100%;
  padding: 3px 5px;
  margin-top: 8px;
  background: var(--color-fill-1);
  border-radius: 4px;
}

.yd-agent-context__server-state {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--color-text-2);
}

.yd-agent-context__server-state[data-status="connected"],
.yd-agent-context__server-state[data-status="error"] {
  color: var(--color-text-1);
}

.yd-agent-context__server-state[data-status="error"] {
  font-weight: 600;
}

.yd-agent-context__tools {
  margin-top: 10px;
}

.yd-agent-context__tools summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 0;
  font-size: 10px;
  color: var(--color-text-2);
  cursor: pointer;
  list-style: none;
}

.yd-agent-context__tools summary::-webkit-details-marker {
  display: none;
}

.yd-agent-context__tools summary:focus-visible {
  outline: 2px solid var(--yd-emphasis);
  outline-offset: 3px;
  border-radius: 3px;
}

.yd-agent-context__tools summary > :last-child {
  transition: transform var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.yd-agent-context__tools[open] summary > :last-child {
  transform: rotate(180deg);
}

.yd-agent-context__tools ul {
  display: grid;
  gap: 6px;
  padding: 8px;
  margin: 4px 0 0;
  list-style: none;
  background: var(--color-fill-1);
  border-radius: var(--yd-control-radius);
}

.yd-agent-context__tools li {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 10px;
  color: var(--color-text-2);
}

.yd-agent-context__tools li > :first-child {
  flex-shrink: 0;
  margin-top: 2px;
}

.yd-agent-context__tools code {
  overflow-wrap: anywhere;
}

.yd-agent-context__tool-empty {
  margin: 10px 0 0;
  font-size: 10px;
  color: var(--color-text-2);
}

.yd-agent-context__empty {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  padding: 30px 16px;
  color: var(--color-text-2);
  text-align: center;
}

.yd-agent-context__empty > :first-child {
  width: 24px;
  height: 24px;
}

.yd-agent-context__empty strong {
  font-size: 12px;
  font-weight: 550;
  color: var(--color-text-1);
}

.yd-agent-context__empty p {
  margin: 0;
  font-size: 11px;
  line-height: 1.8;
}

.yd-agent-context__harness {
  display: grid;
  gap: 10px;
  background: var(--color-fill-1);
  border-top: 1px solid var(--color-border-2);
}

.yd-agent-context__harness-title {
  display: flex;
  gap: 6px;
  align-items: center;
}

.yd-agent-context__harness-title > strong {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.yd-agent-context__harness-status {
  flex-shrink: 0;
  font-size: 10px;
  color: var(--color-text-1);
}

.yd-agent-context__spin {
  animation: yd-agent-context-spin calc(1.2s * var(--yd-motion-loop-scale, 1)) linear infinite;
}

@keyframes yd-agent-context-spin {
  to { transform: rotate(360deg); }
}

.yd-agent-context__harness-phase,
.yd-agent-context__usage-label {
  display: flex;
  gap: 8px;
  justify-content: space-between;
  font-size: 10px;
  line-height: 1.6;
  color: var(--color-text-2);
}

.yd-agent-context__harness-phase > :last-child {
  flex-shrink: 0;
}

.yd-agent-context__usage-label strong {
  font-weight: 550;
  color: var(--color-text-1);
}

.yd-agent-context__progress {
  height: 5px;
  margin-top: 7px;
  background: var(--color-fill-3);
}

.yd-agent-context__progress :deep([data-slot="progress-indicator"]) {
  background: var(--yd-emphasis);
  transition-duration: var(--yd-motion-duration-normal);
}

.yd-agent-context__harness-meta {
  display: grid;
  gap: 7px;
  padding: 0;
  margin: 0;
}

.yd-agent-context__harness-meta > div {
  display: flex;
  gap: 10px;
  justify-content: space-between;
  font-size: 10px;
  line-height: 1.6;
}

.yd-agent-context__harness-meta dt {
  display: flex;
  flex-shrink: 0;
  gap: 4px;
  align-items: center;
  color: var(--color-text-2);
}

.yd-agent-context__harness-meta dd {
  min-width: 0;
  margin: 0;
  color: var(--color-text-1);
  text-align: right;
  overflow-wrap: anywhere;
}

@media (width <= 768px) {
  .yd-agent-context {
    height: auto;
    overflow: visible;
  }

  .yd-agent-context__tabs {
    min-height: 220px;
    max-height: 420px;
  }
}
</style>
