<script setup lang="ts">
import { YdButton, YdFilePicker, YdForm, YdFormItem, YdMultiSelect, YdUiProvider } from '@yudream/components'
import { reactive, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const form = reactive({ categories: ['plugin', 'theme', 'legacy-category', 'disabled'] })
const options = [
  { label: '插件', value: 'plugin' },
  { label: '主题', value: 'theme' },
  { label: '文档', value: 'docs' },
  { label: '停用项', value: 'disabled', disabled: true },
  ...Array.from({ length: 12 }, (_, index) => ({ label: `其他分组 ${index + 1}`, value: `group-${index + 1}` })),
]
const files = ref<File[]>([])
function selectFiles(next: File[]) {
  files.value = next
}
</script>

<template>
  <YdUiProvider>
    <DemoCard title="搜索多选" description="受控 string[]；隐藏或已禁用的既有 ID 会保留，可显式移除，陌生 ID 不会进入模型。">
      <div class="selection-layout">
        <YdForm :model="form" class="selection-form">
          <YdFormItem field="categories" label="内容分类">
            <YdMultiSelect v-model="form.categories" :options="options" :max-tag-count="3" placeholder="搜索并选择分类" />
          </YdFormItem>
        </YdForm>
        <aside class="selection-summary" aria-label="多选绑定状态">
          <div class="summary-heading">
            <span>模型快照</span>
            <YdBadge :value="form.categories.length" />
          </div>
          <p class="summary-value">
            {{ form.categories.join('、') || '暂无已选分类' }}
          </p>
          <div class="summary-tags">
            <YdTag v-for="value in form.categories" :key="value" variant="secondary">
              {{ value }}
            </YdTag>
          </div>
          <p class="summary-note">
            既有但暂未出现在候选中的 ID 仍保留在 string[] 中。
          </p>
        </aside>
      </div>
    </DemoCard>
    <DemoCard title="原生文件选择" description="选择器返回 File[]；上传与业务校验仍由调用方处理。">
      <div class="file-picker-layout">
        <div class="file-picker-action">
          <YdFilePicker accept=".zip,application/zip" multiple @select="selectFiles">
            <template #default="{ open, disabled }">
              <YdButton type="button" :disabled="disabled" @click="open">
                选择 ZIP
              </YdButton>
            </template>
          </YdFilePicker>
          <p class="current-values">
            {{ files.length ? `已选择 ${files.length} 个文件` : '尚未选择文件' }}
          </p>
        </div>
        <div class="file-list" aria-live="polite">
          <div class="summary-heading">
            <span>待处理文件</span>
            <YdTag variant="outline">ZIP</YdTag>
          </div>
          <ul v-if="files.length" class="file-list__items">
            <li v-for="file in files" :key="`${file.name}:${file.lastModified}`">
              <span class="file-name">{{ file.name }}</span>
              <span class="file-size">{{ file.size }} bytes</span>
            </li>
          </ul>
          <p v-else class="summary-note">
            选择器只负责返回 File[]，后续上传和业务校验由调用方接管。
          </p>
        </div>
      </div>
    </DemoCard>
  </YdUiProvider>
</template>

<style scoped>
.selection-layout,
.file-picker-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(240px, 0.8fr);
  gap: var(--yd-content-gap);
  align-items: start;
}

.selection-form {
  min-width: 0;
}

.selection-summary,
.file-list {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: var(--yd-surface-padding);
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.summary-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-1);
}

.summary-value,
.summary-note,
.current-values {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.file-picker-action {
  display: grid;
  gap: 12px;
  align-content: start;
  min-height: 110px;
}

.file-list__items {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.file-list__items li {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
  min-width: 0;
  font-size: 12px;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  flex: 0 0 auto;
  color: var(--color-text-3);
}

@media (width <= 720px) {
  .selection-layout,
  .file-picker-layout {
    grid-template-columns: 1fr;
  }
}
</style>
