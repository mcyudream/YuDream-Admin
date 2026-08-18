<script setup lang="ts">
import type { WikiSearchHit } from '@/api/modules/platform-wiki'
import { inject, ref } from 'vue'
import { testWikiSearch } from '@/api/modules/platform-wiki'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useFaToast()
const query = ref('')
const searching = ref(false)
const hits = ref<WikiSearchHit[]>([])
const searched = ref(false)

async function search() {
  const space = store.space.value
  if (!space || !query.value.trim()) {
    toast.warning('请输入检索关键词')
    return
  }
  searching.value = true
  searched.value = true
  try {
    const res = await testWikiSearch({ spaceSlug: space.slug, query: query.value.trim() })
    hits.value = res.data || []
  }
  catch (error) {
    hits.value = []
    toast.error(error instanceof Error ? error.message : '检索失败')
  }
  finally {
    searching.value = false
  }
}

function openHit(hit: WikiSearchHit) {
  if (hit.kind === 'PAGE') store.openPage({ nodeId: hit.nodeId, title: hit.title, path: hit.path })
  else if (hit.sourceUrl) window.open(hit.sourceUrl, '_blank')
}
</script>

<template>
  <div class="srch-panel">
    <FaScrollArea class="srch-scroll">
      <div class="srch-inner">
        <FaCard class="srch-bar-card">
          <div class="srch-bar">
            <FaInput v-model="query" clearable placeholder="输入问题或关键词，回车检索" class="srch-input" @keydown.enter="search"><template #start><FaIcon name="i-ri:search-line" /></template></FaInput>
            <FaButton :loading="searching" @click="search"><FaIcon name="i-ri:search-line" /> 搜索</FaButton>
          </div>
        </FaCard>
        <div v-if="!searched" class="srch-empty"><FaIcon name="i-ri:search-line" /><strong>搜索知识库</strong><p>输入关键词，查看当前知识库中的相关内容。</p></div>
        <div v-else-if="!searching && !hits.length" class="srch-empty"><FaIcon name="i-ri:file-search-line" /><strong>没有命中结果</strong><p>换个关键词试试。</p></div>
        <div v-else class="srch-hits">
          <button v-for="(hit, index) in hits" :key="`${hit.kind}-${hit.nodeId || hit.sourceId || index}`" type="button" class="srch-hit" @click="openHit(hit)">
            <div class="srch-hit__head"><FaIcon :name="hit.kind === 'SOURCE' ? 'i-ri:file-2-line' : 'i-ri:book-open-line'" /><strong>{{ hit.title }}</strong></div>
            <div class="srch-hit__path">{{ hit.path }}</div>
            <p class="srch-hit__content">{{ hit.content }}</p>
          </button>
        </div>
      </div>
    </FaScrollArea>
  </div>
</template>

<style scoped>
.srch-panel { height: 100%; min-height: 0; background: var(--color-fill-1); }
.srch-scroll { height: 100%; }
.srch-inner { display: grid; gap: 14px; max-width: 960px; margin: 0 auto; padding: 16px; }
.srch-bar { display: flex; gap: 10px; }
.srch-input { flex: 1; }
.srch-empty { display: grid; justify-items: center; gap: 8px; padding: 60px 0; color: var(--color-text-3); }
.srch-empty :deep(svg) { color: var(--color-text-2); font-size: 36px; }
.srch-empty strong { color: var(--color-text-1); }
.srch-empty p { margin: 0; font-size: 12px; }
.srch-hits { display: grid; gap: 10px; }
.srch-hit { display: grid; gap: 6px; padding: 14px 16px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-2); cursor: pointer; font: inherit; text-align: left; transition: border-color .15s, box-shadow .15s; }
.srch-hit:hover { border-color: var(--color-border-2); box-shadow: 0 4px 14px rgb(0 0 0 / 6%); }
.srch-hit__head { display: flex; align-items: center; gap: 8px; color: var(--color-text-1); }
.srch-hit__head :deep(svg) { color: var(--color-text-2); }
.srch-hit__path { color: var(--color-text-3); font-size: 12px; }
.srch-hit__content { display: -webkit-box; overflow: hidden; margin: 0; color: var(--color-text-2); font-size: 13px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
@media (max-width: 560px) { .srch-bar { flex-direction: column; }.srch-bar :deep(button) { width: 100%; } }
</style>
