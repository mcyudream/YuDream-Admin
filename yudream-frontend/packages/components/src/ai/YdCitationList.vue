<script setup lang="ts">
import type { YdChatCitation } from './useYdChatStream'
import { computed, ref } from 'vue'
import FaIcon from '../basic/icon/index.vue'

const props = withDefaults(defineProps<{
  citations?: YdChatCitation[]
  /** 分组标题 */
  label?: string
  /** 默认展开 */
  defaultOpen?: boolean
  /** 图片地址解析（如开发环境补代理前缀），缺省原样使用 */
  imageUrlResolver?: (url: string) => string
}>(), {
  citations: () => [],
  label: '引用来源',
  defaultOpen: true,
  imageUrlResolver: undefined,
})

const emits = defineEmits<{
  select: [citation: YdChatCitation]
}>()

const open = ref(props.defaultOpen)

// 汇总全部引用的相关图片（按地址去重，最多 6 张）
const images = computed(() => {
  const seen = new Set<string>()
  const list: { url: string, caption?: string, source: YdChatCitation }[] = []
  for (const citation of props.citations) {
    for (const image of citation.images ?? []) {
      if (image.url && !seen.has(image.url)) {
        seen.add(image.url)
        list.push({ url: image.url, caption: image.caption, source: citation })
      }
    }
  }
  return list.slice(0, 6)
})

function resolveUrl(url: string) {
  return props.imageUrlResolver ? props.imageUrlResolver(url) : url
}

function openImage(url: string) {
  window.open(resolveUrl(url), '_blank')
}
</script>

<template>
  <div v-if="citations.length" class="yd-citations">
    <button type="button" class="yd-citations__head" @click="open = !open">
      <FaIcon name="i-ri:book-marked-line" />
      <span>{{ label }}（{{ citations.length }}）</span>
      <FaIcon :name="open ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'" class="yd-citations__chevron" />
    </button>
    <div v-show="open" class="yd-citations__list">
      <button
        v-for="(citation, index) in citations"
        :key="citation.nodeId || citation.path || citation.title"
        type="button"
        class="yd-citation"
        :title="citation.excerpt || citation.path || citation.title"
        @click="emits('select', citation)"
      >
        <span class="yd-citation__index">{{ index + 1 }}</span>
        <span class="yd-citation__body">
          <span class="yd-citation__title">{{ citation.title }}</span>
          <span v-if="citation.excerpt" class="yd-citation__excerpt">{{ citation.excerpt }}</span>
        </span>
        <FaIcon name="i-ri:arrow-right-up-line" class="yd-citation__arrow" />
      </button>
    </div>
    <div v-if="images.length" v-show="open" class="yd-citations__images">
      <button
        v-for="image in images"
        :key="image.url"
        type="button"
        class="yd-citation-image"
        :title="image.caption || `来自「${image.source.title}」的图片`"
        @click.stop="openImage(image.url)"
      >
        <img :src="resolveUrl(image.url)" :alt="image.caption || '相关图片'" loading="lazy">
        <span v-if="image.caption" class="yd-citation-image__caption">{{ image.caption }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.yd-citations {
  margin-top: 12px;
}

.yd-citations__head {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.yd-citations__head:hover {
  color: rgb(var(--primary-6));
}

.yd-citations__chevron {
  font-size: 14px;
}

.yd-citations__list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.yd-citation {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 360px;
  padding: 6px 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-1);
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}

.yd-citation__index {
  display: grid;
  width: 17px;
  height: 17px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 50%;
  background: rgba(var(--primary-6), 0.1);
  color: rgb(var(--primary-6));
  font-size: 10px;
  font-weight: 600;
}

.yd-citation__body {
  display: grid;
  min-width: 0;
  gap: 2px;
  text-align: left;
}

.yd-citation__title,
.yd-citation__excerpt {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-citation__excerpt {
  color: var(--color-text-3);
  font-size: 10px;
}

.yd-citation:hover .yd-citation__excerpt {
  color: rgba(var(--primary-6), 0.72);
}

.yd-citation__arrow {
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 12px;
}

.yd-citation:hover {
  border-color: rgba(var(--primary-6), 0.4);
  background: rgba(var(--primary-6), 0.05);
  color: rgb(var(--primary-6));
}

.yd-citations__images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.yd-citation-image {
  position: relative;
  overflow: hidden;
  width: 120px;
  height: 84px;
  padding: 0;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1);
  cursor: zoom-in;
}

.yd-citation-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.yd-citation-image__caption {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  padding: 2px 6px;
  background: rgb(0 0 0 / 55%);
  color: #fff;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-citation-image:hover {
  border-color: rgba(var(--primary-6), 0.4);
}
</style>
