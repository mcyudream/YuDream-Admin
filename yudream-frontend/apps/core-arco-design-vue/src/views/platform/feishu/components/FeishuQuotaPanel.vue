<script setup lang="ts">
import type { FeishuFailure, FeishuQuotaUsage } from '../feishu-contract'
import { computed, onMounted, ref } from 'vue'
import apiFeishu from '@/api/modules/platform-feishu'
import { failureTitle, FEISHU_PERMISSIONS, normalizeFeishuFailure, resolveQuotaConfig, resolveQuotaUsage } from '../feishu-contract'
import { createFeishuQuotaDraft } from '../feishu-state'

/**
 * 额度与限流：当前用户用量（GET quota/me）+ 全局默认上限（GET/PUT quota/config）。
 * 上限字段未返回时不预填、不允许保存，避免把 0 当真实值写回后端。
 */
const toast = useYdToast()
const isMobile = useIsMobile()

const draft = createFeishuQuotaDraft()
const { state, issues } = draft

const usageLoading = ref(false)
const usageFailure = ref<FeishuFailure | null>(null)
const usage = ref<FeishuQuotaUsage | null>(null)

const usageFailureTerminal = computed(() => usageFailure.value?.kind !== 'rate-limited')

/** 用量进度只用真实数值推导；缺少限额时按 0 展示而不假装有余量。 */
function usedPercent(used?: number, limit?: number) {
  if (!used || !limit) {
    return 0
  }
  return Math.min(100, Math.max(0, Math.round(used / limit * 100)))
}

async function loadUsage() {
  usageLoading.value = true
  usageFailure.value = null
  try {
    const { data } = await apiFeishu.getQuotaMe()
    usage.value = resolveQuotaUsage(data)
  }
  catch (error) {
    usageFailure.value = normalizeFeishuFailure(error, 'quota')
    usage.value = null
  }
  finally {
    usageLoading.value = false
  }
}

async function loadConfig() {
  state.loading = true
  try {
    const { data } = await apiFeishu.getQuotaConfig()
    draft.load(resolveQuotaConfig(data))
    state.failure = null
  }
  catch (error) {
    state.failure = normalizeFeishuFailure(error, 'quota')
  }
  finally {
    state.loading = false
  }
}

function reload() {
  void loadUsage()
  void loadConfig()
}

async function save() {
  if (!draft.submittable.value) {
    return
  }
  const token = draft.ticket()
  draft.setSaving(true)
  try {
    const { data } = await apiFeishu.updateQuotaConfig(draft.body())
    draft.acknowledge(token)
    // 后端返回保存后的配置：用它校正已保存快照，避免本地与后端不一致
    const saved = resolveQuotaConfig(data)
    if (saved.resolved) {
      draft.load(saved)
    }
    toast.success('已保存全局默认额度')
    await loadUsage()
  }
  catch (error) {
    draft.fail(normalizeFeishuFailure(error, 'quota'))
  }
}

async function reloadConfig() {
  await loadConfig()
  draft.discard()
}

function usageText(used?: number, limit?: number) {
  if (used === undefined && limit === undefined) {
    return '未返回'
  }
  return `${used ?? '—'} / ${limit ?? '—'}`
}

onMounted(reload)

defineExpose({ reload })
</script>

