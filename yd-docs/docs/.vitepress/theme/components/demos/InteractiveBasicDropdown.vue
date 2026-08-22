<script setup lang="ts">
import { ref } from 'vue'
import { FaButton, FaDropdown } from '@yudream/components'

const result = ref('请选择操作')
const items = [
  [
    { label: '查看详情', handle: () => result.value = '已选择：查看详情' },
    { label: '编辑', handle: () => result.value = '已选择：编辑' },
  ],
  [
    { label: '显示名称列', type: 'checkbox' as const, checked: true, handle: (checked?: boolean) => result.value = `名称列：${checked ? '显示' : '隐藏'}` },
    { label: '删除', variant: 'destructive' as const, handle: () => result.value = '已选择：删除' },
  ],
]
</script>

<template>
  <div class="demo-row">
    <FaDropdown :items="items">
      <FaButton>操作</FaButton>
      <template #header>demo@example.com</template>
      <template #footer>所有操作均有可见反馈</template>
    </FaDropdown>
    <span>{{ result }}</span>
  </div>
</template>
