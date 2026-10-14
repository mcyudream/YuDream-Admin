<script setup lang="ts">
import type { WikiLintIssue, WikiLintReport } from '@/api/modules/platform-wiki'
import { YdAgentAnswer, YdAlert, YdChatReasoning, YdThoughtChain } from '@yudream/components'
import { computed, inject } from 'vue'
import { lintWiki } from '@/api/modules/platform-wiki'
import { useAiRunTask } from '@/composables/use-ai-run-task'
import { readCompletedAiJsonField, readCompletedAiJsonItems } from '@/utils/ai-structured-stream'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const task = useAiRunTask(() => store.spaceId.value ? `wiki-lint:${store.spaceId.value}` : undefined)
const { run: aiRun, running, activities, failure } = task

function projectIssue(value: unknown): WikiLintIssue | undefined {
  if (!value || typeof value !== 'object') {
    return undefined
  }
  const candidate = value as Record<string, unknown>
  if (typeof candidate.title !== 'string' || typeof candidate.description !== 'string' || typeof candidate.category !== 'string' || typeof candidate.severity !== 'string') {
    return undefined
  }
  return { title: candidate.title, description: candidate.description, category: candidate.category, severity: candidate.severity, pageTitles: Array.isArray(candidate.pageTitles) ? candidate.pageTitles.filter((title): title is string => typeof title === 'string') : [], suggestedAction: typeof candidate.suggestedAction === 'string' ? candidate.suggestedAction : '', searchQueries: Array.isArray(candidate.searchQueries) ? candidate.searchQueries.filter((query): query is string => typeof query === 'string') : [] }
}

const report = computed<WikiLintReport | null>(() => {
  const result = aiRun.value?.result
  const source = aiRun.value?.structuredContent || ''
  const summary = aiRun.value?.status === 'COMPLETED' ? result?.summary : readCompletedAiJsonField(source, 'summary')
  const generatedAt = aiRun.value?.status === 'COMPLETED' ? result?.generatedAt : readCompletedAiJsonField(source, 'generatedAt')
  const values = aiRun.value?.status === 'COMPLETED' && Array.isArray(result?.issues) ? result.issues : readCompletedAiJsonItems(source, 'issues')
  const issues = values.flatMap((value) => {
    const projected = projectIssue(value)
    return projected ? [projected] : []
  })
  return typeof summary === 'string' || issues.length ? { summary: typeof summary === 'string' ? summary : '', generatedAt: typeof generatedAt === 'string' ? generatedAt : '', issues } : null
})
const summaryDocument = computed(() => ({ version: 1 as const, blocks: [{ type: 'markdown' as const, text: report.value?.summary || '' }] }))

const severityMeta: Record<string, { label: string, variant: 'destructive' | 'outline' | 'secondary', icon: string }> = {
  error: { label: '错误', variant: 'destructive', icon: 'morph:circle-x' },
  warn: { label: '警告', variant: 'outline', icon: 'morph:triangle-alert' },
  info: { label: '提示', variant: 'secondary', icon: 'morph:info' },
}

async function run() {
  if (!store.spaceId.value) {
    return
  }
  await task.start(() => lintWiki(store.spaceId.value))
}

function meta(severity: string) {
  return severityMeta[severity] || severityMeta.info
}
</script>

