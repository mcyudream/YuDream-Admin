<script setup lang="ts">
import { ref } from 'vue'
import { FaInputOTP } from '@yudream/components'

const basic = ref('')
const short = ref('')
const digits = ref('')
const grouped = ref('')
const eventMessage = ref('等待输入完整验证码')
</script>

<template>
  <div class="space-y-5">
    <div class="space-y-2">
      <FaInputOTP v-model="basic" />
      <p class="text-sm text-muted-foreground">当前值：{{ basic || '未输入' }}</p>
    </div>
    <div class="space-y-2">
      <FaInputOTP v-model="short" :length="4" />
      <p class="text-sm text-muted-foreground">四位值：{{ short || '未输入' }}</p>
    </div>
    <div class="space-y-2">
      <FaInputOTP v-model="digits" pattern="only-digits" />
      <p class="text-sm text-muted-foreground">仅数字：{{ digits || '未输入' }}</p>
    </div>
    <div class="space-y-2">
      <FaInputOTP v-model="grouped" :separator="[2, 3]" @input="value => eventMessage = `输入：${value}`" @complete="value => eventMessage = `已完成：${value}`" />
      <p class="text-sm text-muted-foreground">{{ eventMessage }}</p>
    </div>
  </div>
</template>
