<script setup lang="ts">
import { ref } from 'vue'
import { FaTabs } from '@yudream/components'

defineProps<{ variant: 'basic' | 'icon' | 'style' }>()

const basicTab = ref('profile')
const iconTab = ref('user')
const styleTab = ref('overview')
const basicList = [{ label: '资料', value: 'profile' }, { label: '账号', value: 'account' }, { label: '通知', value: 'notice' }]
const iconList = [{ icon: 'user', label: '用户', value: 'user' }, { icon: 'settings', label: '设置', value: 'setting' }, { icon: 'bell', label: '通知', value: 'notice' }]
const styleList = ['overview', 'trend', 'detail'].map(value => ({ label: { overview: '总览', trend: '趋势', detail: '明细' }[value]!, value, class: 'rounded-md data-[state=active]:bg-muted' }))
</script>

<template>
  <FaTabs v-if="variant === 'basic'" v-model="basicTab" :list="basicList" class="w-96" content-class="rounded-md bg-muted/50 p-4 text-sm">
    <template #profile>这里展示用户基础资料。</template><template #account>这里展示账号安全设置。</template><template #notice>这里展示消息通知偏好。</template>
  </FaTabs>
  <FaTabs v-else-if="variant === 'icon'" v-model="iconTab" :list="iconList" class="w-96" content-class="rounded-md bg-muted/50 p-4 text-sm">
    <template #user>当前用户：本地 mock 用户。</template><template #setting>系统设置内容。</template><template #notice>通知消息内容。</template>
  </FaTabs>
  <FaTabs v-else v-model="styleTab" :list="styleList" class="border rounded-lg p-3 w-96" list-class="gap-2 rounded-md bg-transparent p-0" content-class="rounded-md bg-muted/50 p-4 text-sm">
    <template #overview>当前数据总览。</template><template #trend>趋势分析内容。</template><template #detail>明细数据内容。</template>
  </FaTabs>
</template>
