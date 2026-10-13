<script setup lang="ts">
import type { YdAgentReference, YdAgentReferenceEntry, YdAgentRenderDocument, YdAgentToolCall } from './agent-render'
import { MdPreview } from 'md-editor-v3'
import { computed, ref, useId } from 'vue'
import { YdButton, YdIcon } from '../adapters/index'
import { buildYdAgentReferenceIndex, parseYdAgentRenderDocument, renderYdAgentMarkdown, resolveYdAgentReferenceAnchor, safeYdAgentReferenceUrl, sanitizeYdAgentHtml, ydAgentReferenceAnchor } from './agent-render'
import 'md-editor-v3/lib/style.css'

const props = withDefaults(defineProps<{ document: YdAgentRenderDocument, retryDisabled?: boolean }>(), { retryDisabled: false })
const emits = defineEmits<{
  referenceSelect: [reference: YdAgentReference]
  toolSelect: [tool: YdAgentToolCall]
  toolRetry: [tool: YdAgentToolCall]
}>()
const parsed = computed(() => parseYdAgentRenderDocument(props.document))
const referenceScope = useId()
const references = computed(() => parsed.value ? buildYdAgentReferenceIndex(parsed.value) : new Map<string, YdAgentReferenceEntry>())
const expanded = ref(new Map<string, boolean>())
const statuses = {
  'queued': { label: '等待执行', icon: 'morph:clock' },
  'running': { label: '执行中', icon: 'morph:loader-circle' },
  'awaiting-approval': { label: '等待确认', icon: 'morph:shield-question' },
  'success': { label: '已完成', icon: 'morph:circle-check' },
  'error': { label: '执行失败', icon: 'morph:triangle-alert' },
  'cancelled': { label: '已取消', icon: 'morph:circle-stop' },
}

function onMarkdownClick(event: MouseEvent) {
  const anchor = (event.target as Element | null)?.closest('a')
  if (!anchor) {
    return
  }
  const href = anchor.getAttribute('href') || ''
  const id = resolveYdAgentReferenceAnchor(href, referenceScope)
  if (id !== undefined) {
    event.preventDefault()
    const entry = references.value.get(id)
    if (entry) {
      emits('referenceSelect', entry.reference)
    }
  }
  else if (!safeYdAgentReferenceUrl(href)) {
    event.preventDefault()
  }
}

function lineRange(reference: YdAgentReference): string {
  const start = reference.startLine
  const end = reference.endLine
  if (!Number.isInteger(start) || !start || start < 1) {
    return ''
  }
  return Number.isInteger(end) && end! > start ? `第 ${start}–${end} 行` : `第 ${start} 行`
}

function durationText(duration?: number): string {
  if (duration === undefined || !Number.isFinite(duration) || duration < 0) {
    return ''
  }
  return duration < 1000 ? `${Math.round(duration)} ms` : `${(duration / 1000).toFixed(1)} s`
}

function selectTool(tool: YdAgentToolCall) {
  expanded.value.set(tool.id, !expanded.value.get(tool.id))
  emits('toolSelect', tool)
}
</script>

