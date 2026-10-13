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
      <YdForm :model="form" class="max-w-xl">
        <YdFormItem field="categories" label="内容分类">
          <YdMultiSelect v-model="form.categories" :options="options" :max-tag-count="3" placeholder="搜索并选择分类" />
        </YdFormItem>
      </YdForm>
      <p class="current-values">
        当前值：{{ form.categories.join(', ') || '空' }}
      </p>
    </DemoCard>
    <DemoCard title="原生文件选择" description="选择器返回 File[]；上传与业务校验仍由调用方处理。">
      <YdFilePicker accept=".zip,application/zip" multiple @select="selectFiles">
        <template #default="{ open, disabled }">
          <YdButton type="button" :disabled="disabled" @click="open">
            选择 ZIP
          </YdButton>
        </template>
      </YdFilePicker>
      <ul v-if="files.length" class="text-sm mt-3">
        <li v-for="file in files" :key="`${file.name}:${file.lastModified}`">
          {{ file.name }} ({{ file.size }} bytes)
        </li>
      </ul>
    </DemoCard>
  </YdUiProvider>
</template>

<style scoped>
.current-values { margin-top: 12px; color: var(--color-text-3); font-size: 12px; }
</style>
