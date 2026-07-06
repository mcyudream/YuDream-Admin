<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'

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
const greeting = computed(() => {
  const hour = new Date().getHours()
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
}).format(new Date()))
</script>

<template>
  <div class="dashboard-card__content dashboard-profile">
    <div class="dashboard-profile__welcome">
      <div class="dashboard-profile__copy">
        <span>{{ todayText }}</span>
        <h3>{{ greeting }}，{{ displayName }}</h3>
        <p>{{ dept || '未选择部门' }} · {{ role || '未选择角色' }}</p>
      </div>
      <FaAvatar
        :src="avatar"
        :fallback="displayName.slice(0, 2)"
        class="dashboard-profile__avatar"
      >
        <FaIcon name="i-ri:user-smile-line" />
      </FaAvatar>
    </div>

    <div class="dashboard-profile__chips">
      <span>
        <FaIcon name="i-ri:shield-user-line" />
        {{ role || '未选择角色' }}
      </span>
      <span>
        <FaIcon name="i-ri:building-4-line" />
        {{ dept || '未选择部门' }}
      </span>
    </div>

    <div v-if="impersonating" class="dashboard-profile__impersonate">
      <FaIcon name="i-ri:spy-line" />
      正在以 {{ impersonatorAccount }} 发起伪装访问
    </div>
  </div>
</template>

<style scoped>
.dashboard-profile {
  display: grid;
  gap: 12px;
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
  gap: 7px;
  min-width: 0;
}

.dashboard-profile__copy span {
  color: var(--color-text-3);
  font-size: 12px;
}

.dashboard-profile__copy h3 {
  margin: 0;
  overflow: hidden;
  color: var(--color-text-1);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.22;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-profile__copy p {
  margin: 0;
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-profile__avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 64px;
  height: 64px;
  overflow: hidden;
  color: var(--color-text-1);
  font-size: 26px;
  background:
    linear-gradient(135deg, rgb(var(--primary-6) / 0.12), rgb(var(--success-6) / 0.08)),
    var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: 16px;
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
  min-height: 28px;
  padding: 0 9px;
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 12px;
  text-overflow: ellipsis;
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

@container (max-width: 320px) {
  .dashboard-profile__welcome {
    flex-direction: column-reverse;
  }

  .dashboard-profile__copy h3 {
    white-space: normal;
  }
}
</style>
