<script setup lang="ts">
import FaKbdGroup from '../../kbd-group/index.vue'
// 组件实际使用时无需手动导入，框架会自动导入
import FaKbd from '../index.vue'
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex gap-3 items-center">
      <FaKbd size="xs">
        Esc
      </FaKbd>
      <FaKbd size="sm">
        Ctrl
      </FaKbd>
      <FaKbd>Shift</FaKbd>
      <FaKbd size="lg">
        Enter
      </FaKbd>
      <FaKbd size="xl">
        Space
      </FaKbd>
    </div>
    <p class="text-sm">
      按 <FaKbd size="sm">
        F
      </FaKbd> 搜索
    </p>
    <FaKbdGroup>
      <FaKbd>Ctrl</FaKbd>
      <span>+</span>
      <FaKbd>Shift</FaKbd>
      <span>+</span>
      <FaKbd>Del</FaKbd>
    </FaKbdGroup>
    <FaKbdGroup>
      <FaKbd>↑</FaKbd>
      <FaKbd>←</FaKbd>
      <FaKbd>↓</FaKbd>
      <FaKbd>→</FaKbd>
    </FaKbdGroup>
  </div>
</template>
