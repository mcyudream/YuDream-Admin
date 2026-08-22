<script setup lang="ts">
import { ref } from 'vue'
import { FaSelect } from '@yudream/components'

const status = ref<string>()
const changedValue = ref('尚未选择')
const options = [
  { label: '启用', value: 'enabled' },
  { label: '禁用', value: 'disabled' },
  { label: '归档', value: 'archived' },
]
</script>

<template>
  <div class="demo-row">
    <FaSelect v-model="status" :options="options" placeholder="请选择状态" @change="value => changedValue = String(value ?? '未选择')" />
    <span>当前值：{{ changedValue }}</span>
  </div>
</template>