<template>
  <section class="yd-agent-answer" aria-label="助手回答">
    <p v-if="!parsed" role="alert">
      无法显示此回答，内容格式不受支持
    </p>
    <template v-for="(block, index) in parsed?.blocks || []" :key="index">
      <div v-if="block.type === 'markdown'" class="yd-agent-answer__markdown" @click.capture="onMarkdownClick">
        <MdPreview
          :model-value="renderYdAgentMarkdown(block.text, references, referenceScope)"
          :sanitize="sanitizeYdAgentHtml"
          language="zh-CN"
          preview-theme="github"
          code-theme="github"
          no-mermaid
          no-katex
        />
      </div>

      <figure v-else-if="block.type === 'reference'" :id="ydAgentReferenceAnchor(block.reference.id, referenceScope).slice(1)" class="yd-agent-reference">
        <figcaption class="yd-agent-reference__source">
          <span class="yd-agent-reference__number">{{ references.get(block.reference.id)?.number }}</span>
          <a v-if="safeYdAgentReferenceUrl(block.reference.url)" :href="safeYdAgentReferenceUrl(block.reference.url)" target="_blank" rel="noopener noreferrer" @click="emits('referenceSelect', block.reference)">
            {{ block.reference.title }}
            <YdIcon name="morph:arrow-up-right" aria-hidden="true" />
          </a>
          <button v-else type="button" @click="emits('referenceSelect', block.reference)">
            {{ block.reference.title }}
            <YdIcon name="morph:file-text" aria-hidden="true" />
          </button>
        </figcaption>
        <blockquote class="yd-agent-reference__excerpt">
          {{ block.reference.excerpt }}
        </blockquote>
        <div v-if="block.reference.path || lineRange(block.reference)" class="yd-agent-reference__location">
          <span v-if="block.reference.path">{{ block.reference.path }}</span>
          <span v-if="lineRange(block.reference)">{{ lineRange(block.reference) }}</span>
        </div>
      </figure>

      <article v-else class="yd-agent-tool" :data-status="block.tool.status" :aria-label="`${block.tool.label || block.tool.name}：${statuses[block.tool.status].label}`">
        <button class="yd-agent-tool__head" type="button" :aria-expanded="!!expanded.get(block.tool.id)" @click="selectTool(block.tool)">
          <YdIcon :name="statuses[block.tool.status].icon" class="yd-agent-tool__status-icon" :class="{ 'is-running': block.tool.status === 'running' }" aria-hidden="true" />
          <span class="yd-agent-tool__title">
            <strong>{{ block.tool.label || block.tool.name }}</strong>
            <small v-if="block.tool.label">{{ block.tool.name }}</small>
          </span>
          <span class="yd-agent-tool__status">{{ statuses[block.tool.status].label }}</span>
          <span v-if="durationText(block.tool.durationMs)" class="yd-agent-tool__duration">{{ durationText(block.tool.durationMs) }}</span>
          <YdIcon :name="expanded.get(block.tool.id) ? 'morph:chevron-up' : 'morph:chevron-down'" aria-hidden="true" />
        </button>
        <p v-if="block.tool.description" class="yd-agent-tool__description">
          {{ block.tool.description }}
        </p>
        <p v-if="block.tool.status === 'error' && block.tool.error" class="yd-agent-tool__failure">
          {{ block.tool.error }}
        </p>
        <div v-if="expanded.get(block.tool.id)" class="yd-agent-tool__details">
          <div v-if="block.tool.input !== undefined">
            <span>输入参数</span><pre>{{ block.tool.input }}</pre>
          </div>
          <div v-if="block.tool.output !== undefined">
            <span>工具输出</span><pre>{{ block.tool.output }}</pre>
          </div>
          <div v-if="block.tool.error !== undefined">
            <span>错误详情</span><pre>{{ block.tool.error }}</pre>
          </div>
          <p v-if="block.tool.input === undefined && block.tool.output === undefined && block.tool.error === undefined">
            尚无工具详情
          </p>
        </div>
        <div v-if="block.tool.status === 'error' && block.tool.retry" class="yd-agent-tool__actions">
          <YdButton variant="outline" size="sm" :disabled="retryDisabled" @click="emits('toolRetry', block.tool)">
            <YdIcon name="morph:rotate-cw" aria-hidden="true" />重试工具
          </YdButton>
        </div>
      </article>
    </template>
  </section>
</template>

<style scoped>
.yd-agent-answer {
  display: grid;
  gap: var(--yd-content-gap, 16px);
  min-width: 0;
  font-size: var(--yd-control-font-size, 14px);
  color: var(--color-text-1);
}

.yd-agent-answer__markdown {
  min-width: 0;
}

.yd-agent-answer__markdown :deep(.md-editor) {
  --md-bk-color: transparent;
  --md-color: var(--color-text-1);
  --md-border-color: var(--color-border-2);
  --md-theme-color: var(--color-text-1);
  --md-theme-hover-color: var(--color-text-2);

  background: transparent;
}

.yd-agent-answer__markdown :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.yd-agent-answer__markdown :deep(.md-editor-preview) {
  font-size: var(--yd-control-font-size, 14px);
  color: var(--color-text-1);
}

