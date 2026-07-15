<script setup lang="ts">
import type { AgentDebugStatus, AgentNodeData } from './types'
import { Handle, Position } from '@vue-flow/core'
import { agentModelKind, agentSourceHandles, isAgentChatModelNode } from '../config/agent-node-data'

const props = defineProps<{
  id: string
  data: AgentNodeData
  selected?: boolean
  connectable?: boolean | string | number
  debugStatus?: AgentDebugStatus
  readonly?: boolean
}>()

const emit = defineEmits<{
  delete: [id: string]
}>()

const nodeStyle = computed(() => ({
  '--node-color': props.data.color || '#2563eb',
}))
const sourceHandles = computed(() => agentSourceHandles(props.data.kind))
const isModelNode = computed(() => Boolean(agentModelKind(props.data.kind)))
const isToolEnabledModel = computed(() => isAgentChatModelNode(props.data.kind))
const toolModeLabel = computed(() => ({
  NONE: '禁用',
  AUTO: '自主',
  REQUIRED: '必须',
}[props.data.toolMode] || '禁用'))

function handleStyle(handle: string) {
  if (props.data.kind !== 'condition') {
    return undefined
  }
  return { top: handle === 'true' ? '58%' : '78%' }
}
</script>

<template>
  <div class="workflow-node" :class="[{ 'is-selected': selected }, debugStatus ? `debug-${debugStatus.toLowerCase()}` : '']" :style="nodeStyle">
    <Handle v-if="data.kind !== 'start' && data.kind !== 'input'" id="target" type="target" :position="Position.Left" :connectable="connectable" />

    <button v-if="!readonly" class="node-delete nodrag nowheel" type="button" title="删除节点" aria-label="删除节点" @click.stop="emit('delete', id)">
      <FaIcon name="i-ri:close-line" />
    </button>

    <span v-if="debugStatus" class="node-debug-status">
      <FaIcon :name="debugStatus === 'RUNNING' ? 'i-ri:loader-4-line' : debugStatus === 'COMPLETED' ? 'i-ri:check-line' : debugStatus === 'FAILED' ? 'i-ri:close-line' : 'i-ri:skip-forward-line'" />
      {{ debugStatus === 'RUNNING' ? '运行中' : debugStatus === 'COMPLETED' ? '已完成' : debugStatus === 'FAILED' ? '失败' : '已跳过' }}
    </span>

    <div class="node-heading">
      <span class="node-icon"><FaIcon :name="data.icon" /></span>
      <span class="node-title">
        <strong>{{ data.title || '未命名节点' }}</strong>
        <small>{{ data.label }}</small>
      </span>
      <FaIcon class="node-more" name="i-ri:more-2-fill" />
    </div>

    <p>{{ data.description || '选择节点后在右侧完善配置' }}</p>

    <div v-if="data.kind === 'tool'" class="node-tool legacy-tool">
      <FaIcon name="i-ri:tools-line" />
      <span>兼容节点 · {{ data.toolCode || '尚未选择工具' }}</span>
    </div>
    <div v-else-if="isModelNode" class="node-model">
      <FaIcon :name="data.vision ? 'i-ri:image-circle-line' : 'i-ri:chat-3-line'" />
      <span>{{ data.modelCode ? `${data.providerCode} / ${data.modelCode}` : '尚未选择模型' }}</span>
    </div>
    <div v-if="isToolEnabledModel" class="node-badges">
      <span><FaIcon name="i-ri:tools-line" /> 工具 {{ data.toolCodes.length }} · {{ toolModeLabel }}</span>
      <span v-if="data.kind === 'vision'"><FaIcon name="i-ri:image-2-line" /> 图片变量</span>
      <span v-else-if="data.vision"><FaIcon name="i-ri:eye-line" /> Vision 可用</span>
    </div>

    <div class="node-io">
      <span><i class="input-dot" />输入 <b>{{ data.inputName || 'any' }}</b></span>
      <span>输出 <b>{{ data.outputName || 'any' }}</b><i class="output-dot" /></span>
    </div>

    <template v-for="handle in sourceHandles" :key="handle">
      <span v-if="data.kind === 'condition'" class="branch-label" :class="handle" :style="handleStyle(handle)">
        {{ handle === 'true' ? '真' : '假' }}
      </span>
      <Handle :id="handle" type="source" :position="Position.Right" :connectable="connectable" :style="handleStyle(handle)" />
    </template>
  </div>
</template>

