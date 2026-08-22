<script setup lang="ts">
import { ref } from 'vue'
import { FaButton, FaDrawer } from '@yudream/components'

const open = ref(false)
const side = ref<'left' | 'right'>('right')
const result = ref('尚未操作')
</script>

<template>
  <div class="demo-row">
    <FaButton variant="outline" @click="side = side === 'right' ? 'left' : 'right'">切换到{{ side === 'right' ? '左侧' : '右侧' }}</FaButton>
    <FaButton @click="open = true">打开{{ side === 'right' ? '右侧' : '左侧' }}抽屉</FaButton>
    <span>{{ result }}</span>
    <FaDrawer v-model="open" :side="side" title="抽屉标题" description="这是真实的 FaDrawer 预览" :show-cancel-button="true" @confirm="result = '已确认'" @cancel="result = '已取消'">
      <p>抽屉会从当前选定方向滑出。</p>
    </FaDrawer>
  </div>
</template>
