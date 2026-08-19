<script setup lang="ts">
interface AuditViolation {
  file: string
  rule: string
  message: string
  line?: number
}

interface AuditReport {
  generatedAt?: string
  summary?: {
    filesScanned?: number
    violationCount?: number
  }
  violations?: AuditViolation[]
}

const loading = ref(false)
const unavailable = ref('')
const report = ref<AuditReport | null>(null)

onMounted(loadReport)

async function loadReport() {
  loading.value = true
  unavailable.value = ''
  report.value = null
  try {
    const response = await fetch('/__yudream-devtools/audit.json')
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    report.value = await response.json()
  }
  catch (error: any) {
    unavailable.value = error?.message || '请求失败'
  }
  finally {
    loading.value = false
  }
}

const ruleGroups = computed(() => {
  const groups = new Map<string, AuditViolation[]>()
  for (const violation of report.value?.violations || []) {
    const list = groups.get(violation.rule) || []
    list.push(violation)
    groups.set(violation.rule, list)
  }
  return [...groups.entries()].map(([rule, violations]) => ({ rule, violations }))
})
</script>

<template>
  <div class="audit-panel">
    <div class="audit-toolbar">
      <span v-if="report?.generatedAt" class="text-xs text-secondary-foreground/60">
        报告生成于 {{ new Date(report.generatedAt).toLocaleString() }}
      </span>
      <div class="flex-1" />
      <FaButton variant="outline" size="sm" :loading="loading" @click="loadReport">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </div>

    <div v-if="unavailable" class="audit-unavailable">
      <FaIcon name="i-ri:search-eye-line" class="size-8" />
      <p>审查服务未就绪（{{ unavailable }}）</p>
      <p class="text-xs">
        该报告由 vite dev 中间件 /__yudream-devtools/audit.json 提供（pnpm audit:ui 生成），
        仅在 vite dev 模式下且审查工具已接入时可用。
      </p>
    </div>

    <template v-else-if="report">
      <div class="audit-summary">
        <div class="audit-summary__item">
          <div class="audit-summary__value">
            {{ report.summary?.filesScanned ?? '-' }}
          </div>
          <div class="audit-summary__label">
            扫描文件
          </div>
        </div>
        <div class="audit-summary__item">
          <div class="audit-summary__value">
            {{ report.summary?.violationCount ?? (report.violations?.length || 0) }}
          </div>
          <div class="audit-summary__label">
            审查警告
          </div>
        </div>
      </div>

      <div v-for="group in ruleGroups" :key="group.rule" class="audit-group">
        <div class="audit-group__header">
          <FaTag variant="outline" class="text-xs font-mono">
            {{ group.rule }}
          </FaTag>
          <FaTag variant="secondary" class="text-xs">
            {{ group.violations.length }}
          </FaTag>
        </div>
        <div v-for="(violation, index) in group.violations" :key="index" class="audit-violation">
          <div class="audit-violation__file">
            {{ violation.file }}<span v-if="violation.line">:{{ violation.line }}</span>
          </div>
          <div class="audit-violation__message">
            {{ violation.message }}
          </div>
        </div>
      </div>

      <div v-if="!ruleGroups.length" class="audit-clean">
        <FaIcon name="i-ri:checkbox-circle-line" class="size-6" />
        未发现违规项
      </div>
    </template>
    <div v-else class="audit-unavailable">
      正在加载审查报告…
    </div>
  </div>
</template>

<style scoped>
.audit-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 0;
}

.audit-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.audit-unavailable {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 48px 24px;
  color: var(--color-text-3);
  font-size: 14px;
  text-align: center;
}

.audit-summary {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.audit-summary__item {
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  text-align: center;
}

.audit-summary__value {
  color: var(--color-text-1);
  font-size: 20px;
  font-weight: 700;
}

.audit-summary__label {
  margin-top: 2px;
  color: var(--color-text-3);
  font-size: 12px;
}

.audit-group {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  padding: 8px 12px;
}

.audit-group__header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.audit-violation {
  padding: 6px 0;
  border-top: 1px dashed var(--color-border-2);
}

.audit-violation__file {
  color: var(--color-text-2);
  font-size: 12px;
  font-family: monospace;
  overflow-wrap: anywhere;
}

.audit-violation__message {
  color: var(--color-text-3);
  font-size: 12px;
}

.audit-clean {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  padding: 32px 0;
  color: var(--color-text-3);
  font-size: 13px;
}
</style>
