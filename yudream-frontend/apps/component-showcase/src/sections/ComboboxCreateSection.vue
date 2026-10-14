<script setup lang="ts">
import { YdCombobox } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '../components/DemoCard.vue'

const selected = ref<string | number>()
const options = ref([
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
])
const created = ref<string>()

function createProvider(value: string) {
  options.value = [...options.value, { label: value, value }]
  selected.value = value
  created.value = value
}
</script>

<template>
  <DemoCard title="创建编码" description="搜索未匹配时可选择使用输入编码；创建事件由业务处理并回填受控值。">
    <YdCombobox v-model="selected" :options="options" allow-create @create="createProvider" />
    <p v-if="created" role="status">
      已创建并选择：{{ created }}
    </p>
  </DemoCard>
</template>
