<script setup lang="ts">
import type { YdChatActivity, YdChatGraphNode, YdChatRetrievalHit } from './useYdChatStream'
import { computed, ref, watch } from 'vue'
import YdChatGraph from './YdChatGraph.vue'

const props = withDefaults(defineProps<{
  activities: YdChatActivity[]
  compact?: boolean
}>(), {
  compact: false,
})

const emits = defineEmits<{
  retrievalSelect: [hit: YdChatRetrievalHit]
  graphNodeSelect: [node: YdChatGraphNode]
}>()

const expandedRetrievals = ref<Record<number, boolean>>({})
const expandedGraphs = ref<Record<number, boolean>>({})

const visibleActivities = computed(() => props.activities.filter(activity => activity.activityType))

function activityStatus(activity: YdChatActivity): string {
  const status = activity.status?.trim().toLowerCase().replaceAll('_', '-')
  if (status === 'cancelled' || status === 'canceled' || status === 'stopped') return 'cancelled'
  if (status === 'error' || status === 'failed' || status === 'failure') return 'error'
  if (['complete', 'completed', 'success', 'succeeded', 'finished', 'done'].includes(status || '')) return 'complete'
  return 'running'
}

function activityIcon(activity: YdChatActivity): string {
  if (activityStatus(activity) === 'cancelled') return 'i-ri:stop-circle-line'
  if (activity.activityType === 'wiki-retrieval') return 'i-ri:search-line'
  if (activity.activityType === 'wiki-graph') return 'i-ri:mind-map'
  return activityStatus(activity) === 'error' ? 'i-ri:error-warning-line' : activityStatus(activity) === 'complete' ? 'i-ri:checkbox-circle-line' : 'i-ri:loader-4-line'
}

function activityLabel(activity: YdChatActivity): string {
  if (activity.title) return activity.title
  if (activity.activityType === 'wiki-retrieval') return '检索知识库'
  if (activity.activityType === 'wiki-graph') return '分析关联图谱'
  return activity.phase || '处理任务'
}

function scoreText(score?: number): string {
  return typeof score === 'number' ? score.toFixed(score < 1 ? 2 : 0) : ''
}

function toggleRetrieval(index: number) {
  expandedRetrievals.value[index] = !expandedRetrievals.value[index]
}

function toggleGraph(index: number) {
  expandedGraphs.value[index] = !expandedGraphs.value[index]
}

watch(() => props.activities, (activities) => {
  activities.forEach((activity, index) => {
    if (activity.activityType === 'wiki-graph' && activity.graph?.nodes.length && expandedGraphs.value[index] === undefined) {
      expandedGraphs.value[index] = true
    }
  })
}, { deep: true, immediate: true })
</script>

