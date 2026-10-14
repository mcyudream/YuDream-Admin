<script setup lang="ts">
import type { YdAgentRenderDocument, YdChatActivity, YdChatToolEvent } from '@yudream/components'
import { YdAgentAnswer, YdAlert, YdButton, YdCard, YdChatReasoning, YdCode, YdLoading, YdThoughtChain } from '@yudream/components'
import { computed, ref, watch } from 'vue'
import { getAiRun, getAiSystemRun } from '@/api/modules/platform-ai-runs'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { useAiSystemRunsStore } from '@/store/modules/ai-system-runs'
import { isActiveAiRun } from '@/utils/ai-run-stream'
import { readCompletedAiJsonField } from '@/utils/ai-structured-stream'

const props = withDefaults(defineProps<{ runId: string, scope?: 'personal' | 'system', canCancel?: boolean, canApprove?: boolean }>(), { scope: 'personal', canCancel: true, canApprove: true })
const runtime = props.scope === 'system' ? useAiSystemRunsStore() : useAiAssistantStore()
const loading = ref(false)
const error = ref('')
const expandedGenerations = ref<string[]>([])
const run = computed(() => runtime.runs[props.runId])
const active = computed(() => !!run.value && isActiveAiRun(run.value.status))
const status = computed(() => ({ RUNNING: '执行中', QUEUED: '排队中', WAITING: '等待输入', COMPLETED: '已完成', FAILED: '执行失败', CANCELLED: '已停止' })[run.value?.status || ''] || run.value?.status)
const failure = computed(() => error.value || run.value?.error || (active.value && runtime.connections[props.runId] === 'disconnected' ? '进度连接断开，任务仍在后端运行' : ''))
const activities = computed(() => generationActivities(run.value?.activities))
function generationActivities(items: YdChatActivity[] = []) {
  return items.map((activity, index) => ({ key: `${activity.phase || index}`, title: activity.title || activity.phase || '执行进度', description: activity.content, status: activity.status === 'error' || (activity.status === 'running' && !active.value && run.value?.status === 'FAILED') ? 'error' as const : activity.status === 'running' && active.value ? 'running' as const : activity.status === 'cancelled' || activity.status === 'waiting' || activity.status === 'pending' || activity.status === 'queued' || (activity.status === 'running' && run.value?.status === 'CANCELLED') ? 'pending' as const : 'success' as const }))
}
function generationTools(items: YdChatToolEvent[] = []): YdAgentRenderDocument {
  return { version: 1, blocks: items.map((tool, index) => ({ type: 'tool' as const, tool: { id: tool.toolCallId || `${props.runId}:generation-tool:${index}`, name: tool.toolName || '工具调用', status: tool.status === 'executing' ? 'running' as const : tool.status === 'error' ? 'error' as const : tool.status === 'cancelled' ? 'cancelled' as const : 'success' as const, description: tool.message, output: tool.payload ? JSON.stringify(tool.payload, null, 2) : undefined } })) }
}
const answerDocument = computed<YdAgentRenderDocument>(() => {
  const source = run.value?.structuredContent || ''
  const structured = ['topic', 'rationale', 'summary'].map(key => readCompletedAiJsonField(source, key)).filter((value): value is string => typeof value === 'string')
  const result = run.value?.result
  const text = structured.length ? structured.join('\n\n') : typeof result?.content === 'string' ? result.content : run.value?.content || ''
  return { version: 1, blocks: [
    ...(text ? [{ type: 'markdown' as const, text }] : []),
    ...(run.value?.tools || []).map((tool, index) => ({ type: 'tool' as const, tool: {
      id: tool.toolCallId || `${props.runId}:tool:${index}`,
      name: tool.toolName || '工具调用',
      status: tool.status === 'executing' ? 'running' as const : tool.status === 'error' ? 'error' as const : tool.status === 'cancelled' ? 'cancelled' as const : 'success' as const,
      description: tool.message,
      output: tool.payload ? JSON.stringify(tool.payload, null, 2) : undefined,
    } })),
  ] }
})

async function restore() {
  const id = props.runId
  const epoch = runtime.lifecycleVersion()
  loading.value = true
  error.value = ''
  try {
    if (runtime.runs[id]) {
      await runtime.reconnect(id)
    }
    else {
      const response = await (props.scope === 'system' ? getAiSystemRun(id) : getAiRun(id))
      if (props.runId === id) {
        runtime.attachRun(response.data, epoch)
      }
    }
  }
  catch (cause) {
    if (props.runId === id) {
      error.value = cause instanceof Error ? cause.message : '载入 AI 运行失败'
    }
  }
  finally {
    if (props.runId === id) {
      loading.value = false
    }
  }
}

