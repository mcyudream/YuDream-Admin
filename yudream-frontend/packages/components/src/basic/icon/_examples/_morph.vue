<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { shallowRef } from 'vue'
import FaButton from '../../button/index.vue'
import FaIcon from '../index.vue'

const active = shallowRef(false)

const pairs = [
  ['morph:menu', 'morph:x'],
  ['morph:sun', 'morph:moon'],
  ['morph:play', 'morph:pause'],
  ['morph:eye', 'morph:eye-off'],
  ['morph:heart', 'morph:star'],
] as const
</script>

<template>
  <div class="flex flex-wrap gap-6 items-end">
    <div
      v-for="pair in pairs"
      :key="pair[0]"
      class="px-6 py-4 border rounded-lg flex flex-col gap-3 items-center"
    >
      <span class="text-xs text-muted-foreground font-medium">{{ pair[0].replace('morph:', '') }} ↔ {{ pair[1].replace('morph:', '') }}</span>
      <FaIcon :name="active ? pair[1] : pair[0]" class="size-8 cursor-pointer" @click="active = !active" />
    </div>
    <div class="px-6 py-4 border rounded-lg flex flex-col gap-3 items-center">
      <span class="text-xs text-muted-foreground font-medium">回退 Iconify</span>
      <FaIcon name="lucide:github" class="size-8" />
      <span class="text-xs text-muted-foreground">lucide 已移除的品牌图标</span>
    </div>
    <FaButton @click="active = !active">
      切换图标（弹簧变形）
    </FaButton>
  </div>
</template>
