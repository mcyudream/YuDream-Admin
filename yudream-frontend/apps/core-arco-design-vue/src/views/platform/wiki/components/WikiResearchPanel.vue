<script setup lang="ts">
import type { WikiResearchPlan } from '@/api/modules/platform-wiki'
import { inject, ref } from 'vue'
import { planWikiResearch, startWikiResearch } from '@/api/modules/platform-wiki'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useFaToast()

const seed = ref('')
const plan = ref<WikiResearchPlan | null>(null)
const planning = ref(false)
const starting = ref(false)

async function makePlan() {
  if (!store.spaceId.value || !seed.value.trim()) {
    toast.warning('请输入研究主题')
    return
  }
  planning.value = true
  plan.value = null
  try {
    const res = await planWikiResearch(store.spaceId.value, seed.value.trim())
    plan.value = res.data || null
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '生成研究计划失败')
  }
  finally {
    planning.value = false
  }
}

function removeQuery(index: number) {
  plan.value?.queries.splice(index, 1)
}

async function start() {
  if (!store.spaceId.value || !plan.value) {
    return
  }
  starting.value = true
  try {
    await startWikiResearch(store.spaceId.value, { topic: plan.value.topic, queries: plan.value.queries })
    toast.success('研究任务已启动，可在摄入队列查看进度')
    plan.value = null
    seed.value = ''
    store.openPanel('ingest')
  }
  finally {
    starting.value = false
  }
}
</script>

<template>
  <div class="res-panel">
    <FaScrollArea class="res-scroll">
      <div class="res-inner">
        <FaCard class="res-hero">
          <div class="res-hero__icon">
            <FaIcon name="i-ri:flask-line" />
          </div>
          <strong>深度研究</strong>
          <p>给一个研究起点，系统会规划检索查询、执行检索与网络调研，并把结果写成 Wiki 页面</p>
          <div class="res-hero__bar">
            <FaInput v-model="seed" clearable placeholder="研究起点 / 主题，例如：支付渠道的费率对比" @keydown.enter="makePlan">
              <template #start>
                <FaIcon name="i-ri:compass-3-line" />
              </template>
            </FaInput>
            <FaButton :loading="planning" @click="makePlan">
              <FaIcon name="i-ri:magic-line" /> 生成研究计划
            </FaButton>
          </div>
        </FaCard>

        <FaCard v-if="plan" class="res-plan">
          <div class="res-plan__head">
            <FaIcon name="i-ri:file-list-3-line" class="res-plan__icon" />
            <div>
              <strong>{{ plan.topic }}</strong>
              <p>{{ plan.rationale }}</p>
            </div>
          </div>
          <div class="res-plan__queries">
            <span class="res-plan__queries-label">检索查询（{{ plan.queries.length }}）</span>
            <span v-for="(query, index) in plan.queries" :key="query" class="res-query">
              <FaIcon name="i-ri:search-line" /> {{ query }}
              <FaIcon name="i-ri:close-line" class="res-query__remove" @click="removeQuery(index)" />
            </span>
          </div>
          <div class="res-plan__actions">
            <FaButton :loading="starting" :disabled="!plan.queries.length" @click="start">
              <FaIcon name="i-ri:rocket-line" /> 启动研究
            </FaButton>
            <FaButton variant="outline" @click="plan = null">
              放弃计划
            </FaButton>
          </div>
        </FaCard>
      </div>
    </FaScrollArea>
  </div>
</template>

<style scoped>
.res-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.res-scroll {
  height: 100%;
}

.res-inner {
  display: grid;
  gap: 14px;
  max-width: 860px;
  margin: 0 auto;
  padding: 16px;
}

.res-hero {
  text-align: center;
}

.res-hero__icon {
  display: grid;
  width: 52px;
  height: 52px;
  margin: 6px auto 12px;
  place-items: center;
  border-radius: 14px;
  background: rgba(var(--primary-6), 0.1);
  color: rgb(var(--primary-6));
  font-size: 26px;
}

.res-hero strong {
  font-size: 17px;
}

.res-hero > p {
  max-width: 460px;
  margin: 8px auto 0;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.7;
}

.res-hero__bar {
  display: flex;
  gap: 10px;
  max-width: 560px;
  margin: 18px auto 4px;
}

.res-plan__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.res-plan__icon {
  margin-top: 2px;
  color: rgb(var(--primary-6));
  font-size: 20px;
}

.res-plan__head strong {
  font-size: 15px;
}

.res-plan__head p {
  margin: 6px 0 0;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.7;
}

.res-plan__queries {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.res-plan__queries-label {
  width: 100%;
  color: var(--color-text-3);
  font-size: 12px;
}

.res-query {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 999px;
  background: rgba(var(--primary-6), 0.08);
  color: rgb(var(--primary-6));
  font-size: 12px;
}

.res-query__remove {
  cursor: pointer;
  opacity: 0.6;
}

.res-query__remove:hover {
  color: #ef4444;
  opacity: 1;
}

.res-plan__actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
</style>
