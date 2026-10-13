<script setup lang="ts">
import { computed } from 'vue'
import './extended.css'

const props = withDefaults(defineProps<{
  value: number
  format?: Intl.NumberFormatOptions
  locales?: string
  prefix?: string
  suffix?: string
}>(), { locales: 'zh-CN' })
const label = computed(() => `${props.prefix || ''}${new Intl.NumberFormat(props.locales, props.format).format(props.value)}${props.suffix || ''}`)
</script>

<template>
  <span class="yd-number-flow" role="img" :aria-label="label"><span v-for="(char, index) in label" :key="label.length - index" aria-hidden="true" class="yd-digit"><span v-if="/\d/.test(char)" class="yd-digit-reel yd-animated" :style="{ transform: `translateY(-${Number(char) * 10}%)` }"><span v-for="digit in 10" :key="digit">{{ digit - 1 }}</span></span><span v-else>{{ char }}</span></span></span>
</template>

<style scoped>
.yd-number-flow {
  display: inline-flex;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.yd-digit {
  display: inline-block;
  height: 1.2em;
  overflow: hidden;
}

.yd-digit-reel {
  display: flex;
  flex-direction: column;
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease-spring);
}

.yd-digit-reel > span {
  height: 1.2em;
}
</style>
