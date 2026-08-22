<script setup lang="ts">
import { ref } from 'vue'
import { FaButton, FaModal } from '@yudream/components'

const visible = ref(false)
const result = ref('尚未操作')
</script>

<template>
  <div class="demo-row">
    <FaButton @click="visible = true">打开弹窗</FaButton>
    <span>{{ result }}</span>
    <FaModal v-model="visible" title="编辑资源" description="这是真实的 FaModal 预览" :show-cancel-button="true" @confirm="result = '已确认'" @cancel="result = '已取消'">
      <p>点击底部按钮，事件结果会显示在触发按钮旁。</p>
    </FaModal>
  </div>
</template>
