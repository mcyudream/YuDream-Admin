<script setup lang="ts">
import { ref } from 'vue'
import { FaSwitch } from '@yudream/components'

const enabled = ref(true)
const guarded = ref(false)
const message = ref('点击带图标的开关')

function beforeChange() {
  message.value = 'beforeChange 已放行本次切换'
  return true
}
</script>

<template>
  <div class="demo-row">
    <FaSwitch v-model="enabled" on-icon="i-ri:sun-line" off-icon="i-ri:moon-line" />
    <span>{{ enabled ? '已开启' : '已关闭' }}</span>
    <FaSwitch v-model="guarded" :before-change="beforeChange" />
    <span>{{ message }}</span>
  </div>
</template>
