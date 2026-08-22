<script setup lang="ts">
import { ref } from 'vue'
import { FaButton, FaScrollArea } from '@yudream/components'

const scrollArea = ref<{ scrollTo: (position: number, behavior?: ScrollBehavior) => void } | null>(null)
const scrollPosition = ref(0)
const items = Array.from({ length: 20 }, (_, index) => index + 1)

function scrollToBottom() {
  scrollArea.value?.scrollTo(1000, 'smooth')
}
</script>

<template>
  <div class="space-y-5">
    <div class="demo-row items-start">
      <FaScrollArea ref="scrollArea" mask class="h-36 w-48 border rounded-md" @on-scroll="event => scrollPosition = Math.round((event.target as HTMLElement).scrollTop)">
        <div v-for="item in items" :key="item" class="p-3 text-sm">第 {{ item }} 项</div>
      </FaScrollArea>
      <div class="space-y-2 text-sm text-muted-foreground">
        <FaButton size="sm" variant="outline" @click="scrollToBottom">滚动到底部</FaButton>
        <p>滚动位置：{{ scrollPosition }}</p>
      </div>
    </div>
    <FaScrollArea horizontal class="w-80 border rounded-md">
      <div class="flex">
        <div v-for="item in items" :key="item" class="h-16 w-16 shrink-0 flex items-center justify-center border-r text-sm">{{ item }}</div>
      </div>
    </FaScrollArea>
  </div>
</template>
