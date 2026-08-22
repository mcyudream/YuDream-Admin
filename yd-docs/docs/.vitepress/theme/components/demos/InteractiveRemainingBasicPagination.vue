<script setup lang="ts">
import { computed, ref } from 'vue'
import { FaPagination } from '@yudream/components'

const page = ref(1)
const size = ref(10)
const compactPage = ref(1)
const compactSize = ref(20)
const message = computed(() => `当前第 ${page.value} 页，每页 ${size.value} 条`)
</script>

<template>
  <div class="space-y-5">
    <FaPagination v-model:page="page" v-model:size="size" :total="128" @page-change="value => page = value" @size-change="value => size = value" />
    <p class="text-sm text-muted-foreground">{{ message }}</p>
    <FaPagination v-model:page="compactPage" v-model:size="compactSize" :total="1024" :sizes="[20, 50, 100]" layout="total, sizes, ->, pager, jumper" />
  </div>
</template>