<template>
  <section v-if="visibleActivities.length" class="yd-chat-process" aria-label="回答过程">
    <div class="yd-chat-process__timeline">
      <template v-for="(activity, index) in visibleActivities" :key="`${activity.messageId}-${activity.activityType}-${index}`">
        <div class="yd-chat-process__step" :class="`is-${activityStatus(activity)}`">
          <FaIcon :name="activityIcon(activity)" class="yd-chat-process__status" :class="{ 'is-spinning': activityStatus(activity) === 'running' }" />
          <div class="yd-chat-process__main">
            <div class="yd-chat-process__head">
              <span>{{ activityLabel(activity) }}</span>
              <span v-if="activity.phase" class="yd-chat-process__phase">{{ activity.phase }}</span>
            </div>
            <p v-if="activity.content" class="yd-chat-process__description">{{ activity.content }}</p>
            <p v-else-if="activity.query && activity.activityType !== 'wiki-retrieval'" class="yd-chat-process__description">{{ activity.query }}</p>
          </div>
        </div>

        <div v-if="activity.activityType === 'wiki-retrieval' && activity.hits?.length" class="yd-chat-process__detail">
          <button v-if="activity.hits.length > 3" type="button" class="yd-chat-process__toggle" @click="toggleRetrieval(index)">
            <span>{{ expandedRetrievals[index] ? '收起命中' : `展开 ${activity.hits.length} 条命中` }}</span>
            <FaIcon :name="expandedRetrievals[index] ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'" />
          </button>
          <div class="yd-chat-process__hits">
            <button
              v-for="hit in activity.hits.slice(0, expandedRetrievals[index] ? undefined : 3)"
              :key="`${hit.nodeId || hit.path || hit.title}-${hit.excerpt || ''}`"
              type="button"
              class="yd-chat-hit"
              @click="emits('retrievalSelect', hit)"
            >
              <span class="yd-chat-hit__head">
                <strong>{{ hit.title }}</strong>
                <small v-if="hit.kind">{{ hit.kind }}</small>
                <small v-if="scoreText(hit.score)">{{ scoreText(hit.score) }}</small>
              </span>
              <span v-if="hit.excerpt" class="yd-chat-hit__excerpt">{{ hit.excerpt }}</span>
            </button>
          </div>
        </div>

        <div v-if="activity.activityType === 'wiki-graph' && activity.graph" class="yd-chat-process__detail">
          <button type="button" class="yd-chat-process__toggle" @click="toggleGraph(index)">
            <span>{{ activity.graph.nodes.length }} 个节点 · {{ activity.graph.edges.length }} 条关系</span>
            <FaIcon :name="expandedGraphs[index] ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'" />
          </button>
          <YdChatGraph
            v-if="expandedGraphs[index]"
            :graph="activity.graph"
            :compact="compact"
            @node-select="emits('graphNodeSelect', $event)"
          />
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.yd-chat-process {
  margin-bottom: 10px;
  color: var(--color-text-2);
  font-size: 12px;
}

.yd-chat-process__timeline {
  display: grid;
  gap: 8px;
}

.yd-chat-process__step {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.yd-chat-process__status {
  width: 16px;
  margin-top: 1px;
  color: var(--color-text-3);
  font-size: 15px;
}

.yd-chat-process__step.is-running .yd-chat-process__status { color: rgb(var(--primary-6)); }
.yd-chat-process__step.is-complete .yd-chat-process__status { color: rgb(var(--success-6)); }
.yd-chat-process__step.is-cancelled .yd-chat-process__status { color: var(--color-text-3); }
.yd-chat-process__step.is-error .yd-chat-process__status { color: rgb(var(--danger-6)); }
.yd-chat-process__status.is-spinning { animation: yd-chat-process-spin 1s linear infinite; }

.yd-chat-process__main { min-width: 0; }
.yd-chat-process__head { display: flex; align-items: center; gap: 6px; min-height: 18px; color: var(--color-text-2); font-weight: 500; }
.yd-chat-process__phase { padding: 1px 5px; border-radius: 4px; background: var(--color-fill-2); color: var(--color-text-3); font-size: 11px; font-weight: 400; }
.yd-chat-process__description { margin: 2px 0 0; color: var(--color-text-3); line-height: 1.55; }
.yd-chat-process__detail { margin: -3px 0 2px 24px; }
.yd-chat-process__toggle { display: inline-flex; align-items: center; gap: 3px; padding: 3px 0; border: 0; background: transparent; color: rgb(var(--primary-6)); cursor: pointer; font: inherit; font-size: 11px; }
.yd-chat-process__hits { display: grid; gap: 4px; margin-top: 3px; }
.yd-chat-hit { display: grid; gap: 2px; width: 100%; padding: 6px 8px; border: 1px solid var(--color-border-2); border-radius: 8px; background: transparent; color: var(--color-text-2); cursor: pointer; font: inherit; text-align: left; }
.yd-chat-hit:hover { border-color: rgba(var(--primary-6), 0.45); background: var(--color-fill-1); }
.yd-chat-hit__head { display: flex; align-items: center; gap: 6px; min-width: 0; }
.yd-chat-hit__head strong { overflow: hidden; color: var(--color-text-1); font-size: 12px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.yd-chat-hit__head small { flex-shrink: 0; color: var(--color-text-3); font-size: 11px; }
.yd-chat-hit__excerpt { display: -webkit-box; overflow: hidden; color: var(--color-text-3); font-size: 11px; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }

@keyframes yd-chat-process-spin { to { transform: rotate(360deg); } }
</style>
