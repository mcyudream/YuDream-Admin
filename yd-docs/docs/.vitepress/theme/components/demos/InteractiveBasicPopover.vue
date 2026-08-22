<script setup lang="ts">
import { ref } from 'vue'
import { FaButton, FaInput, FaPopover } from '@yudream/components'

const open = ref(false)
const keyword = ref('')
const result = ref('尚未应用筛选')

function apply() {
  result.value = keyword.value ? `已应用：${keyword.value}` : '已清空筛选'
  open.value = false
}
</script>

<template>
  <div class="demo-row">
    <FaPopover v-model:open="open" align="start">
      <FaButton variant="outline">{{ open ? '关闭' : '打开' }}筛选</FaButton>
      <template #panel>
        <div>
          <FaInput v-model="keyword" placeholder="输入关键字" />
          <FaButton size="sm" @click="apply">应用筛选</FaButton>
        </div>
      </template>
    </FaPopover>
    <span>{{ result }}</span>
  </div>
</template>