async function stop() {
  if (!props.canCancel) {
    return
  }
  try {
    await runtime.stop(props.runId)
  }
  catch (cause) {
    error.value = cause instanceof Error ? cause.message : '停止任务失败'
  }
}

async function resume() {
  if (!('resume' in runtime)) {
    return
  }
  try {
    await runtime.resume(props.runId)
  }
  catch (cause) {
    error.value = cause instanceof Error ? cause.message : '接续任务失败'
  }
}

async function decide(requestId: string, approved: boolean) {
  if (!props.canApprove) {
    return
  }
  try {
    await runtime.respondInteraction(props.runId, requestId, approved)
  }
  catch (cause) {
    error.value = cause instanceof Error ? cause.message : '回应 Harness 审批失败'
  }
}

function toggleGeneration(callId: string) {
  expandedGenerations.value = expandedGenerations.value.includes(callId) ? expandedGenerations.value.filter(id => id !== callId) : [...expandedGenerations.value, callId]
}

watch(() => props.runId, () => {
  if (props.runId) {
    void restore()
  }
}, { immediate: true })
</script>

<template>
  <section class="yd-ai-run-progress" aria-label="AI 运行进度">
    <YdLoading v-if="loading && !run" />
    <YdAlert v-if="failure" variant="destructive" :title="failure" />
    <div v-if="run" class="yd-ai-run-progress__status" role="status">
      <span class="yd-ai-run-progress__summary">{{ status }} · {{ run.module }}</span>
      <YdButton v-if="active && canCancel" variant="outline" size="sm" @click="stop">
        停止运行
      </YdButton>
      <YdButton v-if="scope === 'personal' && run.resumeSupported && !active" size="sm" @click="resume">
        从 Harness 检查点接续
      </YdButton>
      <YdButton variant="ghost" size="sm" @click="restore">
        恢复后端进度
      </YdButton>
    </div>
    <YdChatReasoning v-if="run?.reasoning" :content="run.reasoning" :pending="active" />
    <YdThoughtChain v-if="activities.length" :items="activities" title="实际执行进度" :default-expanded="false" />
    <YdCard v-for="interaction in run?.pendingInteractions" :key="interaction.requestId">
      <strong>{{ interaction.kind === 'confirmation' ? '等待批准工具' : '等待专属页面交互' }} · {{ interaction.toolName }}</strong>
      <YdCode :code="JSON.stringify(interaction.arguments, null, 2)" language="json" :line-numbers="false" />
      <div v-if="interaction.kind === 'confirmation' && canApprove" class="yd-ai-run-progress__status">
        <YdButton variant="outline" @click="decide(interaction.requestId, false)">
          拒绝本次
        </YdButton>
        <YdButton @click="decide(interaction.requestId, true)">
          批准本次
        </YdButton>
      </div>
    </YdCard>
    <YdCard v-for="generation in run?.generations" :key="generation.callId">
      <strong>{{ generation.providerCode }} / {{ generation.modelCode }} · 后台模型阶段</strong>
      <YdChatReasoning v-if="generation.reasoning" :content="generation.reasoning" :pending="active" />
      <YdThoughtChain v-if="generation.activities?.length" :items="generationActivities(generation.activities)" title="模型阶段执行进度" :default-expanded="false" />
      <template v-if="generation.structured">
        <YdButton variant="ghost" size="sm" @click="toggleGeneration(generation.callId)">
          结构化输出 · 已收到 {{ generation.content.length }} 字符
        </YdButton>
        <YdCode v-if="expandedGenerations.includes(generation.callId)" :code="generation.content" language="json" :line-numbers="false" />
      </template>
      <YdAgentAnswer v-else :document="{ version: 1, blocks: [{ type: 'markdown', text: generation.content }] }" />
      <YdAgentAnswer v-if="generation.tools?.length" :document="generationTools(generation.tools)" />
    </YdCard>
    <YdAgentAnswer v-if="answerDocument.blocks.length" :document="answerDocument" />
    <p v-if="run" class="yd-ai-run-progress__note">
      运行记录 {{ run.id }} · 关闭此面板后任务继续执行
    </p>
  </section>
</template>

<style scoped>
.yd-ai-run-progress {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.yd-ai-run-progress__status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-2);
}

.yd-ai-run-progress__status > span {
  margin-right: auto;
}

.yd-ai-run-progress__summary {
  min-width: 0;
  overflow-wrap: anywhere;
}

.yd-ai-run-progress__note {
  font-size: 11px;
  color: var(--color-text-3);
  overflow-wrap: anywhere;
}
</style>
