<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'
import type { RedisMonitor } from '@/api/modules/system-monitor'
import apiMonitor from '@/api/modules/system-monitor'
import { toneTextClass } from './tone'

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
  <div class="dashboard-card__content dashboard-stat">
    <div class="dashboard-stat__grid">
      <div class="dashboard-stat__item">
        <span>在线人数</span>
        <strong :class="toneTextClass(card.tone)">{{ canReadOnline ? onlineCount ?? '-' : '无权限' }}</strong>
      </div>
      <div class="dashboard-stat__item">
        <span>Redis</span>
        <strong :class="toneTextClass(card.tone)">{{ redisStatus }}</strong>
      </div>
      <div class="dashboard-stat__item">
        <span>Key 数</span>
        <strong>{{ redis?.dbSize ?? '-' }}</strong>
      </div>
      <div class="dashboard-stat__item">
        <span>内存</span>
        <strong>{{ redis?.usedMemory || '-' }}</strong>
      </div>
    </div>

    <div class="dashboard-stat__footer">
      <span v-if="loading">正在刷新监控数据</span>
      <span v-else>{{ message || redis?.message || '监控数据来自系统实时接口' }}</span>
      <FaButton v-if="card.actionPath" size="sm" variant="outline" @click="onOpen?.(card)">
        <FaIcon name="i-ri:arrow-right-line" />
        查看
      </FaButton>
    </div>
  </div>
</template>

<style scoped>
.dashboard-stat {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dashboard-stat__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.dashboard-stat__item {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding-top: 8px;
  border-top: 1px solid var(--color-border-1);
}

.dashboard-stat__item span,
.dashboard-stat__footer span {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.dashboard-stat__item strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--color-text-1);
  white-space: nowrap;
}

.dashboard-stat__footer {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  margin-top: auto;
}

.dashboard-stat__footer span {
  flex: 1;
}

@container (max-width: 320px) {
  .dashboard-stat__grid {
    grid-template-columns: 1fr;
  }
}
</style>
