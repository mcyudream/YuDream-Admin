<script setup lang="ts">
import type { YdChatAttachment } from './useYdChatStream'
import FaIcon from '../basic/icon/index.vue'

withDefaults(defineProps<{
  attachments: YdChatAttachment[]
  removable?: boolean
}>(), {
  removable: false,
})

const emits = defineEmits<{
  remove: [attachment: YdChatAttachment]
}>()

function icon(attachment: YdChatAttachment): string {
  if (attachment.kind === 'IMAGE' || attachment.contentType?.startsWith('image/')) {
    return 'i-ri:image-line'
  }
  if (attachment.kind === 'DOCUMENT') {
    return 'i-ri:file-text-line'
  }
  return 'i-ri:attachment-2'
}
</script>

<template>
  <div v-if="attachments.length" class="yd-attachments">
    <a
      v-for="attachment in attachments"
      :key="attachment.fileId ?? attachment.fileName"
      class="yd-attachment"
      :href="attachment.url || undefined"
      :target="attachment.url ? '_blank' : undefined"
      :rel="attachment.url ? 'noopener' : undefined"
    >
      <img v-if="attachment.kind === 'IMAGE' || attachment.contentType?.startsWith('image/')" :src="attachment.dataUrl || attachment.url" alt="" class="yd-attachment__image">
      <FaIcon v-else :name="icon(attachment)" class="yd-attachment__icon" />
      <span class="yd-attachment__name">{{ attachment.fileName }}</span>
      <button
        v-if="removable"
        type="button"
        class="yd-attachment__remove"
        @click.prevent.stop="emits('remove', attachment)"
      >
        <FaIcon name="i-ri:close-line" />
      </button>
    </a>
  </div>
</template>

<style scoped>
.yd-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.yd-attachment {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 260px;
  padding: 5px 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 9px;
  background: var(--color-fill-1);
  color: var(--color-text-2);
  font-size: 12px;
  text-decoration: none;
}

.yd-attachment__image {
  width: 34px;
  height: 34px;
  border-radius: 5px;
  object-fit: cover;
}

.yd-attachment__icon {
  color: var(--color-text-3);
  font-size: 16px;
}

.yd-attachment__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-attachment__remove {
  display: grid;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  place-items: center;
}

.yd-attachment__remove:hover {
  color: rgb(var(--danger-6, 245 63 63));
}
</style>
