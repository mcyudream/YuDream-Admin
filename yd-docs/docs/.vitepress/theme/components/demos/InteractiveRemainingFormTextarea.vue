<script setup lang="ts">
import { ref } from 'vue'
import { FaButton, FaIcon, FaTextarea } from '@yudream/components'

defineProps<{ variant: 'basic' | 'disabled' | 'slot' }>()

const value = ref('')
const code = ref('console.log(\'Hello, world!\');')
const notice = ref('')
</script>

<template>
  <div v-if="variant === 'basic'" class="demo-row">
    <FaTextarea v-model="value" rows="3" placeholder="请输入内容" class="w-96" />
    <span class="text-sm text-muted-foreground">当前字数：{{ value.length }}</span>
  </div>
  <div v-else-if="variant === 'disabled'" class="demo-row">
    <FaTextarea model-value="这是一段不可编辑的文本内容。" rows="3" disabled class="w-96" />
  </div>
  <div v-else class="flex flex-col gap-3 max-w-120">
    <FaTextarea v-model="code" rows="4" align="block" start-class="justify-between" end-class="justify-between">
      <template #start><span>script.ts</span><FaButton variant="ghost" size="icon" @click="code = ''"><FaIcon name="refresh" /></FaButton></template>
      <template #end><span>{{ code.split('\n').length }} 行</span><FaButton size="sm" @click="notice = '已在本地执行示例操作'">Run</FaButton></template>
    </FaTextarea>
    <span class="text-sm text-muted-foreground">{{ notice || '修改内容或点击 Run 体验本地交互。' }}</span>
  </div>
</template>
