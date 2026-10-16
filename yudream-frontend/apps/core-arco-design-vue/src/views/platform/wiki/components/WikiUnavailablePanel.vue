<script setup lang="ts">
import type { WikiUnavailableCapability } from '../wiki-utils'

// 能力待接入：只陈述缺口与所需契约，不放置任何假入口或伪数据
defineProps<{
  title: string
  icon?: string
  capability: WikiUnavailableCapability
}>()
</script>

<template>
  <div class="unavailable">
    <YdScrollArea class="unavailable__scroll">
      <div class="unavailable__inner">
        <YdResult
          status="info"
          :title="`${title}：能力待接入`"
          :description="capability.reason"
        />
        <section class="unavailable__contracts" aria-label="待接入的后端契约">
          <h3 class="unavailable__title">
            <YdIcon :name="icon || 'morph:plug-zap'" /> 需要补齐的后端契约
          </h3>
          <ul class="unavailable__list">
            <li v-for="contract in capability.contracts" :key="contract" class="unavailable__item">
              <YdIcon name="morph:dot" class="unavailable__dot" />
              <span>{{ contract }}</span>
            </li>
          </ul>
          <p class="unavailable__note">
            在接口就绪前，这里保持明确的不可用状态：不隐藏入口、也不伪造成员或评论数据。
          </p>
        </section>
      </div>
    </YdScrollArea>
  </div>
</template>

<style scoped>
.unavailable {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.unavailable__scroll {
  height: 100%;
}

.unavailable__inner {
  display: grid;
  gap: 16px;
  max-width: 720px;
  padding: 24px 16px 40px;
  margin: 0 auto;
}

.unavailable__contracts {
  display: grid;
  gap: 8px;
  padding: 16px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.unavailable__title {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.unavailable__list {
  display: grid;
  gap: 6px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.unavailable__item {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-2);
}

.unavailable__dot {
  flex-shrink: 0;
  margin-top: 5px;
  font-size: 12px;
  color: var(--color-text-3);
}

.unavailable__note {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