.yd-agent-answer__markdown :deep(.md-editor-preview a) {
  color: var(--color-text-1);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.yd-agent-answer__markdown :deep(.md-editor-preview pre),
.yd-agent-answer__markdown :deep(.md-editor-preview code) {
  color: var(--color-text-1);
  background: var(--color-fill-1);
}

.yd-agent-answer__markdown :deep(.md-editor-preview blockquote) {
  color: var(--color-text-2);
  border-color: var(--color-border-2);
}

.yd-agent-answer__markdown :deep(.md-editor-preview code span) {
  color: inherit;
}

.yd-agent-answer__markdown :deep(.md-editor-code-head) {
  color: var(--color-text-2);
  background: var(--color-fill-2);
}

.yd-agent-reference {
  padding: 14px 16px;
  margin: 0;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius, 18px);
}

.yd-agent-reference__source {
  display: flex;
  gap: 8px;
  align-items: center;
}

.yd-agent-reference__number {
  display: grid;
  flex: none;
  place-items: center;
  width: 24px;
  height: 24px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-1);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 12px);
}

.yd-agent-reference__source a,
.yd-agent-reference__source button {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
  padding: 0;
  font: inherit;
  font-weight: 600;
  color: var(--color-text-1);
  text-align: left;
  overflow-wrap: anywhere;
  text-decoration: none;
  cursor: pointer;
  background: none;
  border: 0;
}

.yd-agent-reference__source a:hover,
.yd-agent-reference__source button:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.yd-agent-reference__excerpt {
  padding-left: 12px;
  margin: 12px 0 0;
  line-height: 1.7;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  border-left: 2px solid var(--color-border-3, var(--color-border-2));
}

.yd-agent-reference__location {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
}

.yd-agent-tool {
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius, 18px);
}

.yd-agent-tool__head {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 12px 14px;
  font: inherit;
  color: var(--color-text-1);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  transition: background var(--yd-motion-duration-fast, 140ms) var(--yd-motion-ease, ease);
}

.yd-agent-tool__head:hover {
  background: var(--color-fill-1);
}

.yd-agent-tool__status-icon {
  flex: none;
  width: 18px;
  height: 18px;
}

.yd-agent-tool__status-icon.is-running {
  animation: yd-agent-tool-spin calc(1.2s * var(--yd-motion-loop-scale, 1)) linear infinite;
}

.yd-agent-tool__title {
  display: grid;
  gap: 3px;
  min-width: 0;
  margin-right: auto;
  overflow-wrap: anywhere;
}

.yd-agent-tool__title strong {
  font-weight: 600;
}

.yd-agent-tool__title small,
.yd-agent-tool__duration {
  font-size: 12px;
  color: var(--color-text-2);
}

.yd-agent-tool__status {
  flex: none;
  padding: 3px 7px;
  font-size: 12px;
  color: var(--color-text-1);
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 12px);
}

.yd-agent-tool[data-status="error"] .yd-agent-tool__status,
.yd-agent-tool[data-status="awaiting-approval"] .yd-agent-tool__status {
  font-weight: 600;
  border-style: dashed;
}

.yd-agent-tool[data-status="cancelled"] .yd-agent-tool__status {
  color: var(--color-text-2);
}

.yd-agent-tool__description,
.yd-agent-tool__failure {
  padding: 0 14px 12px 42px;
  margin: 0;
  line-height: 1.6;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
}

.yd-agent-tool__failure {
  color: var(--color-text-1);
}

.yd-agent-tool__details {
  display: grid;
  gap: 12px;
  padding: 14px;
  font-size: 12px;
  border-top: 1px solid var(--color-border-2);
}

.yd-agent-tool__details span {
  color: var(--color-text-2);
}

.yd-agent-tool__details pre {
  padding: 12px;
  margin: 6px 0 0;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 12px);
}

.yd-agent-tool__details p {
  margin: 0;
  color: var(--color-text-2);
}

.yd-agent-tool__actions {
  display: flex;
  justify-content: flex-end;
  padding: 0 14px 12px;
}

.yd-agent-reference__source a:focus-visible,
.yd-agent-reference__source button:focus-visible,
.yd-agent-tool__head:focus-visible {
  outline: 2px solid var(--color-text-3);
  outline-offset: 3px;
}

@keyframes yd-agent-tool-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (width <= 768px) {
  .yd-agent-tool__head {
    flex-wrap: wrap;
  }

  .yd-agent-tool__title {
    flex: 1;
  }

  .yd-agent-tool__duration {
    display: none;
  }
}
</style>
