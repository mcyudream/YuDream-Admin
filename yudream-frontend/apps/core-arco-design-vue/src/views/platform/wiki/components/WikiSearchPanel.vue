<script setup lang="ts">
import type { WikiSearchHit } from '@/api/modules/platform-wiki'
import { inject, ref } from 'vue'
import { testWikiSearch } from '@/api/modules/platform-wiki'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const query = ref('')
const searching = ref(false)
const hits = ref<WikiSearchHit[]>([])
const searched = ref(false)
const searchError = ref('')

async function search() {
  const space = store.space.value
  if (!space || !query.value.trim()) {
    toast.warning('请输入检索关键词')
    return
  }
  searching.value = true
  searched.value = true
  searchError.value = ''
  try {
    const res = await testWikiSearch({ spaceSlug: space.slug, query: query.value.trim() })
    hits.value = res.data || []
  }
  catch (exception) {
    // 检索失败保留错误与重试入口，不能渲染成「没有命中结果」
    hits.value = []
    searchError.value = exception instanceof Error ? exception.message : '检索失败，请稍后重试'
  }
  finally {
    searching.value = false
  }
}

function openHit(hit: WikiSearchHit) {
  if (hit.kind === 'PAGE') {
    void store.openPage({ nodeId: hit.nodeId, title: hit.title, path: hit.path })
  }
  else if (hit.sourceUrl) {
    window.open(hit.sourceUrl, '_blank')
  }
}
</script>

<template>
  <div class="srch-panel">
    <YdScrollArea class="srch-scroll">
      <div class="srch-inner">
        <YdCard class="srch-bar-card">
          <div class="srch-bar">
            <YdInput
              v-model="query"
              clearable
              placeholder="输入问题或关键词，回车检索"
              class="srch-input"
              @keydown.enter="search"
            >
              <template #start>
                <YdIcon name="morph:search" />
              </template>
            </YdInput>
            <YdButton :loading="searching" @click="search">
              <YdIcon name="morph:search" />
              搜索
            </YdButton>
          </div>
        </YdCard>

        <YdAlert v-if="searchError" variant="destructive" title="检索失败" :description="searchError">
          <YdButton variant="outline" size="sm" :loading="searching" @click="search">
            <YdIcon name="morph:rotate-cw" />
            重新检索
          </YdButton>
        </YdAlert>

        <div v-if="searching" class="srch-loading">
          <YdLoading :size="32" text="正在检索…" />
        </div>
        <YdEmpty
          v-else-if="!searched && !searchError"
          icon="morph:search"
          title="搜索知识库"
          description="输入关键词，查看当前知识库中的相关内容。"
        />
        <YdEmpty
          v-else-if="!searchError && !hits.length"
          icon="morph:file-search"
          title="没有命中结果"
          description="换个关键词试试。"
        />
        <div v-else-if="hits.length" class="srch-hits">
          <YdButton v-for="(hit, index) in hits" :key="`${hit.kind}-${hit.nodeId || hit.sourceId || index}`" variant="ghost" type="button" class="srch-hit" @click="openHit(hit)">
            <div class="srch-hit__head">
              <YdIcon :name="hit.kind === 'SOURCE' ? 'morph:file' : 'morph:book-open'" /><strong>{{ hit.title }}</strong>
            </div>
            <div class="srch-hit__path">
              {{ hit.path }}
            </div>
            <p class="srch-hit__content">
              {{ hit.content }}
            </p>
          </YdButton>
        </div>
      </div>
    </YdScrollArea>
  </div>
</template>

<style scoped>
.srch-panel { height: 100%; min-height: 0; background: var(--color-fill-1); }
.srch-scroll { height: 100%; }
.srch-inner { display: grid; gap: 14px; max-width: 960px; margin: 0 auto; padding: 16px; }
.srch-bar { display: flex; gap: 10px; }
.srch-input { flex: 1; }
.srch-loading { display: flex; justify-content: center; padding: 56px 0; }
.srch-hits { display: grid; gap: 10px; }
.srch-hit { display: grid; gap: 6px; padding: 14px 16px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-2); cursor: pointer; font: inherit; text-align: left; transition: border-color .15s, box-shadow .15s; }
.srch-hit:hover { border-color: var(--color-border-2); box-shadow: 0 4px 14px rgb(0 0 0 / 6%); }
.srch-hit__head { display: flex; align-items: center; gap: 8px; color: var(--color-text-1); }
.srch-hit__head :deep(svg) { color: var(--color-text-2); }
.srch-hit__path { color: var(--color-text-3); font-size: 12px; }
.srch-hit__content { display: -webkit-box; overflow: hidden; margin: 0; color: var(--color-text-2); font-size: 13px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
@media (max-width: 768px) {
  .srch-bar { flex-direction: column; }
  .srch-bar :deep(button) { width: 100%; }
}
</style>
