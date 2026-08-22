<script setup lang="ts">
import { ref } from 'vue'
import { FaButton, FaResponsiveTable } from '@yudream/components'

defineProps<{ variant: 'basic' | 'card' }>()

const data = ref([{ id: '1932574829104', name: 'husky', phone: '138****0000' }, { id: '1932574829105', name: 'momo', phone: '139****0001' }])
const columns = [{ accessorKey: 'id', header: 'ID' }, { accessorKey: 'name', header: '用户名' }, { accessorKey: 'phone', header: '手机号' }]
const detail = ref('')
</script>

<template>
  <FaResponsiveTable v-if="variant === 'basic'" :columns="columns" :data="data" mobile-breakpoint="(max-width: 0px)" border stripe row-key="id" />
  <div v-else class="flex flex-col gap-3">
    <FaResponsiveTable :columns="columns" :data="data" mobile-breakpoint="(max-width: 100000px)" row-key="id">
      <template #card="{ row }"><div class="border rounded-lg p-4"><div class="font-medium">{{ row.name }}</div><div class="text-sm text-muted-foreground">{{ row.phone }}</div><FaButton size="sm" class="mt-2" @click="detail = `正在查看 ${row.id} 的本地详情`">详情</FaButton></div></template>
    </FaResponsiveTable>
    <span class="text-sm text-muted-foreground">{{ detail || '此演示固定为卡片断点，按钮不访问 API。' }}</span>
  </div>
</template>