<template>
  <div class="lint-panel">
    <YdScrollArea class="lint-scroll">
      <div class="lint-inner">
        <YdCard class="lint-head-card">
          <div class="lint-head">
            <div>
              <strong>知识库 Lint</strong>
              <p>检查孤立页面、断链、覆盖缺口与结构问题</p>
            </div>
            <YdButton :loading="running" @click="run">
              <YdIcon name="morph:shield-check" /> 运行 Lint
            </YdButton>
          </div>
          <div v-if="report" class="lint-summary">
            <YdAgentAnswer :document="summaryDocument" />
            <span v-if="report.generatedAt" class="lint-summary__time">{{ new Date(report.generatedAt).toLocaleString('zh-CN') }}</span>
          </div>
        </YdCard>

        <YdAlert v-if="failure" variant="destructive" :title="failure">
          <YdButton variant="outline" size="sm" @click="task.reconnect">
            恢复后端进度
          </YdButton>
        </YdAlert>
        <YdCard v-if="aiRun">
          <p>{{ running ? 'Lint 正在运行，切换页面后任务继续执行' : aiRun.status === 'COMPLETED' ? 'Lint 已完成' : aiRun.status === 'CANCELLED' ? 'Lint 已停止' : 'Lint 运行失败' }}</p>
          <YdChatReasoning v-if="aiRun.reasoning" :content="aiRun.reasoning" :pending="running" />
          <YdThoughtChain v-if="activities.length" :items="activities" title="知识库检查进度" :default-expanded="false" />
          <YdButton v-if="running" variant="outline" size="sm" @click="task.stop">
            停止检查
          </YdButton>
        </YdCard>

        <div v-if="!report && !running" class="lint-empty">
          <YdIcon name="morph:shield-check" />
          <strong>还没有 Lint 报告</strong>
          <p>点击「运行 Lint」对当前知识库做一次全面体检</p>
        </div>

        <template v-else-if="report">
          <div v-if="!running && aiRun?.status === 'COMPLETED' && !report.issues.length" class="lint-empty">
            <YdIcon name="morph:circle-check" />
            <strong>未发现问题</strong>
            <p>知识库结构健康</p>
          </div>
          <YdCard v-for="(issue, index) in report.issues" :key="`${issue.category}-${index}`" class="lint-issue">
            <div class="lint-issue__head">
              <YdTag :variant="meta(issue.severity).variant">
                <YdIcon :name="meta(issue.severity).icon" /> {{ meta(issue.severity).label }}
              </YdTag>
              <YdTag variant="secondary">
                {{ issue.category }}
              </YdTag>
              <strong>{{ issue.title }}</strong>
            </div>
            <p class="lint-issue__desc">
              {{ issue.description }}
            </p>
            <div v-if="issue.pageTitles?.length" class="lint-issue__pages">
              <YdButton
                v-for="title in issue.pageTitles"
                :key="title"
                variant="ghost"
                type="button"
                class="lint-issue__page"
                @click="store.openPage({ title })"
              >
                <YdIcon name="morph:file-text" /> {{ title }}
              </YdButton>
            </div>
            <p v-if="issue.suggestedAction" class="lint-issue__action">
              <YdIcon name="morph:lightbulb" /> 建议：{{ issue.suggestedAction }}
            </p>
          </YdCard>
        </template>
      </div>
    </YdScrollArea>
  </div>
</template>

<style scoped>
.lint-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.lint-scroll {
  height: 100%;
}

.lint-inner {
  display: grid;
  gap: 12px;
  max-width: 960px;
  padding: 16px;
  margin: 0 auto;
}

.lint-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.lint-head strong {
  font-size: 15px;
}

.lint-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.lint-summary {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 10px 12px;
  margin: 14px 0 0;
  font-size: 13px;
  background: var(--color-fill-2);
  border-radius: 9px;
}

.lint-summary__time {
  flex-shrink: 0;
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-3);
}

.lint-empty {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 50px 0;
  color: var(--color-text-3);
}

.lint-empty :deep(svg) {
  font-size: 36px;
  color: var(--color-text-2);
}

.lint-empty strong {
  color: var(--color-text-1);
}

.lint-empty p {
  margin: 0;
  font-size: 12px;
}

.lint-issue__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.lint-issue__severity {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
}

.lint-issue__desc {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
}

.lint-issue__pages {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.lint-issue__page {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 3px 10px;
  font: inherit;
  font-size: 12px;
  color: var(--color-text-2);
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
}

.lint-issue__page:hover {
  background: var(--color-fill-2);
  border-color: var(--color-border-3);
}

.lint-issue__action {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
