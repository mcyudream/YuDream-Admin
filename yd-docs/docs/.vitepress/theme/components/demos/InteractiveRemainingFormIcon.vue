<script setup lang="ts">
import { ref } from 'vue'
import { FaButton, FaIcon } from '@yudream/components'

const active = ref<'check' | 'close'>('check')
</script>

<template>
  <div class="demo-row text-xl">
    <FaIcon :name="active" transition />
    <FaIcon name="check" />
    <FaIcon name="/logo.svg" class="size-6" />
    <FaButton variant="outline" size="sm" @click="active = active === 'check' ? 'close' : 'check'">切换本地图标</FaButton>
  </div>
</template>
