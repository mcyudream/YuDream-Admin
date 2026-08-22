<script setup lang="ts">
import { ref, useTemplateRef } from 'vue'
import { FaButton, FaInput } from '@yudream/components'

const value = ref('可清空的内容')
const password = ref('secret')
const clearMessage = ref('')
const inputRef = useTemplateRef('inputRef')

function focusInput() {
  inputRef.value?.ref?.focus()
}
</script>

<template>
  <div class="demo-row">
    <FaInput ref="inputRef" v-model="value" clearable placeholder="请输入内容" @clear="clearMessage = '已触发 clear 事件'">
      <template #start>https://</template>
    </FaInput>
    <FaInput v-model="password" type="password" placeholder="请输入密码" />
    <FaButton variant="outline" size="sm" @click="focusInput">聚焦输入框</FaButton>
    <span>{{ clearMessage || `当前值：${value ?? '空'}` }}</span>
  </div>
</template>
