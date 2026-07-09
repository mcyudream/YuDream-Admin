<script setup lang="ts">
import type { ProjectProgressModel } from '../composables/useProjectProgress'
import type { ProjectFileEvidence } from '../types'
import { FaButton, FaIcon, FaTag } from '@fantastic-admin/components'

defineProps<{
  model: ProjectProgressModel
  files: ProjectFileEvidence[]
  compact?: boolean
}>()
</script>

<template>
  <div v-if="files.length" class="pp-file-list" :class="{ compact }">
    <article v-for="file in files" :key="file.objectKey" class="pp-file-item">
      <button
        v-if="model.canPreviewEvidence(file)"
        type="button"
        class="pp-file-thumb"
        :title="`预览 ${file.filename}`"
        @click="model.previewEvidence(file)"
      >
        <FaIcon :name="file.image ? 'i-ri:image-line' : 'i-ri:file-search-line'" />
      </button>
      <div class="pp-file-main">
        <strong>{{ file.filename }}</strong>
        <span>{{ file.contentType || 'application/octet-stream' }} / {{ model.formatFileSize(file.size) }}</span>
      </div>
      <div class="pp-file-actions">
        <FaTag v-if="file.image">图片</FaTag>
        <FaButton v-if="model.canPreviewEvidence(file)" variant="outline" size="sm" @click="model.previewEvidence(file)">
          <FaIcon name="i-ri:eye-line" />
          预览
        </FaButton>
        <FaButton variant="outline" size="sm" :loading="model.saving" @click="model.downloadEvidence(file)">
          <FaIcon name="i-ri:download-2-line" />
          下载
        </FaButton>
      </div>
    </article>
  </div>
  <span v-else class="pp-muted">暂无附件</span>
</template>
