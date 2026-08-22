<script setup lang="ts">
import { ref } from 'vue'
import { FaInput, FaPasswordStrength } from '@yudream/components'

defineProps<{ variant: 'basic' | 'custom' }>()

const password = ref('')
const customPassword = ref('')
const rules = [
  { label: '长度至少为 10 个字符', rule: (value: string) => value.length >= 10 },
  { label: '包含大写字母', rule: (value: string) => /[A-Z]/.test(value) },
  { label: '包含数字', rule: (value: string) => /\d/.test(value) },
  { label: '包含特殊字符', rule: (value: string) => /[^A-Z0-9]/i.test(value) },
]
const colorThresholds = [{ min: 0, color: 'bg-red-500' }, { min: 2, color: 'bg-yellow-500' }, { min: 4, color: 'bg-green-500' }]
</script>

<template>
  <div v-if="variant === 'basic'" class="flex flex-col gap-3 w-80"><FaInput v-model="password" type="password" placeholder="请输入密码" /><FaPasswordStrength :password="password" /></div>
  <div v-else class="flex flex-col gap-3 w-80"><FaInput v-model="customPassword" type="password" placeholder="自定义 4 条规则示例" /><FaPasswordStrength :password="customPassword" :rules="rules" :color-thresholds="colorThresholds" /></div>
</template>
