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
  padding-top: 12px;
}

.dashboard-profile__welcome {
  display: flex;
  gap: 16px;
  align-items: flex-start;
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
  color: var(--yd-dashboard-feature-muted);
}

.dashboard-profile__copy h3 {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: clamp(24px, 7cqi, 34px);
  font-weight: 700;
  line-height: 1.35;
  color: var(--yd-dashboard-feature-text);
  letter-spacing: -0.04em;
  overflow-wrap: anywhere;
  white-space: normal;
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
  width: 48px;
  height: 48px;
  margin-left: auto;
  overflow: hidden;
  font-size: 26px;
  color: var(--color-text-1);
  background: color-mix(in srgb, var(--yd-dashboard-feature-text) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--yd-dashboard-feature-text) 24%, transparent);
  border-radius: var(--yd-surface-radius, 18px);
}

.dashboard-profile__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  min-width: 0;
  padding-top: 14px;
  border-top: 1px solid color-mix(in srgb, var(--yd-dashboard-feature-text) 18%, transparent);
}

.dashboard-profile__chips span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  max-width: 100%;
  min-height: 30px;
  padding: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--yd-dashboard-feature-muted);
  white-space: nowrap;
  background: transparent;
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
