<script setup lang="ts">
import { ref } from 'vue'
import { FaCard, FaFileUpload, FaRadioGroup, FaTag, FaTrend } from '@yudream/components'

const props = defineProps<{ type: 'card' | 'file-upload' | 'radio-group' | 'tag' | 'trend' }>()
const files = ref<any[]>([])
const plan = ref('monthly')
const tags = ref(['标签一', '标签二'])

function localUpload({ file, onProgress }: any) {
  onProgress(100)
  return Promise.resolve({ name: file.name })
}
</script>

<template>
  <FaCard v-if="props.type === 'card'" title="卡片标题" class="w-80">
    这是由 FaCard 渲染的内容。
  </FaCard>
  <FaFileUpload
    v-else-if="props.type === 'file-upload'"
    v-model="files"
    multiple
    description="选择本地文件（不会发送网络请求）"
    :http-request="localUpload"
  />
  <FaRadioGroup
    v-else-if="props.type === 'radio-group'"
    v-model="plan"
    :options="[
      { label: '按月付费', value: 'monthly', description: '每月自动续费，随时取消' },
      { label: '按年付费', value: 'yearly', description: '一次支付 12 个月，享 8 折优惠' },
      { label: '企业定制', value: 'enterprise', disabled: true },
    ]"
  />
  <div v-else-if="props.type === 'tag'" class="flex flex-wrap gap-2">
    <FaTag v-for="tag in tags" :key="tag" closable @close="tags = tags.filter(item => item !== tag)">{{ tag }}</FaTag>
    <FaTag variant="destructive">危险标签</FaTag><FaTag variant="outline">边框标签</FaTag>
  </div>
  <div v-else class="flex gap-3">
    <FaTrend value="12.3%" /><FaTrend value="12.3%" type="down" />
  </div>
</template>
