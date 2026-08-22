<script setup lang="ts">
import { ref } from 'vue'
import { FaInput, FaLabel, FaTextarea } from '@yudream/components'

const name = ref('')
const email = ref('')
const description = ref('')
</script>

<template>
  <div class="space-y-3">
    <FaLabel label="用户名" label-width="5rem">
      <FaInput v-model="name" placeholder="请输入用户名" class="w-64" />
    </FaLabel>
    <FaLabel label="邮箱" :label-width="80">
      <FaInput v-model="email" placeholder="请输入邮箱" class="w-64" />
    </FaLabel>
    <FaLabel label="描述" label-width="5rem" class="items-start">
      <FaTextarea v-model="description" placeholder="请输入描述" class="w-64" />
    </FaLabel>
    <p class="text-sm text-muted-foreground">当前值：{{ name || '未填写' }} / {{ email || '未填写' }}</p>
  </div>
</template>