<template>
  <section class="quota">
    <header class="quota__head">
      <h2>额度与限流</h2>
      <p>飞书调用额度按用户分钟/日两个维度计量；这里展示当前账号用量与全局默认上限，不引入新的权限维度。</p>
    </header>

    <YdLoadingOverlay :loading="usageLoading" text="加载用量">
      <YdAlert
        v-if="usageFailure"
        class="quota__notice"
        :variant="usageFailureTerminal ? 'destructive' : 'default'"
        icon="morph:circle-alert"
        :title="failureTitle(usageFailure.kind)"
        :description="usageFailure.message"
      >
        <template #description>
          <span>{{ usageFailure.message }}</span>
          <YdButton variant="ghost" size="sm" @click="loadUsage">
            重新加载用量
          </YdButton>
        </template>
      </YdAlert>

      <YdStats v-else class="quota__stats">
        <YdStat
          title="每分钟用量"
          :value="usageText(usage?.minuteUsed, usage?.minuteLimit)"
          :description="usage?.minuteRemaining !== undefined ? `剩余 ${usage.minuteRemaining} 次` : '剩余额度未返回'"
        >
          <template #figure>
            <YdProgress :model-value="usedPercent(usage?.minuteUsed, usage?.minuteLimit)" />
          </template>
        </YdStat>
        <YdStat
          title="每日用量"
          :value="usageText(usage?.dailyUsed, usage?.dailyLimit)"
          :description="usage?.dailyRemaining !== undefined ? `剩余 ${usage.dailyRemaining} 次` : '剩余额度未返回'"
        >
          <template #figure>
            <YdProgress :model-value="usedPercent(usage?.dailyUsed, usage?.dailyLimit)" />
          </template>
        </YdStat>
      </YdStats>
    </YdLoadingOverlay>

    <YdLoadingOverlay :loading="state.loading" text="加载额度配置">
      <YdAlert
        v-if="state.failure"
        class="quota__notice"
        :variant="state.failure.kind === 'rate-limited' ? 'default' : 'destructive'"
        icon="morph:circle-alert"
        :title="failureTitle(state.failure.kind)"
        :description="state.failure.message"
      />

      <YdAlert
        v-if="state.config && !state.config.resolved"
        class="quota__notice"
        variant="destructive"
        icon="morph:triangle-alert"
        title="配置字段缺失"
        :description="`后端未返回字段：${state.config.missing.join('、')}。已停用保存，避免把 0 写回上限。`"
      />

      <YdAlert
        v-if="state.conflict"
        class="quota__notice"
        icon="morph:triangle-alert"
        title="远端配置已更新"
        description="你本地有未保存修改，因此没有被远端值覆盖；可继续保存你的修改，或重新载入远端配置。"
      />

      <YdForm class="quota__form" :model="state.form" layout="vertical">
        <YdFormItem
          label="每分钟上限"
          :validate-status="issues.defaultPerMinuteLimit ? 'error' : undefined"
          :help="issues.defaultPerMinuteLimit || '单个用户每分钟允许的飞书调用次数（后端 defaultPerMinuteLimit）'"
        >
          <YdNumberField v-model="state.form.defaultPerMinuteLimit" :min="1" :step="1" />
        </YdFormItem>
        <YdFormItem
          label="每日上限"
          :validate-status="issues.defaultDailyLimit ? 'error' : undefined"
          :help="issues.defaultDailyLimit || '单个用户每日允许的飞书调用次数（后端 defaultDailyLimit）'"
        >
          <YdNumberField v-model="state.form.defaultDailyLimit" :min="1" :step="1" />
        </YdFormItem>
      </YdForm>

      <footer class="quota__footer">
        <span class="quota__state">
          <YdTag v-if="draft.dirty.value">有未保存修改</YdTag>
          <span v-else-if="state.loaded">已同步后端配置</span>
          <span v-else>配置未载入</span>
        </span>
        <div class="quota__footer-actions">
          <YdButton variant="ghost" :disabled="!draft.dirty.value || state.saving" @click="draft.discard()">
            放弃修改
          </YdButton>
          <YdButton v-if="state.conflict" variant="outline" @click="reloadConfig">
            重新载入远端配置
          </YdButton>
          <YdButton v-auth="FEISHU_PERMISSIONS.config" :loading="state.saving" :disabled="!draft.submittable.value" @click="save">
            保存默认额度
          </YdButton>
        </div>
      </footer>

      <YdDescriptions
        v-if="state.config"
        class="quota__meta"
        :column="isMobile ? 1 : 2"
        :mobile-column="1"
        border
        :items="[
          { label: '每分钟上限', value: state.config.defaultPerMinuteLimit === undefined ? '未返回' : String(state.config.defaultPerMinuteLimit) },
          { label: '每日上限', value: state.config.defaultDailyLimit === undefined ? '未返回' : String(state.config.defaultDailyLimit) },
        ]"
      />
    </YdLoadingOverlay>
  </section>
</template>

<style scoped>
.quota {
  display: grid;
  gap: 14px;
  align-content: start;
  min-width: 0;
}

.quota__head {
  display: grid;
  gap: 4px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border-2);
}

.quota__head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

.quota__head p {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.quota__notice {
  font-size: 12px;
}

.quota__stats {
  margin-bottom: 4px;
}

.quota__form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px 20px;
  max-width: 720px;
}

.quota__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-2);
}

.quota__state {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.quota__footer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.quota__meta {
  margin-top: 12px;
}
</style>
