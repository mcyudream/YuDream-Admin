<script setup lang="ts">
import { FaButton, FaHoverCard } from '@yudream/components'
</script>

<template>
  <div class="demo-row">
    <FaHoverCard class="w-72">
      <FaButton variant="outline">悬停查看 @husky</FaButton>
      <template #card>
        <div class="space-y-2 text-sm">
          <p class="font-medium">SiberianHusky</p>
          <p class="text-muted-foreground">前端工程师，专注于 Vue 3 与组件库建设。</p>
        </div>
      </template>
    </FaHoverCard>
    <FaHoverCard side="top" class="w-72">
      <FaButton variant="outline">上方卡片</FaButton>
      <template #card><p class="text-sm">空间不足时会自动调整位置。</p></template>
    </FaHoverCard>
  </div>
</template>
