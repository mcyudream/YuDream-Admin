<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'
import { useNow } from '@vueuse/core'

interface Props {
  card: DashboardCard
  account?: string
  dept?: string
  role?: string
  impersonating?: boolean
  impersonatorAccount?: string
  avatar?: string
}

const props = defineProps<Props>()

const displayName = computed(() => props.account || '朋友')
const now = useNow({ interval: 60_000 })
const greeting = computed(() => {
  const hour = now.value.getHours()
  if (hour < 6) {
    return '夜深了'
  }
  if (hour < 12) {
    return '早上好'
  }
  if (hour < 18) {
    return '下午好'
  }
  return '晚上好'
})
const todayText = computed(() => new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(now.value))
</script>

<template>
  <div class="dashboard-card__content dashboard-profile">
    <div class="dashboard-profile__welcome">
      <div class="dashboard-profile__copy">
        <span class="dashboard-profile__date"><FaIcon name="morph:calendar-days" />{{ todayText }}</span>
        <h3>{{ greeting }}，{{ displayName }}</h3>
        <p>欢迎回来，从这里开始今天的工作。</p>
      </div>
      <FaAvatar
        :src="avatar || ''"
        :fallback="displayName.slice(0, 2)"
        class="dashboard-profile__avatar"
      >
        <FaIcon name="morph:circle-user-round" />
      </FaAvatar>
    </div>

    <div class="dashboard-profile__chips">
      <span>
        <FaIcon name="morph:shield-user" />
        {{ role || '未选择角色' }}
      </span>
      <span>
        <FaIcon name="morph:building" />
        {{ dept || '未选择部门' }}
      </span>
    </div>

    <div v-if="impersonating" class="dashboard-profile__impersonate">
      <FaIcon name="morph:venetian-mask" />
      正在以 {{ impersonatorAccount }} 发起伪装访问
    </div>
  </div>
</template>

<style scoped>
.dashboard-profile {
  display: grid;
  gap: 18px;
  align-content: space-between;
}

.dashboard-profile__welcome {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
}

.dashboard-profile__copy {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.dashboard-profile__date {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.dashboard-profile__copy h3 {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: clamp(22px, 4.8cqi, 30px);
  font-weight: 700;
  line-height: 1.35;
  color: var(--color-text-1);
  letter-spacing: -0.04em;
  white-space: nowrap;
}

.dashboard-profile__copy p {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  line-height: 1.45;
  color: var(--color-text-3);
  white-space: nowrap;
}

.dashboard-profile__avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 68px;
  height: 68px;
  overflow: hidden;
  font-size: 26px;
  color: var(--color-text-1);
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius, 18px);
  box-shadow: 0 5px 14px color-mix(in srgb, var(--color-text-1) 6%, transparent);
}

.dashboard-profile__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.dashboard-profile__chips span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  max-width: 100%;
  min-height: 30px;
  padding: 0 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-2);
  white-space: nowrap;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-1);
  border-radius: 999px;
}

.dashboard-profile__chips :deep(.fa-icon) {
  flex: 0 0 auto;
}

.dashboard-profile__impersonate {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px 10px;
  font-size: 12px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 6px;
}

@container (max-width: 280px) {
  .dashboard-profile__welcome {
    gap: 10px;
  }

  .dashboard-profile__avatar {
    width: 44px;
    height: 44px;
  }

  .dashboard-profile__copy h3 {
    overflow-wrap: anywhere;
    white-space: normal;
  }
}
</style>
