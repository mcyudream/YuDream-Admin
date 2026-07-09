<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectFileEvidence } from '../types'
import { FaButton, FaIcon, FaModal, FaTag } from '@fantastic-admin/components'
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  model: ProjectProgressModel
  files: ProjectFileEvidence[]
  compact?: boolean
}>()

const open = ref(false)
const loading = ref(false)
const previewErrors = ref<Record<string, string>>({})

const previewableFiles = computed(() => props.files.filter(file => props.model.canPreviewEvidence(file)))
const imageCount = computed(() => props.files.filter(file => file.image).length)

watch(open, async (value) => {
  if (!value || !previewableFiles.value.length) {
    return
  }
  loading.value = true
  const nextErrors: Record<string, string> = {}
  try {
    await Promise.all(previewableFiles.value.map(async (file) => {
      try {
        await props.model.evidencePreviewUrl(file)
      }
      catch (error) {
        nextErrors[file.objectKey] = error instanceof Error ? error.message : '预览加载失败'
      }
    }))
    previewErrors.value = nextErrors
  }
  finally {
    loading.value = false
  }
})

function previewUrl(file: ProjectFileEvidence) {
  return props.model.evidencePreviewUrls[file.objectKey] || ''
}

function isImage(file: ProjectFileEvidence) {
  return file.image || file.contentType?.startsWith('image/')
}

function isPdf(file: ProjectFileEvidence) {
  return file.contentType === 'application/pdf'
}

function isText(file: ProjectFileEvidence) {
  return !!file.contentType?.startsWith('text/')
}
</script>

<template>
  <div v-if="files.length" class="pp-evidence-trigger">
    <FaButton :size="compact ? 'sm' : 'default'" variant="outline" @click="open = true">
      <FaIcon name="i-ri:attachment-2" />
      查看附件 ({{ files.length }})
    </FaButton>
    <FaTag v-if="imageCount" variant="secondary">{{ imageCount }} 张图片</FaTag>

    <FaModal
      v-model="open"
      title="附件预览"
      class="pp-evidence-modal"
      :show-confirm-button="false"
      show-cancel-button
      cancel-button-text="关闭"
    >
      <div class="pp-evidence-summary">
        <strong>共 {{ files.length }} 个附件</strong>
        <span>图片会直接显示在这里，其他文件保留预览和下载操作。</span>
      </div>

      <div class="pp-evidence-grid">
        <article v-for="file in files" :key="file.objectKey" class="pp-evidence-card">
          <div class="pp-evidence-card-head">
            <div class="pp-evidence-card-title">
              <strong>{{ file.filename }}</strong>
              <span>{{ file.contentType || 'application/octet-stream' }} / {{ model.formatFileSize(file.size) }}</span>
            </div>
            <div class="pp-file-actions">
              <FaTag v-if="isImage(file)">图片</FaTag>
              <FaButton
                v-if="model.canPreviewEvidence(file) && !isImage(file)"
                variant="outline"
                size="sm"
                @click="model.previewEvidence(file)"
              >
                <FaIcon name="i-ri:eye-line" />
                新窗预览
              </FaButton>
              <FaButton variant="outline" size="sm" :loading="model.saving" @click="model.downloadEvidence(file)">
                <FaIcon name="i-ri:download-2-line" />
                下载
              </FaButton>
            </div>
          </div>

          <div v-if="model.canPreviewEvidence(file) && loading && !previewUrl(file)" class="pp-evidence-placeholder">
            正在加载预览
          </div>

          <div v-else-if="isImage(file)" class="pp-evidence-preview">
            <img
              v-if="previewUrl(file)"
              :src="previewUrl(file)"
              :alt="file.filename"
              class="pp-evidence-image"
              @click="model.previewEvidence(file)"
            >
            <div v-else class="pp-evidence-placeholder">
              {{ previewErrors[file.objectKey] || '图片预览加载失败' }}
            </div>
          </div>

          <div v-else-if="isPdf(file) && previewUrl(file)" class="pp-evidence-preview">
            <iframe :src="previewUrl(file)" :title="file.filename" class="pp-evidence-frame" />
          </div>

          <div v-else-if="isText(file) && previewUrl(file)" class="pp-evidence-preview">
            <iframe :src="previewUrl(file)" :title="file.filename" class="pp-evidence-frame" />
          </div>

          <div v-else class="pp-evidence-placeholder">
            {{ previewErrors[file.objectKey] || '当前附件不支持直接展示，可使用预览或下载。' }}
          </div>
        </article>
      </div>
    </FaModal>
  </div>
  <span v-else class="pp-muted">暂无附件</span>
</template>
