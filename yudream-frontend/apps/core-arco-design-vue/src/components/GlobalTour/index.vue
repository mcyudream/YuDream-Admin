<script setup lang="ts">
import type { YdTourStep } from '@yudream/components'
import { YdTour } from '@yudream/components'
import { computed } from 'vue'
import { useTourStore } from '@/store/modules/tour'

// 全局引导挂载点：宿主内置与插件 manifest 声明的分步引导统一由 tour store 驱动
const tour = useTourStore()
const steps = computed<YdTourStep[]>(() => tour.activeSteps)
</script>

<template>
  <YdTour v-model:open="tour.open" :steps="steps" @finish="tour.finish()" @skip="tour.skip()" />
</template>
