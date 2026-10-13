<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'
import type { RedisMonitor } from '@/api/modules/system-monitor'
import apiMonitor from '@/api/modules/system-monitor'
import DashboardMetrics from './DashboardMetrics.vue'

interface Props {
  card: DashboardCard
  permissions: string[]
  onOpen?: (card?: DashboardCard) => void
}

const props = defineProps<Props>()

const loading = ref(false)
const onlineCount = ref<number | null>(null)
const redis = ref<RedisMonitor | null>(null)
const message = ref('')

const canReadRedis = computed(() => hasPermission('system:monitor:redis:view'))
const canReadOnline = computed(() => hasPermission('system:monitor:online:view'))
const redisStatus = computed(() => {
  if (!canReadRedis.value) {
    return '无权限'
  }
  if (!redis.value) {
    return '-'
  }
  return redis.value.connected ? '正常' : '离线'
})
const metrics = computed(() => [
  { label: '在线人数', value: canReadOnline.value ? onlineCount.value : '无权限' },
  { label: 'Redis', value: redisStatus.value },
  { label: 'Key 数', value: canReadRedis.value ? redis.value?.dbSize : '无权限' },
  { label: '内存', value: canReadRedis.value ? redis.value?.usedMemory : '无权限', compact: true },
])

onMounted(loadStats)

async function loadStats() {
  loading.value = true
  message.value = ''
  try {
    const tasks: Promise<void>[] = []
    if (canReadOnline.value) {
      tasks.push(apiMonitor.onlineUsers({ limit: 1000 }).then((res) => {
        onlineCount.value = res.data.length
      }))
    }
    if (canReadRedis.value) {
      tasks.push(apiMonitor.redis({ limit: 5 }).then((res) => {
        redis.value = res.data
      }))
    }
    const results = await Promise.allSettled(tasks)
    if (results.some(item => item.status === 'rejected')) {
      message.value = '部分监控数据暂不可用'
    }
  }
  finally {
    loading.value = false
  }
}

function hasPermission(permission: string) {
  return props.permissions.includes('*') || props.permissions.includes(permission)
}
</script>

<template>
  <DashboardMetrics
    :metrics="metrics"
    :loading="loading"
    :message="loading ? '正在刷新监控数据' : (message || redis?.message || '监控数据来自系统实时接口')"
    :show-action="!!card.actionPath"
    action-label="查看"
    @open="onOpen?.(card)"
  />
</template>
