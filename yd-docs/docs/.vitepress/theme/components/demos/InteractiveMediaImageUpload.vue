<script setup lang="ts">
import type { UploadRequestOptions } from '../../../../../../yudream-frontend/packages/components/src/basic/image-upload/index.vue'
import { ref } from 'vue'
import FaImageUpload from '../../../../../../yudream-frontend/packages/components/src/basic/image-upload/index.vue'

const images = ref<string[]>([])
const multipleImages = ref<string[]>([])
const validationMessage = ref('请选择不超过 200 KB 的图片')

async function localUpload({ file, onProgress }: UploadRequestOptions): Promise<{ url: string }> {
  onProgress(20)
  const url = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
  onProgress(100)
  return { url }
}

function validateImage(file: File) {
  const valid = file.type.startsWith('image/') && file.size <= 200 * 1024
  validationMessage.value = valid ? '本地读取成功，可继续添加图片' : '仅允许不超过 200 KB 的图片'
  return valid
}
</script>

<template>
  <div class="interactive-media-upload">
    <section>
      <strong>单图本地读取</strong>
      <FaImageUpload v-model="images" :http-request="localUpload" :after-upload="response => response.url" />
    </section>
    <section>
      <strong>多图、预览与排序</strong>
      <FaImageUpload v-model="multipleImages" :http-request="localUpload" :after-upload="response => response.url" multiple :max="3" />
    </section>
    <section>
      <strong>校验与自定义尺寸</strong>
      <FaImageUpload v-model="multipleImages" :http-request="localUpload" :after-upload="response => response.url" :before-upload="validateImage" :width="160" :height="104" :max="3" />
      <span>{{ validationMessage }}</span>
    </section>
  </div>
</template>

<style scoped>
.interactive-media-upload { display: flex; flex-wrap: wrap; gap: 24px; }
.interactive-media-upload section { display: grid; gap: 8px; }
.interactive-media-upload strong { color: var(--vp-c-text-1); font-size: 13px; }
.interactive-media-upload span { color: var(--vp-c-text-3); font-size: 12px; }
</style>
