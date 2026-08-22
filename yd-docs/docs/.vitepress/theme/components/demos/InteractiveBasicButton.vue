<script setup lang="ts">
import { ref } from 'vue'
import { FaButton } from '@yudream/components'

const saving = ref(false)
const clicks = ref(0)

async function save() {
  saving.value = true
  await new Promise(resolve => setTimeout(resolve, 600))
  clicks.value += 1
  saving.value = false
}
</script>

<template>
  <div class="demo-row">
    <FaButton variant="outline" size="sm" @click="clicks += 1">取消</FaButton>
    <FaButton :loading="saving" @click="save">{{ saving ? '保存中' : '保存' }}</FaButton>
    <span>已触发 {{ clicks }} 次</span>
  </div>
</template>
