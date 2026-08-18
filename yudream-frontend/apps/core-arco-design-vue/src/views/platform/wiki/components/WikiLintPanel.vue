<script setup lang="ts">
import type { WikiLintReport } from '@/api/modules/platform-wiki'
import { inject, ref } from 'vue'
import { lintWiki } from '@/api/modules/platform-wiki'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useFaToast()

const report = ref<WikiLintReport | null>(null)
const running = ref(false)

const severityMeta: Record<string, { label: string, color: string, icon: string }> = {
  error: { label: '错误', color: '#ef4444', icon: 'i-ri:close-circle-line' },
  warn: { label: '警告', color: '#f59e0b', icon: 'i-ri:error-warning-line' },
  info: { label: '提示', color: '#0ea5e9', icon: 'i-ri:information-line' },
}

async function run() {
  if (!store.spaceId.value) {
    return
  }
  running.value = true
  try {
    const res = await lintWiki(store.spaceId.value)
    report.value = res.data || null
    if (report.value && !report.value.issues.length) {
      toast.success('Lint 完成，未发现问题')
    }
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : 'Lint 运行失败')
  }
  finally {
    running.value = false
  }
}

function meta(severity: string) {
  return severityMeta[severity] || severityMeta.info
}
</script>

<template>
  <div class="lint-panel">
    <FaScrollArea class="lint-scroll">
      <div class="lint-inner">
        <FaCard class="lint-head-card">
          <div class="lint-head">
            <div>
              <strong>知识库 Lint</strong>
              <p>检查孤立页面、断链、覆盖缺口与结构问题</p>
            </div>
            <FaButton :loading="running" @click="run">
              <FaIcon name="i-ri:shield-check-line" /> 运行 Lint
            </FaButton>
          </div>
          <p v-if="report" class="lint-summary">
            <FaIcon name="i-ri:file-list-3-line" /> {{ report.summary }}
            <span class="lint-summary__time">{{ new Date(report.generatedAt).toLocaleString('zh-CN') }}</span>
          </p>
        </FaCard>

        <div v-if="!report && !running" class="lint-empty">
          <FaIcon name="i-ri:shield-check-line" />
          <strong>还没有 Lint 报告</strong>
          <p>点击「运行 Lint」对当前知识库做一次全面体检</p>
        </div>

        <template v-else-if="report">
          <div v-if="!report.issues.length" class="lint-empty">
            <FaIcon name="i-ri:checkbox-circle-line" />
            <strong>未发现问题</strong>
            <p>知识库结构健康</p>
          </div>
          <FaCard v-for="(issue, index) in report.issues" :key="`${issue.category}-${index}`" class="lint-issue">
            <div class="lint-issue__head">
              <span class="lint-issue__severity" :style="{ color: meta(issue.severity).color, background: `${meta(issue.severity).color}1a` }">
                <FaIcon :name="meta(issue.severity).icon" /> {{ meta(issue.severity).label }}
              </span>
              <FaTag variant="secondary">{{ issue.category }}</FaTag>
              <strong>{{ issue.title }}</strong>
            </div>
            <p class="lint-issue__desc">{{ issue.description }}</p>
            <div v-if="issue.pageTitles?.length" class="lint-issue__pages">
              <button
                v-for="title in issue.pageTitles"
                :key="title"
                type="button"
                class="lint-issue__page"
                @click="store.openPage({ title })"
              >
                <FaIcon name="i-ri:file-text-line" /> {{ title }}
              </button>
            </div>
            <p v-if="issue.suggestedAction" class="lint-issue__action">
              <FaIcon name="i-ri:lightbulb-line" /> 建议：{{ issue.suggestedAction }}
            </p>
          </FaCard>
        </template>
      </div>
    </FaScrollArea>
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
  margin: 0 auto;
  padding: 16px;
}

.lint-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.lint-head strong {
  font-size: 15px;
}

.lint-head p {
  margin: 4px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
}

.lint-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 14px 0 0;
  padding: 10px 12px;
  border-radius: 9px;
  background: var(--color-fill-2);
  font-size: 13px;
}

.lint-summary__time {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 11px;
}

.lint-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 50px 0;
  color: var(--color-text-3);
}

.lint-empty :deep(svg) {
  color: rgb(var(--primary-6));
  font-size: 36px;
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
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.lint-issue__severity {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
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
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  background: transparent;
  color: rgb(var(--primary-6));
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.lint-issue__page:hover {
  border-color: rgba(var(--primary-6), 0.4);
  background: rgba(var(--primary-6), 0.06);
}

.lint-issue__action {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
