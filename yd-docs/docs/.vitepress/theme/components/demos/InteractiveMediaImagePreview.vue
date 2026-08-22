<script setup lang="ts">
import { useFaImagePreview } from '../../../../../../yudream-frontend/packages/components/src'
import FaImagePreview from '../../../../../../yudream-frontend/packages/components/src/basic/image-preview/index.vue'

const blueImage = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="480" height="320" viewBox="0 0 480 320"%3E%3Crect width="480" height="320" fill="%231d4ed8"/%3E%3Ccircle cx="240" cy="160" r="88" fill="%2393c5fd"/%3E%3Cpath d="M120 250l84-86 55 54 45-42 56 74H120z" fill="white" fill-opacity=".82"/%3E%3C/svg%3E'
const purpleImage = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="480" height="320" viewBox="0 0 480 320"%3E%3Crect width="480" height="320" fill="%237c3aed"/%3E%3Cpath d="M0 240Q120 120 240 240T480 240V320H0z" fill="%23ddd6fe"/%3E%3Ccircle cx="350" cy="90" r="42" fill="%23fef3c7"/%3E%3C/svg%3E'
const brokenImage = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg"'
const { open } = useFaImagePreview()

function openSingle() {
  open(blueImage)
}

function openMultiple(index = 0) {
  open([blueImage, purpleImage], index)
}
</script>

<template>
  <div class="interactive-media-preview">
    <FaImagePreview :src="blueImage" class="interactive-media-preview__image" />
    <FaImagePreview :src="purpleImage" class="interactive-media-preview__wide" />
    <FaImagePreview :src="brokenImage" class="interactive-media-preview__image">
      <template #error>
        <span class="interactive-media-preview__error">本地失败状态</span>
      </template>
    </FaImagePreview>
    <div class="interactive-media-preview__actions">
      <button type="button" @click="openSingle">预览单张</button>
      <button type="button" @click="openMultiple()">预览多张</button>
      <button type="button" @click="openMultiple(1)">从第 2 张开始</button>
    </div>
  </div>
</template>

<style scoped>
.interactive-media-preview { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
.interactive-media-preview__image { width: 120px; height: 100px; }
.interactive-media-preview__wide { width: 180px; height: 100px; }
.interactive-media-preview__error { display: grid; width: 100%; height: 100%; place-items: center; color: var(--vp-c-text-3); font-size: 12px; }
.interactive-media-preview__actions { display: flex; flex-wrap: wrap; gap: 8px; width: 100%; }
.interactive-media-preview__actions button { padding: 5px 10px; border: 1px solid var(--vp-c-divider); border-radius: 6px; background: var(--vp-c-bg); color: var(--vp-c-text-1); cursor: pointer; }
</style>