<style scoped>
.workflow-node { position: relative; width: 230px; overflow: visible; border: 1px solid var(--color-border-2); border-top: 3px solid var(--node-color); border-radius: 7px; background: var(--color-bg-1); box-shadow: 0 6px 20px rgb(15 23 42 / 10%); transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s; }
.workflow-node:hover { box-shadow: 0 9px 24px rgb(15 23 42 / 14%); transform: translateY(-1px); }
.workflow-node.is-selected { border-color: var(--node-color); box-shadow: 0 0 0 3px color-mix(in srgb, var(--node-color), transparent 78%), 0 9px 24px rgb(15 23 42 / 14%); }
.workflow-node.debug-running { border-color: rgb(var(--primary-6)); box-shadow: 0 0 0 4px rgb(var(--primary-2)), 0 10px 28px rgb(var(--primary-6) / 24%); animation: debug-pulse 1.4s ease-in-out infinite; }
.workflow-node.debug-completed { border-color: rgb(var(--success-6)); box-shadow: 0 0 0 3px rgb(var(--success-2)), 0 7px 20px rgb(15 23 42 / 10%); }
.workflow-node.debug-failed { border-color: rgb(var(--danger-6)); box-shadow: 0 0 0 3px rgb(var(--danger-2)), 0 7px 20px rgb(var(--danger-6) / 18%); }
.workflow-node.debug-skipped { opacity: 0.68; }
.node-debug-status { position: absolute; top: -26px; left: 0; display: flex; height: 20px; align-items: center; gap: 4px; padding: 0 6px; border-radius: 4px; color: var(--color-text-2); background: var(--color-bg-1); box-shadow: 0 3px 10px rgb(15 23 42 / 12%); font-size: 8px; white-space: nowrap; }
.debug-running .node-debug-status { color: rgb(var(--primary-6)); }
.debug-completed .node-debug-status { color: rgb(var(--success-6)); }
.debug-failed .node-debug-status { color: rgb(var(--danger-6)); }
.debug-running .node-debug-status > :first-child { animation: debug-spin 1s linear infinite; }
.node-heading { display: grid; grid-template-columns: 34px minmax(0, 1fr) 18px; align-items: center; gap: 9px; padding: 11px 12px 8px; }
.node-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 6px; color: var(--node-color); background: color-mix(in srgb, var(--node-color), transparent 88%); font-size: 17px; }
.node-title { display: grid; min-width: 0; gap: 2px; }
.node-title strong { overflow: hidden; color: var(--color-text-1); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.node-title small { color: var(--color-text-3); font-size: 9px; }
.node-more { color: var(--color-text-4); }
.workflow-node p { min-height: 31px; margin: 0; padding: 0 12px 9px; color: var(--color-text-3); font-size: 10px; line-height: 1.55; }
.node-tool, .node-model { display: flex; min-width: 0; align-items: center; gap: 5px; margin: 0 12px 8px; padding: 5px 7px; border-radius: 4px; color: var(--node-color); background: color-mix(in srgb, var(--node-color), transparent 91%); font-size: 9px; }
.node-tool span, .node-model span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.legacy-tool { color: rgb(var(--warning-6)); background: rgb(var(--warning-1)); }
.node-badges { display: flex; min-width: 0; flex-wrap: wrap; gap: 4px; margin: 0 12px 9px; }
.node-badges span { display: inline-flex; max-width: 100%; align-items: center; gap: 3px; padding: 3px 5px; border-radius: 3px; color: var(--color-text-2); background: var(--color-fill-2); font-size: 8px; white-space: nowrap; }
.node-io { display: flex; min-height: 33px; align-items: center; justify-content: space-between; padding: 0 12px; border-top: 1px solid var(--color-border-2); color: var(--color-text-3); font-size: 9px; }
.node-io span { display: flex; align-items: center; gap: 4px; }
.node-io b { max-width: 70px; overflow: hidden; color: var(--color-text-2); font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.input-dot, .output-dot { width: 5px; height: 5px; border-radius: 50%; background: var(--color-text-4); }
.output-dot { background: var(--node-color); }
.node-delete { position: absolute; top: -12px; right: -10px; z-index: 4; display: grid; width: 24px; height: 24px; place-items: center; border: 1px solid rgb(var(--danger-3)); border-radius: 50%; color: rgb(var(--danger-6)); background: var(--color-bg-1); box-shadow: 0 4px 12px rgb(15 23 42 / 14%); cursor: pointer; opacity: 0; transition: opacity 0.15s, transform 0.15s; transform: scale(0.85); }
.workflow-node:hover .node-delete, .workflow-node.is-selected .node-delete { opacity: 1; transform: scale(1); }
.workflow-node :deep(.vue-flow__handle) { width: 11px; height: 11px; border: 2px solid var(--color-bg-1); background: var(--node-color); box-shadow: 0 0 0 1px color-mix(in srgb, var(--node-color), transparent 45%); }
.workflow-node :deep(.vue-flow__handle-left) { left: -6px; }
.workflow-node :deep(.vue-flow__handle-right) { right: -6px; }
.branch-label { position: absolute; right: 10px; z-index: 2; padding: 1px 4px; border-radius: 3px; font-size: 8px; line-height: 14px; transform: translateY(-50%); }
.branch-label.true { color: rgb(var(--success-7)); background: rgb(var(--success-1)); }
.branch-label.false { color: rgb(var(--danger-7)); background: rgb(var(--danger-1)); }
@keyframes debug-pulse { 50% { box-shadow: 0 0 0 6px rgb(var(--primary-2)), 0 10px 28px rgb(var(--primary-6) / 18%); } }
@keyframes debug-spin { to { transform: rotate(360deg); } }
</style>
