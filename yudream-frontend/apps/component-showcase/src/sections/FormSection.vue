<script setup lang="ts">
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const inputBasic = ref('')
const inputClearable = ref('可清空的内容')
const inputPassword = ref('')
const textareaValue = ref('')
const selectSingle = ref('1')
const selectMultiple = ref<unknown[]>([])
const switchValue = ref(true)
const switchOff = ref(false)
const switchOn = ref(true)

const selectOptions = [
  { label: 'Vue 3', value: '1' },
  { label: 'TypeScript', value: '2' },
  { label: 'Arco Design', value: '3' },
]
</script>

<template>
  <DemoCard title="FaInput" description="基础 / 可清空 / 密码 / 禁用">
    <div class="grid max-w-md gap-3">
      <FaInput v-model="inputBasic" placeholder="请输入内容" />
      <FaInput v-model="inputClearable" clearable placeholder="可清空" />
      <FaInput v-model="inputPassword" type="password" placeholder="请输入密码" />
      <FaInput model-value="禁用状态" disabled />
    </div>
  </DemoCard>

  <DemoCard title="FaTextarea" description="基础 / 禁用">
    <div class="grid max-w-md gap-3">
      <FaTextarea v-model="textareaValue" :rows="3" placeholder="请输入多行内容" />
      <FaTextarea model-value="禁用状态的多行文本" :rows="2" disabled />
    </div>
  </DemoCard>

  <DemoCard title="FaSelect" description="单选 / 多选 / 禁用">
    <div class="grid max-w-md gap-3">
      <FaSelect v-model="selectSingle" :options="selectOptions" />
      <FaSelect v-model="selectMultiple" :options="selectOptions" multiple />
      <FaSelect model-value="1" :options="selectOptions" disabled />
    </div>
  </DemoCard>

  <DemoCard title="FaSwitch" description="基础 / 禁用">
    <div class="demo-row">
      <FaSwitch v-model="switchValue" />
      <FaSwitch v-model="switchOff" disabled />
      <FaSwitch v-model="switchOn" disabled />
    </div>
  </DemoCard>
</template>
