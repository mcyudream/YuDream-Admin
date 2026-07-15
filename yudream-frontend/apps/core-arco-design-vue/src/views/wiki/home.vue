<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchPublicWikiSpaces, searchPublicWiki, type WikiPublicSpace, type WikiSearchHit } from '@/api/modules/platform-wiki'

const loading = ref(false)
const searching = ref(false)
const spaces = ref<WikiPublicSpace[]>([])
const selectedSlug = ref('')
const query = ref('')
const hits = ref<WikiSearchHit[]>([])

async function load() {
  loading.value = true
  try {
    spaces.value = (await fetchPublicWikiSpaces()).data
    selectedSlug.value = spaces.value[0]?.slug || ''
  }
  finally {
    loading.value = false
  }
}

async function search() {
  if (!selectedSlug.value || !query.value.trim()) return
  searching.value = true
  try {
    hits.value = (await searchPublicWiki(selectedSlug.value, { query: query.value.trim(), topK: 8, graphExpansion: true })).data
  }
  finally {
    searching.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="wiki-home">
    <header class="wiki-home__header"><a href="/" class="wiki-home__brand"><FaIcon name="i-ri:book-2-line" /> YuDream 控制台</a><a href="/login">登录</a></header>
    <section class="wiki-home__content">
      <div class="wiki-home__intro"><span>知识库</span><h1>公开知识库</h1><p>浏览已发布文档，或直接检索知识库内容。</p></div>
      <section class="wiki-home__search"><a-select v-model="selectedSlug" :options="spaces.map(space => ({ label: space.name, value: space.slug }))" placeholder="选择知识库" /><a-input v-model="query" placeholder="输入问题或关键词" @keyup.enter="search" /><FaButton :loading="searching" @click="search"><FaIcon name="i-ri:search-line" /> 检索</FaButton></section>
      <section v-if="hits.length" class="wiki-home__hits"><article v-for="hit in hits" :key="`${hit.nodeId}-${hit.path}`"><div><a :href="hit.sourceUrl"><strong>{{ hit.title }}</strong></a><small>{{ hit.path }}</small></div><FaTag variant="outline">{{ hit.score.toFixed(3) }}</FaTag><p>{{ hit.content }}</p></article></section>
      <section class="wiki-home__spaces"><a v-for="space in spaces" :key="space.slug" :href="`/wiki/${space.slug}`"><FaIcon name="i-ri:book-open-line" /><strong>{{ space.name }}</strong><p>{{ space.description || '查看已发布文档' }}</p><span>进入知识库 <FaIcon name="i-ri:arrow-right-line" /></span></a><a-empty v-if="!loading && !spaces.length" description="暂无公开知识库" /></section>
    </section>
  </main>
</template>

<style scoped>
.wiki-home { min-height: 100vh; background: #f8fafc; color: #17202a; }.wiki-home__header { display: flex; height: 62px; padding: 0 max(24px, calc((100% - 1200px) / 2)); border-bottom: 1px solid #e5e7eb; align-items: center; justify-content: space-between; background: #fff; }.wiki-home__header a { color: #475569; font-size: 14px; text-decoration: none; }.wiki-home__brand { display: inline-flex; gap: 8px; align-items: center; color: #0f172a !important; font-weight: 750; }.wiki-home__brand :deep(svg) { color: #087443; font-size: 20px; }.wiki-home__content { width: min(1080px, calc(100% - 40px)); margin: 0 auto; padding: 72px 0; }.wiki-home__intro span { color: #087443; font-size: 13px; font-weight: 700; }.wiki-home__intro h1 { margin: 10px 0; font-size: 34px; }.wiki-home__intro p { margin: 0; color: #64748b; }.wiki-home__search { display: grid; grid-template-columns: 210px minmax(0, 1fr) auto; gap: 10px; margin: 34px 0; }.wiki-home__hits { display: grid; gap: 8px; margin-bottom: 36px; }.wiki-home__hits article { display: grid; grid-template-columns: 1fr auto; padding: 16px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; }.wiki-home__hits div { display: grid; gap: 4px; }.wiki-home__hits a { color: #0f172a; text-decoration: none; }.wiki-home__hits small { color: #94a3b8; }.wiki-home__hits p { grid-column: 1 / -1; margin: 10px 0 0; color: #475569; line-height: 1.7; }.wiki-home__spaces { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }.wiki-home__spaces > a { display: grid; gap: 10px; min-height: 185px; padding: 18px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; color: #17202a; text-decoration: none; }.wiki-home__spaces > a:hover { border-color: #8bc9ad; }.wiki-home__spaces > a > :deep(svg) { color: #087443; font-size: 22px; }.wiki-home__spaces p { margin: 0; color: #64748b; font-size: 14px; line-height: 1.6; }.wiki-home__spaces span { display: inline-flex; gap: 4px; margin-top: auto; align-items: center; color: #087443; font-size: 13px; }@media(max-width:760px){.wiki-home__content{padding:42px 0}.wiki-home__search{grid-template-columns:1fr}.wiki-home__spaces{grid-template-columns:1fr}.wiki-home__intro h1{font-size:28px}}
</style>
